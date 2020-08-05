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

/**
 * Event listener interface for when Agelena send and receive messages
 *
 * @since 0.1
 */
interface MessageListener {
    /**
     * Occurs when a message is received
     */
    fun onMessageReceived(message: Message) { }

    /**
     * Occurs when a broadcast is received
     */
    fun onBroadcastMessageReceived(message: Message) { }

    /**
     * Occurs when a message has been sent
     */
    fun onMessageSent(message: Message) { }

    /**
     * Occurs when a message couldn't be send
     */
    fun onMessageFailed(message: Message, errorCode: Int) { }

    /**
     * Occurs when a message acknowledgement is received
     */
    fun onAckReceived(messageId: Int) { }
}