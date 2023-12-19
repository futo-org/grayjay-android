package com.futo.platformplayer.casting

import android.os.Looper
import android.util.Base64
import android.util.Log
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.casting.models.FCastDecryptedMessage
import com.futo.platformplayer.casting.models.FCastEncryptedMessage
import com.futo.platformplayer.casting.models.FCastKeyExchangeMessage
import com.futo.platformplayer.casting.models.FCastPlayMessage
import com.futo.platformplayer.casting.models.FCastPlaybackErrorMessage
import com.futo.platformplayer.casting.models.FCastPlaybackUpdateMessage
import com.futo.platformplayer.casting.models.FCastSeekMessage
import com.futo.platformplayer.casting.models.FCastSetSpeedMessage
import com.futo.platformplayer.casting.models.FCastSetVolumeMessage
import com.futo.platformplayer.casting.models.FCastVersionMessage
import com.futo.platformplayer.casting.models.FCastVolumeUpdateMessage
import com.futo.platformplayer.getConnectedSocket
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.CastingDeviceInfo
import com.futo.platformplayer.toHexString
import com.futo.platformplayer.toInetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.DHParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class Opcode(val value: Byte) {
    None(0),
    Play(1),
    Pause(2),
    Resume(3),
    Stop(4),
    Seek(5),
    PlaybackUpdate(6),
    VolumeUpdate(7),
    SetVolume(8),
    PlaybackError(9),
    SetSpeed(10),
    Version(11),
    Ping(12),
    Pong(13);

    companion object {
        private val _map = entries.associateBy { it.value }
        fun find(value: Byte): Opcode = _map[value] ?: Opcode.None
    }
}

class FCastCastingDevice : CastingDevice {
    //See for more info: TODO

    override val protocol: CastProtocolType get() = CastProtocolType.FCAST;
    override val isReady: Boolean get() = name != null && addresses != null && addresses?.isNotEmpty() == true && port != 0;
    override var usedRemoteAddress: InetAddress? = null;
    override var localAddress: InetAddress? = null;
    override val canSetVolume: Boolean get() = true;
    override val canSetSpeed: Boolean get() = true;

    var addresses: Array<InetAddress>? = null;
    var port: Int = 0;

    private var _socket: Socket? = null;
    private var _outputStream: DataOutputStream? = null;
    private var _inputStream: DataInputStream? = null;
    private var _scopeIO: CoroutineScope? = null;
    private var _started: Boolean = false;
    private var _version: Long = 1;
    private var _thread: Thread? = null

    constructor(name: String, addresses: Array<InetAddress>, port: Int) : super() {
        this.name = name;
        this.addresses = addresses;
        this.port = port;
    }

    constructor(deviceInfo: CastingDeviceInfo) : super() {
        this.name = deviceInfo.name;
        this.addresses = deviceInfo.addresses.map { a -> a.toInetAddress() }.filterNotNull().toTypedArray();
        this.port = deviceInfo.port;
    }

    override fun getAddresses(): List<InetAddress> {
        return addresses?.toList() ?: listOf();
    }

    override fun loadVideo(streamType: String, contentType: String, contentId: String, resumePosition: Double, duration: Double, speed: Double?) {
        if (invokeInIOScopeIfRequired({ loadVideo(streamType, contentType, contentId, resumePosition, duration, speed) })) {
            return;
        }

        //TODO: Remove this later, temporary for the transition
        if (_version <= 1L) {
            UIDialogs.toast("Version not received, if you are experiencing issues, try updating FCast")
        }

        Logger.i(TAG, "Start streaming (streamType: $streamType, contentType: $contentType, contentId: $contentId, resumePosition: $resumePosition, duration: $duration, speed: $speed)");

        setTime(resumePosition);
        setDuration(duration);
        send(Opcode.Play, FCastPlayMessage(
            container = contentType,
            url = contentId,
            time = resumePosition,
            speed = speed
        ));

        setSpeed(speed ?: 1.0);
    }

    override fun loadContent(contentType: String, content: String, resumePosition: Double, duration: Double, speed: Double?) {
        if (invokeInIOScopeIfRequired({ loadContent(contentType, content, resumePosition, duration, speed) })) {
            return;
        }

        //TODO: Remove this later, temporary for the transition
        if (_version <= 1L) {
            UIDialogs.toast("Version not received, if you are experiencing issues, try updating FCast")
        }

        Logger.i(TAG, "Start streaming content (contentType: $contentType, resumePosition: $resumePosition, duration: $duration, speed: $speed)");

        setTime(resumePosition);
        setDuration(duration);
        send(Opcode.Play, FCastPlayMessage(
            container = contentType,
            content = content,
            time = resumePosition,
            speed = speed
        ));

        setSpeed(speed ?: 1.0);
    }

    override fun changeVolume(volume: Double) {
        if (invokeInIOScopeIfRequired({ changeVolume(volume) })) {
            return;
        }

        setVolume(volume);
        send(Opcode.SetVolume, FCastSetVolumeMessage(volume))
    }

    override fun changeSpeed(speed: Double) {
        if (invokeInIOScopeIfRequired({ changeSpeed(speed) })) {
            return;
        }

        setSpeed(speed);
        send(Opcode.SetSpeed, FCastSetSpeedMessage(speed))
    }

    override fun seekVideo(timeSeconds: Double) {
        if (invokeInIOScopeIfRequired({ seekVideo(timeSeconds) })) {
            return;
        }

        send(Opcode.Seek, FCastSeekMessage(
            time = timeSeconds
        ));
    }

    override fun resumeVideo() {
        if (invokeInIOScopeIfRequired(::resumeVideo)) {
            return;
        }

        send(Opcode.Resume);
    }

    override fun pauseVideo() {
        if (invokeInIOScopeIfRequired(::pauseVideo)) {
            return;
        }

        send(Opcode.Pause);
    }

    override fun stopVideo() {
        if (invokeInIOScopeIfRequired(::stopVideo)) {
            return;
        }

        send(Opcode.Stop);
    }

    private fun invokeInIOScopeIfRequired(action: () -> Unit): Boolean {
        if(Looper.getMainLooper().thread == Thread.currentThread()) {
            _scopeIO?.launch { action(); }
            return true;
        }

        return false;
    }

    override fun stopCasting() {
        if (invokeInIOScopeIfRequired(::stopCasting)) {
            return;
        }

        stopVideo();

        Logger.i(TAG, "Stopping active device because stopCasting was called.")
        stop();
    }

    override fun start() {
        if (_started) {
            return;
        }

        _started = true;
        Logger.i(TAG, "Starting...");

        ensureThreadStarted();
        Logger.i(TAG, "Started.");
    }

    fun ensureThreadStarted() {
        val adrs = addresses ?: return;

        val thread = _thread
        if (thread == null || !thread.isAlive) {
            Log.i(TAG, "Restarting thread because the thread has died")

            _scopeIO?.cancel();
            Logger.i(TAG, "Cancelled previous scopeIO because a new one is starting.")
            _scopeIO = CoroutineScope(Dispatchers.IO);

            _thread = Thread {
                connectionState = CastConnectionState.CONNECTING;

                while (_scopeIO?.isActive == true) {
                    try {
                        val connectedSocket = getConnectedSocket(adrs.toList(), port);
                        if (connectedSocket == null) {
                            Thread.sleep(3000);
                            continue;
                        }

                        usedRemoteAddress = connectedSocket.inetAddress;
                        localAddress = connectedSocket.localAddress;
                        connectedSocket.close();
                        break;
                    } catch (e: Throwable) {
                        Logger.w(ChromecastCastingDevice.TAG, "Failed to get setup initial connection to FastCast device.", e)
                    }
                }

                //Connection loop
                while (_scopeIO?.isActive == true) {
                    Logger.i(TAG, "Connecting to FastCast.");
                    connectionState = CastConnectionState.CONNECTING;

                    try {
                        _socket = Socket(usedRemoteAddress, port);
                        Logger.i(TAG, "Successfully connected to FastCast at $usedRemoteAddress:$port");

                        _outputStream = DataOutputStream(_socket?.outputStream);
                        _inputStream = DataInputStream(_socket?.inputStream);
                    } catch (e: IOException) {
                        _socket?.close();
                        Logger.i(TAG, "Failed to connect to FastCast.", e);

                        connectionState = CastConnectionState.CONNECTING;
                        Thread.sleep(3000);
                        continue;
                    }

                    localAddress = _socket?.localAddress;
                    connectionState = CastConnectionState.CONNECTED;

                    val buffer = ByteArray(4096);

                    Logger.i(TAG, "Started receiving.");
                    var exceptionOccurred = false;
                    while (_scopeIO?.isActive == true && !exceptionOccurred) {
                        try {
                            val inputStream = _inputStream ?: break;
                            Log.d(TAG, "Receiving next packet...");
                            val b1 = inputStream.readUnsignedByte();
                            val b2 = inputStream.readUnsignedByte();
                            val b3 = inputStream.readUnsignedByte();
                            val b4 = inputStream.readUnsignedByte();
                            val size = ((b4.toLong() shl 24) or (b3.toLong() shl 16) or (b2.toLong() shl 8) or b1.toLong()).toInt();
                            if (size > buffer.size) {
                                Logger.w(TAG, "Skipping packet that is too large $size bytes.")
                                inputStream.skip(size.toLong());
                                continue;
                            }

                            Log.d(TAG, "Received header indicating $size bytes. Waiting for message.");
                            inputStream.read(buffer, 0, size);

                            val messageBytes = buffer.sliceArray(IntRange(0, size));
                            Log.d(TAG, "Received $size bytes: ${messageBytes.toHexString()}.");

                            val opcode = messageBytes[0];
                            var json: String? = null;
                            if (size > 1) {
                                json = messageBytes.sliceArray(IntRange(1, size - 1)).decodeToString();
                            }

                            try {
                                handleMessage(Opcode.find(opcode), json);
                            } catch (e: Throwable) {
                                Logger.w(TAG, "Failed to handle message.", e);
                            }
                        } catch (e: java.net.SocketException) {
                            Logger.e(TAG, "Socket exception while receiving.", e);
                            exceptionOccurred = true;
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Exception while receiving.", e);
                            exceptionOccurred = true;
                        }
                    }

                    try {
                        _socket?.close();
                        Logger.i(TAG, "Socket disconnected.");
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to close socket.", e)
                    }

                    connectionState = CastConnectionState.CONNECTING;
                    Thread.sleep(3000);
                }

                Logger.i(TAG, "Stopped connection loop.");
                connectionState = CastConnectionState.DISCONNECTED;
            }.apply { start() };
        } else {
            Log.i(TAG, "Thread was still alive, not restarted")
        }
    }

    private fun handleMessage(opcode: Opcode, json: String? = null) {
        Log.i(TAG, "Processing packet (opcode: $opcode, size: ${json?.length ?: 0})")

        when (opcode) {
            Opcode.PlaybackUpdate -> {
                if (json == null) {
                    Logger.w(TAG, "Got playback update without JSON, ignoring.");
                    return;
                }

                val playbackUpdate = FCastCastingDevice.json.decodeFromString<FCastPlaybackUpdateMessage>(json);
                setTime(playbackUpdate.time, playbackUpdate.generationTime);
                setDuration(playbackUpdate.duration, playbackUpdate.generationTime);
                isPlaying = when (playbackUpdate.state) {
                    1 -> true
                    else -> false
                }
            }
            Opcode.VolumeUpdate -> {
                if (json == null) {
                    Logger.w(TAG, "Got volume update without JSON, ignoring.");
                    return;
                }

                val volumeUpdate = FCastCastingDevice.json.decodeFromString<FCastVolumeUpdateMessage>(json);
                setVolume(volumeUpdate.volume, volumeUpdate.generationTime);
            }
            Opcode.PlaybackError -> {
                if (json == null) {
                    Logger.w(TAG, "Got playback error without JSON, ignoring.");
                    return;
                }

                val playbackError = FCastCastingDevice.json.decodeFromString<FCastPlaybackErrorMessage>(json);
                Logger.e(TAG, "Remote casting playback error received: $playbackError")
            }
            Opcode.Version -> {
                if (json == null) {
                    Logger.w(TAG, "Got version without JSON, ignoring.");
                    return;
                }

                val version = FCastCastingDevice.json.decodeFromString<FCastVersionMessage>(json);
                _version = version.version;
                Logger.i(TAG, "Remote version received: $version")
            }
            Opcode.Ping -> send(Opcode.Pong)
            else -> { }
        }
    }

    private fun send(opcode: Opcode, message: String? = null) {
        try {
            val data: ByteArray = message?.encodeToByteArray() ?: ByteArray(0)
            val size = 1 + data.size
            val outputStream = _outputStream
            if (outputStream == null) {
                Log.w(TAG, "Failed to send $size bytes, output stream is null.")
                return
            }

            val serializedSizeLE = ByteArray(4)
            serializedSizeLE[0] = (size and 0xff).toByte()
            serializedSizeLE[1] = (size shr 8 and 0xff).toByte()
            serializedSizeLE[2] = (size shr 16 and 0xff).toByte()
            serializedSizeLE[3] = (size shr 24 and 0xff).toByte()
            outputStream.write(serializedSizeLE)

            val opcodeBytes = ByteArray(1)
            opcodeBytes[0] = opcode.value
            outputStream.write(opcodeBytes)

            if (data.isNotEmpty()) {
                outputStream.write(data)
            }

            Log.d(TAG, "Sent $size bytes: (opcode: $opcode, body: $message).")
        } catch (e: Throwable) {
            Log.i(TAG, "Failed to send message.", e)
            throw e
        }
    }

    private inline fun <reified T> send(opcode: Opcode, message: T) {
        try {
            send(opcode, message?.let { Json.encodeToString(it) })
        } catch (e: Throwable) {
            Log.i(TAG, "Failed to encode message to string.", e)
            throw e
        }
    }

    override fun stop() {
        Logger.i(TAG, "Stopping...");
        usedRemoteAddress = null;
        localAddress = null;
        _started = false;
        //TODO: Kill and/or join thread?
        _thread = null;

        val socket = _socket;
        val scopeIO = _scopeIO;

        if (scopeIO != null && socket != null) {
            Logger.i(TAG, "Cancelling scopeIO with open socket.")

            scopeIO.launch {
                socket.close();
                connectionState = CastConnectionState.DISCONNECTED;
                scopeIO.cancel();
                Logger.i(TAG, "Cancelled scopeIO with open socket.")
            }
        } else {
            scopeIO?.cancel();
            Logger.i(TAG, "Cancelled scopeIO without open socket.")
        }

        _scopeIO = null;
        _socket = null;
        _outputStream = null;
        _inputStream = null;
        connectionState = CastConnectionState.DISCONNECTED;
    }

    override fun getDeviceInfo(): CastingDeviceInfo {
        return CastingDeviceInfo(name!!, CastProtocolType.FCAST, addresses!!.filter { a -> a.hostAddress != null }.map { a -> a.hostAddress!!  }.toTypedArray(), port);
    }

    companion object {
        val TAG = "FCastCastingDevice";
        private val json = Json { ignoreUnknownKeys = true }

        fun getKeyExchangeMessage(keyPair: KeyPair): FCastKeyExchangeMessage {
            return FCastKeyExchangeMessage(1, Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
        }

        fun generateKeyPair(): KeyPair {
            //modp14
            val p = BigInteger("ffffffffffffffffc90fdaa22168c234c4c6628b80dc1cd129024e088a67cc74020bbea63b139b22514a08798e3404ddef9519b3cd3a431b302b0a6df25f14374fe1356d6d51c245e485b576625e7ec6f44c42e9a637ed6b0bff5cb6f406b7edee386bfb5a899fa5ae9f24117c4b1fe649286651ece45b3dc2007cb8a163bf0598da48361c55d39a69163fa8fd24cf5f83655d23dca3ad961c62f356208552bb9ed529077096966d670c354e4abc9804f1746c08ca18217c32905e462e36ce3be39e772c180e86039b2783a2ec07a28fb5c55df06f4c52c9de2bcbf6955817183995497cea956ae515d2261898fa051015728e5a8aacaa68ffffffffffffffff", 16)
            val g = BigInteger("2", 16)
            val dhSpec = DHParameterSpec(p, g)

            val keyGen = KeyPairGenerator.getInstance("DH")
            keyGen.initialize(dhSpec)

            return keyGen.generateKeyPair()
        }

        fun computeSharedSecret(privateKey: PrivateKey, keyExchangeMessage: FCastKeyExchangeMessage): SecretKeySpec {
            val keyFactory = KeyFactory.getInstance("DH")
            val receivedPublicKeyBytes = Base64.decode(keyExchangeMessage.publicKey, Base64.NO_WRAP)
            val receivedPublicKeySpec = X509EncodedKeySpec(receivedPublicKeyBytes)
            val receivedPublicKey = keyFactory.generatePublic(receivedPublicKeySpec)

            val keyAgreement = KeyAgreement.getInstance("DH")
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(receivedPublicKey, true)

            val sharedSecret = keyAgreement.generateSecret()
            Log.i(TAG, "sharedSecret ${Base64.encodeToString(sharedSecret, Base64.NO_WRAP)}")
            val sha256 = MessageDigest.getInstance("SHA-256")
            val hashedSecret = sha256.digest(sharedSecret)
            Log.i(TAG, "hashedSecret ${Base64.encodeToString(hashedSecret, Base64.NO_WRAP)}")

            return SecretKeySpec(hashedSecret, "AES")
        }

        fun encryptMessage(aesKey: SecretKeySpec, decryptedMessage: FCastDecryptedMessage): FCastEncryptedMessage {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey)
            val iv = cipher.iv
            val json = Json.encodeToString(decryptedMessage)
            val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
            return FCastEncryptedMessage(
                version = 1,
                iv = Base64.encodeToString(iv, Base64.NO_WRAP),
                blob = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            )
        }

        fun decryptMessage(aesKey: SecretKeySpec, encryptedMessage: FCastEncryptedMessage): FCastDecryptedMessage {
            val iv = Base64.decode(encryptedMessage.iv, Base64.NO_WRAP)
            val encrypted = Base64.decode(encryptedMessage.blob, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(iv))
            val decryptedJson = cipher.doFinal(encrypted)
            return Json.decodeFromString(String(decryptedJson, Charsets.UTF_8))
        }
    }
}