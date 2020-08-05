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

import android.util.Base64
import ch.hepia.agelena.GlobalConfig
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.DHParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encapsulation and utility class managing key pair used during secret key exchange
 *
 * Use Diffie-Hellman algorithm
 *
 * @since 0.3
 */
internal class ExchangeKeyPair private constructor(private val keyPair: KeyPair, val selfParams: Boolean) {
    /**
     * Get the public exchange key as a hex string
     */
    val publicKeyString: String
        get() = Base64.encodeToString(
            KeyFactory.getInstance(GlobalConfig.KEY_EXCHANGE_ALGO)
            .getKeySpec(keyPair.public, X509EncodedKeySpec::class.java).encoded
        , Base64.DEFAULT)

    /**
     * Generate a [SecretKey] using this keypair and the [other public key][keyString] encoded as a hex string
     */
    fun generateSecretKey(keyString: String): SecretKey {
        val key = KeyFactory.getInstance(GlobalConfig.KEY_EXCHANGE_ALGO).generatePublic(
            X509EncodedKeySpec(Base64.decode(keyString, Base64.DEFAULT))
        )

        val keyAgreement = KeyAgreement.getInstance(GlobalConfig.KEY_EXCHANGE_ALGO)
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(key, true)
        return SecretKeySpec(keyAgreement.generateSecret(), 0, GlobalConfig.SYMMETRIC_KEY_SIZE, GlobalConfig.ENCRYPTION_ALGO)
    }

    companion object {
        var params: DHParameterSpec = run {
            val paramGen: AlgorithmParameterGenerator =
                AlgorithmParameterGenerator.getInstance(GlobalConfig.KEY_EXCHANGE_ALGO)
            paramGen.init(GlobalConfig.EXCHANGE_KEY_SIZE)
            val par: AlgorithmParameters = paramGen.generateParameters()
            par.getParameterSpec(DHParameterSpec::class.java) as DHParameterSpec
        }

        /**
         * Generate a new [ExchangeKeyPair] using random parameters
         */
        fun generateKeyPair(): ExchangeKeyPair {
            val gen = KeyPairGenerator.getInstance(GlobalConfig.KEY_EXCHANGE_ALGO)
            gen.initialize(params)
            return ExchangeKeyPair(gen.generateKeyPair(), true)
        }

        /**
         * Generate a new [ExchangeKeyPair] using [parameters]
         */
        fun generateKeyPair(parameters: DHParameterSpec): ExchangeKeyPair {
            val gen = KeyPairGenerator.getInstance(GlobalConfig.KEY_EXCHANGE_ALGO)
            gen.initialize(parameters)
            return ExchangeKeyPair(gen.generateKeyPair(), false)
        }
    }
}