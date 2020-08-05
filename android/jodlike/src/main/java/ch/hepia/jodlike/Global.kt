package ch.hepia.jodlike

import java.util.*

/**
 * Application pages
 */
enum class Locations {
    Home,
    Plus,
    All,
    Messages,
    Settings,
    About,
    Lang
}

/**
 * Global constants and variables
 */
object Global {
    val SHARED_PREF = "Jodlike"
    val PREF_USERNAME = "username"
    val PREF_CHANNELS = "channels"
    val TTL = 3

    var username: String? = null
    val channels = mutableMapOf<String, Channel>()
    val colors = mutableMapOf<Int, Int>()
    var nbDevicesNearby = 0
    val locationsStack = Stack<Locations>()
    var location: Locations
    get() = locationsStack.peek()
    set(value) {
        locationsStack.push(value)
    }
    var locale: Locale = Locale.getDefault()

    var displayPanel: (() -> Unit)? = null
    var hidePanel: (() -> Unit)? = null

    val listeners = mutableMapOf<Int, (Message) -> Unit>()
    var sendCallback: ((Int, Boolean) -> Unit)? = null
}