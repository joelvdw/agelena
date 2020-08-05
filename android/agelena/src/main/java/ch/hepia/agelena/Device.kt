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
