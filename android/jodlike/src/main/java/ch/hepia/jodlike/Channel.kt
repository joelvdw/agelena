package ch.hepia.jodlike

/**
 * Store a channel and its messages
 */
class Channel(
    val name: String,
    val messages: MutableList<Message>,
    var unread: Int,
    var favorite: Boolean
)