package com.futo.platformplayer.views.announcements

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.dp
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.Announcement
import com.futo.platformplayer.states.AnnouncementType
import com.futo.platformplayer.states.SessionAnnouncement
import com.futo.platformplayer.states.StateAnnouncement
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.toHumanNowDiffString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AnnouncementView : LinearLayout {
    private val _root: FrameLayout;
    private val _textTitle: TextView;
    private val _textCounter: TextView;
    private val _textBody: TextView;
    private val _textClose: TextView;
    private val _textNever: TextView;
    private val _buttonAction: FrameLayout;
    private val _textAction: TextView;
    private val _textTime: TextView;
    private val _category: String?;
    private var _currentAnnouncement: Announcement? = null;

    val onClose = Event0();

    private val _scope: CoroutineScope?;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_announcement, this);

        _scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: StateApp.instance.scopeOrNull; //TODO: Fetch correct scope

        _root = findViewById(R.id.root);
        _textTitle = findViewById(R.id.text_title);
        _textCounter = findViewById(R.id.text_counter);
        _textBody = findViewById(R.id.text_body);
        _textClose = findViewById(R.id.text_close);
        _textNever = findViewById(R.id.text_never);
        _buttonAction = findViewById(R.id.button_action);
        _textAction = findViewById(R.id.text_action);
        _textTime = findViewById(R.id.text_time);

        _buttonAction.setOnClickListener {
            val a = _currentAnnouncement ?: return@setOnClickListener;
            StateAnnouncement.instance.actionAnnouncement(a);
        };

        _textClose.setOnClickListener {
            val a = _currentAnnouncement ?: return@setOnClickListener;
            StateAnnouncement.instance.closeAnnouncement(a.id);
            refresh();
        };

        _textNever.setOnClickListener {
            val a = _currentAnnouncement ?: return@setOnClickListener;
            StateAnnouncement.instance.neverAnnouncement(a.id);
            refresh();
        };

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.AnnouncementView, 0, 0);
        _category = attrArr.getText(R.styleable.AnnouncementView_category)?.toString();
        attrArr.recycle()

        refresh();
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        StateAnnouncement.instance.onAnnouncementChanged.subscribe(this) {
            _scope?.launch(Dispatchers.Main) {
                refresh();
            }
        }

        refresh();
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        StateAnnouncement.instance.onAnnouncementChanged.remove(this)
    }

    private fun refresh() {
        Logger.v(TAG, "refresh");
        val announcements = StateAnnouncement.instance.getVisibleAnnouncements(_category);
        setAnnouncement(announcements.firstOrNull(), announcements.size);
    }

    fun isClosed(): Boolean{
        return _currentAnnouncement == null
    }

    private fun setAnnouncement(announcement: Announcement?, count: Int) {
        if(count == 0 && announcement == null)
            Logger.i(TAG, "setAnnouncement announcement=$announcement count=$count");

        _currentAnnouncement = announcement;

        if (announcement == null) {
            _root.visibility = View.GONE
            onClose.emit()
            return;
        }

        _root.visibility = View.VISIBLE

        _textTitle.text = announcement.title;
        _textBody.text = announcement.msg;
        _textCounter.text = "1/${count}";

        if (announcement.actionName != null) {
            _textAction.text = announcement.actionName;
            _buttonAction.visibility = View.VISIBLE;
        } else {
            _buttonAction.visibility = View.GONE;
        }

        if(announcement is SessionAnnouncement) {
            if(announcement.cancelName != null)
            {
                _textClose.text = announcement.cancelName;
            }
            else
                _textClose.text = context.getString(R.string.dismiss);
        }
        else
            _textClose.text = context.getString(R.string.dismiss);

        when (announcement.announceType) {
            AnnouncementType.DELETABLE -> {
                _textClose.visibility = View.VISIBLE;
                _textNever.visibility = View.GONE;
            }
            AnnouncementType.RECURRING -> {
                _textClose.visibility = View.VISIBLE;
                _textNever.visibility = View.VISIBLE;
            }
            AnnouncementType.PERMANENT -> {
                _textClose.visibility = View.VISIBLE;
                _textNever.visibility = View.GONE;
            }
            AnnouncementType.SESSION -> {
                _textClose.visibility = View.VISIBLE;
                _textNever.visibility = View.GONE;
            }
            AnnouncementType.SESSION_RECURRING -> {
                _textClose.visibility = View.VISIBLE;
                _textNever.visibility = View.VISIBLE;
            }
        }

        if (announcement.time != null) {
            _textTime.visibility = View.VISIBLE;
            _textTime.text = announcement.time.toHumanNowDiffString(true) + " ago"
        } else {
            _textTime.visibility = View.GONE;
        }
    }

    companion object {
        const val TAG = "AnnouncementView"
    }
}