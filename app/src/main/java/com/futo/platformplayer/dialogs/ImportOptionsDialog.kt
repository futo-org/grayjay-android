package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.fragment.mainactivity.main.SourcesFragment
import com.futo.platformplayer.readBytes
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateBackup
import com.futo.platformplayer.views.buttons.BigButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportOptionsDialog: AlertDialog {
    private val _context: MainActivity;

    private lateinit var _button_import_zip: BigButton;
    private lateinit var _button_import_ezip: BigButton;
    private lateinit var _button_import_txt: BigButton;
    private lateinit var _button_import_newpipe_subs: BigButton;
    private lateinit var _button_import_platform: BigButton;
    private lateinit var _button_close: Button;


    constructor(context: MainActivity): super(context) {
        _context = context;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_import_options, null));
        _button_import_zip = findViewById(R.id.button_import_zip);
        _button_import_ezip = findViewById(R.id.button_import_ezip);
        _button_import_txt = findViewById(R.id.button_import_txt);
        _button_import_newpipe_subs = findViewById(R.id.button_import_newpipe_subs);
        _button_import_platform = findViewById(R.id.button_import_platform);
        _button_close = findViewById(R.id.button_cancel);

        _button_import_zip.onClick.subscribe {
            dismiss();
            StateApp.instance.requestFileReadAccess(_context, null, "application/zip") {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    val zipBytes = it?.readBytes(context) ?: return@launch;
                    withContext(Dispatchers.Main) {
                        try {
                            StateBackup.importZipBytes(_context, StateApp.instance.scope, zipBytes);
                        }
                        catch(ex: Throwable) {
                            UIDialogs.toast("Failed to import, invalid format?\n" + ex.message);
                        }
                    }
                }
            };
        }
        _button_import_ezip.setOnClickListener {

        }
        _button_import_txt.onClick.subscribe  {
            dismiss();
            StateApp.instance.requestFileReadAccess(_context, null, "text/plain") {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    val txtBytes = it?.readBytes(context) ?: return@launch;
                    val txt = String(txtBytes);
                    withContext(Dispatchers.Main) {
                        try {
                            StateBackup.importTxt(_context, txt);
                        }
                        catch(ex: Throwable) {
                            UIDialogs.toast("Failed to import, invalid format?\n" + ex.message);
                        }
                    }
                }
            };
        }
        _button_import_newpipe_subs.onClick.subscribe  {
            dismiss();
            StateApp.instance.requestFileReadAccess(_context, null, "application/json") {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    val jsonBytes = it?.readBytes(context) ?: return@launch;
                    val json = String(jsonBytes);
                    withContext(Dispatchers.Main) {
                        try {
                            StateBackup.importNewPipeSubs(_context, json);
                        }
                        catch(ex: Throwable) {
                            UIDialogs.toast("Failed to import, invalid format?\n" + ex.message);
                        }
                    }
                }
            };
        };
        _button_import_platform.onClick.subscribe  {
            dismiss();
            _context.navigate(_context.getFragment<SourcesFragment>());
        };
        _button_close.setOnClickListener {
            dismiss();
        }
    }

    override fun dismiss() {
        super.dismiss();
    }

}