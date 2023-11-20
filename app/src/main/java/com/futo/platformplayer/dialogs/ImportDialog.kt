package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.content.Context
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
import com.futo.platformplayer.api.media.exceptions.NoPlatformClientException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.stores.v2.ManagedStore
import kotlinx.coroutines.*

class ImportDialog : AlertDialog {
    companion object {
        private val TAG = "ImportDialog";
    }
    private val _context: Context;

    private lateinit var _buttonCancel: Button;
    private lateinit var _buttonImport: LinearLayout;

    private lateinit var _buttonCancelImport: Button;
    private lateinit var _buttonOk: LinearLayout;
    private lateinit var _buttonRetry: Button;

    private lateinit var _import_name_text: TextView;
    private lateinit var _import_type_text: TextView;

    private lateinit var _import_result_restored_text: TextView;
    private lateinit var _import_result_failed_text: TextView;
    private lateinit var _import_result_fplugin_text: TextView;
    private lateinit var _import_result_failed_count_text: TextView;

    private lateinit var _uiChoiceTop: FrameLayout;
    private lateinit var _uiProgressTop: FrameLayout;

    private lateinit var _uiChoiceBot: LinearLayout;
    private lateinit var _uiResultBot: LinearLayout;

    private lateinit var _textProgress: TextView;
    private lateinit var _updateSpinner: ImageView;

    private var _isImport: Boolean = false;

    private val _store: ManagedStore<*>;
    private val _onConcluded: ()->Unit;

    private val _name: String;
    private val _toImport: List<String>;


    constructor(context: Context, importStore: ManagedStore<*>, name: String, toReconstruct: List<String>, onConcluded: ()->Unit): super(context) {
        _context = context;
        _store = importStore;
        _onConcluded = onConcluded;
        _name = name;
        _toImport = ArrayList(toReconstruct);
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_import, null));

        _buttonCancel = findViewById(R.id.button_cancel);
        _buttonImport = findViewById(R.id.button_import);

        _buttonOk = findViewById(R.id.button_ok);
        _buttonCancelImport = findViewById(R.id.button_cancel_import);
        _buttonRetry = findViewById(R.id.button_retry);

        _import_type_text = findViewById(R.id.import_type_text);
        _import_name_text = findViewById(R.id.import_name_text);

        _import_result_restored_text = findViewById(R.id.import_result_restored_text);
        _import_result_failed_text = findViewById(R.id.import_result_failed_text);
        _import_result_fplugin_text = findViewById(R.id.import_result_fplugin_text);
        _import_result_failed_count_text = findViewById(R.id.import_result_failed_count_text);

        _uiChoiceTop = findViewById(R.id.dialog_ui_choice_top);
        _uiProgressTop = findViewById(R.id.dialog_ui_progress_top);

        _uiChoiceBot = findViewById(R.id.dialog_ui_bottom_choice);
        _uiResultBot = findViewById(R.id.dialog_ui_bottom_result)

        _textProgress = findViewById(R.id.text_progress);
        _updateSpinner = findViewById(R.id.update_spinner);

        val toMigrateCount = _store.getMissingReconstructionCount();
        _import_type_text.text = _store.name;
        _import_name_text.text = _name;

        _import_result_failed_text.movementMethod = ScrollingMovementMethod.getInstance()

        _buttonCancel.setOnClickListener {
            dismiss();
        };
        _buttonImport.setOnClickListener {
            if (_isImport)
                return@setOnClickListener;
            _isImport = true;
            import();
        };

        _buttonRetry.setOnClickListener {
            import();
        };
    }

    override fun dismiss() {
        super.dismiss();
        _onConcluded.invoke();
    }

    private fun import() {
        _uiChoiceTop.visibility = View.GONE;
        _uiChoiceBot.visibility = View.GONE;
        _uiResultBot.visibility = View.GONE;
        _uiProgressTop.visibility = View.VISIBLE;
        _textProgress.text = "0/${_store.getMissingReconstructionCount()}";

        setCancelable(false);
        setCanceledOnTouchOutside(false);

        Logger.i(TAG, "Keep screen on set import")
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        _updateSpinner.drawable?.assume<Animatable>()?.start();

        val scope = StateApp.instance.scopeOrNull;
        scope?.launch(Dispatchers.IO) {
            try {
                val migrationResult = _store.importReconstructions(_toImport) { finished, total ->
                    scope.launch(Dispatchers.Main) {
                        _textProgress.text = "${finished}/${total}";
                    }
                };

                withContext(Dispatchers.Main) {
                    try {
                        val realFailures = migrationResult.exceptions.filter { it !is NoPlatformClientException };
                        val pluginFailures = migrationResult.exceptions.filter { it is NoPlatformClientException };

                        _import_result_restored_text.text = "Imported ${migrationResult.success} items";
                        _import_result_fplugin_text.visibility = View.GONE;

                        if(realFailures.isNotEmpty() || migrationResult.messages.isNotEmpty()) {
                            val messagesText = migrationResult.messages.map { it }.joinToString("\n") + (if(migrationResult.messages.isNotEmpty()) "\n" else "");
                            val errorText = realFailures.map { it.message }.joinToString("\n");
                            val spannable = SpannableString(messagesText + errorText);
                            spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, messagesText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            spannable.setSpan(ForegroundColorSpan(Color.RED), messagesText.length, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                            _import_result_failed_text.text = spannable
                            _import_result_failed_text.visibility = View.VISIBLE;
                        }
                        else
                            _import_result_failed_text.visibility = View.GONE;

                        if (realFailures.isEmpty()) {
                            _import_result_failed_count_text.visibility = View.GONE;
                            _buttonCancelImport.visibility = View.GONE;
                            _buttonRetry.visibility = View.GONE;
                        } else {
                            _import_result_failed_count_text.visibility = View.VISIBLE;
                            _import_result_failed_count_text.text = "(${migrationResult.exceptions.size} failed)"
                            _buttonCancelImport.visibility = View.VISIBLE;
                            _buttonRetry.visibility = View.VISIBLE;
                        }

                        if(pluginFailures.isEmpty()) {
                            _import_result_fplugin_text.visibility = View.GONE;
                        } else {
                            _import_result_fplugin_text.visibility = View.VISIBLE;
                            _import_result_fplugin_text.text = "Plugin not enabled for ${pluginFailures} items";
                        }

                        _buttonCancelImport.setOnClickListener {
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
                        Logger.i(TAG, "Keep screen on unset update")
                        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to import reconstruction.", e)
            }
        }
    }
}