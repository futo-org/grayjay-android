package com.futo.platformplayer.fragment.mainactivity.topbar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.constructs.Event0

class ImportTopBarFragment : TopFragment() {
    private var _buttonBack: ImageButton? = null;
    private var _textImport: TextView? = null;
    private var _textTitle: TextView? = null;
    private var _importEnabled: Boolean = false;

    val onImport = Event0();
    var title: String
        get() = _textTitle?.text?.toString() ?: ""
        set(v) { _textTitle?.text = v; };

    override fun onShown(parameter: Any?) {
        if (parameter is String) {
            _textTitle?.text = parameter;
        } else if (parameter is IPlatformClient) {
            _textTitle?.text = parameter.name;
        }
    }

    override fun onHide() {

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_import_top_bar, container, false);

        val buttonBack: ImageButton = view.findViewById(R.id.button_back);
        val textImport: TextView = view.findViewById(R.id.text_import);
        _textTitle = view.findViewById(R.id.text_title);

        buttonBack.setOnClickListener {
            closeSegment();
        };

        textImport.isClickable = true;
        textImport.setOnClickListener {
            if (!_importEnabled) {
                return@setOnClickListener;
            }

            onImport.emit();
        };

        _buttonBack = buttonBack;
        _textImport = textImport;

        setImportEnabled(false);

        return view;
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _buttonBack?.setOnClickListener(null);
        _buttonBack = null;
        _textImport?.setOnClickListener(null);
        _textImport = null;
        _textTitle = null;
    }

    fun setImportEnabled(enabled: Boolean) {
        if (enabled) {
            _textImport?.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary));
        } else {
            _textImport?.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_67));
        }

        _importEnabled = enabled;
    }

    companion object {
        fun newInstance() = ImportTopBarFragment().apply { }
    }
}