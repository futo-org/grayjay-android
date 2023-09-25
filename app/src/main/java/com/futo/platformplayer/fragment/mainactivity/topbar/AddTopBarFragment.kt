package com.futo.platformplayer.fragment.mainactivity.topbar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.views.casting.CastButton

class AddTopBarFragment : TopFragment() {
    private var _buttonAdd: ImageButton? = null;
    private var _buttonCast: CastButton? = null;

    val onAdd = Event0();

    override fun onShown(parameter: Any?) {

    }
    override fun onHide() {
        onAdd.clear();
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_add_top_bar, container, false);

        _buttonAdd = view.findViewById<ImageButton?>(R.id.button_add).apply {
            this.setOnClickListener {
                onAdd.emit();
            }
        };
        _buttonCast = view.findViewById(R.id.button_cast);

        return view;
    }

    override fun onDestroyView() {
        super.onDestroyView()

        onAdd.clear();
        _buttonCast?.cleanup();
        _buttonCast = null;
    }

    companion object {
        fun newInstance() = AddTopBarFragment().apply { }
    }
}