package com.futo.platformplayer.fragment.mainactivity.main

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.SettingsDev
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.DeveloperActivity
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.assume
import com.futo.platformplayer.dp
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.Album
import com.futo.platformplayer.states.Artist
import com.futo.platformplayer.states.ArtistOrdering
import com.futo.platformplayer.states.FileEntry
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateLibrary
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.AnyInsertedAdapterView
import com.futo.platformplayer.views.AnyInsertedAdapterView.Companion.asAnyWithTop
import com.futo.platformplayer.views.AnyInsertedAdapterView.Companion.asAnyWithViews
import com.futo.platformplayer.views.LibrarySection
import com.futo.platformplayer.views.LoaderView
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.InsertedViewAdapter
import com.futo.platformplayer.views.adapters.viewholders.AlbumTileViewHolder
import com.futo.platformplayer.views.adapters.viewholders.ArtistTileViewHolder
import com.futo.platformplayer.views.adapters.viewholders.FileViewHolder
import com.futo.platformplayer.views.adapters.viewholders.LocalVideoTileViewHolder
import com.futo.platformplayer.views.buttons.BigButton
import com.futo.platformplayer.views.fields.FieldForm
import com.futo.platformplayer.views.fields.IField
import com.futo.platformplayer.views.fields.ReadOnlyTextField
import com.google.android.material.button.MaterialButton


class DeveloperFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var view: FragView? = null;


    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val newView = FragView(this);
        view = newView;
        return newView;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        view?.onShown();
    }

    override fun onDestroyMainView() {
        view = null;
        super.onDestroyMainView();
    }

    companion object {
        fun newInstance() = DeveloperFragment().apply {}
    }


    class FragView: ConstraintLayout {
        val fragment: DeveloperFragment;

        private lateinit var _form: FieldForm;
        private lateinit var _buttonBack: ImageButton;

        private var _isFinished = false;

        lateinit var overlay: FrameLayout;

        val notifPermission = "android.permission.POST_NOTIFICATIONS";

        constructor(fragment: DeveloperFragment) : super(fragment.requireContext()) {
            inflate(context, R.layout.activity_dev, this);
            this.fragment = fragment;

            val activity = fragment.activity;
            findViewById<LinearLayout>(R.id.container_topbar).isVisible = false;

            _buttonBack = findViewById(R.id.button_back);
            _form = findViewById(R.id.settings_form);

            _form.fromObject(SettingsDev.instance);
            _form.onChanged.subscribe { _, _ ->
                _form.setObjectValues();
                SettingsDev.instance.save();
            };
        }

        fun getField(id: String): IField? {
            return _form.findField(id);
        }

        fun onShown() {

        }

    }
}