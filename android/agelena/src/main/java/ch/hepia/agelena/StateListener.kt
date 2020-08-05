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

/**
 * Event listener interface for when the Agelena state changes
 *
 * @since 0.1
 */
interface StateListener {
    /**
     * Occurs when Agelena has started
     */
    fun onStarted() { }

    /**
     * Occurs when Agelena failed to start
     */
    fun onStartError() { }

    /**
     * Occurs when Agelena is stopped
     */
    fun onStopped() { }

    /**
     * Occurs when a new device is detected nearby
     */
    fun onDeviceConnected(device: Device) { }

    /**
     * Occurs when a device isn't nearby anymore
     */
    fun onDeviceLost(device: Device) { }
}