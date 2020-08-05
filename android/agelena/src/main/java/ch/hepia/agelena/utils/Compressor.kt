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