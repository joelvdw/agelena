package ch.hepia.agelena.bluetooth

import ch.hepia.agelena.Agelena
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import ch.hepia.agelena.GlobalConfig.BLEConfig
import ch.hepia.agelena.utils.ExchangeKeyPair

/**
 * Store handshake information and provide JSON serialization mechanism
 *
 * @since 0.1
 */
internal class Handshake(
    @Json(name = "sess_id")
    val sessionID: Int,
    @Json(name = "user_id")
    val userID: Int,
    @Json(name = "user_pub_key")
    val userPubKey: String,
    sessionKeyPair: ExchangeKeyPair? = null
) {
    constructor(userID: Int, userPubKey: String, sessionKeyPair: ExchangeKeyPair? = null) : this(Random.nextInt(1, Int.MAX_VALUE), userID, userPubKey, sessionKeyPair)

    @Json(name = "v")
    val version: String = Agelena.PROTOCOL_VERSION

    @Json(name = "sess_key")
    val sessionKey: String? = sessionKeyPair?.publicKeyString

    val g: String? = if (sessionKeyPair?.selfParams == true) ExchangeKeyPair.params.g.toString() else null
    val p: String? = if (sessionKeyPair?.selfParams == true) ExchangeKeyPair.params.p.toString() else null

    fun toJson(): String {
        return Klaxon().toJsonString(this)
    }

    fun isVersionCompatible() = version == Agelena.PROTOCOL_VERSION

    companion object {
        fun fromJson(json: String): Handshake? {
            return Klaxon().parse<Handshake>(json)
        }
    }

    class Builder {
        private val queue: Queue<ByteArray> = ConcurrentLinkedQueue()

        fun addMessagePart(bytes: ByteArray): Builder {
            queue.offer(bytes)
            return this
        }

        fun build(): Handshake? {
            if (queue.isEmpty()) return null

            var s = ""
            while (queue.isNotEmpty()) {
                s += queue.poll()?.toString(BLEConfig.CHARSET) ?: ""
            }
            return fromJson(s)
        }
    }
}