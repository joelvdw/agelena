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

package ch.hepia.agelena.message

import ch.hepia.agelena.Agelena
import ch.hepia.agelena.Device
import com.beust.klaxon.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*
import kotlin.random.Random

/**
 * Store information of a message
 * Its content is split in two forms : a [Map] and an [InputStream], one of them can be empty
 * During serialization, the map will be converted in JSON but not data
 *
 * @since 0.1
 */
class Message internal constructor(
    @Json(name = "msg_id")
    val id: Int,
    @Json(name = "payload")
    val content: Map<String, Any>? = null,
    receiverID: Int? = null,
    @Json(name = "sender")
    val senderID: Int,
    @Json(name = "timestamp")
    val datetime: Date
) {
    @Json(name = "recipient")
    var receiverID: Int? = receiverID
        internal set

    @Json(name = "has_data")
    var hasData: Boolean = false
        internal set

    @Json(ignored = true)
    var data: InputStream? = null
        internal set(value) {
            hasData = value != null
            field = value
        }

    @Json(ignored = true)
    internal var ttl: Int = -1

    fun toJson(): String {
        return Klaxon().converter(dateConverter).toJsonString(this)
    }

    companion object {
        private val dateConverter = object : Converter {
            override fun canConvert(cls: Class<*>): Boolean
                = cls == Date::class.java

            override fun fromJson(jv: JsonValue)
                = Date((jv.longValue ?: jv.int!!.toLong())*1000)

            override fun toJson(value: Any): String
                = ((value as Date).time / 1000).toString()
        }

        internal fun fromJson(json: String): Message? {
            return try {
                Klaxon().converter(dateConverter).parse(json)
            } catch (_: KlaxonException) {
                null
            }
        }
    }

    class Builder {
        private var receiverID: Int? = null
        private var data: InputStream? = null
        private var content: Map<String, Any>? = null

        fun setReceiver(id: Int): Builder {
            receiverID = id
            return this
        }

        fun setReceiver(device: Device): Builder {
            receiverID = device.id
            return this
        }

        fun setContent(map: Map<String, Any>): Builder {
            content = map
            return this
        }

        fun setData(input: InputStream): Builder {
            data = input
            return this
        }

        fun setData(bytes: ByteArray): Builder {
            data = ByteArrayInputStream(bytes)
            return this
        }

        fun build(): Message {
            val m = Message(Random.nextInt(1, Int.MAX_VALUE), content, receiverID, Agelena.getUserID(), Calendar.getInstance().time)
            m.data = data
            return m
        }
    }
}