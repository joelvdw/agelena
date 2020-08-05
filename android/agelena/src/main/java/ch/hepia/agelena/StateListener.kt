package ch.hepia.agelena

/**
 * Event listener interface for when the Agelena state changes
 *
 * @since 0.1
 */
interface StateListener {
    /**
     * Occurs when Agelena has started
     */
    fun onStarted() { }

    /**
     * Occurs when Agelena failed to start
     */
    fun onStartError() { }

    /**
     * Occurs when Agelena is stopped
     */
    fun onStopped() { }

    /**
     * Occurs when a new device is detected nearby
     */
    fun onDeviceConnected(device: Device) { }

    /**
     * Occurs when a device isn't nearby anymore
     */
    fun onDeviceLost(device: Device) { }
}