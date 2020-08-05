package ch.hepia.agelena.message

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.util.Log
import ch.hepia.agelena.Agelena
import ch.hepia.agelena.Device
import ch.hepia.agelena.GlobalConfig
import ch.hepia.agelena.bluetooth.Session
import ch.hepia.agelena.GlobalConfig.BLEConfig
import ch.hepia.agelena.bluetooth.BLEManager
import ch.hepia.agelena.db.FileManager
import ch.hepia.agelena.db.SQLite
import ch.hepia.agelena.service.WorkType
import ch.hepia.agelena.utils.*
import ch.hepia.agelena.utils.ByteConverter
import ch.hepia.agelena.utils.Compressor
import ch.hepia.agelena.utils.MessageKeyPair
import java.io.ByteArrayInputStream
import java.io.SequenceInputStream
import java.lang.Exception
import java.security.PublicKey
import kotlin.concurrent.thread

/**
 * Message types with specific informations
 *
 * @since 0.1
 */
internal enum class MessageType(val value: Int, val headerSize: Int){
    Unknown(0, 0),
    HandshakeRequest(1, 1),
    HandshakeResponse(2, 1),
    Message(3, 11),
    Broadcast(4, 8),
    Acknowledgment(5, 1);

    companion object {
        fun fromValue(value: Int): MessageType {
            var v = Unknown
            values().forEach { if (it.value == value) v = it }
            return v
        }
    }
}

/**
 * Object who offers utility functions to manage message formats
 * and message processing pipeline (fragmentation, compression and ciphering)
 *
 * @since 0.1
 */
internal object MessageManager {
    /**
     * Process message reception pipeline (defragmenting, uncompressing and deciphering)
     * for a block of fragmented message
     * @param manager [BLEManager] used to propagate messages
     * @param sender The [BluetoothDevice] who sent the bytes
     * @param bytes The received block
     * @param session The current session with the sender
     * @param receiveCb Callback to use when a message is completely received
     */
    fun processBlock(manager: BLEManager, sender: BluetoothDevice, bytes: ByteArray, session: Session, receiveCb: (Int, Int?, Boolean?, Boolean) -> Unit) {
        try {
            Log.d(
                "RECEIVED",
                "${ByteConverter.bytesToInt32(bytes.sliceArray(1 until 5))!!}-${ByteConverter.bytesToInt16(
                    bytes.sliceArray(5 until 7)
                )!!} : ${getMessageType(bytes).name}, direct : ${isDirect(bytes)}, last : ${isLast(
                    bytes
                )}, size : ${getPayload(bytes).size} B"
            )
        } catch (_:Exception) {}

        when (getMessageType(bytes)) {
            MessageType.Message -> {
                if (isDirect(bytes)) {
                    val data = getPayload(bytes)
                    session.addMessageBlock(data)

                    if (isLast(bytes)) {
                        val m = session.buildMessage()

                        if (m != null) {
                            receiveCb(m.first, m.second, false, false)
                        }
                    }
                } else {
                    val msgId = ByteConverter.bytesToInt32(bytes.sliceArray(1 until 5))!!
                    val seq = ByteConverter.bytesToInt16(bytes.sliceArray(5 until 7))!!.toInt()
                    val receiver = ByteConverter.bytesToInt32(bytes.sliceArray(7 until 11))!!

                    if (SQLite.getInstance(manager.context).insertBlock(msgId, receiver, seq, isLast(bytes))) {
                        if (receiver == Agelena.getUserID() && SQLite.getInstance(manager.context).hasAllBlocks(msgId)) {
                            FileManager.getInstance(manager.context).writeBlockSync(msgId, seq, getPayload(bytes))


                            val n = SQLite.getInstance(manager.context).getMessageSize(msgId)!!
                            receiveCb(msgId, n, false, false)
                        } else {
                            FileManager.getInstance(manager.context).writeBlock(msgId, seq, getPayload(bytes))
                            if (receiver != Agelena.getUserID()) {
                                manager.propagateData(bytes, sender, false)
                            }
                        }
                    }
                }
            }
            MessageType.Broadcast -> {
                val msgId = ByteConverter.bytesToInt32(bytes.sliceArray(1 until 5))!!
                val seq = ByteConverter.bytesToInt16(bytes.sliceArray(5 until 7))!!.toInt()
                val ttl = ByteConverter.byteToInt(bytes[7]) - 1

                if (SQLite.getInstance(manager.context).insertBlock(msgId, null, seq, isLast(bytes), ttl)) {
                    val all = SQLite.getInstance(manager.context).hasAllBlocks(msgId)

                    if (all)
                        FileManager.getInstance(manager.context).writeBlockSync(msgId, seq, getPayload(bytes))
                    else
                        FileManager.getInstance(manager.context).writeBlock(msgId, seq, getPayload(bytes))

                    // Broadcast to others
                    if (ttl > 0) {
                        bytes[7] = ByteConverter.intToByte(ttl)
                        manager.propagateData(bytes, sender, false)
                    }

                    if (all) {
                        val n = SQLite.getInstance(manager.context).getMessageSize(msgId)!!
                        receiveCb(msgId, n, true, false)
                    }
                }
            }
            else -> return
        }
    }

    /**
     * Build a message reading the encrypted and compressed stream get from file blocks
     * @param id Message id used to save blocks
     * @param nbBlock Number of blocks in message
     * @param broadcast If its a broadcast message
     */
    fun buildMessage(context: Context, id: Int, nbBlock: Int, broadcast: Boolean): Message? {
        try {
            val data = FileManager.getInstance(context).readMessage(id, nbBlock) ?: return null

            // Decrypt message
            var stream = if (broadcast) {
                data
            } else {
                if (data.read() == 1) {
                    val encrypted = ByteArray(GlobalConfig.ASYMMETRIC_BLOCK_SIZE)
                    data.read(encrypted)
                    val header = Encryptor.decryptAsymmetric(Agelena.getPrivateKey(), encrypted)
                    val key =
                        MessageKeyPair.getSecretKeyFromBytes(header.sliceArray(0 until GlobalConfig.SYMMETRIC_KEY_SIZE))
                    val iv = MessageKeyPair.getIvFromBytes(
                        header.sliceArray(
                            GlobalConfig.SYMMETRIC_KEY_SIZE until (GlobalConfig.SYMMETRIC_KEY_SIZE + GlobalConfig.SYMMETRIC_IV_SIZE)
                        )
                    )

                    Encryptor.decryptSymmetric(key, iv, data)
                } else data
            }

            //Decompress message
            stream = Compressor.decompress(stream)

            val sizeB = ByteArray(4)
            var n = stream.read(sizeB)
            if (n == 4) {
                val size = ByteConverter.bytesToInt32(sizeB)!!
                val jsonB = ByteArray(size)
                n = jsonB.size
                for (i in jsonB.indices) {
                    val b = stream.read()
                    if (b == -1) {
                        n = i
                        break
                    }
                    jsonB[i] = b.toByte()
                }

                if (n == size) {
                    val m = Message.fromJson(jsonB.toString(BLEConfig.CHARSET)) ?: run {
                        stream.close()
                        return null
                    }

                    if (m.hasData) {
                        m.data = stream
                    }

                    if (!broadcast) {
                        val b = Bundle()
                        b.putInt("message", m.id)
                        b.putInt("sender", m.senderID)
                        Agelena.sendToService(WorkType.SendAck, b)
                    }
                    return m
                }
            }
            stream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Rebuild the block header and send the [block] to [device] using [manager]
     * @param data Block payload
     */
    fun sendBlock(manager: BLEManager, device: Int, block: Block, data: ByteArray) {
        val broadcast = block.receiverID == null

        var h = createHeader(
            if (broadcast) MessageType.Broadcast else MessageType.Message,
            false,
            block.isLast
        )
        h += if (broadcast) {
            ByteConverter.int32ToBytes(block.messageID) + ByteConverter.int16ToBytes(block.seqNumber.toShort()) + ByteConverter.intToByte(block.ttl)
        } else {
            ByteConverter.int32ToBytes(block.messageID) + ByteConverter.int16ToBytes(block.seqNumber.toShort()) +
                    ByteConverter.int32ToBytes(block.receiverID!!)
        }

        manager.sendData(device, h + data, false)
    }

    /**
     * Send a [message] to the [device] after compressing, ciphering and fragmenting it
     *
     * If the [device] is null, it will propagate the message to all connected devices
     * @param isDirect If it's a direct message or not
     * @param broadcast If it's a broadcast message. If true, doesn't cipher message
     */
    private fun sendMessage(context: Context, device: Device?, message: Message, publicKey: PublicKey?, isDirect: Boolean, broadcast: Boolean) {
        // Manage encryption
        val enc = Agelena.getConfig().isEncryption && publicKey != null && !broadcast
        val secretKey = if (enc) MessageKeyPair.generateSecretKey() else null
        val iv = if (enc) MessageKeyPair.generateIv() else null
        var header = ByteArray(1)
        if (enc) {
            header[0] = 1
            header += Encryptor.encryptAsymmetric(publicKey!!, secretKey!!.encoded + iv!!.iv)
        } else {
            header[0] = 0
        }

        // Build message
        val json = message.toJson().toByteArray(BLEConfig.CHARSET)
        val size = ByteConverter.int32ToBytes(json.size)

        val stream = if (message.hasData) {
            SequenceInputStream(ByteArrayInputStream(size + json), message.data)
        } else {
            ByteArrayInputStream(size + json)
        }

        // Compress message
        val comp = Compressor.compress(stream)

        // Encrypt message
        val encrypt = if (enc) Encryptor.encryptSymmetric(secretKey!!, iv!!, comp) else comp

        val finalStream = if (!broadcast) SequenceInputStream(ByteArrayInputStream(header), encrypt) else encrypt

        // Asynchronously send message by blocks
        thread(start = true) {
            var cpt: Short = 1
            var next = finalStream.read() // Used to detect end of stream
            val headerSize = if (broadcast) {
                MessageType.Broadcast.headerSize
            } else {
                (if (isDirect) MessageType.HandshakeRequest.headerSize else MessageType.Message.headerSize)
            }
            val max = BLEConfig.PAYLOAD_SIZE - headerSize
            val block = ByteArray(max)

            while (next != -1) {
                // Read a block (didn't used block read because sometimes it reads a small block instead of a full one)
                var i = 0
                while (i < max && next != -1) {
                    block[i] = next.toByte()
                    next = finalStream.read()
                    i++
                }

                var h = createHeader(
                    if (broadcast) MessageType.Broadcast else MessageType.Message,
                    isDirect,
                    next == -1
                )
                if (broadcast) {
                    h += ByteConverter.int32ToBytes(message.id) + ByteConverter.int16ToBytes(cpt) + ByteConverter.intToByte(message.ttl)
                } else if (!isDirect) {
                    h += ByteConverter.int32ToBytes(message.id) + ByteConverter.int16ToBytes(cpt) +
                            ByteConverter.int32ToBytes(message.receiverID!!)
                }

                // Save block to propagate it later
                if (!isDirect && SQLite.getInstance(context).insertBlock(
                        message.id,
                        message.receiverID,
                        cpt.toInt(),
                        next == -1,
                        if (broadcast) message.ttl else null)
                ) {
                    FileManager.getInstance(context).writeBlock(message.id, cpt.toInt(), block.sliceArray(0 until i))
                }

                val data = h + block.sliceArray(0 until i)
                val b = Bundle()
                b.putByteArray("data", data)
                if (device != null) {
                    b.putInt("receiver", device.id)
                }
                Agelena.sendToService(WorkType.Send, b)
                cpt++
            }
        }
    }

    /**
     * Send a [message] to the [device] after compressing, ciphering (with [publicKey]) and fragmenting it
     *
     * If the [device] is null, it will propagate the message to all connected devices
     * Will only cipher the message if its enabled in the [ch.hepia.agelena.Config]
     * @param isDirect If it's a direct message or not
     */
    fun sendMessage(context: Context, device: Device?, message: Message, publicKey: PublicKey?, isDirect: Boolean) {
        if (message.receiverID == null) return

        sendMessage(context, device, message, publicKey, isDirect, false)
    }

    /**
     * Send a [message] of type broadcast to all devices after compressing and fragmenting it
     */
    fun sendBroadcast(context: Context, message: Message) {
        if (message.ttl <= 0) return
        message.receiverID = null

        sendMessage(context, null, message, null, isDirect = false, broadcast = true)
    }

    /**
     * Return the message type of a received [bytes]
     */
    fun getMessageType(bytes: ByteArray): MessageType {
        if (bytes.isEmpty()) return MessageType.Unknown
        val b = bytes[0]
        return MessageType.fromValue(b.toInt() shr 4 and 0b1111)
    }

    /**
     * Return if the received [bytes] are a direct message or not
     */
    fun isDirect(bytes: ByteArray): Boolean = bytes.isNotEmpty() and ((bytes[0].toInt() and 0b1) > 0)

    /**
     * Return if the received [bytes] are the last bloc of the complete message
     */
    fun isLast(bytes: ByteArray): Boolean = bytes.isNotEmpty() and ((bytes[0].toInt() and 0b10) > 0)

    /**
     * Add the message header for a handshake block to the [bytes]
     * @param isRequest True if it's a handshake request, false for a response
     * @param isLast True if it's the last block of the handshake
     */
    fun addHandshakeHeader(bytes: ByteArray, isRequest: Boolean, isLast: Boolean): ByteArray
            = createHeader(if (isRequest) MessageType.HandshakeRequest else MessageType.HandshakeResponse, true, isLast) + bytes

    /**
     * Add the message header for a acknowledgment block to the [bytes]
     */
    fun addAckHeader(bytes: ByteArray): ByteArray
            = createHeader(MessageType.Acknowledgment, isDirect = false, isLast = true) + bytes

    /**
     * Create a block header matching parameters
     */
    private fun createHeader(type: MessageType, isDirect: Boolean, isLast: Boolean): ByteArray {
        var b = type.value
        b = b shl 4
        b = if (isDirect) {
            b or 0b1
        } else {
            b and 0b1.inv()
        }
        b = if (isLast) {
            b or 0b10
        } else {
            b and 0b10.inv()
        }
        return byteArrayOf(b.toByte())
    }

    /**
     * Remove message header and return only the payload from [bytes]
     */
    fun getPayload(bytes: ByteArray): ByteArray {
        val i = getHeaderSize(bytes)
        return bytes.sliceArray(i until bytes.size)
    }

    /**
     * Return the size of the header using status byte of the block
     */
    fun getHeaderSize(bytes: ByteArray): Int {
        return if (isDirect(bytes)) MessageType.HandshakeRequest.headerSize else getMessageType(bytes).headerSize
    }
}