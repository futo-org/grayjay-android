package com.futo.platformplayer.playback

import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.sync.internal.GJSyncOpcodes
import com.futo.platformplayer.sync.models.SyncSubscriptionsPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PlaybackTracking {
    private val _trackerLock = Any()
    private val _trackingLock = Any()
    @Volatile
    private var _tracker: IPlaybackTracker? = null
    private var _lastUpdateTime = System.currentTimeMillis()
    private var _lastPosition = 0L
    private var _lastVideo: IPlatformVideoDetails? = null
    private var _subscription: Subscription? = null
    private var _totalWatched = 0L
    private var _didCountView = false
    private var _dirty = false
    private var _lastSyncTime = 0L

    fun updatePlaybackTracking(video: IPlatformVideoDetails?, position: Long, isPlaying: Boolean) {
        synchronized(_trackingLock) {
            val now = System.currentTimeMillis()
            if (video?.url != _lastVideo?.url) {
                flushPlayback()
                _lastVideo = video
                _subscription = video?.author?.url?.let { StateSubscriptions.instance.getSubscription(it) }
                _totalWatched = 0
                _didCountView = false
                _lastPosition = position
                _lastUpdateTime = now
                return
            }
            if (!Settings.instance.subscriptions.allowPlaytimeTracking || StateApp.instance.privateMode || !isPlaying) {
                _lastPosition = position
                _lastUpdateTime = now
                return
            }
            if (now - _lastUpdateTime <= UPDATE_INTERVAL) return
            _lastUpdateTime = now
            val delta = position - _lastPosition
            _lastPosition = position
            val subscription = _subscription ?: return
            if (video == null || delta <= 500 || delta >= UPDATE_INTERVAL * 1.5) return
            subscription.updatePlayback((delta / 1000).toInt())
            _totalWatched += delta
            var countedView = false
            if (!_didCountView && video.duration > 0 && (_totalWatched / 1000) / video.duration.toDouble() > 0.4) {
                _didCountView = true
                subscription.addPlaybackView()
                countedView = true
            }
            subscription.saveAsync()
            _dirty = true
            if (countedView || now - _lastSyncTime >= SYNC_INTERVAL) flushPlayback()
        }
    }

    fun setTracker(tracker: IPlaybackTracker?) {
        val previous = synchronized(_trackerLock) {
            val old = _tracker
            _tracker = tracker
            old
        }
        if (previous != null && previous !== tracker) conclude(previous)
    }

    fun updateTracker(positionMs: Long, isPlaying: Boolean, forceUpdate: Boolean = false) {
        if (forceUpdate && !isPlaying) flushPlayback()
        if (StateApp.instance.privateMode) return
        val tracker = _tracker ?: return
        if (!forceUpdate && !tracker.shouldUpdate()) return
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            synchronized(tracker) {
                if (_tracker !== tracker || StateApp.instance.privateMode) return@synchronized
                try {
                    tracker.onProgress(positionMs.toDouble() / 1000, isPlaying)
                } catch (error: Throwable) {
                    Logger.e(TAG, "Failed to notify progress", error)
                }
            }
        }
    }

    fun cleanupTracker() {
        flushPlayback()
        setTracker(null)
    }

    fun flushPlayback() {
        synchronized(_trackingLock) {
            val subscription = _subscription ?: return
            if (!_dirty) return
            _dirty = false
            _lastSyncTime = System.currentTimeMillis()
            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                try {
                    val packet = synchronized(subscription) {
                        Json.encodeToString(SyncSubscriptionsPackage(listOf(subscription), emptyMap()))
                    }
                    StateSync.instance.broadcastData(GJSyncOpcodes.syncSubscriptions, packet)
                } catch (error: Throwable) {
                    Logger.w(TAG, "Failed to sync playback totals", error)
                }
            }
        }
    }

    private fun conclude(tracker: IPlaybackTracker) {
        CoroutineScope(Dispatchers.IO).launch {
            synchronized(tracker) {
                try {
                    tracker.onConcluded()
                } catch (error: Throwable) {
                    Logger.e(TAG, "Failed to clean up playback tracker", error)
                }
            }
        }
    }

    companion object {
        private const val TAG = "PlaybackTracking"
        private const val UPDATE_INTERVAL = 2500
        private const val SYNC_INTERVAL = 30_000

        fun getTracker(video: IPlatformVideoDetails): IPlaybackTracker? {
            if (StateApp.instance.privateMode) return null
            return video.getPlaybackTracker() ?: StatePlatform.instance.getPlaybackTracker(video.url)
        }
    }
}
