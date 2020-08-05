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

import ch.hepia.agelena.message.Message
import java.security.PublicKey

/**
 * Other device to/from which it's possible to send/receive a message
 *
 * @since 0.1
 * @property id Unique ID which identifies the device
 * @property publicKey Public key used to encrypt messages destined to this device
 */
class Device internal constructor(
    val id: Int,
    val publicKey: PublicKey,
    internal val isConnected: Boolean
) {
    fun sendMessage(content: Map<String, Any>) {
        sendMessage(Message.Builder().setContent(content).setReceiver(id).build())
    }

    fun sendMessage(message: Message) {
        message.receiverID = id
        Agelena.sendDirectMessage(message)
    }
}
