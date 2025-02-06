package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.media.MediaCas.PluginDescriptor
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.AddSourceActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.exceptions.NoPlatformClientException
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginDescriptor
import com.futo.platformplayer.assume
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.ImportCache
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateBackup
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.stores.v2.ManagedStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PluginUpdateDialog : AlertDialog {
    companion object {
        private val TAG = "PluginUpdateDialog";
    }
    private val _context: Context;

    private lateinit var _buttonCancel1: Button;
    private lateinit var _buttonCancel2: Button;
    private lateinit var _buttonUpdate: LinearLayout;

    private lateinit var _buttonOk: LinearLayout;
    private lateinit var _buttonInstall: LinearLayout;

    private lateinit var _textPlugin: TextView;
    private lateinit var _textChangelog: TextView;
    private lateinit var _textProgres: TextView;
    private lateinit var _textError: TextView;
    private lateinit var _textResult: TextView;

    private lateinit var _uiChoiceTop: FrameLayout;
    private lateinit var _uiProgressTop: FrameLayout;
    private lateinit var _uiRiskTop: FrameLayout;

    private lateinit var _uiChoiceBot: LinearLayout;
    private lateinit var _uiResultBot: LinearLayout;
    private lateinit var _uiRiskBot: LinearLayout;
    private lateinit var _uiProgressBot: LinearLayout;

    private lateinit var _iconPlugin: ImageView;
    private lateinit var _updateSpinner: ImageView;

    private var _isUpdating = false;

    private val _oldConfig: SourcePluginConfig;
    private val _newConfig: SourcePluginConfig;


    constructor(context: Context, oldConfig: SourcePluginConfig, newConfig: SourcePluginConfig): super(context) {
        _context = context;
        _oldConfig = oldConfig;
        _newConfig = newConfig;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_plugin_update, null));

        _buttonCancel1 = findViewById(R.id.button_cancel_1);
        _buttonCancel2 = findViewById(R.id.button_cancel_2);
        _buttonUpdate = findViewById(R.id.button_update);

        _buttonOk = findViewById(R.id.button_ok);
        _buttonInstall = findViewById(R.id.button_install);

        _textPlugin = findViewById(R.id.text_plugin);
        _textChangelog = findViewById(R.id.text_changelog);
        _textProgres = findViewById(R.id.text_progress);
        _textError = findViewById(R.id.text_error);
        _textResult = findViewById(R.id.text_result);

        _uiChoiceTop = findViewById(R.id.dialog_ui_choice_top);
        _uiProgressTop = findViewById(R.id.dialog_ui_progress_top);
        _uiRiskTop = findViewById(R.id.dialog_ui_risk_top);

        _uiChoiceBot = findViewById(R.id.dialog_ui_bottom_choice);
        _uiResultBot = findViewById(R.id.dialog_ui_bottom_result);
        _uiRiskBot = findViewById(R.id.dialog_ui_bottom_risk);
        _uiProgressBot = findViewById(R.id.dialog_ui_bottom_progress);

        _updateSpinner = findViewById(R.id.update_spinner);
        _iconPlugin = findViewById(R.id.icon_plugin);

        try {
            var changelogVersion = _newConfig.version.toString();
                if (_newConfig.changelog != null && _newConfig.changelog?.containsKey(changelogVersion) == true) {
                _textChangelog.movementMethod = ScrollingMovementMethod();
                val changelog = _newConfig.changelog!![changelogVersion]!!;
                if(changelog.size > 1) {
                    _textChangelog.text = "Changelog (${_newConfig.version})\n" + changelog.map { " - " + it.trim() }.joinToString("\n");
                }
                else if(changelog.size == 1) {
                    _textChangelog.text = "Changelog (${_newConfig.version})\n" + changelog[0].trim();
                }
                else
                    _textChangelog.visibility = View.GONE;
            } else
                _textChangelog.visibility = View.GONE;
        }
        catch(ex: Throwable) {
            _textChangelog.visibility = View.GONE;
            Logger.e(TAG, "Invalid changelog? ", ex);
        }

        _buttonCancel1.setOnClickListener {
            dismiss();
        };
        _buttonCancel2.setOnClickListener {
            dismiss();
        };
        _buttonUpdate.setOnClickListener {
            if (_isUpdating)
                return@setOnClickListener;
            _isUpdating = true;
            update();
        };

        Glide.with(_iconPlugin)
            .load(_oldConfig.absoluteIconUrl)
            .fallback(R.drawable.ic_sources)
            .into(_iconPlugin);
        _textPlugin.text = _oldConfig.name;

        val descriptor = StatePlugins.instance.getPlugin(_oldConfig.id);
        if(descriptor != null) {
            if(descriptor.appSettings.automaticUpdate) {
                if (_isUpdating)
                    return;
                _isUpdating = true;
                update();
            }
        }
    }

    override fun dismiss() {
        super.dismiss();
    }

    private fun update() {
        _uiChoiceTop.visibility = View.GONE;
        _uiRiskTop.visibility = View.GONE;
        _uiChoiceBot.visibility = View.GONE;
        _uiResultBot.visibility = View.GONE;
        _uiRiskBot.visibility = View.GONE;
        _uiProgressTop.visibility = View.VISIBLE;
        _uiProgressBot.visibility = View.VISIBLE;

        setCancelable(false);
        setCanceledOnTouchOutside(false);

        Logger.i(TAG, "Keep screen on set import")
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        _updateSpinner.drawable?.assume<Animatable>()?.start();

        val scope = StateApp.instance.scopeOrNull;
        scope?.launch(Dispatchers.IO) {
            try {
                val client = ManagedHttpClient();
                val script = StatePlugins.instance.getScript(_oldConfig.id) ?: "";

                val newScript = client.get(_newConfig.absoluteScriptUrl)?.body?.string();
                if(newScript.isNullOrEmpty())
                    throw IllegalStateException("No script found");

                if(_oldConfig.isLowRiskUpdate(script, _newConfig, newScript)){

                    StatePlugins.instance.installPluginBackground(context, StateApp.instance.scope, _newConfig, newScript,
                        { text: String, progress: Double ->
                            _textProgres.setText(text);
                        },
                        { ex ->
                            if(ex == null) {
                                StatePlugins.instance.clearUpdateAvailable(_newConfig);
                                _iconPlugin.setImageResource(R.drawable.ic_check);
                                _textError.visibility = View.GONE;
                                _textResult.visibility = View.VISIBLE;
                            }
                            else {
                                _iconPlugin.setImageResource(R.drawable.ic_error_pred);
                                _textError.text = ex.message + "\n\nYou can retry inside the sources tab";
                                _textError.visibility = View.VISIBLE;
                                _textResult.visibility = View.GONE;
                            }
                            try {
                                _buttonOk.setOnClickListener {
                                    dismiss();
                                }
                                _uiProgressTop.visibility = View.GONE;
                                _uiProgressBot.visibility = View.GONE;
                                _uiChoiceTop.visibility = View.VISIBLE;
                                _uiResultBot.visibility = View.VISIBLE;
                            } catch (e: Throwable) {
                                Logger.e(TAG, "Failed to update UI.", e)
                            } finally {
                                Logger.i(TAG, "Keep screen on unset update")
                                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            }
                        });

                }
                else {
                    withContext(Dispatchers.Main) {
                        try {
                            _buttonInstall.setOnClickListener {
                                dismiss();

                                val intent = Intent(_context, AddSourceActivity::class.java).apply {
                                    data = Uri.parse(_newConfig.sourceUrl)
                                };

                                _context.startActivity(intent);
                            }

                            _uiProgressTop.visibility = View.GONE;
                            _uiProgressBot.visibility = View.GONE;
                            _uiRiskTop.visibility = View.VISIBLE;
                            _uiRiskBot.visibility = View.VISIBLE;
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Failed to update UI.", e)
                        } finally {
                            Logger.i(TAG, "Keep screen on unset update")
                            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        }
                    }
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to update.", e);
                withContext(Dispatchers.Main) {
                    _buttonOk.setOnClickListener {
                        dismiss();
                    }
                    _iconPlugin.setImageResource(R.drawable.ic_error_pred);
                    _textResult.visibility = View.GONE;
                    _uiProgressTop.visibility = View.GONE;
                    _uiProgressBot.visibility = View.GONE;
                    _uiChoiceTop.visibility = View.VISIBLE;
                    _uiResultBot.visibility = View.VISIBLE;
                    _textError.visibility = View.VISIBLE;
                    _textError.text = e.message + "\n\nYou can retry inside the sources tab"
                }
            }
        }
    }
}