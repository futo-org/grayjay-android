package com.futo.platformplayer.states

import android.content.Context
import android.util.Log
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylistDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.services.MediaPlaybackService
import com.futo.platformplayer.video.PlayerManager
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.upstream.DefaultAllocator
import kotlin.random.Random

/***
 * Used to keep track of queue and other player related stuff
 */
class StatePlayer {
    private val MIN_BUFFER_DURATION = 10000;
    private val MAX_BUFFER_DURATION = 60000;
    private val MIN_PLAYBACK_START_BUFFER = 500;
    private val MIN_PLAYBACK_RESUME_BUFFER = 1000;
    private val BUFFER_SIZE = 1024 * 64;

    var isOpen : Boolean = false
        private set;

    //Players
    private var _exoplayer : PlayerManager? = null;
    private var _thumbnailExoPlayer : PlayerManager? = null;

    //Video Status
    var rotationLock : Boolean = false;

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

    val queueName: String get() = _queueName ?: _queueType;

    //Events
    val onVideoChanging = Event1<IPlatformVideo>();
    val onQueueChanged = Event1<Boolean>();
    val onPlayerOpened = Event0();
    val onPlayerClosed = Event0();

    var currentVideo: IPlatformVideoDetails? = null
        private set;

    fun setCurrentlyPlaying(video: IPlatformVideoDetails?) {
        currentVideo = video;
    }


    //Player Status
    fun setPlayerOpen() {
        isOpen = true;
        onPlayerOpened.emit();
    }
    fun setPlayerClosed() {
        setCurrentlyPlaying(null);
        isOpen = false;
        clearQueue();
        onPlayerClosed.emit();
        closeMediaSession();
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
    fun setQueueShuffle(shuffle: Boolean, excludeCurrent: Boolean = true) {
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
    fun setPlaylist(playlist: IPlatformPlaylistDetails, toPlayIndex: Int = 0, focus: Boolean = false, shuffle: Boolean = false) {
        synchronized(_queue) {
            _queue.clear();
            setQueueType(TYPE_PLAYLIST);
            _queueName = playlist.name;
            _queue.addAll(playlist.contents.getResults());
            queueFocused = focus;
            queueShuffle = shuffle;
            if (shuffle) {
                createShuffledQueue();
            }
            _queuePosition = toPlayIndex;
        }
        playlist.id.value?.let { StatePlaylists.instance.didPlay(it); };

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
        synchronized(_queue) {
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
        }
        onQueueChanged.emit(true);
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
            return if (queueRepeat && queueShuffled != null)
                queueShuffled.indexOf(video);
            else
                _queue.indexOf(video);
        }
    }
    fun removeFromQueue(video: IPlatformVideo, shouldSwapCurrentItem: Boolean = false) {
        synchronized(_queue) {
            _queue.remove(video);
            if (queueShuffle)  {
                removeFromShuffledQueue(video);
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
                if(_queuePosition == -1)
                    return queue[0];
                else if(_queuePosition < queue.size)
                    return queue[_queuePosition];
            } else if(_queuePosition >= 0 && _queuePosition < queue.size) {
                return queue[_queuePosition];
            }
        }
        return null;
    }

    fun getNextQueueItem() : IPlatformVideo? {
        synchronized(_queue) {
            val shuffledQueue = _queueShuffled;
            val queue = if (queueShuffle && shuffledQueue != null) {
                shuffledQueue;
            } else {
                _queue;
            }

            //Init Behavior
            if(_queuePosition == -1 && queue.isNotEmpty())
                return queue[0];
            //Standard Behavior
            if(_queuePosition + 1 < queue.size)
                return queue[_queuePosition + 1];
            //Repeat Behavior (End of queue)
            if(_queuePosition + 1 == queue.size && queue.isNotEmpty() && queueRepeat)
                return queue[0];
        }
        return null;
    }
    fun restartQueue() : IPlatformVideo? {
        synchronized(_queue) {
            _queuePosition = -1;
            return nextQueueItem();
        }
    };
    fun nextQueueItem(withoutRemoval: Boolean = false) : IPlatformVideo? {
        synchronized(_queue) {
            if (_queue.isEmpty())
                return null;

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

    fun prevQueueItem(withoutRemoval: Boolean = false) : IPlatformVideo? {
        synchronized(_queue) {
            if (_queue.size == 0) {
                return null;
            }

            val currentPos = _queuePosition;

            if(_queueRemoveOnFinish && !withoutRemoval) {
                _queue.removeAt(currentPos);
                _queuePosition = (_queuePosition - 1);
            }
            else
                _queuePosition = (_queuePosition - 1);
            if(_queuePosition < 0)
                _queuePosition += _queue.size;
            if(_queuePosition < _queue.size)
                return _queue[_queuePosition];
        }
        return null;
    }

    fun setQueueItem(video: IPlatformVideo) : IPlatformVideo? {
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
    fun getPlayerOrCreate(context : Context) : PlayerManager {
        if(_exoplayer == null) {
            val player = createExoPlayer(context);
            _exoplayer = PlayerManager(player);
        }
        return _exoplayer!!;
    }
    fun getThumbnailPlayerOrCreate(context : Context) : PlayerManager {
        if(_thumbnailExoPlayer == null) {
            val player = createExoPlayer(context);
            _thumbnailExoPlayer = PlayerManager(player);
        }
        return _thumbnailExoPlayer!!;
    }
    private fun createExoPlayer(context : Context) : ExoPlayer {
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
        _exoplayer = null;
        _thumbnailExoPlayer = null;
        player?.release();
        thumbPlayer?.release();
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
        }
    }
}