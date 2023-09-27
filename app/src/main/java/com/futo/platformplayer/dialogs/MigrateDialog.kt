package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.app.PendingIntent.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.*
import com.futo.platformplayer.receivers.InstallReceiver
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.exceptions.NoPlatformClientException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateUpdate
import com.futo.platformplayer.stores.v2.ManagedStore
import kotlinx.coroutines.*

class MigrateDialog : AlertDialog {
    companion object {
        private val TAG = "MigrateDialog";
    }
    private val _context: Context;

    private lateinit var _buttonIgnore: Button;
    private lateinit var _buttonDelete: Button;
    private lateinit var _buttonRestore: LinearLayout;

    private lateinit var _buttonDeleteFailed: Button;
    private lateinit var _buttonOk: LinearLayout;
    private lateinit var _buttonRetry: Button;

    private lateinit var _migrate_type_text: TextView;
    private lateinit var _migrate_count_text: TextView;

    private lateinit var _migrate_result_restored_text: TextView;
    private lateinit var _migrate_result_failed_text: TextView;
    private lateinit var _migrate_result_fplugin_text: TextView;
    private lateinit var _migrate_result_failed_count_text: TextView;

    private lateinit var _uiChoiceTop: FrameLayout;
    private lateinit var _uiProgressTop: FrameLayout;

    private lateinit var _uiChoiceBot: LinearLayout;
    private lateinit var _uiResultBot: LinearLayout;

    private lateinit var _textProgress: TextView;
    private lateinit var _updateSpinner: ImageView;

    private var _isRestoring: Boolean = false;

    private val _store: ManagedStore<*>;
    private val _onConcluded: ()->Unit;


    constructor(context: Context, toMigrate: ManagedStore<*>, onConcluded: ()->Unit): super(context) {
        _context = context;
        _store = toMigrate;
        _onConcluded = onConcluded;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_migrate, null));

        _buttonIgnore = findViewById(R.id.button_ignore);
        _buttonDelete = findViewById(R.id.button_delete);
        _buttonRestore = findViewById(R.id.button_restore);

        _buttonOk = findViewById(R.id.button_ok);
        _buttonRetry = findViewById(R.id.button_retry);
        _buttonDeleteFailed = findViewById(R.id.button_delete_failed);

        _migrate_type_text = findViewById(R.id.migrate_type_text);
        _migrate_count_text = findViewById(R.id.migrate_count_text);

        _migrate_result_restored_text = findViewById(R.id.migrate_result_restored_text);
        _migrate_result_failed_text = findViewById(R.id.migrate_result_failed_text);
        _migrate_result_fplugin_text = findViewById(R.id.migrate_result_fplugin_text);
        _migrate_result_failed_count_text = findViewById(R.id.migrate_result_failed_count_text);

        _uiChoiceTop = findViewById(R.id.dialog_ui_choice_top);
        _uiProgressTop = findViewById(R.id.dialog_ui_progress_top);

        _uiChoiceBot = findViewById(R.id.dialog_ui_bottom_choice);
        _uiResultBot = findViewById(R.id.dialog_ui_bottom_result)

        _textProgress = findViewById(R.id.text_progress);
        _updateSpinner = findViewById(R.id.update_spinner);

        val toMigrateCount = _store.getMissingReconstructionCount();
        _migrate_type_text.text = _store.name;
        _migrate_count_text.text = "${toMigrateCount} items";

        _migrate_result_failed_text.movementMethod = ScrollingMovementMethod.getInstance()

        _buttonIgnore.setOnClickListener {
            UIDialogs.toast(_context, "${toMigrateCount} items will be invisible\nWe will ask again next boot");
            dismiss();
        };
        _buttonDelete.setOnClickListener {
            _store.deleteMissing();
            UIDialogs.toast(_context, "Deleted ${toMigrateCount} failed items");
            dismiss();
        };
        _buttonRestore.setOnClickListener {
            if (_isRestoring)
                return@setOnClickListener;
            _isRestoring = true;
            restore();
        };

        _buttonRetry.setOnClickListener {
            restore();
        };
    }

    override fun dismiss() {
        super.dismiss();
        _onConcluded.invoke();
    }

    private fun restore() {
        _uiChoiceTop.visibility = View.GONE;
        _uiChoiceBot.visibility = View.GONE;
        _uiResultBot.visibility = View.GONE;
        _uiProgressTop.visibility = View.VISIBLE;

        _textProgress.text = "0/${_store.getMissingReconstructionCount()}";

        setCancelable(false);
        setCanceledOnTouchOutside(false);
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        _updateSpinner.drawable?.assume<Animatable>()?.start();

        val scope = StateApp.instance.scopeOrNull;
        scope?.launch(Dispatchers.IO) {
            try {
                val migrationResult = _store.reconstructMissing { finished, total ->
                    scope.launch(Dispatchers.Main) {
                        _textProgress.text = "${finished}/${total}";
                    }
                };

                withContext(Dispatchers.Main) {
                    try {
                        val realFailures = migrationResult.exceptions.filter { it !is NoPlatformClientException };
                        val pluginFailures = migrationResult.exceptions.filter { it is NoPlatformClientException };

                        _migrate_result_restored_text.text = "Restored ${migrationResult.success} items";
                        _migrate_result_fplugin_text.visibility = View.GONE;

                        if(realFailures.isNotEmpty() || migrationResult.messages.isNotEmpty()) {
                            val messagesText = migrationResult.messages.map { it }.joinToString("\n") + (if(migrationResult.messages.isNotEmpty()) "\n" else "");
                            val errorText = realFailures.map { it.message }.joinToString("\n");
                            val spannable = SpannableString(messagesText + errorText);
                            spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, messagesText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            spannable.setSpan(ForegroundColorSpan(Color.RED), messagesText.length, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                            _migrate_result_failed_text.text = spannable
                            _migrate_result_failed_text.visibility = View.VISIBLE;
                        }
                        else
                            _migrate_result_failed_text.visibility = View.GONE;


                        if (realFailures.isEmpty()) {
                            _migrate_result_failed_count_text.visibility = View.GONE;
                            _buttonDeleteFailed.visibility = View.GONE;
                            _buttonRetry.visibility = View.GONE;
                        } else {
                            _migrate_result_failed_count_text.visibility = View.VISIBLE;
                            _migrate_result_failed_count_text.text = "(${migrationResult.exceptions.size} failed)"
                            _buttonDeleteFailed.visibility = View.VISIBLE;
                            _buttonRetry.visibility = View.VISIBLE;
                        }

                        if(pluginFailures.isEmpty()) {
                            _migrate_result_fplugin_text.visibility = View.GONE;
                        } else {
                            _migrate_result_fplugin_text.visibility = View.VISIBLE;
                            _migrate_result_fplugin_text.text = "Plugin not enabled for ${pluginFailures} items";
                        }

                        _buttonDeleteFailed.setOnClickListener {
                            _store.deleteMissing();
                            UIDialogs.toast(_context, "Deleted ${realFailures} failed items", false);
                            dismiss();
                        };
                        _buttonOk.setOnClickListener {
                            if(migrationResult.exceptions.size > 0)
                                UIDialogs.toast(_context, "${migrationResult.exceptions.size} items will be invisible\nWe will ask again next boot");
                            dismiss();
                        }

                        _uiProgressTop.visibility = View.GONE;
                        _uiChoiceTop.visibility = View.VISIBLE;
                        _uiResultBot.visibility = View.VISIBLE;
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to update import UI.", e)
                    } finally {
                        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to import reconstruction.", e)
            }
        }
    }
}