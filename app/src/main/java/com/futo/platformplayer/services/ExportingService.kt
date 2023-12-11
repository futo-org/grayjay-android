package com.futo.platformplayer.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.downloads.VideoExport
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.share
import com.futo.platformplayer.states.Announcement
import com.futo.platformplayer.states.AnnouncementType
import com.futo.platformplayer.states.StateAnnouncement
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.stores.FragmentedStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.util.UUID


class ExportingService : Service() {
    private val TAG = "ExportingService";

    private val EXPORT_NOTIF_ID = 4;
    private val EXPORT_NOTIF_TAG = "export";
    private val EXPORT_NOTIF_CHANNEL_ID = "exportChannel";

    //Context
    private val _scope: CoroutineScope = CoroutineScope(Dispatchers.Default);
    private var _notificationManager: NotificationManager? = null;
    private var _notificationChannel: NotificationChannel? = null;

    private val _client = ManagedHttpClient();

    private var _started = false;

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "onStartCommand");

        synchronized(this) {
            if(_started)
                return START_STICKY;

            if(!FragmentedStorage.isInitialized) {
                closeExportSession();
                return START_NOT_STICKY;
            }

            _started = true;
        }
        setupNotificationRequirements();

        _callOnStarted?.invoke(this);
        _instance = this;

        _scope.launch {
            try {
                doExporting();
            }
            catch(ex: Throwable) {
                try {
                    StateAnnouncement.instance.registerAnnouncementSession(
                        Announcement(
                            "rootExportException",
                            "An root export service exception happened",
                            ex.message ?: "",
                            AnnouncementType.SESSION,
                            OffsetDateTime.now()
                        )
                    );
                } catch(_: Throwable){}
            }
        };

        return START_STICKY;
    }
    fun setupNotificationRequirements() {
        _notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        _notificationChannel = NotificationChannel(EXPORT_NOTIF_CHANNEL_ID, "Temp", NotificationManager.IMPORTANCE_DEFAULT).apply {
            this.enableVibration(false);
            this.setSound(null, null);
        };
        _notificationManager!!.createNotificationChannel(_notificationChannel!!);
    }

    override fun onCreate() {
        Logger.i(TAG, "onCreate");
        super.onCreate()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null;
    }

    private suspend fun doExporting() {
        Logger.i(TAG, "doExporting - Starting Exports");
        val ignore = mutableListOf<VideoExport>();
        var currentExport: VideoExport? = StateDownloads.instance.getExporting().firstOrNull();
        while (currentExport != null)
        {
            try{
                notifyExport(currentExport);
                doExport(applicationContext, currentExport);
            }
            catch(ex: Throwable) {
                Logger.e(TAG, "Failed export [${currentExport.videoLocal.name}]: ${ex.message}", ex);
                currentExport.error = ex.message;
                currentExport.changeState(VideoExport.State.ERROR);
                ignore.add(currentExport);

                //Give it a sec
                Thread.sleep(500);
            }

            currentExport = StateDownloads.instance.getExporting().filter { !ignore.contains(it) }.firstOrNull();
        }
        Logger.i(TAG, "doExporting - Ending Exports");
        stopService(this);
    }

    private suspend fun doExport(context: Context, export: VideoExport) {
        Logger.i(TAG, "Exporting [${export.videoLocal.name}] started");

        export.changeState(VideoExport.State.EXPORTING);

        var lastNotifyTime: Long = 0L;
        val file = export.export(context) { progress ->
            export.progress = progress;

            val currentTime = System.currentTimeMillis();
            if (currentTime - lastNotifyTime > 500) {
                notifyExport(export);
                lastNotifyTime = currentTime;
            }
        }
        export.changeState(VideoExport.State.COMPLETED);
        Logger.i(TAG, "Export [${export.videoLocal.name}] finished");
        StateDownloads.instance.removeExport(export);
        notifyExport(export);

        withContext(Dispatchers.Main) {
            StateAnnouncement.instance.registerAnnouncement(UUID.randomUUID().toString(), "File exported", "Exported [${file.uri}]", AnnouncementType.SESSION, time = null, category = "download", actionButton = "Open") {
                file.share(this@ExportingService);
            };
        }
    }

    private fun notifyExport(export: VideoExport) {
        val channel = _notificationChannel ?: return;

        val bringUpIntent = Intent(this, MainActivity::class.java);
        bringUpIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        bringUpIntent.action = "TAB";
        bringUpIntent.putExtra("TAB", "Exports");

        var builder = NotificationCompat.Builder(this, EXPORT_NOTIF_TAG)
            .setSmallIcon(R.drawable.ic_export)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(this, 5, bringUpIntent, PendingIntent.FLAG_IMMUTABLE))
            .setContentTitle("${export.state}: ${export.videoLocal.name}")
            .setContentText(export.getExportInfo())
            .setProgress(100, (export.progress * 100).toInt(), export.progress == 0.0)
            .setChannelId(channel.id)

        val notif = builder.build();
        notif.flags = notif.flags or NotificationCompat.FLAG_ONGOING_EVENT or NotificationCompat.FLAG_NO_CLEAR;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(EXPORT_NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(EXPORT_NOTIF_ID, notif);
        }
    }

    fun closeExportSession() {
        Logger.i(TAG, "closeExportSession");
        stopForeground(STOP_FOREGROUND_DETACH);
        _notificationManager?.cancel(EXPORT_NOTIF_ID);
        stopService();
        _started = false;
        super.stopSelf();
    }
    override fun onDestroy() {
        Logger.i(TAG, "onDestroy");
        _instance = null;
        _scope.cancel("onDestroy");
        super.onDestroy();
    }

    companion object {
        private var _instance: ExportingService? = null;
        private var _callOnStarted: ((ExportingService)->Unit)? = null;

        @Synchronized
        fun getOrCreateService(context: Context, handle: ((ExportingService)->Unit)? = null) {
            if(!FragmentedStorage.isInitialized)
                return;
            if(_instance == null) {
                _callOnStarted = handle;
                val intent = Intent(context, ExportingService::class.java);
                context.startForegroundService(intent);
            }
            else _instance?.let {
                if(handle != null)
                    handle(it);
            }
        }
        @Synchronized
        fun getService() : ExportingService? {
            return _instance;
        }

        @Synchronized
        fun stopService(service: ExportingService? = null) {
            (service ?: _instance)?.let {
                if(_instance == it)
                    _instance = null;
                it.closeExportSession();
            }
        }
    }
}