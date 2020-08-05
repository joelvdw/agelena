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

package ch.hepia.agelena.bluetooth

import android.content.Context
import android.util.Log
import ch.hepia.agelena.Agelena
import ch.hepia.agelena.db.FileManager
import ch.hepia.agelena.db.SQLite
import javax.crypto.SecretKey
import kotlin.random.Random

/**
 * Manage a session between two devices
 * Allow the reconstruction of fragmented direct messages
 *
 * @since 0.1
 */
internal class Session(
    val context: Context,
    val id: Int,
    userID: Int,
    val sessionKey: SecretKey?,
    private val selfId: Int
) {
    private var currentMsgID: Int = 0
    var nbParts: Int = 0
        private set

    /**
     * Pseudo-random number generator used for the initiation vector in AES encryption
     */
    val sendingRandom = Random(id * selfId)
    /**
     * Pseudo-random number generator used for the initiation vector in AES decryption
     */
    val receivingRandom = Random(id * userID)

    /**
     * Add a [block] of fragmented message to the session
     */
    fun addMessageBlock(block: ByteArray): Session {
        if (nbParts == 0) currentMsgID = Random.nextInt(1, Int.MAX_VALUE)
        val seq = nbParts + 1

        // Save message in storage
        FileManager.getInstance(context).writeBlockSync(currentMsgID, seq, block)
        // Add message to database for the cleaning system to know about it
        SQLite.getInstance(context).insertBlock(currentMsgID,
            selfId, seq, false, 0)

        nbParts += 1
        return this
    }

    /**
     * Provide the message id and number of blocks used to save message in files
     */
    fun buildMessage(): Pair<Int, Int>? {
        return if (nbParts > 0) {
            val n = nbParts
            clearBlocks()
            Pair(currentMsgID, n)
        } else {
            null
        }
    }

    /**
     * Clear all pending message blocks
     */
    fun clearBlocks() {
        nbParts = 0
    }
}