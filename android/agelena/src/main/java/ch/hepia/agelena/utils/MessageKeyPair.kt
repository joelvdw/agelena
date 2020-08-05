package ch.hepia.agelena.utils

import android.util.Base64
import ch.hepia.agelena.GlobalConfig
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encapsulation and utility class managing key pair used during message encryption
 *
 * Use RSA and AES algorithm
 *
 * @since 0.3
 */
class MessageKeyPair private constructor(val publicKey: PublicKey, val privateKey: PrivateKey) {
    private constructor(keyPair: KeyPair) : this(keyPair.public, keyPair.private)

    /**
     * Get the public message key as a hex string
     */
    internal val publicKeyString: String
        get() = publicKeyToString(publicKey)

    /**
     * Get the private message key as a hex string
     */
    internal val privateKeyString: String
        get() = privateKeyToString(privateKey)

    companion object {
        /**
         * Generate a new [MessageKeyPair]
         */
        internal fun generateKeyPair(): MessageKeyPair {
            val gen = KeyPairGenerator.getInstance(GlobalConfig.MESSAGE_KEY_ALGO)
            gen.initialize(GlobalConfig.ASYMMETRIC_KEY_SIZE)
            return MessageKeyPair(gen.generateKeyPair())
        }

        /**
         * Generate a [PublicKey] from the given [keyString] in the hex string format
         */
        fun publicKeyFromString(keyString: String): PublicKey
            = KeyFactory.getInstance(GlobalConfig.MESSAGE_KEY_ALGO).generatePublic(
                X509EncodedKeySpec(Base64.decode(keyString, Base64.DEFAULT))
            )

        /**
         * Generate a hex encoded string from the given [key]
         */
        fun publicKeyToString(key: PublicKey): String
            = Base64.encodeToString(
                KeyFactory.getInstance(GlobalConfig.MESSAGE_KEY_ALGO)
                    .getKeySpec(key, X509EncodedKeySpec::class.java).encoded
            , Base64.DEFAULT)

        /**
         * Generate a heyx encoded string from the given [key]
         */
        fun privateKeyToString(key: PrivateKey): String
            = Base64.encodeToString(
                KeyFactory.getInstance(GlobalConfig.MESSAGE_KEY_ALGO)
                    .getKeySpec(key, PKCS8EncodedKeySpec::class.java).encoded
        , Base64.DEFAULT)

        /**
         * Generate a [PrivateKey] from the given [keyString] in the hex string format
         */
        fun privateKeyFromString(keyString: String): PrivateKey
                = KeyFactory.getInstance(GlobalConfig.MESSAGE_KEY_ALGO).generatePrivate(
            PKCS8EncodedKeySpec(Base64.decode(keyString, Base64.DEFAULT))
        )

        /**
         * Generate a secure symmetric key
         */
        internal fun generateSecretKey(): SecretKey {
            val b = ByteArray(GlobalConfig.SYMMETRIC_KEY_SIZE)
            SecureRandom().nextBytes(b)
            return SecretKeySpec(b, GlobalConfig.ENCRYPTION_ALGO)
        }

        /**
         * Generate a secret key using [key]
         */
        internal fun getSecretKeyFromBytes(key: ByteArray): SecretKey {
            return SecretKeySpec(key, 0, GlobalConfig.SYMMETRIC_KEY_SIZE, GlobalConfig.ENCRYPTION_ALGO)
        }

        /**
         * Generate an initialization vector key using [iv]
         */
        internal fun getIvFromBytes(iv: ByteArray): IvParameterSpec {
            return IvParameterSpec(iv)
        }

        /**
         * Recreate a [MessageKeyPair] from two string encoded keys
         */
        internal fun getFromStrings(publicKey: String, privateKey: String): MessageKeyPair
                = MessageKeyPair(publicKeyFromString(publicKey), privateKeyFromString(privateKey))

        /**
         * Generate a secure initialization vector
         */
        fun generateIv(): IvParameterSpec {
            val b = ByteArray(GlobalConfig.SYMMETRIC_IV_SIZE)
            SecureRandom().nextBytes(b)
            return IvParameterSpec(b)
        }
    }
}