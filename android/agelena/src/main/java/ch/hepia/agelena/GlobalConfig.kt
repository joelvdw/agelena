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

import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanSettings

/**
 * Agelena global default configuration
 *
 * @since 0.2
 */
internal object GlobalConfig {
    // Default configuration
    const val DEFAULT_ENCRYPTION = true

    // Compression
    const val COMPRESSION_LEVEL = 9

    // Encryption
    const val KEY_EXCHANGE_ALGO = "DH"
    const val MESSAGE_KEY_ALGO = "RSA"
    const val MESSAGE_KEY_ALGO_LONG = "RSA/ECB/OAEPwithSHA-256andMGF1Padding"
    const val ENCRYPTION_ALGO = "AES"
    const val ENCRYPTION_ALGO_LONG = "AES/CBC/PKCS5Padding"
    const val EXCHANGE_KEY_SIZE = 512
    const val ASYMMETRIC_KEY_SIZE = 2048
    const val SYMMETRIC_KEY_SIZE = 16 // in bytes
    const val SYMMETRIC_IV_SIZE = 16 // in bytes
    const val ASYMMETRIC_BLOCK_SIZE = 256 // in bytes

    // Store-and-Forward
    const val MAX_BLOCKS = 1250
    const val BLOCK_PERSISTENCE_DAYS = 7

    // Other
    const val INCOMPATIBILITY_TIMEOUT = 300000 // Time, in millis, after when an incompatible device tries to reconnect

    /**
     * Bluetooth LE default configuration
     */
    object BLEConfig {
        const val PAYLOAD_SIZE = 495 // Biggest number divisible by 16 minus 1, must be smaller than 512-4 (so AES padding fits)
        const val MTU = PAYLOAD_SIZE+4 // Max 512
        val CHARSET = Charsets.UTF_8
        const val ADVERTISE_MODE = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
        const val POWER_MODE = AdvertiseSettings.ADVERTISE_TX_POWER_LOW
        const val SCAN_MODE = ScanSettings.SCAN_MODE_LOW_POWER
    }
}