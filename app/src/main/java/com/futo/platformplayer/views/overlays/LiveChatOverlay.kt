package com.futo.platformplayer.views.overlays

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.LiveChatManager
import com.futo.platformplayer.api.media.models.live.ILiveChatWindowDescriptor
import com.futo.platformplayer.api.media.models.live.ILiveEventChatMessage
import com.futo.platformplayer.api.media.models.live.IPlatformLiveEvent
import com.futo.platformplayer.api.media.models.live.LiveEventComment
import com.futo.platformplayer.api.media.models.live.LiveEventDonation
import com.futo.platformplayer.api.media.models.live.LiveEventRaid
import com.futo.platformplayer.api.media.models.live.LiveEventViewCount
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.dp
import com.futo.platformplayer.isHexColor
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.livechat.LiveChatDonationPill
import com.futo.platformplayer.views.livechat.LiveChatListAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import toAndroidColor


class LiveChatOverlay : LinearLayout {
    val onClose = Event0();

    private val _closeButton: ImageView;
    private val _donationList: LinearLayout;

    private val _overlay: View;

    private val _chatContainer: RecyclerView;
    private val _chatWindowContainer: WebView;
    private val _overlayHeader: ConstraintLayout;

    private val _overlayDonation: ConstraintLayout;
    private val _overlayDonation_AuthorImage: ImageView;
    private val _overlayDonation_AuthorName: TextView;
    private val _overlayDonation_Text: TextView;
    private val _overlayDonation_Amount: TextView;
    private val _overlayDonation_AmountContainer: LinearLayout;

    private val _overlayRaid: ConstraintLayout;
    private val _overlayRaid_Name: TextView;
    private val _overlayRaid_Thumbnail: ImageView;

    private val _overlayRaid_ButtonGo: Button;
    private val _overlayRaid_ButtonDismiss: Button;

    private val _textViewers: TextView;

    private val _headerHeightBase = 59;
    private val _headerHeightDonations = 94;

    private var _scope: CoroutineScope? = null;
    private var _manager: LiveChatManager? = null;
    private var _window: ILiveChatWindowDescriptor? = null;

    private val _chatLayoutManager: ChatLayoutManager;
    private val _chats = arrayListOf<ChatMessage>();
    //private val _chatAdapter: AnyAdapterView<ChatMessage, LiveChatMessageListItem>;
    private val _chatAdapter: LiveChatListAdapter;

    private var _detachCounter: Int = 0;

    private var _shownDonations: HashMap<LiveEventDonation, LiveChatDonationPill> = hashMapOf();

    private var _currentRaid: LiveEventRaid? = null;

    val onRaidNow = Event1<LiveEventRaid>();
    val onRaidPrevent = Event1<LiveEventRaid>();

    private val _argJsonSerializer = Json;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.overlay_livechat, this)

        _chatWindowContainer = findViewById(R.id.chatWindowContainer);
        _chatWindowContainer.settings.javaScriptEnabled = true;
        _chatWindowContainer.settings.domStorageEnabled = true;
        _chatWindowContainer.webViewClient = object: WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url);
                _window?.let {
                    var toRemoveJS = "";
                    for(req in it.removeElements)
                        toRemoveJS += "document.querySelectorAll("  + _argJsonSerializer.encodeToString(req) + ").forEach(x=>x.remove());\n";
                    view?.evaluateJavascript(toRemoveJS) {};
                    var toRemoveJSInterval = "";
                    for(req in it.removeElementsInterval)
                        toRemoveJSInterval += "document.querySelectorAll("  + _argJsonSerializer.encodeToString(req) + ").forEach(x=>x.remove());\n";
                    //Cleanup every second as fallback
                    view?.evaluateJavascript("setInterval(()=>{" + toRemoveJSInterval + "}, 1000)") {};
                };
            }
        };

        _chatContainer = findViewById(R.id.chatContainer);
        _chatLayoutManager = ChatLayoutManager(context);
        _chatAdapter = LiveChatListAdapter(_chats);
        _chatContainer.adapter = _chatAdapter;
        _chatContainer.layoutManager = _chatLayoutManager;

        _donationList = findViewById(R.id.donation_list);

        _overlay = findViewById(R.id.overlay);
        _overlay.setOnClickListener {
            hideOverlay();
        }

        _overlayHeader = findViewById(R.id.topbar);
        _overlayHeader.layoutTransition = LayoutTransition().apply {
            this.enableTransitionType(LayoutTransition.CHANGING);
        }

        _textViewers = findViewById(R.id.text_viewers);

        _overlayDonation = findViewById(R.id.overlay_donation);
        _overlayDonation_AuthorImage = findViewById(R.id.donation_author_image);
        _overlayDonation_AuthorName = findViewById(R.id.donation_author_name);
        _overlayDonation_Text = findViewById(R.id.donation_text);
        _overlayDonation_Amount = findViewById(R.id.donation_amount);
        _overlayDonation_AmountContainer = findViewById(R.id.donation_amount_container)

        _overlayRaid = findViewById(R.id.overlay_raid);
        _overlayRaid_Name = findViewById(R.id.raid_name);
        _overlayRaid_Thumbnail = findViewById(R.id.raid_thumbnail);
        _overlayRaid_ButtonGo = findViewById(R.id.raid_button_go);
        _overlayRaid_ButtonDismiss = findViewById(R.id.raid_button_prevent);

        _overlayRaid.visibility = View.GONE;

        _overlayRaid_ButtonGo.setOnClickListener {
            _currentRaid?.let {
                onRaidNow.emit(it);
            }
        }
        _overlayRaid_ButtonDismiss.setOnClickListener {
            _currentRaid?.let {
                _currentRaid = null;
                _overlayRaid.visibility = View.GONE;
                onRaidPrevent.emit(it);
            }
        }


        _closeButton = findViewById(R.id.button_close);
        _closeButton.setOnClickListener {
            close();
        };

        hideOverlay();
        updateDonationUI();
    }

    fun updateDonationUI() {
        _overlayHeader.layoutParams = ConstraintLayout.LayoutParams(_overlayHeader.layoutParams).apply {
                if (_shownDonations.size > 0)
                    this.height = _headerHeightDonations.dp(resources);
                else
                    this.height = _headerHeightBase.dp(resources);
            };
    }

    fun close() {
        cancel();
        onClose.emit();
    }

    fun load(scope: CoroutineScope, manager: LiveChatManager?, window: ILiveChatWindowDescriptor? = null, viewerCount: Long? = null) {
        _scope = scope;
        _donationList.removeAllViews();
        _chats.clear();
        //_chatAdapter.notifyContentChanged();
        _chatAdapter.notifyDataSetChanged();
        _manager = manager;
        _window = window;

        if(viewerCount != null)
            _textViewers.text = viewerCount.toHumanNumber() + " " + context.getString(R.string.viewers);
        else if(manager != null)
            _textViewers.text = manager.viewCount.toHumanNumber() + " " + context.getString(R.string.viewers);
        else
            _textViewers.text = "";

        if(window != null) {
            _chatWindowContainer.visibility = View.VISIBLE;
            _chatContainer.visibility = View.GONE;
            _chatWindowContainer.loadUrl(window.url);
        }
        else {
            _chatContainer.visibility = View.VISIBLE;
            _chatWindowContainer.visibility = View.GONE;
        }

        manager?.getHistory()?.let {history ->
            for(event in history)
                handleLiveEvent(event);
        }
        setRaid(null);

        //handleLiveEvent(LiveEventDonation("Test", null, "TestDonation", "$50.00", 6000, "#FF0000"))

        manager?.follow(this) {
            val comments = arrayListOf<ChatMessage>()
            for(event in it) {
                if(event is LiveEventComment)
                    comments.add(ChatMessage(event, manager, scope));
                else if(event is LiveEventDonation) {
                    comments.add(ChatMessage(event, manager, scope));
                    handleLiveEvent(event);
                }
                else if(event is LiveEventViewCount)
                    scope.launch(Dispatchers.Main) {
                        _textViewers.text = "${event.viewCount.toLong().toHumanNumber()} " + context.getString(R.string.viewers);
                    }
                else
                    handleLiveEvent(event);
            }
            checkDonations();
            addComments(*comments.toTypedArray());
        }
    }

    fun cancel() {
        _detachCounter++;
        _scope = null;
        _chats.clear();
        //_chatAdapter.notifyContentChanged();
        _chatWindowContainer.loadUrl("about:blank");
        _chatAdapter.notifyDataSetChanged();
        _manager?.unfollow(this);
        _manager?.stop(); //TODO: Remove this after proper manager gets stopped in videodetail for reuse
        _manager = null;
    }

    fun handleLiveEvent(liveEvent: IPlatformLiveEvent) {
        when(liveEvent::class) {
            LiveEventDonation::class -> addDonation(liveEvent as LiveEventDonation);
            LiveEventRaid::class -> setRaid(liveEvent as LiveEventRaid);
            LiveEventViewCount::class -> setViewCount((liveEvent as LiveEventViewCount).viewCount);
        }
    }

    fun showOverlay(action: ()->Unit) {
        _overlay.visibility = VISIBLE;
        action();
    }
    fun hideOverlay() {
        _overlay.visibility = GONE;
        _overlayDonation.visibility = GONE;
    }

    fun showDonation(donation: LiveEventDonation) {
        showOverlay {
            //TODO: Fancy animations
            if(donation.thumbnail.isNullOrEmpty())
                _overlayDonation_AuthorImage.visibility = View.GONE;
            else {
                _overlayDonation_AuthorImage.visibility = View.VISIBLE;
                Glide.with(_overlayDonation_AuthorImage)
                    .load(donation.thumbnail)
                    .into(_overlayDonation_AuthorImage);
            }
            _overlayDonation_AuthorName.text = donation.name;
            _overlayDonation_Text.text = donation.message;
            _overlayDonation_Amount.text = donation.amount.trim();
            _overlayDonation.visibility = VISIBLE;
            if(donation.colorDonation != null && donation.colorDonation.isHexColor()) {
                val color = CSSColor.parseColor(donation.colorDonation);
                _overlayDonation_AmountContainer.background.setTint(color.toAndroidColor());

                if(color.lightness > 0.5)
                    _overlayDonation_Amount.setTextColor(Color.BLACK)
                else
                    _overlayDonation_Amount.setTextColor(Color.WHITE);
            }
            else {
                _overlayDonation_AmountContainer.background.setTint(Color.parseColor("#2A2A2A"));
                _overlayDonation_Amount.setTextColor(Color.WHITE);
            }
        };
    }
    private var _dedupHackfix = "";
    fun addDonation(donation: LiveEventDonation) {
        val uniqueIdentifier = "${donation.name}${donation.amount}${donation.message}";
        if(donation.hasExpired()) {
            Logger.i(TAG, "Donation that is already expired: [${donation.amount}]" + donation.name + ":" + donation.message + " EXPIRE: ${donation.expire}");
            return;
        }
        else if(_dedupHackfix == uniqueIdentifier) {
            Logger.i(TAG, "Donation duplicate found, ignoring");
            return;
        }
        else
            Logger.i(TAG, "Donation Added: [${donation.amount}]" + donation.name + ":" + donation.message + " EXPIRE: ${donation.expire}");
        _dedupHackfix = uniqueIdentifier;

        val view = LiveChatDonationPill(context, donation);
        view.setOnClickListener {
            showDonation(donation);
        };
        _donationList.addView(view, 0);
        synchronized(_shownDonations) {
            _shownDonations.put(donation, view);
        }
        updateDonationUI();
        view.animateExpire(donation.expire);
    }
    fun checkDonations() {
        val expireds = synchronized(_shownDonations) {
            val toRemove = _shownDonations.filter { it.key.hasExpired() }
            for(remove in toRemove)
                _shownDonations.remove(remove.key);
            return@synchronized toRemove;
        }
        for(expired in expireds) {
            expired.value.animate()
                .alpha(0f)
                .setDuration(1000)
                .withEndAction({
                    _donationList.removeView(expired.value);
                    updateDonationUI();
                }).start();
        }
    }


    fun addComments(vararg comments: ChatMessage) {
        val startLength = _chats.size;

        if(_window == null) {
            _chats.addAll(comments);
            _chatAdapter.notifyItemRangeInserted(startLength, comments.size);
            _chatContainer.smoothScrollToPosition(_chats.size);
        }
    }
    fun setRaid(raid: LiveEventRaid?) {
        _currentRaid = raid;
        _scope?.launch(Dispatchers.Main) {
            _overlayRaid_Name.text = raid?.targetName ?: "";
            Glide.with(_overlayRaid_Thumbnail).clear(_overlayRaid_Thumbnail);
            if(raid != null) {
                Glide.with(_overlayRaid_Thumbnail)
                    .load(raid.targetThumbnail)
                    .into(_overlayRaid_Thumbnail);
                _overlayRaid.visibility = View.VISIBLE;
            }
            else
                _overlayRaid.visibility = View.GONE;

            _overlayRaid_ButtonGo.visibility = if (raid?.isOutgoing == true) View.VISIBLE else View.GONE
        }
    }
    fun setViewCount(viewCount: Int) {
        _scope?.launch(Dispatchers.Main) {
            _textViewers.text = viewCount.toLong().toHumanNumber() + " viewers";
        }
    }


    class ChatLayoutManager: LinearLayoutManager {
        var scrollTime: Long = 1000;

        constructor(context: Context): super(context) {
            stackFromEnd = true;
        }
        override fun smoothScrollToPosition(
            recyclerView: RecyclerView,
            state: RecyclerView.State?,
            position: Int
        ) {
            val linearSmoothScroller: LinearSmoothScroller =
                object : LinearSmoothScroller(recyclerView.context) {
                    val MILLISECONDS_PER_INCH = 2000f;
                    //TODO: Make scrollspeed = nextRequest time
                    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
                        return this@ChatLayoutManager
                            .computeScrollVectorForPosition(targetPosition);
                    }

                    override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                        return MILLISECONDS_PER_INCH / displayMetrics.densityDpi
                    }
                }
            linearSmoothScroller.targetPosition = position
            startSmoothScroll(linearSmoothScroller)
        }
    }

    class ChatMessage(
        val event: ILiveEventChatMessage,
        val manager: LiveChatManager,
        val scope: CoroutineScope
    );

    companion object {
        val TAG = "LiveChatOverlay";
    }
}