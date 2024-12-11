package com.futo.platformplayer.services

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaMetadata
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.receivers.MediaButtonReceiver
import com.futo.platformplayer.receivers.MediaControlReceiver
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.stores.FragmentedStorage

class MediaPlaybackService : Service() {
    private val TAG = "MediaPlaybackService";

    private val MEDIA_NOTIF_ID = 2;
    private val MEDIA_NOTIF_TAG = "media";
    private val MEDIA_NOTIF_CHANNEL_ID = "mediaChannel";
    private val MEDIA_NOTIF_CHANNEL_NAME = "Player";

    //Notifs
    private var _notif_last_video: IPlatformVideo? = null;
    private var _notif_last_bitmap: Bitmap? = null;

    //Context
    private var _audioManager: AudioManager? = null;
    private var _notificationManager: NotificationManager? = null;
    private var _notificationChannel: NotificationChannel? = null;
    private var _mediaSession: MediaSessionCompat? = null;
    private var _hasFocus: Boolean = false;
    private var _isTransientLoss: Boolean = false;
    private var _focusRequest: AudioFocusRequest? = null;
    private var _audioFocusLossTime_ms: Long? = null
    private var _playbackState = PlaybackStateCompat.STATE_NONE;
    private var _lastAudioFocusAttempt_ms: Long? = null
    private val isPlaying get() = _playbackState != PlaybackStateCompat.STATE_PAUSED &&
        _playbackState != PlaybackStateCompat.STATE_STOPPED &&
        _playbackState != PlaybackStateCompat.STATE_NONE &&
        _playbackState != PlaybackStateCompat.STATE_ERROR

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.v(TAG, "onStartCommand");


        if(!FragmentedStorage.isInitialized) {
            Logger.i(TAG, "Attempted to start MediaPlaybackService without initialized files");
            closeMediaSession();
            return START_NOT_STICKY;
        }

        try {
            setupNotificationRequirements();

            notifyMediaSession(null, null);

            _callOnStarted?.invoke(this);
            _instance = this;
        }
        catch(ex: Throwable) {
            Logger.e(TAG, "Failed to start MediaPlaybackService due to: " + ex.message, ex);
            closeMediaSession();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    fun setupNotificationRequirements() {
        _audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager;
        _notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        _notificationChannel = NotificationChannel(MEDIA_NOTIF_CHANNEL_ID, MEDIA_NOTIF_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            this.enableVibration(false);
            this.setSound(null, null);
        };
        _notificationManager!!.createNotificationChannel(_notificationChannel!!);

        _mediaSession = MediaSessionCompat(this, "PlayerState");
        _mediaSession?.isActive = true
        _mediaSession?.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
            .build());
        _mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
                Logger.i(TAG, "Media session callback onSeekTo(pos = $pos)");
                MediaControlReceiver.onSeekToReceived.emit(pos);
            }

            override fun onPlay() {
                super.onPlay();
                Logger.i(TAG, "Media session callback onPlay()");
                MediaControlReceiver.onPlayReceived.emit();
            }

            override fun onPause() {
                super.onPause();
                Logger.i(TAG, "Media session callback onPause()");
                MediaControlReceiver.onPauseReceived.emit();
            }

            override fun onStop() {
                super.onStop();
                Logger.i(TAG, "Media session callback onStop()");
                //MediaControlReceiver.onCloseReceived.emit();
                MediaControlReceiver.onPauseReceived.emit();
                updateMediaSession( null);
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious();
                Logger.i(TAG, "Media session callback onSkipToPrevious()");
                MediaControlReceiver.onPreviousReceived.emit();
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                Logger.i(TAG, "Media session callback onSkipToNext()");
                MediaControlReceiver.onNextReceived.emit();
            }
        });
        _mediaSession?.setMediaButtonReceiver(PendingIntent.getBroadcast(
            this@MediaPlaybackService,
            0,
            Intent(Intent.ACTION_MEDIA_BUTTON).setClass(this@MediaPlaybackService, MediaButtonReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ))
    }

    override fun onCreate() {
        Logger.v(TAG, "onCreate");
        super.onCreate()
    }

    override fun onDestroy() {
        Logger.v(TAG, "onDestroy");
        _instance = null;
        MediaControlReceiver.onPauseReceived.emit();
        super.onDestroy();
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null;
    }

    fun closeMediaSession() {
        Logger.v(TAG, "closeMediaSession");
        stopForeground(STOP_FOREGROUND_REMOVE);

        abandonAudioFocus()

        val notifManager = _notificationManager;
        Logger.i(TAG, "Cancelling playback notification (notifManager: ${notifManager != null})");
        notifManager?.cancel(MEDIA_NOTIF_ID);
        _notif_last_video = null;
        _notif_last_bitmap = null;
        _mediaSession = null;

        if(_instance == this)
            _instance = null;
        this.stopSelf();
    }

    fun updateMediaSession(videoUpdated: IPlatformVideo?) {
        Logger.v(TAG, "updateMediaSession");
        var isUpdating = false;
        val video: IPlatformVideo;
        var lastBitmap: Bitmap? = null
        if(videoUpdated == null) {
            val notifLastVideo = _notif_last_video ?: return;
            video = notifLastVideo;
            isUpdating = true;
            lastBitmap = _notif_last_bitmap;
        }
        else
            video = videoUpdated;

        if(_notificationChannel == null || _mediaSession == null)
            setupNotificationRequirements();

        _mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadata.METADATA_KEY_ARTIST, video.author.name)
                .putString(MediaMetadata.METADATA_KEY_TITLE, video.name)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, video.duration * 1000)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, lastBitmap)
                .build());

        val thumbnail = video.thumbnails.getHQThumbnail();

        _notif_last_video = video;

        if(isUpdating)
            notifyMediaSession(video, _notif_last_bitmap);
        else if(thumbnail != null) {
            notifyMediaSession(video, null);
            val tag = video;
            Glide.with(this).asBitmap()
                .load(thumbnail)
                .into(object: CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap,transition: Transition<in Bitmap>?) {
                        if(tag == _notif_last_video) {
                            notifyMediaSession(video, resource)
                            _mediaSession?.setMetadata(
                                MediaMetadataCompat.Builder()
                                    .putString(MediaMetadata.METADATA_KEY_ARTIST, video.author.name)
                                    .putString(MediaMetadata.METADATA_KEY_TITLE, video.name)
                                    .putLong(MediaMetadata.METADATA_KEY_DURATION, video.duration * 1000)
                                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, resource)
                                    .build());
                        }
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        if(tag == _notif_last_video)
                            notifyMediaSession(video, null)
                    }
                });
        }
        else
            notifyMediaSession(video, null);
    }
    private fun generateMediaAction(icon: Int, title: String, intent: PendingIntent) : NotificationCompat.Action {
        return NotificationCompat.Action.Builder(icon, title, intent).build();
    }
    private fun notifyMediaSession(video: IPlatformVideo?, desiredBitmap: Bitmap?) {
        val channel = _notificationChannel ?: return;
        val session = _mediaSession ?: return;
        val icon = StatePlatform.instance.getPlatformIcon(video?.id?.pluginId)?.resId ?: R.drawable.ic_play_white_nopad;
        var bitmap = desiredBitmap;

        val bringUpIntent = Intent(this, MainActivity::class.java);
        bringUpIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        val hasQueue = StatePlayer.instance.getNextQueueItem() != null;

        /* Fixes album art on older devices, not sure we wanna use it yet.
        if(desiredBitmap != null) {
            _mediaSession?.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, video.author.name)
                    .putString(MediaMetadata.METADATA_KEY_TITLE, video.name)
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, desiredBitmap)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, video.duration * 1000)
                    .build());
        }*/

        val deleteIntent = MediaControlReceiver.getCloseIntent(this, 99);
        var builder = NotificationCompat.Builder(this, MEDIA_NOTIF_TAG)
            .setSmallIcon(icon)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(this, 5, bringUpIntent, PendingIntent.FLAG_IMMUTABLE))
            .setStyle(if(hasQueue)
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            else
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0))
            .setDeleteIntent(deleteIntent)
            .setChannelId(channel.id)

        val playWhenReady = StatePlayer.instance.isPlaying;

        if(hasQueue)
            builder = builder.addAction(generateMediaAction(
                R.drawable.ic_fast_rewind_notif,
                "Back",
                MediaControlReceiver.getPrevIntent(this, 3)
            ))

        if(playWhenReady)
            builder = builder.addAction(generateMediaAction(
                R.drawable.ic_pause_notif,
                "Pause",
                MediaControlReceiver.getPauseIntent(this, 2)
            ));
        else
            builder = builder.addAction(generateMediaAction(
                R.drawable.ic_play_notif,
                "Play",
                MediaControlReceiver.getPlayIntent(this, 1)
            ));

        if(hasQueue)
            builder = builder.addAction(generateMediaAction(
                R.drawable.ic_fast_forward_notif,
                "Forward",
                MediaControlReceiver.getNextIntent(this, 4)
            ));

        builder = builder.addAction(generateMediaAction(
            R.drawable.ic_stop_notif,
            "Stop",
            MediaControlReceiver.getCloseIntent(this, 5)
        ));

        if(bitmap?.isRecycled ?: false)
            bitmap = null;
        if(bitmap != null)
            builder.setLargeIcon(bitmap);

        val notif = builder.build();
        notif.flags = notif.flags or NotificationCompat.FLAG_ONGOING_EVENT or NotificationCompat.FLAG_NO_CLEAR;

        Logger.i(TAG, "Updating notification bitmap=${if (bitmap != null) "yes" else "no."} channelId=${channel.id} icon=${icon} video=${video?.name ?: ""} playWhenReady=${playWhenReady} session.sessionToken=${session.sessionToken}");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(MEDIA_NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(MEDIA_NOTIF_ID, notif);
        }

        _notif_last_bitmap = bitmap;
    }

    fun updateMediaSessionPlaybackState(state: Int, pos: Long) {
        _mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .setState(state, pos, 1f, SystemClock.elapsedRealtime())
                .build());

        _playbackState = state;
        try {
            setAudioFocus()
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to set audio focus", e)
        }
    }

    //TODO: (TBD) This code probably more fitting inside FutoVideoPlayer, as this service is generally only used for global events
    private fun setAudioFocus() {
        if (!isPlaying) {
            return
        }

        if (_hasFocus || _isTransientLoss) {
            return;
        }

        val now = System.currentTimeMillis()
        val lastAudioFocusAttempt_ms = _lastAudioFocusAttempt_ms
        if (lastAudioFocusAttempt_ms == null || now - lastAudioFocusAttempt_ms > 1000) {
            _lastAudioFocusAttempt_ms = now
        } else {
            Log.v(TAG, "Skipped trying to get audio focus because gaining audio focus was recently attempted.");
            return
        }

        if (_focusRequest == null) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(_audioFocusChangeListener)
                .build()

            _focusRequest = focusRequest;
            Log.i(TAG, "Created audio focus request.");
        }

        Log.i(TAG, "Requesting audio focus.");

        val result = _audioManager?.requestAudioFocus(_focusRequest!!)
        Log.i(TAG, "Audio focus request result $result");
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            _hasFocus = true
            _isTransientLoss = false
            Log.i(TAG, "Audio focus received");
        } else if (result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
            _hasFocus = false
            _isTransientLoss = false
            Log.i(TAG, "Audio focus delayed, waiting for focus")
        } else {
            _hasFocus = false
            _isTransientLoss = false
            Log.i(TAG, "Audio focus not granted, retrying later")
        }

        Log.i(TAG, "Audio focus requested.");
    }

    private fun abandonAudioFocus() {
        val focusRequest = _focusRequest;
        if (focusRequest != null) {
            Logger.i(TAG, "Audio focus abandoned")
            _audioManager?.abandonAudioFocusRequest(focusRequest);
            _focusRequest = null;
        }
        _hasFocus = false;
        _isTransientLoss = false;
    }

    private val _audioFocusChangeListener =
        OnAudioFocusChangeListener { focusChange ->
            try {
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        _hasFocus = true;
                        _isTransientLoss = false;

                        val audioFocusLossDuration = _audioFocusLossTime_ms?.let { System.currentTimeMillis() - it }
                        _audioFocusLossTime_ms = null
                        Log.i(TAG, "Audio focus gained (restartPlaybackAfterLoss = ${Settings.instance.playback.restartPlaybackAfterLoss}, _audioFocusLossTime_ms = $_audioFocusLossTime_ms, audioFocusLossDuration = ${audioFocusLossDuration})");

                        if (Settings.instance.playback.restartPlaybackAfterLoss == 1) {
                            if (audioFocusLossDuration != null && audioFocusLossDuration < 1000 * 10) {
                                MediaControlReceiver.onPlayReceived.emit()
                            }
                        } else if (Settings.instance.playback.restartPlaybackAfterLoss == 2) {
                            if (audioFocusLossDuration != null && audioFocusLossDuration < 1000 * 30) {
                                MediaControlReceiver.onPlayReceived.emit()
                            }
                        } else if (Settings.instance.playback.restartPlaybackAfterLoss == 3) {
                            MediaControlReceiver.onPlayReceived.emit()
                        }
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        _audioFocusLossTime_ms = if (isPlaying) {
                            System.currentTimeMillis()
                        } else {
                            null
                        }

                        _hasFocus = false;
                        _isTransientLoss = true;
                        MediaControlReceiver.onPauseReceived.emit();
                        Log.i(TAG, "Audio focus transient loss (_audioFocusLossTime_ms = ${_audioFocusLossTime_ms})");
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        Log.i(TAG, "Audio focus transient loss, can duck");
                        _hasFocus = true;
                        _isTransientLoss = true;
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        _audioFocusLossTime_ms = if (isPlaying) {
                            System.currentTimeMillis()
                        } else {
                            null
                        }

                        MediaControlReceiver.onPauseReceived.emit();
                        abandonAudioFocus();
                        Log.i(TAG, "Audio focus lost");
                    }
                }
            } catch(ex: Throwable) {
                Logger.w(TAG, "Failed to handle audio focus event", ex);
            }
        }

    companion object {
        private const val TAG = "MediaPlaybackService";
        private var _ignore = false;
        private var _instance: MediaPlaybackService? = null;

        private var _callOnStarted: ((MediaPlaybackService)->Unit)? = null;

        @Synchronized
        fun getOrCreateService(context: Context, handle: (MediaPlaybackService)->Unit) {
            if(_instance == null) {
                _callOnStarted = handle;
                val intent = Intent(context, MediaPlaybackService::class.java);
                context.startForegroundService(intent);
            }
            else _instance?.let {
                handle(it);
            }
        }
        @Synchronized
        fun getService() : MediaPlaybackService? {
            return _instance;
        }

        @Synchronized
        fun closeService() {
            _instance?.let {
                _instance = null;
                it.closeMediaSession();
            }
        }
    }
}