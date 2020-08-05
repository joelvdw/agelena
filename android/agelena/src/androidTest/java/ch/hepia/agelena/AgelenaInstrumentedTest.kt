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

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.hepia.agelena.bluetooth.Handshake
import ch.hepia.agelena.utils.Encryptor
import ch.hepia.agelena.utils.ExchangeKeyPair
import ch.hepia.agelena.utils.MessageKeyPair
import com.beust.klaxon.Klaxon

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.io.StringReader
import java.math.BigInteger
import javax.crypto.spec.DHParameterSpec
import kotlin.random.Random

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class AgelenaInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("ch.hepia.agelena.test", appContext.packageName)
    }

    @Test fun byteStringConversion() {
        val bs = arrayOf(ByteArray(20) { (it % 256).toByte() }, Random.nextBytes(10), ByteArray(5))
        bs.forEach {
            val s = Base64.encodeToString(it, Base64.DEFAULT)

            val b = Base64.decode(s, Base64.DEFAULT)
            assertEquals(it.size, b.size)
            for (i in it.indices) {
                assertEquals(it[i], b[i])
            }
        }
    }

    @Test fun keyExchange() {
        val kA = ExchangeKeyPair.generateKeyPair()
        val kB = ExchangeKeyPair.generateKeyPair(ExchangeKeyPair.params)

        assertEquals(
            kA.generateSecretKey(kB.publicKeyString),
            kB.generateSecretKey(kA.publicKeyString)
        )

        val keyPair = MessageKeyPair.generateKeyPair()
        assertEquals(keyPair.publicKey, MessageKeyPair.publicKeyFromString(keyPair.publicKeyString))
    }

    @Test fun handshake() {
        val key = ExchangeKeyPair.generateKeyPair()
        val h = Handshake(123, "1234", key)
        assertEquals(123, h.userID)
        assertTrue(h.sessionID > 0)
        assertTrue(h.sessionID <= Int.MAX_VALUE)
        val json = h.toJson()
        assertTrue(json.contains("\"user_id\""))
        assertTrue(json.contains("\"sess_id\""))
        assertTrue(json.contains("\"sess_key\""))
        assertTrue(json.contains("\"g\""))
        assertTrue(json.contains("\"p\""))
        assertTrue(json.contains("\"v\""))
        assertTrue(json.contains("\"user_pub_key\""))
        val j = Klaxon().parseJsonObject(StringReader(json))
        assertEquals(123, j.int("user_id"))
        val h2 = Klaxon().parse<Handshake>(json)
        assertEquals(h.userID, h2?.userID)
        assertEquals(h.sessionID, h2?.sessionID)

        val key2 = ExchangeKeyPair.generateKeyPair(DHParameterSpec(BigInteger(h2?.p!!), BigInteger(h2.g!!)))
        assertEquals(key.generateSecretKey(key2.publicKeyString), key2.generateSecretKey(h2.sessionKey!!))
    }

    @Test fun symmetricEncryption() {
        val kA = ExchangeKeyPair.generateKeyPair()
        val kB = ExchangeKeyPair.generateKeyPair()
        val key = kA.generateSecretKey(kB.publicKeyString)

        val r1 = Random(12345678)
        val r2 = Random(12345678)

        arrayOf(
            ByteArray(20),
            ByteArray(500) {(it%256).toByte()},
            ByteArray(295){47},
            Random.nextBytes(100),
            Random.nextBytes(381)
        ).forEach {
            val crypt = Encryptor.encryptSymmetric(key, r1, it)
            assertTrue((it.size+16) > crypt.size)
            val decrypt = Encryptor.decryptSymmetric(key, r2, crypt)
            assertEquals(it.size, decrypt.size)
            for (i in it.indices) {
                assertEquals(it[i], decrypt[i])
            }
        }
    }

}
