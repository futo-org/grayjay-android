package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.casting.CastProtocolType
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.models.CastingDeviceInfo
import com.futo.platformplayer.toInetAddress


class CastingAddDialog(context: Context?) : AlertDialog(context) {
    private lateinit var _spinnerType: Spinner;
    private lateinit var _editName: EditText;
    private lateinit var _editIP: EditText;
    private lateinit var _editPort: EditText;
    private lateinit var _textError: TextView;
    private lateinit var _buttonCancel: Button;
    private lateinit var _buttonConfirm: LinearLayout;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_casting_add, null));

        _spinnerType = findViewById(R.id.spinner_type);
        _editName = findViewById(R.id.edit_name);
        _editIP = findViewById(R.id.edit_ip);
        _editPort = findViewById(R.id.edit_port);
        _textError = findViewById(R.id.text_error);
        _buttonCancel = findViewById(R.id.button_cancel);
        _buttonConfirm = findViewById(R.id.button_confirm);

        ArrayAdapter.createFromResource(context, R.array.casting_device_type_array, R.layout.spinner_item_simple).also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner_dropdownitem_simple);
            _spinnerType.adapter = adapter;
        };

        _buttonCancel.setOnClickListener {
            performDismiss();
        }

        _spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                _editPort.text.clear();
                _editPort.text.append(when (_spinnerType.selectedItemPosition) {
                    0 -> "46899" //FastCast
                    1 -> "8009" //ChromeCast
                    else -> ""
                });
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        };

        _buttonConfirm.setOnClickListener {
            val castProtocolType: CastProtocolType = when (_spinnerType.selectedItemPosition) {
                0 -> CastProtocolType.FCAST
                1 -> CastProtocolType.CHROMECAST
                2 -> CastProtocolType.AIRPLAY
                else -> {
                    _textError.text = "Device type is invalid expected values like FastCast or ChromeCast.";
                    _textError.visibility = View.VISIBLE;
                    return@setOnClickListener;
                }
            };

            val name = _editName.text.toString().trim();
            if (name.isNullOrBlank()) {
                _textError.text = "Name can not be empty.";
                _textError.visibility = View.VISIBLE;
                return@setOnClickListener;
            }

            val ip = _editIP.text.toString().trim();
            if (ip.isNullOrBlank()) {
                _textError.text = "IP can not be empty.";
                _textError.visibility = View.VISIBLE;
                return@setOnClickListener;
            }

            val address = ip.toInetAddress();
            if (address == null) {
                _textError.text = "IP address is invalid, expected an IPv4 or IPv6 address.";
                _textError.visibility = View.VISIBLE;
                return@setOnClickListener;
            }

            val port: UShort? = _editPort.text.toString().trim().toUShortOrNull();
            if (port == null) {
                _textError.text = "Port number is invalid, expected a number between 0 and 65535.";
                _textError.visibility = View.VISIBLE;
                return@setOnClickListener;
            }

            _textError.visibility = View.GONE;
            val castingDeviceInfo = CastingDeviceInfo(name, castProtocolType, arrayOf(ip), port.toInt());
            StateCasting.instance.addRememberedDevice(castingDeviceInfo);
            performDismiss();
        };
    }

    override fun show() {
        super.show();

        _spinnerType.setSelection(0);
        _editPort.text.clear();
        _editPort.text.append("46899");
        _editIP.text.clear();
        _editName.text.clear();
        _textError.visibility = View.GONE;

        window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        };
    }

    private fun performDismiss(shouldShowCastingDialog: Boolean = true) {
        if (shouldShowCastingDialog) {
            UIDialogs.showCastingDialog(context);
        }

        dismiss();
    }

    companion object {
        private val TAG = "CastingAddDialog";
    }
}