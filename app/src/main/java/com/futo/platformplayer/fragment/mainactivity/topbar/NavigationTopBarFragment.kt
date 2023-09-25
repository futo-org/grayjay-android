package com.futo.platformplayer.fragment.mainactivity.topbar

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.views.casting.CastButton

class NavigationTopBarFragment : TopFragment() {
    private var _buttonBack: ImageButton? = null;
    private var _buttonCast: CastButton? = null;
    private var _textTitle: TextView? = null;
    private var _menuItems: LinearLayout? = null;

    override fun onShown(parameter: Any?) {
        if(parameter is IPlatformChannel) {
            _textTitle?.text = parameter.name;
        } else if(parameter is PlatformAuthorLink) {
            _textTitle?.text = parameter.name;
        } else if (parameter is Playlist) {
            _textTitle?.text = parameter.name;
        } else if (parameter is String) {
            _textTitle?.text = parameter;
        } else if (parameter is IPlatformClient) {
            _textTitle?.text = parameter.name;
        } else if (parameter is PolycentricProfile) {
            _textTitle?.text = parameter.systemState.username;
        }

        setMenuItems(listOf());
    }
    override fun onHide() {

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_navigation_top_bar, container, false);

        val buttonBack: ImageButton = view.findViewById(R.id.button_back);
        _buttonCast = view.findViewById(R.id.button_cast);
        _textTitle = view.findViewById(R.id.text_title);
        _menuItems = view.findViewById(R.id.menu_buttons)

        buttonBack.setOnClickListener {
            closeSegment();
        };

        _buttonBack = buttonBack;

        return view;
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _buttonBack?.setOnClickListener(null);
        _buttonBack = null;
        _buttonCast?.cleanup();
        _buttonCast = null;
        _textTitle = null;
    }

    fun setMenuItems(items: List<Pair<Int, ()->Unit>>) {
        _menuItems?.removeAllViews();

        val dp4 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt();
        val dp9 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 9f, resources.displayMetrics).toInt();

        for(item in items) {
            val compatImageItem = AppCompatImageView(requireContext());
            compatImageItem.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
            compatImageItem.setImageResource(item.first);
            compatImageItem.setPadding(dp4, dp9, dp4, dp9);
            compatImageItem.scaleType = ImageView.ScaleType.FIT_CENTER;
            compatImageItem.setOnClickListener {
                item.second.invoke();
            };

            _menuItems?.addView(compatImageItem);
        }
    }

    companion object {
        fun newInstance() = NavigationTopBarFragment().apply { }
    }
}