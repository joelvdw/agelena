package ch.hepia.agelena.message

/**
 * Store information about a message block
 *
 * Used to propagate blocks during the Store-and-Forward
 * @since 0.2
 */
internal class Block(
    val messageID: Int,
    val seqNumber: Int,
    val ttl: Int,
    val receiverID: Int?,
    val isLast: Boolean
)