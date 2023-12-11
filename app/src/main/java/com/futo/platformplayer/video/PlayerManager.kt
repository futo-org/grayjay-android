@file:Suppress("DEPRECATION")

package com.futo.platformplayer.video

import android.media.session.PlaybackState
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView

class PlayerManager {
    private var _currentView: StyledPlayerView? = null;
    private val _stateMap = HashMap<String, PlayerState>();
    private var _currentState: PlayerState? = null;
    val currentState: PlayerState get() {
        if(_currentState == null)
            throw java.lang.IllegalStateException("Attempted to access CurrentState while no state is set");
        else
            return _currentState!!;
    };

    val player: ExoPlayer;

    constructor(exoPlayer: ExoPlayer) {
        this.player = exoPlayer;
    }

    fun getPlaybackStateCompat() : Int {
        return when(player.playbackState) {
            ExoPlayer.STATE_READY -> if(player.playWhenReady) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED;
            ExoPlayer.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING;
            else -> PlaybackState.STATE_NONE
        }
    }

    @Synchronized
    fun attach(view: StyledPlayerView, stateName: String) {
        if(view != _currentView) {
            _currentView?.player = null;
            switchState(stateName);
            view.player = player;
            _currentView = view;
        }
    }
    fun detach() {
        _currentView?.player = null;
    }

    fun getState(name: String): PlayerState {
        if(!_stateMap.containsKey(name))
            _stateMap[name] = PlayerState();
        return _stateMap[name]!!;
    }
    fun modifyState(name: String, cb: (PlayerState) -> Unit) {
        val state = getState(name);
        cb(state);
        if(_currentState == state)
            applyState(state);
    }
    fun switchState(name: String) {
        val newState = getState(name);
        applyState(newState);

        if(_currentState != newState) {

            if(_currentState?.listener != null)
                player.removeListener(_currentState!!.listener!!);
            if(newState.listener != null)
                player.addListener(newState.listener!!);

            _currentState = newState;
        }
    }
    fun applyState(state: PlayerState) {
        player.volume = if(state.muted) 0f else state.volume;
    }

    fun setMuted(muted: Boolean) {
        currentState.muted = muted;
        applyState(currentState);
    }
    fun setVolume(volume: Float) {
        currentState.volume = volume;
        applyState(currentState);
    }
    fun setListener(listener: Player.Listener) {
        if(currentState.listener == listener)
            return;
        if(currentState.listener != null)
            player.removeListener(currentState.listener!!);
        currentState.listener = listener;
        player.addListener(listener);
    }

    fun release(){
        player.release();
    }

    class PlayerState {
        var muted: Boolean = false;
        var volume: Float = 1f;

        var listener: Player.Listener? = null;
    }
}