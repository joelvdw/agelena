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

import ch.hepia.agelena.Agelena
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import ch.hepia.agelena.GlobalConfig.BLEConfig
import ch.hepia.agelena.utils.ExchangeKeyPair

/**
 * Store handshake information and provide JSON serialization mechanism
 *
 * @since 0.1
 */
internal class Handshake(
    @Json(name = "sess_id")
    val sessionID: Int,
    @Json(name = "user_id")
    val userID: Int,
    @Json(name = "user_pub_key")
    val userPubKey: String,
    sessionKeyPair: ExchangeKeyPair? = null
) {
    constructor(userID: Int, userPubKey: String, sessionKeyPair: ExchangeKeyPair? = null) : this(Random.nextInt(1, Int.MAX_VALUE), userID, userPubKey, sessionKeyPair)

    @Json(name = "v")
    val version: String = Agelena.PROTOCOL_VERSION

    @Json(name = "sess_key")
    val sessionKey: String? = sessionKeyPair?.publicKeyString

    val g: String? = if (sessionKeyPair?.selfParams == true) ExchangeKeyPair.params.g.toString() else null
    val p: String? = if (sessionKeyPair?.selfParams == true) ExchangeKeyPair.params.p.toString() else null

    fun toJson(): String {
        return Klaxon().toJsonString(this)
    }

    fun isVersionCompatible() = version == Agelena.PROTOCOL_VERSION

    companion object {
        fun fromJson(json: String): Handshake? {
            return Klaxon().parse<Handshake>(json)
        }
    }

    class Builder {
        private val queue: Queue<ByteArray> = ConcurrentLinkedQueue()

        fun addMessagePart(bytes: ByteArray): Builder {
            queue.offer(bytes)
            return this
        }

        fun build(): Handshake? {
            if (queue.isEmpty()) return null

            var s = ""
            while (queue.isNotEmpty()) {
                s += queue.poll()?.toString(BLEConfig.CHARSET) ?: ""
            }
            return fromJson(s)
        }
    }
}