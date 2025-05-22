package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.getBroadcast
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.copyToOutputStream
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.receivers.InstallReceiver
import com.futo.platformplayer.states.StateUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class AutoUpdateDialog(context: Context?) : AlertDialog(context) {
    companion object {
        private val TAG = "AutoUpdateDialog";
    }

    private lateinit var _buttonNever: Button;
    private lateinit var _buttonClose: Button;
    private lateinit var _buttonUpdate: LinearLayout;
    private lateinit var _text: TextView;
    private lateinit var _textProgress: TextView;
    private lateinit var _updateSpinner: ImageView;
    private lateinit var _buttonShowChangelog: Button;
    private var _maxVersion: Int = 0;

    private var _updating: Boolean = false;
    private var _apkFile: File? = null;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_update, null));

        _buttonNever = findViewById(R.id.button_never);
        _buttonClose = findViewById(R.id.button_close);
        _buttonUpdate = findViewById(R.id.button_update);
        _text = findViewById(R.id.text_dialog);
        _textProgress = findViewById(R.id.text_progress);
        _updateSpinner = findViewById(R.id.update_spinner);
        _buttonShowChangelog = findViewById(R.id.button_show_changelog);

        _buttonNever.setOnClickListener {
            Settings.instance.autoUpdate.check = 1;
            Settings.instance.save();
            dismiss();
        };

        _buttonClose.setOnClickListener {
            dismiss();
        };

        _buttonShowChangelog.setOnClickListener {
            dismiss();
            UIDialogs.showChangelogDialog(context, _maxVersion);
        };

        _buttonUpdate.setOnClickListener {
            if (_updating) {
                return@setOnClickListener;
            }

            _updating = true;
            update();
        };
    }

    fun showPredownloaded(apkFile: File) {
        _apkFile = apkFile;
        super.show()
    }

    override fun dismiss() {
        super.dismiss()
        InstallReceiver.onReceiveResult.clear();
        Logger.i(TAG, "Cleared InstallReceiver.onReceiveResult handler.")
    }

    fun hideExceptionButtons() {
        _buttonNever.visibility = View.GONE
        _buttonShowChangelog.visibility = View.GONE
    }

    private fun update() {
        _buttonShowChangelog.visibility = Button.GONE;
        _buttonNever.visibility = Button.GONE;
        _buttonClose.visibility = Button.GONE;
        _buttonUpdate.visibility = Button.GONE;
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        Logger.i(TAG, "Keep screen on set update")
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        _text.text = context.resources.getText(R.string.downloading_update);
        (_updateSpinner.drawable as Animatable?)?.start();

        GlobalScope.launch(Dispatchers.IO) {
            var inputStream: InputStream? = null;
            try {
                val apkFile = _apkFile;
                if (apkFile != null) {
                    inputStream = apkFile.inputStream();
                    val dataLength = apkFile.length();
                    install(inputStream, dataLength);
                } else {
                    val client = ManagedHttpClient();
                    val response = client.get(StateUpdate.APK_URL);
                    if (response.isOk && response.body != null) {
                        inputStream = response.body.byteStream();
                        val dataLength = response.body.contentLength();
                        install(inputStream, dataLength);
                    } else {
                        throw Exception("Failed to download latest version of app.");
                    }
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Exception thrown while downloading and installing latest version of app.", e);
                withContext(Dispatchers.Main) {
                    onReceiveResult("Failed to download update.");
                }
            } finally {
                inputStream?.close();
            }
        }
    }

    private suspend fun install(inputStream: InputStream, dataLength: Long) {
        var lastProgressText = "";
        var session: PackageInstaller.Session? = null;

        try {
            Logger.i(TAG, "Hooked InstallReceiver.onReceiveResult.")
            InstallReceiver.onReceiveResult.subscribe(this) { message -> onReceiveResult(message); };

            val packageInstaller: PackageInstaller = context.packageManager.packageInstaller;
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = packageInstaller.createSession(params);
            session = packageInstaller.openSession(sessionId)

            session.openWrite("package", 0, dataLength).use { sessionStream ->
                inputStream.copyToOutputStream(dataLength, sessionStream) { progress ->
                    val progressText = "${(progress * 100.0f).toInt()}%";
                    if (lastProgressText != progressText) {
                        lastProgressText = progressText;

                        //TODO: Use proper scope
                        GlobalScope.launch(Dispatchers.Main) {
                            _textProgress.text = progressText;
                        };
                    }
                }

                session.fsync(sessionStream);
            };

            val intent = Intent(context, InstallReceiver::class.java);
            val pendingIntent = getBroadcast(context, 0, intent, FLAG_MUTABLE or FLAG_UPDATE_CURRENT);
            val statusReceiver = pendingIntent.intentSender;

            session.commit(statusReceiver);
            session.close();

            withContext(Dispatchers.Main) {
                _textProgress.text = "";
                _text.text = context.resources.getText(R.string.installing_update);
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "Exception thrown while downloading and installing latest version of app.", e);
            session?.abandon();
            withContext(Dispatchers.Main) {
                onReceiveResult("Failed to download update.");
            }
        } finally {
            withContext(Dispatchers.Main) {
                Logger.i(TAG, "Keep screen on unset install")
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }

    private fun onReceiveResult(result: String?) {
        InstallReceiver.onReceiveResult.remove(this);
        Logger.i(TAG, "Cleared InstallReceiver.onReceiveResult handler.");

        setCancelable(true);
        setCanceledOnTouchOutside(true);
        _buttonClose.visibility = View.VISIBLE;
        (_updateSpinner.drawable as Animatable?)?.stop();

        if (result == null || result.isBlank()) {
            _updateSpinner.setImageResource(R.drawable.ic_update_success_251dp);
            _text.text = context.resources.getText(R.string.success);
        } else {
            _updateSpinner.setImageResource(R.drawable.ic_update_fail_251dp);
            _text.text = "${context.resources.getText(R.string.failed_to_update_with_error)}: '${result}'.";
        }
    }

    fun setMaxVersion(version: Int) {
        _maxVersion = version;
    }
}