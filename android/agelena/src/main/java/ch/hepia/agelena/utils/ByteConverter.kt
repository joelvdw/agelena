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

/**
 * Utility object providing Int serializing functions
 *
 * @since 0.1
 */
internal object ByteConverter {
    fun bytesToInt32(bytes: ByteArray): Int? {
        return if (bytes.size < 4) {
            null
        } else {
            (bytes[0].toInt() and 0xFF shl 24) or (bytes[1].toInt() and 0xFF shl 16)  or (bytes[2].toInt() and 0xFF shl 8)  or (bytes[3].toInt() and 0xFF)
        }
    }

    fun bytesToInt16(bytes: ByteArray): Short? {
        return if (bytes.size < 2) {
            null
        } else {
            ((bytes[0].toInt() and 0xFF shl 8) or (bytes[1].toInt() and 0xFF)).toShort()
        }
    }

    fun int32ToBytes(value: Int): ByteArray {
        val b = ByteArray(4)
        b[0] = ((value shr 24) and 0xFF).toByte()
        b[1] = ((value shr 16) and 0xFF).toByte()
        b[2] = ((value shr 8) and 0xFF).toByte()
        b[3] = (value and 0xFF).toByte()
        return b
    }

    fun int16ToBytes(value: Short): ByteArray {
        val b = ByteArray(2)
        val i = value.toInt()
        b[0] = ((i shr 8) and 0xFF).toByte()
        b[1] = (i and 0xFF).toByte()
        return b
    }

    fun intToByte(value: Int): Byte = value.toByte()

    fun byteToInt(value: Byte): Int = value.toInt() and 0xFF
}