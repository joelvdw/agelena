package ch.hepia.jodlike

import java.util.*

/**
 * Data class storing a message
 */
class Message(
    val messageId: Int,
    val userId: Int,
    val username: String,
    val channel: String,
    val text: String,
    val date: Calendar,
    var sent: Boolean
) {
    companion object {
        /**
         * Create a Message from a Agelena Message
         */
        fun fromAgelenaMessage(message: ch.hepia.agelena.message.Message, sent: Boolean): Message? {
            return if (message.content?.contains("username") == true && message.content?.contains("channel") == true && message.content?.contains("text") == true)
                Message(message.id,
                    message.senderID,
                    message.content!!["username"].toString(),
                    message.content!!["channel"].toString(),
                    message.content!!["text"].toString(),
                    Calendar.getInstance().also { it.time = message.datetime },
                    sent
                )
            else null
        }
    }
}