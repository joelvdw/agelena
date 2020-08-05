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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import ch.hepia.agelena.Agelena
import ch.hepia.agelena.Device
import ch.hepia.agelena.GlobalConfig
import ch.hepia.agelena.message.Block
import ch.hepia.agelena.utils.MessageKeyPair
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

/**
 * Singleton class that manages SQLite database access to store messages, blocks and devices
 *
 * @since 0.1
 */
internal class SQLite private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_DEVICES)
        db.execSQL(SQL_CREATE_MESSAGES)
        db.execSQL(SQL_CREATE_BLOCKS)
        db.execSQL(SQL_CREATE_ACKS)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_DEVICES)
        db.execSQL(SQL_DELETE_MESSAGES)
        db.execSQL(SQL_DELETE_BLOCKS)
        db.execSQL(SQL_DELETE_ACKS)

        onCreate(db)
    }
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    /**
     * Delete the database with its data and recreate it
     */
    fun clearDatabase() {
        onUpgrade(writableDatabase, DATABASE_VERSION, DATABASE_VERSION)
    }

    /**
     * Insert a [device] in database
     */
    fun insertDevice(device: Device) {
        val db = writableDatabase

        val d = getDevice(device.id)
        if (d == null) {
            val values = ContentValues()
            values.put("device_id", device.id)
            values.put("device_pub_key", MessageKeyPair.publicKeyToString(device.publicKey))

            db.insert("devices", null, values)
        } else if (device.publicKey != d.publicKey) {
            val keyVal = ContentValues()
            keyVal.put("device_pub_key", MessageKeyPair.publicKeyToString(device.publicKey))
            db.update("devices", keyVal,"device_id = ?", arrayOf(device.id.toString()))
        }
    }

    /**
     * Insert a message with [messageId] and [receiverId] in database
     * [receiverId] must be null if it's a broadcast message
     */
    private fun insertMessage(messageId: Int, receiverId: Int?) {
        val db = writableDatabase

        if (!messageExists(messageId)) {
            val values = ContentValues()
            values.put("message_id", messageId)
            if (receiverId != null) {
                values.put("receiver_id", receiverId)
            }
            db.insert("messages", null, values)
        }
    }

    /**
     * Insert a block in database
     * @param messageId Message from which the block comes
     * @param seqNumber Sequence number of the block
     * @return if the block was inserted or not
     */
    fun insertBlock(messageId: Int, receiverId: Int?, seqNumber: Int, isLast: Boolean, ttl: Int? = null): Boolean {
        val db = writableDatabase

        if (blockExists(messageId, seqNumber)) return false
        if (messageExists(messageId)) {
            if (getMessageReceiver(messageId) != receiverId) return false
        } else {
            insertMessage(messageId, receiverId)
        }

        val values = ContentValues()
        values.put("message_id", messageId)
        values.put("seq_num", seqNumber)
        if (ttl != null) values.put("ttl", ttl)
        db.insert("blocks", null, values)

        if (isLast) {
            val msgVal = ContentValues()
            msgVal.put("message_size", seqNumber)
            db.update("messages", msgVal, "message_id = ?", arrayOf(messageId.toString()))
        }

        // Test if all blocks have been received
        var all = false
        val s = getMessageSize(messageId)
        if (s != null && s > 0) {
            val cursor = db.rawQuery("SELECT COUNT(seq_num) as cnt FROM blocks WHERE message_id = ?", arrayOf(messageId.toString()))

            if (cursor.count > 0 && cursor.moveToFirst()) {
                val n = cursor.getInt(cursor.getColumnIndex("cnt"))
                all = s == n
            }
            cursor.close()
        }

        if (all) {
            val msgVal = ContentValues()
            msgVal.put("is_complete", 1)
            db.update("messages", msgVal, "message_id = ?", arrayOf(messageId.toString()))
        }

        return true
    }

    /**
     * Insert a ack for the given [messageId]
     * @param waited If the ack is waited or received
     */
    fun insertAck(messageId: Int, waited: Boolean): Boolean {
        val db = writableDatabase

        return if (!ackExists(messageId)) {
            val values = ContentValues()
            values.put("message_id", messageId)
            values.put("waited", if (waited) 1 else 0)
            db.insert("acks", null, values)
            true
        } else false
    }

    /**
     * Return the [Device] matching the [deviceId] if it exists
     */
    fun getDevice(deviceId: Int): Device? {
        val db = readableDatabase

        return try {
            val cursor = db.query("devices", arrayOf("device_pub_key"),
                "device_id = ?", arrayOf(deviceId.toString()),
                null, null, null)

            var d: Device? = null
            if (cursor.count > 0 && cursor.moveToFirst()) {
                val pk = cursor.getString(cursor.getColumnIndex("device_pub_key"))
                d = Device(deviceId, MessageKeyPair.publicKeyFromString(pk),false)
            }
            cursor.close()

            d
        } catch (e: SQLiteException) {
            null
        }
    }

    /**
     * Return if a message matching [messageId] is present in database
     */
    fun messageExists(messageId: Int): Boolean {
        val db = readableDatabase

        return try {
            val cursor = db.query("messages", arrayOf("message_id"),
                "message_id = ?", arrayOf(messageId.toString()),
                null, null, null)

            val e = (cursor.count > 0 && cursor.moveToFirst())
            cursor.close()

            e
        } catch (e: SQLiteException) {
            false
        }
    }

    /**
     * Return if a block matching [messageId] and [seqNumber] is present in database
     */
    fun blockExists(messageId: Int, seqNumber: Int): Boolean {
        val db = readableDatabase

        return try {
            val cursor = db.query("blocks", arrayOf("message_id"),
                "message_id = ? AND seq_num = ?", arrayOf(messageId.toString(), seqNumber.toString()),
                null, null, null)

            val e = (cursor.count > 0 && cursor.moveToFirst())
            cursor.close()

            e
        } catch (e: SQLiteException) {
            false
        }
    }

    /**
     * Return if a ack matching [messageId] is present in database
     */
    fun ackExists(messageId: Int): Boolean {
        val db = readableDatabase

        return try {
            val cursor = db.query("acks", arrayOf("message_id"),
                "message_id = ?", arrayOf(messageId.toString()),
                null, null, null)

            val e = (cursor.count > 0 && cursor.moveToFirst())
            cursor.close()

            e
        } catch (e: SQLiteException) {
            false
        }
    }


    /**
     * Return if the message with [messageId] has all its blocks in database
     */
    fun hasAllBlocks(messageId: Int): Boolean {
        val db = readableDatabase

        return try {
            var all = false
            val cursor = db.rawQuery("SELECT is_complete FROM messages WHERE message_id = ?", arrayOf(messageId.toString()))

            if (cursor.count > 0 && cursor.moveToFirst()) {
                val n = cursor.getInt(cursor.getColumnIndex("is_complete"))
                all = n == 1
            }
            cursor.close()

            all
        } catch (e: SQLiteException) {
            false
        }
    }

    /**
     * Get all blocks that hasn't expired and whose ttl > 0
     */
    fun getAllSendableBlocks(selfId: Int): List<Block> {
        val db = readableDatabase
        val list = mutableListOf<Block>()

        return try {
            val cursor = db.rawQuery(
                "SELECT b.message_id, b.seq_num, b.ttl, m.receiver_id, m.message_size FROM blocks b LEFT JOIN messages m ON b.message_id=m.message_id WHERE b.ttl > 0 AND (julianday('now') - julianday(b.reception_date)) < ? AND (m.receiver_id IS NULL OR m.receiver_id <> ?) ORDER BY b.reception_date DESC LIMIT ?",
                arrayOf(GlobalConfig.BLOCK_PERSISTENCE_DAYS.toString(), selfId.toString(), GlobalConfig.MAX_BLOCKS.toString())
            )

            while (cursor.moveToNext()) {
                val receiver = if (cursor.getColumnIndex("receiver_id") != -1) {
                    val r = cursor.getInt(cursor.getColumnIndex("receiver_id"))
                    if (r == 0) null else r
                } else null

                val seq = cursor.getInt(cursor.getColumnIndex("seq_num"))
                list.add(Block(
                    cursor.getInt(cursor.getColumnIndex("message_id")),
                    seq,
                    cursor.getInt(cursor.getColumnIndex("ttl")),
                    receiver,
                    seq == cursor.getInt(cursor.getColumnIndex("message_size"))
                ))
            }
            cursor.close()

            list
        } catch (e: SQLiteException) {
            list
        }
    }

    /**
     * Get all messages whose reception date is older than persistence duration
     */
    fun getAllRemovableBlocks(): List<Pair<Int, Int>> {
        val db = readableDatabase
        val list = mutableListOf<Pair<Int, Int>>()

        return try {
            val cursor = db.rawQuery(
                "SELECT b.message_id, b.seq_num, m.receiver_id FROM blocks b LEFT JOIN messages m ON b.message_id=m.message_id WHERE (m.receiver_id = ? AND m.is_complete = 1) OR ((julianday('now') - julianday(b.reception_date)) >= ?)",
                arrayOf(Agelena.getUserID().toString(), GlobalConfig.BLOCK_PERSISTENCE_DAYS.toString())
            )

            while (cursor.moveToNext()) {
                list.add(Pair(cursor.getInt(cursor.getColumnIndex("message_id")), cursor.getInt(cursor.getColumnIndex("seq_num"))))
            }
            cursor.close()

            list
        } catch (e: SQLiteException) {
            list
        }
    }

    /**
     * Return the number of blocks of the message matching [messageId]
     * A size of 0 means that the last block hasn't been received yet
     */
    fun getMessageSize(messageId: Int): Int? {
        val db = readableDatabase

        return try {
            val cursor = db.query(
                "messages", arrayOf("message_size"),
                "message_id = ?", arrayOf(messageId.toString()),
                null, null, null
            )

            var nb: Int? = null
            if (cursor.count > 0 && cursor.moveToFirst()) {
                nb = cursor.getInt(cursor.getColumnIndex("message_size"))
            }
            cursor.close()

            nb
        } catch (e: SQLiteException) {
            null
        }
    }

    /**
     * Get the message receiver from the message with [messageId]
     * Return null if the message was not found or if it's a broadcast message
     */
    fun getMessageReceiver(messageId: Int): Int? {
        val db = readableDatabase

        return try {
            val cursor = db.query("messages", arrayOf("receiver_id"),
                "message_id = ?", arrayOf(messageId.toString()),
                null, null, null)

            var r: Int? = null
            if (cursor.count > 0 && cursor.moveToFirst()) {
                r = cursor.getInt(cursor.getColumnIndex("receiver_id"))
                if (r == 0) r = null
            }
            cursor.close()

            r
        } catch (e: SQLiteException) {
            null
        }
    }

    /**
     * Get all acks that hasn't expired and are sendables
     */
    fun getSendableAcks(): List<Int> {
        val db = readableDatabase
        val list = mutableListOf<Int>()

        return try {
            val cursor = db.rawQuery(
                "SELECT message_id FROM acks WHERE (julianday('now') - julianday(creation_date)) < ? AND waited = 0",
                arrayOf(GlobalConfig.BLOCK_PERSISTENCE_DAYS.toString())
            )

            while (cursor.moveToNext()) {
                list.add(cursor.getInt(cursor.getColumnIndex("message_id")))
            }
            cursor.close()

            list
        } catch (e: SQLiteException) {
            list
        }
    }

    /**
     * Return if a ack matching [messageId] is waited
     */
    fun isAckWaited(messageId: Int): Boolean {
        val db = readableDatabase

        return try {
            val cursor = db.query("acks", arrayOf("message_id"),
                "message_id = ? AND waited = 1", arrayOf(messageId.toString()),
                null, null, null)

            val e = (cursor.count > 0 && cursor.moveToFirst())
            cursor.close()

            e
        } catch (e: SQLiteException) {
            false
        }
    }

    /**
     * Remove the message identified by [messageId] and all its blocks from database
     */
    fun removeMessage(messageId: Int) {
        writableDatabase.delete("blocks", "message_id = ?", arrayOf(messageId.toString()))
        writableDatabase.delete("messages", "message_id = ?", arrayOf(messageId.toString()))
    }

    /**
     * Remove a block identified by [messageId] and [seqNumber] from database
     */
    fun removeBlock(messageId: Int, seqNumber: Int) {
        writableDatabase.delete("blocks", "message_id = ? AND seq_num = ?", arrayOf(messageId.toString(), seqNumber.toString()))
        val msgVal = ContentValues()
        msgVal.put("is_complete", 0)
        writableDatabase.update("messages", msgVal, "message_id = ?", arrayOf(messageId.toString()))
    }

    /**
     * Remove the ack corresponding to the [messageId]
     */
    fun removeAck(messageId: Int) {
        writableDatabase.delete("acks", "message_id = ?", arrayOf(messageId.toString()))
    }

    /**
     * Remove too old acknowledgments
     */
    fun removeOldAcks() {
        writableDatabase.delete("acks", "(julianday('now') - julianday(creation_date)) >= ?", arrayOf(GlobalConfig.BLOCK_PERSISTENCE_DAYS.toString()))
    }

    companion object : SingletonHolder<SQLite, Context>(::SQLite){
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "agelena.db"

        private const val SQL_CREATE_DEVICES = "CREATE TABLE devices (device_id INTEGER PRIMARY KEY, device_pub_key TEXT NOT NULL, device_first_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)"
        private const val SQL_CREATE_MESSAGES = "CREATE TABLE messages (message_id INTEGER PRIMARY KEY, message_size INTEGER DEFAULT 0, receiver_id INTEGER DEFAULT NULL, is_complete INTEGER DEFAULT 0)"
        private const val SQL_CREATE_BLOCKS = "CREATE TABLE blocks (message_id INTEGER, seq_num INTEGER, ttl INTEGER DEFAULT 1, reception_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (message_id, seq_num))"
        private const val SQL_CREATE_ACKS = "CREATE TABLE acks (message_id INTEGER PRIMARY KEY, waited INTEGER NOT NULL, creation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)"

        private const val SQL_DELETE_DEVICES = "DROP TABLE IF EXISTS devices"
        private const val SQL_DELETE_MESSAGES = "DROP TABLE IF EXISTS messages"
        private const val SQL_DELETE_BLOCKS = "DROP TABLE IF EXISTS blocks"
        private const val SQL_DELETE_ACKS = "DROP TABLE IF EXISTS acks"
    }
}