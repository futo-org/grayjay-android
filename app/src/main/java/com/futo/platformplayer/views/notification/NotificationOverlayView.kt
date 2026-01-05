package com.futo.platformplayer.views.notification

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.Announcement
import com.futo.platformplayer.states.AnnouncementType
import com.futo.platformplayer.states.SessionAnnouncement
import com.futo.platformplayer.states.StateAnnouncement
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.LoaderView
import com.futo.platformplayer.views.adapters.AnyAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationOverlayView: ConstraintLayout {

    lateinit var recycler: RecyclerView;
    var adapterNotifications: AnyAdapterView<Announcement, ViewHolder>;

    constructor(context: Context) : super(context) {
        inflate(context, R.layout.overlay_notifications, this)

        recycler = findViewById<RecyclerView>(R.id.container_notifications);
        adapterNotifications = recycler.asAny<Announcement, ViewHolder>(RecyclerView.VERTICAL, false, {

        });

    }

    fun onShown(parameter: Any?) {
        val announcements = StateAnnouncement.instance.getVisibleAnnouncements();
        adapterNotifications.adapter.setData(announcements);

        StateAnnouncement.instance.onAnnouncementChanged.subscribe(this) {
            StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                Logger.i("NotificationOverlayView", "Announcements Changed");
                val adapter = adapterNotifications;
                val announcements = StateAnnouncement.instance.getVisibleAnnouncements();
                adapter.adapter.setData(announcements);
            }
        }
    }

    fun onResume() {

    }

    fun onPause() {
        StateAnnouncement.instance.onAnnouncementChanged.remove(this);
    }

    class ViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<Announcement>(
        LayoutInflater.from(_viewGroup.context).inflate(
            R.layout.list_announcement,
            _viewGroup, false)) {

        protected var _announcement: Announcement? = null;
        protected val _textName: TextView
        protected val _textMetadata: TextView;
        protected val _icon: ImageView;
        protected val _buttonIgnore: ImageView
        protected val _buttonNever: LinearLayout
        protected val _buttonAction: LinearLayout
        protected val _buttonActionText: TextView
        protected val _buttonExtra: LinearLayout
        protected val _buttonExtraText: TextView
        protected val _loader: LoaderView;
        protected val _progress: ProgressBar;

        init {
            _textName = _view.findViewById(R.id.text_name);
            _textMetadata = _view.findViewById(R.id.text_metadata);
            _buttonIgnore = _view.findViewById(R.id.button_ignore);
            _buttonNever = _view.findViewById(R.id.button_never);
            _buttonAction = _view.findViewById(R.id.button_action);
            _buttonActionText = _view.findViewById(R.id.button_action_text);
            _buttonExtra = _view.findViewById(R.id.button_extra);
            _buttonExtraText = _view.findViewById(R.id.button_extra_text);
            _icon = _view.findViewById(R.id.icon);
            _loader = _view.findViewById(R.id.loader);
            _progress = _view.findViewById(R.id.progress);

            _buttonIgnore.setOnClickListener {
                _announcement.let {
                    StateAnnouncement.instance.closeAnnouncement(it?.id);
                }
            }
            _buttonNever.setOnClickListener {
                _announcement.let {
                    StateAnnouncement.instance.neverAnnouncement(it?.id);
                }
            }
            _buttonExtra.setOnClickListener {
                _announcement.let {
                    StateAnnouncement.instance.actionAnnouncement(it?.id, true)
                }
            }
            _buttonAction.setOnClickListener {
                _announcement.let {
                    StateAnnouncement.instance.actionAnnouncement(it?.id);
                }
            }
        }



        override fun bind(value: Announcement) {
            val oldAnnouncement = _announcement;
            _announcement = value;

            if(oldAnnouncement is SessionAnnouncement)
                oldAnnouncement.onProgressChanged.clear();

            _textName.text = value.title;
            _textMetadata.text = value.msg;

            if(value is SessionAnnouncement) {
                if(value.icon != null) {
                    value.icon.setImageView(_icon);
                    _icon.visibility = View.VISIBLE;
                }
                else
                    _icon.visibility = View.GONE;
                if(value.extraActionName != null && value.extraActionId != null) {
                    _buttonExtraText.text = value.extraActionName;
                    _buttonExtra.visibility = View.VISIBLE;
                }
                else
                    _buttonExtra.visibility = View.GONE;

                if(value.announceType == AnnouncementType.ONGOING) {
                    _buttonIgnore.visibility = View.GONE;
                }
                else {
                    _buttonIgnore.visibility = View.VISIBLE;
                }
                if(value.progress != null && value.announceType == AnnouncementType.ONGOING) {
                    _progress.isVisible = true;
                    _progress.min = 0;
                    _progress.max = 100;
                    value.onProgressChanged.subscribe {
                        val prog = it.progress;
                        if(prog == 0.toDouble() || prog == 100.toDouble()) {
                            _progress.isIndeterminate = true;
                        }
                        else {
                            _progress.isIndeterminate = false;
                            _progress.setProgress(it.progress?.times(100)?.toInt() ?: 0, false);
                        }
                    }
                }
                else
                    _progress.isVisible = false;
            }
            else {
                _buttonExtra.visibility = View.GONE;
                _icon.visibility = View.GONE;
                _buttonIgnore.visibility = View.VISIBLE;
            }

            if(value.announceType == AnnouncementType.ONGOING) {
                _loader.visibility = View.VISIBLE;
                _loader.start();
            }
            else {
                _loader.visibility = View.GONE;
                _loader.stop();
            }

            _buttonNever.visibility =
                if (value.announceType == AnnouncementType.RECURRING || value.announceType == AnnouncementType.SESSION_RECURRING)
                    View.VISIBLE
                else
                    View.GONE;

            _buttonAction.visibility =
                if(value.actionId != null && value.actionName != null)
                    View.VISIBLE;
                else View.GONE;

            if(value.actionId != null && value.actionName != null) {
                _buttonActionText.text = value.actionName;
            }
        }

    }


    class Frag  : MainFragment() {
        override val isMainView : Boolean = true;
        override val isTab: Boolean = true;
        override val hasBottomBar: Boolean get() = true;

        private var _view: NotificationOverlayView? = null;

        override fun onShownWithView(parameter: Any?, isBack: Boolean) {
            super.onShownWithView(parameter, isBack);
            _view?.onShown(parameter);
        }

        override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = NotificationOverlayView(requireContext());
            _view = view;
            return view;
        }

        override fun onDestroyMainView() {
            super.onDestroyMainView();
            _view = null;
        }

        override fun onResume() {
            super.onResume()
            _view?.onResume();
        }

        override fun onPause() {
            super.onPause()
            _view?.onPause();
        }
    }
}