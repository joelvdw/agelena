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

package ch.hepia.agelena

import ch.hepia.agelena.bluetooth.Handshake
import ch.hepia.agelena.utils.Encryptor
import ch.hepia.agelena.message.Message
import ch.hepia.agelena.utils.ByteConverter
import ch.hepia.agelena.utils.MessageKeyPair
import org.junit.Test

import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.random.Random

/**
 * Agelena library unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class AgelenaUnitTest {

    @Test fun configBuilder() {
        val b = Config.Builder()
        b.setEncryption(true)
        val c = b.build()
        assertEquals(true, c.isEncryption)
        assertEquals(true, Config.Builder().build().isEncryption)
        assertEquals(false, Config.Builder().setEncryption(false).build().isEncryption)
    }

    @Test fun intToByteArrayConversions() {
        arrayOf(0, 1, 483783, 3344432, 89 -942, -44, -743875895, Int.MAX_VALUE, Int.MIN_VALUE).forEach {
            val b = ByteConverter.int32ToBytes(it)
            assertEquals(4, b.size)
            assertEquals(it, ByteConverter.bytesToInt32(b))
        }

        arrayOf(0, 1, 14783, 432, 89 -942, -44, -30895, Short.MAX_VALUE, Short.MIN_VALUE).forEach {
            val b = ByteConverter.int16ToBytes(it)
            assertEquals(2, b.size)
            assertEquals(it, ByteConverter.bytesToInt16(b))
        }

        assertNull(ByteConverter.bytesToInt16(ByteArray(1)))
        assertNull(ByteConverter.bytesToInt16(ByteArray(0)))
        assertNull(ByteConverter.bytesToInt32(ByteArray(3)))
        assertNull(ByteConverter.bytesToInt32(ByteArray(0)))

        arrayOf(0, 1, 255, 128, 127, 64, 35, 199, 246, 254).forEach {
            assertEquals(it, ByteConverter.byteToInt(
                ByteConverter.intToByte(it)))
        }
    }

    @Test fun handshakeBuilder() {
        val h = Handshake(183227, 123456, "123456",null)
        val json = h.toJson()
        assertFalse(json.contains("\"session_key\""))
        val bytes = json.toByteArray()
        val b = Handshake.Builder()
        val h2 = b.addMessagePart(bytes.sliceArray(0 until 20))
            .addMessagePart(bytes.sliceArray(20 until 40))
            .addMessagePart(bytes.sliceArray(40 until bytes.size))
            .build()
        assertNotNull(h2)
        assertEquals(h.sessionID, h2?.sessionID)
        assertEquals(h.userID, h2?.userID)
        assertNull(h.sessionKey)
        assertNull(h2?.sessionKey)
    }

    @Test fun messageJson() {
        val m = Message(123, mapOf(Pair("test", 123)), 1234,12345, Date(123456000))
        val json = m.toJson()

        assertFalse(json.contains("\"data\""))
        assertTrue(json.contains("timestamp"))
        assertTrue(json.contains("msg_id"))
        assertTrue(json.contains("recipient"))
        assertTrue(json.contains("sender"))
        assertTrue(json.contains("payload"))

        val m2 = Message.fromJson(json) ?: error("")

        assertEquals(123, m2.id)
        assertEquals(m.content?.get("test") ?: error(""), m2.content?.get("test") ?: error(""))
        assertEquals(123456000, m2.datetime.time)
        assertEquals(1234, m2.receiverID)
        assertEquals(12345, m2.senderID)
        assertFalse(m2.hasData)
        assertNull(m2.data)

        val m3 = Message(1234, null, null,123, Date(11111))
        val json2 = m3.toJson()

        assertFalse(json2.contains("payload"))
        assertFalse(json2.contains("\"data\""))
        assertFalse(json2.contains("recipient"))

        val m4 = Message.fromJson(json2) ?: error("")

        assertEquals(1234, m4.id)
        assertEquals(123, m4.senderID)
        assertNull(m4.receiverID)
        assertNull(m4.content)
        assertFalse(m4.hasData)
        assertNull(m4.data)
    }

    @Test fun symmetricStreamEncryption() {
        val key = MessageKeyPair.generateSecretKey()

        arrayOf(
            ByteArray(20),
            ByteArray(500) {(it%256).toByte()},
            ByteArray(295){47},
            Random.nextBytes(100),
            Random.nextBytes(381)
        ).forEach {
            val iv = MessageKeyPair.generateIv()
            val crypt = Encryptor.encryptSymmetric(key, iv, ByteArrayInputStream(it))
            val decrypt = Encryptor.decryptSymmetric(key, iv, crypt)

            var cpt = 0
            var v = decrypt.read()
            while (v != -1) {
                assertEquals(it[cpt], v.toByte())
                v = decrypt.read()
                cpt++
            }
            assertEquals(it.size, cpt)
        }
    }

    @Test fun asymmetricEncryption() {
        val kA = MessageKeyPair.generateKeyPair()

        arrayOf(
            ByteArray(20),
            ByteArray(15) {(it%256).toByte()},
            ByteArray(95){47},
            Random.nextBytes(100),
            Random.nextBytes(81)
        ).forEach {
            val crypt = Encryptor.encryptAsymmetric(kA.publicKey, it)
            assertEquals(256, crypt.size)
            val decrypt = Encryptor.decryptAsymmetric(kA.privateKey, crypt)
            assertEquals(it.size, decrypt.size)
            for (i in it.indices) {
                assertEquals(it[i], decrypt[i])
            }
        }
    }
}
