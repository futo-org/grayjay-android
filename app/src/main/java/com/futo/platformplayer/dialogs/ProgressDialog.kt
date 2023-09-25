package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.app.PendingIntent.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.receivers.InstallReceiver
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProgressDialog : AlertDialog {
    companion object {
        private val TAG = "AutoUpdateDialog";
    }

    private lateinit var _text: TextView;
    private lateinit var _textProgress: TextView;
    private lateinit var _updateSpinner: ImageView;

    private val _handler: ((ProgressDialog) -> Unit);

    constructor(context: Context, act: ((ProgressDialog) -> Unit)) : super(context) {
        _handler = act;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_progress, null));

        _text = findViewById(R.id.text_dialog);
        _textProgress = findViewById(R.id.text_progress);
        _updateSpinner = findViewById(R.id.update_spinner);
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        _text.text = "";
        (_updateSpinner?.drawable as Animatable?)?.start();

        _handler(this);
    }

    fun setProgress(progress: Float) { setProgress(progress.toDouble()); }
    fun setProgress(progress: Double) { _textProgress.text = "${Math.floor((progress * 100)).toInt()}%" }
    fun setProgress(percentage: String) { _textProgress.text = percentage; }
    fun setText(str: String) { _text.text = str; }
}