package com.futo.platformplayer.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.downloads.VideoDownload
import com.futo.platformplayer.exceptions.DownloadException
import com.futo.platformplayer.getNowDiffMinutes
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.Announcement
import com.futo.platformplayer.states.AnnouncementType
import com.futo.platformplayer.states.StateAnnouncement
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.stores.FragmentedStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketException
import java.time.Duration
import java.time.OffsetDateTime

class DownloadService : Service() {
    private val TAG = "DownloadService";

    private val DOWNLOAD_NOTIF_ID = 3;
    private val DOWNLOAD_NOTIF_TAG = "download";
    private val DOWNLOAD_NOTIF_CHANNEL_ID = "downloadChannel";
    private val DOWNLOAD_NOTIF_CHANNEL_NAME = "Downloads";

    //Context
    private val _scope: CoroutineScope = CoroutineScope(Dispatchers.Default);
    private var _notificationManager: NotificationManager? = null;
    private var _notificationChannel: NotificationChannel? = null;
    private var _isForeground = false

    private val _client = ManagedHttpClient(OkHttpClient.Builder()
        //.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getByName("192.168.1.175"), 8081)))
        .readTimeout(Duration.ofMinutes(0))
        .writeTimeout(Duration.ofMinutes(0))
        .connectTimeout(Duration.ofSeconds(100))
        .callTimeout(Duration.ofMinutes(0)))

    private var _started = false;

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "onStartCommand");
        synchronized(this) {
            if(_started)
                return START_NOT_STICKY;

            if(!FragmentedStorage.isInitialized) {
                Logger.i(TAG, "Attempted to start DownloadService without initialized files")
                closeDownloadSession()
                return START_NOT_STICKY
            }
            _started = true;
        }
        setupNotificationRequirements();
        notifyDownload(null);

        _callOnStarted?.invoke(this);
        _instance = this;

        _scope.launch {
            try {
                doDownloading();
            }
            catch(ex: Throwable) {
                try {
                    StateAnnouncement.instance.registerAnnouncementSession(
                        Announcement(
                            "rootDownloadException",
                            "An root download service exception happened",
                            ex.message ?: "",
                            AnnouncementType.SESSION,
                            OffsetDateTime.now()
                        )
                    );
                } catch(_: Throwable){}
                try {
                    closeDownloadSession();
                }
                catch(ex: Throwable) {

                }
            }
        };

        return START_STICKY;
    }
    fun setupNotificationRequirements() {
        _notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (_notificationChannel == null) {
            _notificationChannel = NotificationChannel(
                DOWNLOAD_NOTIF_CHANNEL_ID,
                DOWNLOAD_NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
            }
        }
        _notificationManager?.createNotificationChannel(_notificationChannel!!)
    }

    override fun onCreate() {
        Logger.i(TAG, "onCreate");
        super.onCreate()

        setupNotificationRequirements()

        val bootstrapNotif = NotificationCompat.Builder(this, DOWNLOAD_NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Preparing downloads...")
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(DOWNLOAD_NOTIF_ID, bootstrapNotif, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else
            startForeground(DOWNLOAD_NOTIF_ID, bootstrapNotif)

        _isForeground = true
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null;
    }

    private suspend fun doDownloading() {
        Logger.i(TAG, "doDownloading - Starting Downloads");
        val ignore = mutableListOf<VideoDownload>();
        var currentVideo: VideoDownload? = StateDownloads.instance.getDownloading().firstOrNull();
        while (currentVideo != null)
        {
            try {
                doDownload(currentVideo);
            }
            catch(ex: SocketException) {
                var msg = ex.message;
                if(ex.message == "Software caused connection abort")
                    msg = "Downloading disabled on current network";

                Logger.e(TAG, "Failed download [${currentVideo.name}]: ${msg}", ex);
                currentVideo.error = msg;
                currentVideo.changeState(VideoDownload.State.ERROR);
                ignore.add(currentVideo);

                //Give it a sec
                Thread.sleep(500);
            }
            catch(ex: Throwable) {
                Logger.e(TAG, "Download failed", ex);
                if(currentVideo.video == null && currentVideo.videoDetails == null) {
                    //Corrupt?
                    Logger.w(TAG, "Video had no video or videodetail, removing download");
                    StateDownloads.instance.removeDownload(currentVideo);
                }
                else if(ex is DownloadException && !ex.isRetryable) {
                    Logger.w(TAG, "Video had exception that should not be retried");
                    StateDownloads.instance.removeDownload(currentVideo);
                    StateDownloads.instance.preventPlaylistDownload(currentVideo);
                }
                else
                    Logger.e(TAG, "Failed download [${currentVideo.name}]: ${ex.message}", ex);
                currentVideo.error = ex.message;
                currentVideo.changeState(VideoDownload.State.ERROR);
                ignore.add(currentVideo);

                if(ex !is CancellationException) {
                    StateAnnouncement.instance.registerAnnouncement(
                        currentVideo.id.value ?: ("" + currentVideo.id.pluginId),
                        "Download failed",
                        "Download for [${currentVideo.name}] failed.\nDownloads are automatically retried.\nReason: ${ex.message}",
                        AnnouncementType.SESSION,
                        null,
                        "download"
                    );
                }

                //Give it a sec
                Thread.sleep(500);
            }
            StateDownloads.instance.updateDownloading(currentVideo);

            currentVideo = StateDownloads.instance.getDownloading().filter { !ignore.contains(it) }.firstOrNull();
        }
        Logger.i(TAG, "doDownloading - Ending Downloads");
        stopService(this);
    }


    private suspend fun doDownload(download: VideoDownload) {
        if(!Settings.instance.downloads.shouldDownload())
            throw IllegalStateException("Downloading disabled on current network");

        if((download.prepareTime?.getNowDiffMinutes() ?: 99) > 15) {
            Logger.w(TAG, "Video Download [${download.name}] expired, re-preparing");
            download.videoDetails = null;

            if(download.targetVideoName == null && download.videoSource != null)
                download.targetVideoName = download.videoSource!!.name;
            if(download.targetPixelCount == null && download.videoSource != null)
                download.targetPixelCount = (download.videoSource!!.width * download.videoSource!!.height).toLong();
            download.videoSource = null;

            if(download.targetAudioName == null && download.audioSource != null)
                download.targetAudioName = download.audioSource!!.name;
            if(download.targetBitrate == null && download.audioSource != null)
                download.targetBitrate = download.audioSource!!.bitrate.toLong();
            download.audioSource = null;
        }
        if(download.videoDetails == null || (!download.isVideoDownloadReady || !download.isAudioDownloadReady))
            download.changeState(VideoDownload.State.PREPARING);
        notifyDownload(download);

        Logger.i(TAG, "Preparing [${download.name}] started");
        if(download.state == VideoDownload.State.PREPARING)
            download.prepare(_client);
        download.changeState(VideoDownload.State.DOWNLOADING);
        notifyDownload(download);

        var lastNotifyTime: Long = 0L;
        Logger.i(TAG, "Downloading [${download.name}] started");
        //TODO: Use plugin client?
        download.download(applicationContext, _client) { progress ->
            download.progress = progress;

            val currentTime = System.currentTimeMillis();
            if (currentTime - lastNotifyTime > 800) {
                notifyDownload(download);
                lastNotifyTime = currentTime;
            }
        };
        Logger.i(TAG, "Download [${download.name}] finished");

        download.changeState(VideoDownload.State.VALIDATING);
        notifyDownload(download);

        Logger.i(TAG, "Validating [${download.name}]");
        download.validate();
        download.changeState(VideoDownload.State.FINALIZING);
        notifyDownload(download);

        Logger.i(TAG, "Completing [${download.name}]");
        download.complete();
        download.changeState(VideoDownload.State.COMPLETED);

        StateDownloads.instance.removeDownload(download);
        notifyDownload(download);
    }

    private fun notifyDownload(download: VideoDownload?) {
        val channelId = DOWNLOAD_NOTIF_CHANNEL_ID
        val bringUpIntent = Intent(this, MainActivity::class.java);
        bringUpIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        bringUpIntent.action = "TAB";
        bringUpIntent.putExtra("TAB", "Downloads");

        val builder = if(download != null)
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_download)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(PendingIntent.getActivity(this, 5, bringUpIntent, PendingIntent.FLAG_IMMUTABLE))
                .setContentTitle("${download.state}: ${download.name}")
                .setContentText(download.getDownloadInfo())
                .setProgress(100, (download.progress * 100).toInt(), download.progress == 0.0)
                .setChannelId(channelId)
        else
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_download)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(PendingIntent.getActivity(this, 5, bringUpIntent, PendingIntent.FLAG_IMMUTABLE))
                .setContentTitle("Preparing for download...")
                .setContentText("Initializing download process...")
                .setChannelId(channelId)

        val notif = builder.build();
        notif.flags = notif.flags or NotificationCompat.FLAG_ONGOING_EVENT or NotificationCompat.FLAG_NO_CLEAR;

        if (_isForeground) {
            _notificationManager?.notify(DOWNLOAD_NOTIF_ID, notif)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                startForeground(DOWNLOAD_NOTIF_ID, notif, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else
                startForeground(DOWNLOAD_NOTIF_ID, notif)
            _isForeground = true
        }
    }

    fun closeDownloadSession() {
        Logger.i(TAG, "closeDownloadSession")
        if (_isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            _isForeground = false
        }
        _notificationManager?.cancel(DOWNLOAD_NOTIF_ID)
        _started = false
        super.stopSelf()
    }

    override fun onDestroy() {
        Logger.i(TAG, "onDestroy");
        _instance = null;
        _scope.cancel("onDestroy");
        super.onDestroy();
    }

    companion object {
        private var _instance: DownloadService? = null;
        private var _callOnStarted: ((DownloadService)->Unit)? = null;

        @Synchronized
        fun getOrCreateService(context: Context, handle: ((DownloadService)->Unit)? = null) {
            if(!FragmentedStorage.isInitialized)
                return;
            if(_instance == null) {
                _callOnStarted = handle;
                val intent = Intent(context, DownloadService::class.java);
                context.startForegroundService(intent);
            }
            else _instance?.let {
                if(handle != null)
                    handle(it);
            }
        }
        @Synchronized
        fun getService() : DownloadService? {
            return _instance;
        }

        @Synchronized
        fun stopService(service: DownloadService? = null) {
            (service ?: _instance)?.let {
                if(_instance == it)
                    _instance = null;
                it.closeDownloadSession();
            }
        }
    }
}