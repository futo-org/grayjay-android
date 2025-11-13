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
import com.futo.platformplayer.fragment.mainactivity.main.LibraryFilesFragment
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.states.FileEntry
import com.futo.platformplayer.views.casting.CastButton
import com.futo.polycentric.core.PolycentricProfile

class FilesTopBarFragment : TopFragment() {
    private var _buttonBack: ImageButton? = null;
    private var _buttonCast: CastButton? = null;
    private var _textTitle: TextView? = null;
    private var _menuItems: LinearLayout? = null;

    private var _upHandle: (()->Unit)? = null;

    override fun onShown(parameter: Any?) {
        setTitle(parameter);
        setMenuItems(listOf());
    }
    override fun onHide() {

    }

    fun setTitle(parameter: Any? = null) {
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
        } else if(parameter is FileEntry) {
            val treePrefix = "content://com.android.externalstorage.documents/tree/";
            if(parameter.path.startsWith(treePrefix)) {
                _textTitle?.text = parameter.path.substring(treePrefix.length - 1).replace("%3A", " ").replace("%2F", "/");
            }
            else if(parameter.path.isNullOrBlank())
                _textTitle?.text = parameter.name;
            else
                _textTitle?.text = parameter.path;
        }
        else if(parameter is LibraryFilesFragment.FileStack) {
            val treePrefix = "content://com.android.externalstorage.documents/tree/";
            if(parameter.path.startsWith(treePrefix)) {
                _textTitle?.text = parameter.path.substring(treePrefix.length - 1).replace("%3A", " ").replace("%2F", "/");
            }
            else
                _textTitle?.text = parameter.path;
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_files_top_bar, container, false);

        val buttonBack: ImageButton = view.findViewById(R.id.button_back);
        _textTitle = view.findViewById(R.id.text_title);
        _menuItems = view.findViewById(R.id.menu_buttons)

        buttonBack.setOnClickListener {
            if(_upHandle != null)
                _upHandle?.invoke();
            else
                closeSegment();
        };

        _buttonBack = buttonBack;

        return view;
    }

    fun setUpNavigate(handle: (()->Unit)? = null) {
        _upHandle = handle;
        _buttonBack?.setImageResource(if(handle == null) R.drawable.ic_back_nav else R.drawable.ic_arrow_up);
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
        fun newInstance() = FilesTopBarFragment().apply { }
    }
}