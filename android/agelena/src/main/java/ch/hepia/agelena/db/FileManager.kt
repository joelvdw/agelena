/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2020  VON DER WEID Joël
 *
 * This file is part of Agelena.
 *
 * Agelena is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Agelena is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.hepia.agelena.db

import android.content.Context
import android.util.Log
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Stream
import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Singleton class that manages blocks persistence in private storage
 *
 * Uses a thread and a queue to write block asynchronously
 *
 * @since 0.1
 */
internal class FileManager(private val context: Context) {
    private class Node(val messageId: Int, val seqNumber: Int, val block: ByteArray, val wait: Continuation<Boolean>? = null)
    private val fileQueue: LinkedBlockingQueue<Node> = LinkedBlockingQueue()
    @Volatile private var finish: Boolean = false

    /**
     * Thread that write queued files to persistent storage
     */
    private val writer = thread(start = true) {
        while (!finish) {
            try {
                fileQueue.take().let { node ->
                    val filename = buildFilename(node.messageId, node.seqNumber)
                    context.openFileOutput(filename, Context.MODE_PRIVATE).use {
                        it.write(node.block)
                        Log.d(TAG, "Wrote file : $filename, ${node.block.size} bytes")
                    }
                    node.wait?.resume(true)
                }
            } catch (_: InterruptedException) { }
        }
    }

    /**
     * Write a message [block] to persistent storage
     *
     * [messageId] and [seqNumber] are used to identify the block
     */
    fun writeBlock(messageId: Int, seqNumber: Int, block: ByteArray) {
        fileQueue.put(Node(messageId, seqNumber, block))
    }

    /**
     * Write a message [block] to persistent storage and wait for the write to finish
     *
     * [messageId] and [seqNumber] are used to identify the block
     */
    fun writeBlockSync(messageId: Int, seqNumber: Int, block: ByteArray) {
        runBlocking { suspendCoroutine<Boolean> {
            fileQueue.put(Node(messageId, seqNumber, block, it))
        } }
    }

    /**
     * Remove a message block identified by its [messageId] and [seqNumber] from the persistent storage
     */
    fun removeBlock(messageId: Int, seqNumber: Int) {
        context.deleteFile(buildFilename(messageId, seqNumber))
        Log.d(TAG, "Deleted file : ${buildFilename(messageId, seqNumber)}")
    }

    /**
     * Remove all blocks identified by [messageId]
     */
    fun removeMessage(messageId: Int) {
        context.fileList().forEach {
            if (it.contains(buildPartialFilename(messageId))) {
                context.deleteFile(it)
                Log.d(TAG, "Deleted file : $it")
            }
        }
    }

    /**
     * Remove all saved blocks
     */
    fun removeAll() {
        context.fileList().forEach {
            context.deleteFile(it)
            Log.d(TAG, "Deleted file : $it")
        }
    }

    /**
     * Read a block identified by its [messageId] and [seqNumber]
     */
    fun readBlock(messageId: Int, seqNumber: Int): ByteArray? {
        return try {
            val s = context.openFileInput(buildFilename(messageId, seqNumber))
            s?.readBytes()
        } catch (_: FileNotFoundException) {
            null
        }
    }

    /**
     * Read all message blocks identified by [messageId] until [nbBlocks]
     */
    fun readMessage(messageId: Int, nbBlocks: Int): InputStream? {
        return try {
            if (nbBlocks > 0) {
                var stream: InputStream? = null
                (1..nbBlocks).forEach {
                    val s = context.openFileInput(buildFilename(messageId, it))
                    stream = if (stream == null) s else SequenceInputStream(stream, s)
                }
                stream
            } else {
                null
            }
        } catch (_: FileNotFoundException) {
            null
        }
    }

    companion object : SingletonHolder<FileManager, Context>(::FileManager) {
        private const val TAG = "FileManager"

        private fun buildFilename(messageId: Int, seqNumber: Int): String {
            return "${messageId}_${seqNumber}"
        }

        private fun buildPartialFilename(messageId: Int): String {
            return "${messageId}_"
        }
    }
}