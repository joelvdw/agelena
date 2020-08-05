package ch.hepia.agelena

/**
 * Configuration for library initialization
 *
 * @since 0.1
 * @property isEncryption Defines is the message encryption is enabled. Default : TRUE
 */
class Config private constructor(val isEncryption: Boolean) {
    class Builder {
        private var isEncryption: Boolean = GlobalConfig.DEFAULT_ENCRYPTION

        /**
         * Set if the encryption is [enabled] or not in the configuration
         */
        fun setEncryption(enabled: Boolean): Builder {
            isEncryption = enabled
            return this
        }

        /**
         * Build a [Config] object using set properties or default values instead
         */
        fun build(): Config = Config(isEncryption)
    }
}
