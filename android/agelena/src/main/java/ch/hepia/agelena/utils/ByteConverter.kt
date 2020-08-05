package ch.hepia.agelena.utils

import java.util.*

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