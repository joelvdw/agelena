package ch.hepia.agelena.utils

import ch.hepia.agelena.GlobalConfig
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Utility object that compress or decompress an [InputStream], using Deflate compression
 *
 * @since 0.2
 */
internal object Compressor {
    /**
     * Compress the [stream]
     */
    fun compress(stream: InputStream): InputStream
            = DeflaterInputStream(stream, Deflater(GlobalConfig.COMPRESSION_LEVEL, false))

    /**
     * Decompress the [stream]
     */
    fun decompress(stream: InputStream): InputStream
            = InflaterInputStream(stream, Inflater(false))
}