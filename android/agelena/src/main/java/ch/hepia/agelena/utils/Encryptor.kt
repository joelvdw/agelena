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

package ch.hepia.agelena.utils

import ch.hepia.agelena.GlobalConfig
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.*
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import kotlin.random.Random

/**
 * Utility object that encrypt and decrypt data
 *
 * Use AES for symmetric encryption and RSA for asymmetric encryption
 *
 * @since 0.3
 */
internal object Encryptor {
    /**
     * Encrypt [data] using the [key] and AES algorithm
     * @param random Pseudo-random generator used for the iv generation
     * @return The encrypted data
     */
    fun encryptSymmetric(key: SecretKey, random: Random, data: ByteArray): ByteArray
            = encryptSymmetric(key, IvParameterSpec(random.nextBytes(GlobalConfig.SYMMETRIC_IV_SIZE)), ByteArrayInputStream(data)).readBytes()

    /**
     * Decrypt [data] using the [key] and AES algorithm
     * @param random Pseudo-random generator used for the iv generation
     * @return The decrypted data
     */
    fun decryptSymmetric(key: SecretKey, random: Random, data: ByteArray): ByteArray
            = decryptSymmetric(key, IvParameterSpec(random.nextBytes(GlobalConfig.SYMMETRIC_IV_SIZE)), ByteArrayInputStream(data)).readBytes()

    /**
     * Encrypt [data] stream with AES algorithm using given [key] and [initialization vector][iv]
     * @return The encrypted data stream
     */
    fun encryptSymmetric(key: SecretKey, iv: IvParameterSpec, data: InputStream): InputStream {
        val cipher = Cipher.getInstance(GlobalConfig.ENCRYPTION_ALGO_LONG)
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        return CipherInputStream(data, cipher)
    }

    /**
     * Decrypt [data] stream with AES algorithm using given [key] and [initialization vector][iv]
     * @return The decrypted data stream
     */
    fun decryptSymmetric(key: SecretKey, iv: IvParameterSpec, data: InputStream): InputStream {
        val cipher = Cipher.getInstance(GlobalConfig.ENCRYPTION_ALGO_LONG)
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        return CipherInputStream(data, cipher)
    }

    /**
     * Encrypt [data] using the public [key] and RSA algorithm
     * @return The encrypted data
     */
    fun encryptAsymmetric(key: PublicKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(GlobalConfig.MESSAGE_KEY_ALGO_LONG)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data)
    }

    /**
     * Decrypt [data] using the private [key] and RSA algorithm
     * @return The decrypted data
     */
    fun decryptAsymmetric(key: PrivateKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(GlobalConfig.MESSAGE_KEY_ALGO_LONG)
        cipher.init(Cipher.DECRYPT_MODE, key)
        return cipher.doFinal(data)
    }
}