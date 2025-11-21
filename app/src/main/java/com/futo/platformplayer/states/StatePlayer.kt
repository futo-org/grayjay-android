package com.futo.platformplayer.states

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylistDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.services.MediaPlaybackService
import com.futo.platformplayer.video.PlayerManager
import kotlin.random.Random

/***
 * Used to keep track of queue and other player related stuff
 */
class StatePlayer {
    private val MIN_BUFFER_DURATION = 10000;
    private val MAX_BUFFER_DURATION = 60000;
    private val MIN_PLAYBACK_START_BUFFER = 500;
    private val MIN_PLAYBACK_RESUME_BUFFER = 2500;
    private val BUFFER_SIZE = 1024 * 64;

    var isOpen : Boolean = false
        private set;

    //Players
    private var _exoplayer : PlayerManager? = null;
    private var _thumbnailExoPlayer : PlayerManager? = null;
    private var _shortExoPlayer: PlayerManager? = null

    //Video Status
    var rotationLock: Boolean = false
        get() = field
        set(value) {
            field = value
            onRotationLockChanged.emit(value)
        }
    val onRotationLockChanged = Event1<Boolean>()
    var autoplay: Boolean = Settings.instance.playback.autoplay
        get() = field
        set(value) {
            if (field != value)
                _autoplayed.clear()
            field = value
            autoplayChanged.emit(value)
        }
    private val _autoplayed = hashSetOf<String>()
    fun wasAutoplayed(url: String?): Boolean {
        if (url == null) {
            return false
        }
        synchronized(_autoplayed) {
            return _autoplayed.contains(url)
        }
    }
    fun setAutoplayed(url: String?) {
        if (url == null) {
            return
        }
        synchronized(_autoplayed) {
            _autoplayed.add(url)
        }
    }

    val autoplayChanged = Event1<Boolean>()
    var loopVideo : Boolean = false;

    val isPlaying: Boolean get() = _exoplayer?.player?.playWhenReady ?: false;

    //Queue
    private val _queue = ArrayList<IPlatformVideo>();
    private var _queueShuffled: MutableList<IPlatformVideo>? = null;
    private var _queueType = TYPE_QUEUE;
    private var _queueName: String? = null;
    private var _queuePosition = -1;
    private var _queueRemoveOnFinish = false;
    var queueFocused : Boolean = false
        private set;
    var queueRepeat: Boolean = false
        private set;
    var queueShuffle: Boolean = false
        private set;

    val queueSize: Int get() {
        synchronized(_queue) {
            return _queue.size
        }
    }

    val hasQueue: Boolean get() {
        return queueSize > 1
    }

    val queueName: String get() = _queueName ?: _queueType;

    //Events
    val onVideoChanging = Event1<IPlatformVideo>();
    val onQueueChanged = Event1<Boolean>();
    val onPlayerOpened = Event0();
    val onPlayerClosed = Event0();

    var currentVideo: IPlatformVideo? = null
        private set;

    private var _currentPlaylistId: String? = null
    val playlistId: String? get() = if (_queueType == TYPE_PLAYLIST) _currentPlaylistId else null

    init {
        onQueueChanged.subscribe {
            updateLastQueue()
        }
    }

    fun setCurrentlyPlaying(video: IPlatformVideo?) {
        Log.i(TAG, "setCurrentlyPlaying ${video?.url} (${video?.name})")
        currentVideo = video;
    }


    //Player Status
    fun setPlayerOpen() {
        isOpen = true;
        onPlayerOpened.emit();
    }
    fun setPlayerClosed() {
        Log.i(TAG, "setCurrentlyPlaying (setPlayerClosed) null")
        setCurrentlyPlaying(null);
        isOpen = false;
        clearQueue();
        onPlayerClosed.emit();
        closeMediaSession();
    }

    fun saveQueueAsPlaylist(name: String){
        val videos = _queue.toList();
        val playlist = Playlist(name, videos.map { SerializedPlatformVideo.fromVideo(it) });
        StatePlaylists.instance.createOrUpdatePlaylist(playlist);
    }

    //Notifications
    fun hasMediaSession() : Boolean {
        return MediaPlaybackService.getService() != null;
    }
    fun startOrUpdateMediaSession(context: Context, videoUpdated: IPlatformVideoDetails?) {
        MediaPlaybackService.getOrCreateService(context) {
            it.updateMediaSession(videoUpdated);
        };
    }
    fun updateMediaSession(videoUpdated: IPlatformVideoDetails?) {
        MediaPlaybackService.getService()?.updateMediaSession(videoUpdated);
    }
    fun updateMediaSessionPlaybackState(state: Int, pos: Long) {
        MediaPlaybackService.getService()?.updateMediaSessionPlaybackState(state, pos);
    }
    fun closeMediaSession() {
        MediaPlaybackService.getService()?.closeMediaSession();
    }

    //Queue Status
    fun getQueueProgress(): Int {
        synchronized(_queue) {
            return _queuePosition;
        }
    }
    fun getQueueLength() : Int {
        synchronized(_queue) {
            return _queue.size;
        }
    }
    fun isInQueue(id : String) : Boolean {
        synchronized(_queue) {
            return _queue.any { it.id.value == id };
        }
    }

    fun isUrlInQueue(url : String) : Boolean {
        synchronized(_queue) {
            return _queue.any { it.url == url };
        }
    }

    fun getQueueType() : String {
        return _queueType;
    }
    fun getQueue() : List<IPlatformVideo> {
        synchronized(_queue) {
            val queueShuffled = _queueShuffled;
            if (queueShuffle && queueShuffled != null) {
                return queueShuffled.toList()
            } else {
                return _queue.toList()
            }
        }
    }

    fun setQueueType(queueType : String) {
        when(queueType) {
            TYPE_QUEUE -> {
                _queueRemoveOnFinish = false;
            }
            TYPE_WATCHLATER -> {
                _queueRemoveOnFinish = true;
            }
            TYPE_PLAYLIST -> {
                _queueRemoveOnFinish = false;
            }
        }
        _queueType = queueType;
    }

    fun setQueueRepeat(enabled: Boolean) {
        synchronized(_queue) {
            queueRepeat = enabled;
        }
    }
    fun setQueueShuffle(shuffle: Boolean) {
        synchronized(_queue) {
            queueShuffle = shuffle;
            if (shuffle) {
                createShuffledQueue();
            } else {
                _queueShuffled = null;
            }

            onQueueChanged.emit(false);
        }
    }

    private fun createShuffledQueue() {
        val currentItem = getCurrentQueueItem();
        if (_queuePosition == -1 || currentItem == null) {
            _queueShuffled = _queue.shuffled().toMutableList()
            return;
        }

        val nextItems = _queue.subList(Math.min(_queuePosition + 1, _queue.size - 1), _queue.size).shuffled();
        val previousItems = _queue.subList(0, _queuePosition).shuffled();
        _queueShuffled = (previousItems + currentItem + nextItems).toMutableList();
    }

    private fun addToShuffledQueue(video: IPlatformVideo) {
        val isLastVideo = _queuePosition + 1 >= _queue.size;
        if (isLastVideo) {
            _queueShuffled?.add(video)
        } else {
            val indexToInsert = Random.nextInt(_queuePosition + 1, _queue.size)
            _queueShuffled?.add(indexToInsert, video)
        }
    }
    private fun removeFromShuffledQueue(video: IPlatformVideo) {
        _queueShuffled?.remove(video);
    }

    //Modify Queue
    fun setQueue(videos: List<IPlatformVideo>, type: String, queueName: String? = null, focus: Boolean = false, shuffle: Boolean = false) {
        synchronized(_queue) {
            _queue.clear();
            setQueueType(type);
            _queueName = queueName;
            queueRepeat = false;
            _queue.addAll(videos);
            _queuePosition = 0;
            queueFocused = focus;
            queueShuffle = shuffle;
            if (shuffle) {
                createShuffledQueue();
            }
        }
        onQueueChanged.emit(true);
    }
    fun setPlaylist(playlist: Playlist, toPlayIndex: Int = 0, focus: Boolean = false, shuffle: Boolean = false) {
        synchronized(_queue) {
            _queue.clear();
            setQueueType(TYPE_PLAYLIST);
            _queueName = playlist.name;
            _queue.addAll(playlist.videos);
            queueFocused = focus;
            queueShuffle = shuffle;
            if (shuffle) {
                createShuffledQueue();
            }
            _queuePosition = toPlayIndex;
        }
        _currentPlaylistId = playlist.id
        StatePlaylists.instance.didPlay(playlist.id);

        onQueueChanged.emit(true);
    }
    fun setQueueWithPosition(videos: List<IPlatformVideo>, type: String, pos: Int, focus: Boolean = false) {
        //TODO: Implement support for pagination
        val index = if(videos.size <= pos) 0 else pos;
        synchronized(_queue) {
            _queue.clear();
            setQueueType(type);
            _queue.addAll(videos);
            queueShuffle = false;
            _queuePosition = index;
            queueFocused = focus;
        }
        onQueueChanged.emit(true);
    }
    fun setQueueWithExisting(videos: List<IPlatformVideo>, withFocus: Boolean = false) {
        val currentItem = getCurrentQueueItem();
        val index = videos.indexOf(currentItem);
        setQueueWithPosition(videos, _queueType, index, withFocus);
    }
    fun addToQueue(video: IPlatformVideo) {
        var didAdd = false;
        synchronized(_queue) {
            if(_queue.any { it.url == video.url }) {
                return@synchronized;
            }

            if(_queue.isEmpty()) {
                setQueueType(TYPE_QUEUE);
                currentVideo?.let {
                    _queue.add(it);
                }
            }

            _queue.add(video);
            if (queueShuffle) {
                addToShuffledQueue(video);
            }

            if (_queuePosition < 0) {
                _queuePosition = 0;
            }
            didAdd = true;
        }
        if(didAdd) {
            onQueueChanged.emit(true);
            StateApp.instance.contextOrNull?.let { context ->
                val name = if (video.name.length > 20) (video.name.subSequence(0, 20).toString() + "...") else video.name;
                UIDialogs.toast(context, context.getString(R.string.queued) + " [$name]", false);
            }
        }
        else
            StateApp.instance.contextOrNull?.let { context ->
                UIDialogs.toast(context, context.getString(R.string.already_queued), false);
            }
    }
    fun insertToQueue(video: IPlatformVideo, playNow: Boolean = false) {
        synchronized(_queue) {
            if(_queue.isEmpty()) {
                setQueueType(TYPE_QUEUE);
                currentVideo?.let {
                    _queue.add(it);
                }
            }
            if(_queue.isEmpty()) {
                _queue.add(video);
            } else {
                _queue.add(_queuePosition.coerceAtLeast(0).coerceAtMost(_queue.size - 1), video);
            }

            if (queueShuffle) {
                addToShuffledQueue(video);
            }

            if (_queuePosition < 0) {
                _queuePosition = 0;
            }
        }
        onQueueChanged.emit(true);
        if(playNow) {
            setQueuePosition(video);
        }
    }

    fun updateLastQueue() {
        val queueVideos = synchronized(_queue) {
            if (!_queue.isEmpty()) {
                return@synchronized _queue.map { SerializedPlatformVideo.fromVideo(it) }.toList()
            }

            return@synchronized null
        }

        if (queueVideos != null) {
            Logger.i(TAG, "Update last queue: ${queueVideos.size} videos.")
            val playlist = StatePlaylists.instance.getPlaylist(StatePlaylists.LAST_QUEUE_PLAYLIST_ID)?.apply {
                videos.clear()
                videos.addAll(queueVideos)
            } ?: Playlist("Last Queue", queueVideos).apply {
                id = StatePlaylists.LAST_QUEUE_PLAYLIST_ID
            }
            StatePlaylists.instance.createOrUpdatePlaylist(playlist)
        }
    }
    fun setQueuePosition(video: IPlatformVideo) {
          synchronized(_queue) {
              if (getCurrentQueueItem() == video) {
                  return;
              }

              _queuePosition = getQueuePosition(video);
              onVideoChanging.emit(video);
          }
    }
    fun getQueuePosition(video: IPlatformVideo): Int {
        synchronized(_queue) {
            val queueShuffled = _queueShuffled;
            return if (queueRepeat && queueShuffled != null) {
                queueShuffled.indexOf(video);
            } else {
                _queue.indexOf(video);
            }
        }
    }
    fun removeFromQueue(video: IPlatformVideo, shouldSwapCurrentItem: Boolean = false) {
        synchronized(_queue) {
            _queue.remove(video);
            if (queueShuffle)  {
                removeFromShuffledQueue(video);
            }
            if(currentVideo != null) {
                val newPos = _queue.indexOfFirst { it.url == currentVideo?.url };
                if(newPos >= 0)
                    _queuePosition = newPos;
            }

        }

        onQueueChanged.emit(shouldSwapCurrentItem);
    }
    fun clearQueue() {
        synchronized(_queue) {
            _queue.clear();
            _queueShuffled = null;
            queueShuffle = false;
            _queuePosition = -1;
        }
        onQueueChanged.emit(false);
    }

    //Queue Nav
    fun getCurrentQueueItem(adjustIfNegative: Boolean = true) : IPlatformVideo? {
        synchronized(_queue) {
            val shuffledQueue = _queueShuffled;
            val queue = if (queueShuffle && shuffledQueue != null) {
                shuffledQueue;
            } else {
                _queue;
            }

            if(adjustIfNegative && queue.isNotEmpty()) {
                if(_queuePosition == -1) {
                    return queue[0];
                } else if(_queuePosition < queue.size) {
                    return queue[_queuePosition];
                }
            } else if(_queuePosition >= 0 && _queuePosition < queue.size) {
                return queue[_queuePosition];
            }
        }
        return null;
    }

    /***
     * Checks what the prev queue item would without consuming it.
     * @param forceLoop If start of queue should be ignored and loop around to end without queueRepeat being true
     */
    fun getPrevQueueItem(forceLoop: Boolean = false) : IPlatformVideo? {
        synchronized(_queue) {
            if(_queue.size == 1) {
                return null;
            }
            if(_queue.size <= _queuePosition && currentVideo != null) {
                //Out of sync position
                val newPos = _queue.indexOfFirst { it.url == currentVideo?.url }
                if(newPos != -1)
                    _queuePosition = newPos;
            }

            val shuffledQueue = _queueShuffled;
            val queue = if (queueShuffle && shuffledQueue != null) {
                shuffledQueue;
            } else {
                _queue;
            }

            //Init Behavior
            if(_queuePosition == -1 && queue.isNotEmpty()) {
                return queue[0];
            }
            //Standard Behavior
            if(_queuePosition - 1 >= 0) {
                if(queue.size <= _queuePosition)
                    return null;
                return queue[_queuePosition - 1];
            }
            //Repeat Behavior (End of queue)
            if(queue.isNotEmpty() && (forceLoop || queueRepeat)) {
                return queue[_queue.size - 1];
            }
        }
        return null;
    }
    /***
     * Checks what the next queue item would without consuming it.
     * @param forceLoop If end of queue should be ignored and loop around to start without queueRepeat being true
     */
    fun getNextQueueItem(forceLoop: Boolean = false) : IPlatformVideo? {
        synchronized(_queue) {
            if(_queue.size == 1) {
                return null;
            }

            val shuffledQueue = _queueShuffled;
            val queue = if (queueShuffle && shuffledQueue != null) {
                shuffledQueue;
            } else {
                _queue;
            }

            //Init Behavior
            if(_queuePosition == -1 && queue.isNotEmpty()) {
                return queue[0];
            }
            //Standard Behavior
            if(_queuePosition + 1 < queue.size) {
                return queue[_queuePosition + 1];
            }
            //Repeat Behavior (End of queue)
            if(_queuePosition + 1 == queue.size && queue.isNotEmpty() && (forceLoop || queueRepeat)) {
                return queue[0];
            }
        }
        return null;
    }
    fun restartQueue() : IPlatformVideo? {
        synchronized(_queue) {
            _queuePosition = -1;
            return nextQueueItem(false, true);
        }
    };

    /***
     * Triggers the next queue item, removing it depending on the queue type, should ONLY be used if you're directly consuming this item
     * @param withoutRemoval Prevents the removal behavior of certain playlists, should be true for manual user actions like next
     * @param bypassVideoLoop Bypasses any single-video-looping behavior, should be true for manual user actions like next
     */
    fun nextQueueItem(withoutRemoval: Boolean = false, bypassVideoLoop: Boolean = false) : IPlatformVideo? {
        if(loopVideo && !bypassVideoLoop) {
            return currentVideo;
        }

        synchronized(_queue) {
            if (_queue.isEmpty()) {
                return null;
            }

            val nextPosition: Int;
            var isRepeat = false;
            val lastItem = getCurrentQueueItem(false);
            if(_queueRemoveOnFinish && !withoutRemoval && lastItem != null) {
                _queue.remove(lastItem);
                removeFromShuffledQueue(lastItem);
                nextPosition = _queuePosition;
            } else {
                if (_queuePosition + 1 >= _queue.size) {
                    isRepeat = true;
                    nextPosition = 0;
                } else {
                    nextPosition = _queuePosition + 1;
                }
            }

            if (_queue.isEmpty()) {
                return null;
            }

            if (isRepeat && !queueRepeat || isRepeat && _queue.size == 1) {
                return null;
            }

            _queuePosition = nextPosition
            return getCurrentQueueItem();
        }
    }

    /***
     * Triggers the prev queue item, removing it depending on the queue type
     * @param withoutRemoval Prevents the removal behavior of certain playlists, should be true for manual user actions like next
     */
    fun prevQueueItem(withoutRemoval: Boolean = false) : IPlatformVideo? {
        synchronized(_queue) {
            if (_queue.size == 0) {
                return null;
            }

            val currentPos = _queuePosition;
            _queuePosition = if(_queueRemoveOnFinish && !withoutRemoval) {
                _queue.removeAt(currentPos);
                (_queuePosition - 1);
            } else {
                (_queuePosition - 1);
            }

            if(_queuePosition < 0) {
                _queuePosition += _queue.size;
            }

            if(_queuePosition < _queue.size) {
                return getCurrentQueueItem();
            }
        }
        return null;
    }

    fun setQueueItem(video: IPlatformVideo) : IPlatformVideo {
        synchronized(_queue) {
            val index = _queue.indexOf(video);
            if(index >= 0) {
                _queuePosition = index;
                return video;
            }
            else {
                _queue.add(_queuePosition, video);
                return video;
            }
        }
    }

    //Player Initialization
    fun getPlayerOrCreate(context: Context) : PlayerManager {
        if(_exoplayer == null) {
            val player = createExoPlayer(context);
            _exoplayer = PlayerManager(player);
        }
        return _exoplayer!!;
    }
    fun getThumbnailPlayerOrCreate(context: Context) : PlayerManager {
        if(_thumbnailExoPlayer == null) {
            val player = createExoPlayer(context);
            _thumbnailExoPlayer = PlayerManager(player);
        }
        return _thumbnailExoPlayer!!;
    }
    fun getShortPlayerOrCreate(context: Context) : PlayerManager {
        if(_shortExoPlayer == null) {
            val player = createExoPlayer(context);
            _shortExoPlayer = PlayerManager(player);
        }
        return _shortExoPlayer!!;
    }

    @OptIn(UnstableApi::class)
    private fun createExoPlayer(context : Context): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setAllocator(DefaultAllocator(true, BUFFER_SIZE))
                    .setBufferDurationsMs(
                        MIN_BUFFER_DURATION,
                        MAX_BUFFER_DURATION,
                        MIN_PLAYBACK_START_BUFFER,
                        MIN_PLAYBACK_RESUME_BUFFER
                    )
                    .setTargetBufferBytes(-1)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build())
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .build();
    }

    fun dispose(){
        val player = _exoplayer;
        val thumbPlayer = _thumbnailExoPlayer;
        val shortPlayer = _shortExoPlayer
        _exoplayer = null;
        _thumbnailExoPlayer = null;
        _shortExoPlayer = null
        player?.release();
        thumbPlayer?.release();
        shortPlayer?.release()
    }


    companion object {
        val TAG = "PlayerState";
        val TYPE_QUEUE = "Queue";
        val TYPE_PLAYLIST = "Playlist";
        val TYPE_WATCHLATER = "Watch Later";

        private var _instance : StatePlayer? = null;
        val instance : StatePlayer
            get(){
            if(_instance == null)
                _instance = StatePlayer();
            return _instance!!;
        };

        fun dispose(){
            val instance = _instance;
            _instance = null;
            instance?.dispose();
            Logger.i(TAG, "Disposed StatePlayer");
        }
    }
}