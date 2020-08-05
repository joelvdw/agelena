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