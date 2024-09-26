package com.futo.platformplayer.sync.internal

import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.sync.internal.SyncSocketSession.Opcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

interface IAuthorizable {
    val isAuthorized: Boolean
}

class SyncSession : IAuthorizable {
    private val _socketSessions: MutableList<SyncSocketSession> = mutableListOf()
    private var _authorized: Boolean = false
    private var _remoteAuthorized: Boolean = false
    private val _onAuthorized: (session: SyncSession) -> Unit
    private val _onUnauthorized: (session: SyncSession) -> Unit
    private val _onClose: (session: SyncSession) -> Unit
    private val _onConnectedChanged: (session: SyncSession, connected: Boolean) -> Unit
    val remotePublicKey: String
    override val isAuthorized get() = _authorized && _remoteAuthorized

    var connected: Boolean = false
        private set(v) {
        if (field != v) {
            field = v
            this._onConnectedChanged(this, v)
        }
    }

    constructor(remotePublicKey: String, onAuthorized: (session: SyncSession) -> Unit, onUnauthorized: (session: SyncSession) -> Unit, onConnectedChanged: (session: SyncSession, connected: Boolean) -> Unit, onClose: (session: SyncSession) -> Unit) {
        this.remotePublicKey = remotePublicKey
        _onAuthorized = onAuthorized
        _onUnauthorized = onUnauthorized
        _onConnectedChanged = onConnectedChanged
        _onClose = onClose
    }

    fun addSocketSession(socketSession: SyncSocketSession) {
        if (socketSession.remotePublicKey != remotePublicKey) {
            throw Exception("Public key of session must match public key of socket session")
        }

        synchronized(_socketSessions) {
            _socketSessions.add(socketSession)
            connected = _socketSessions.isNotEmpty()
        }

        socketSession.authorizable = this
    }

    fun authorize(socketSession: SyncSocketSession) {
        socketSession.send(Opcode.NOTIFY_AUTHORIZED.value)
        _authorized = true
        checkAuthorized()
    }

    fun unauthorize(socketSession: SyncSocketSession? = null) {
        if (socketSession != null)
            socketSession.send(Opcode.NOTIFY_UNAUTHORIZED.value)
        else {
            val ss = synchronized(_socketSessions) {
                _socketSessions.first()
            }

            ss.send(Opcode.NOTIFY_UNAUTHORIZED.value)
        }
    }

    private fun checkAuthorized() {
        if (isAuthorized)
            _onAuthorized.invoke(this)
    }

    fun removeSocketSession(socketSession: SyncSocketSession) {
        synchronized(_socketSessions) {
            _socketSessions.remove(socketSession)
            connected = _socketSessions.isNotEmpty()
        }
    }

    fun close() {
        synchronized(_socketSessions) {
            for (socketSession in _socketSessions) {
                socketSession.stop()
            }

            _socketSessions.clear()
        }

        _onClose.invoke(this)
    }

    fun handlePacket(socketSession: SyncSocketSession, opcode: UByte, data: ByteBuffer) {
        Logger.i(TAG, "Handle packet (opcode: ${opcode}, data.length: ${data.remaining()})")

        when (opcode) {
            Opcode.NOTIFY_AUTHORIZED.value -> {
                _remoteAuthorized = true
                checkAuthorized()
            }
            Opcode.NOTIFY_UNAUTHORIZED.value -> {
                _remoteAuthorized = false
                _onUnauthorized(this)
            }
            //TODO: Handle any kind of packet (that is not necessarily authorized)
        }

        if (!isAuthorized) {
            return
        }

        Logger.i(TAG, "Received ${opcode} (${data.remaining()} bytes)")
        //TODO: Abstract this out
        when(opcode) {
            GJSyncOpcodes.sendToDevices -> {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                    val context = StateApp.instance.contextOrNull;
                    if(context != null && context is MainActivity) {
                        val url = String(data.array(), Charsets.UTF_8);
                        UIDialogs.appToast("Received url from device [${socketSession.remotePublicKey}]:\n{$url}");
                        context.handleUrl(url);
                    }
                };
            }
        }
    }

    private companion object {
        const val TAG = "SyncSession"
    }
}