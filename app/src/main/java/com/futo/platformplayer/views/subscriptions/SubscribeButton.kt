package com.futo.platformplayer.views.subscriptions

import android.content.Context
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions

class SubscribeButton : LinearLayout {
    private val _root: FrameLayout;
    private val _textSubscribe: TextView;
    private val _channelLoader: ImageView;

    var channel : IPlatformChannel? = null
        private set;
    var url : String? = null
        private set;

    var isSubscribed: Boolean = false
        private set;

    private val _subscribeTask = if (!isInEditMode) {
        TaskHandler<String, IPlatformChannel>(StateApp.instance.scopeGetter, StatePlatform.instance::getChannelLive).success(::handleSubscribe)
    } else { null };

    val onSubscribed = Event1<Subscription>();
    val onUnSubscribed = Event1<String>();


    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.button_subscribe, this, true);

        _textSubscribe = findViewById(R.id.text_subscribe);
        _channelLoader = findViewById(R.id.channel_loader);

        _root = findViewById(R.id.root);
        _root.visibility = View.INVISIBLE;
        _root.setOnClickListener {
            if (channel == null && url == null)
                return@setOnClickListener;

            var isSubscribed = if(channel != null)
                StateSubscriptions.instance.isSubscribed(channel!!)
            else StateSubscriptions.instance.isSubscribed(url!!)

            if (isSubscribed)
                handleUnSubscribe(channel?.url ?: url!!);
            else {
                if (channel != null)
                    handleSubscribe(channel!!);
                else if (url != null) {
                    setIsLoading(true);
                    _subscribeTask?.run(url!!);
                }
            }
        };

        setIsLoading(false);
    }

    private fun handleSubscribe(channel: IPlatformChannel) {
        setIsLoading(false);
        val sub = StateSubscriptions.instance.addSubscription(channel);
        UIDialogs.toast(context, context.getString(R.string.subscribed_to) + channel.name);
        setIsSubscribed(true);
        onSubscribed.emit(sub);
    }
    private fun handleUnSubscribe(url: String) {
        setIsLoading(false);
        val removed = StateSubscriptions.instance.removeSubscription(url);
        if (removed != null)
            UIDialogs.toast(context, context.getString(R.string.unsubscribed_from) + removed.channel.name);
        setIsSubscribed(false);
        onUnSubscribed.emit(url);
    }

    fun setSubscribeChannel(url: String) {
        this.channel = null;
        this.url = url;
        setIsSubscribed(StateSubscriptions.instance.isSubscribed(url));
    }
    fun setSubscribeChannel(channel: IPlatformChannel) {
        this.channel = channel;
        this.url = null;
        setIsSubscribed(StateSubscriptions.instance.isSubscribed(channel));
    }

    private fun setIsLoading(isLoading: Boolean) {
        if (isLoading) {
            _channelLoader.visibility = View.VISIBLE;
            (_channelLoader.drawable as Animatable?)?.start();
        } else {
            (_channelLoader.drawable as Animatable?)?.stop();
            _channelLoader.visibility = View.GONE;
        }
    }

    private fun setIsSubscribed(isSubcribed: Boolean) {
        val url = this.channel?.url ?: this.url;
        if (url != null) {
            if (isSubcribed) {
                _textSubscribe.text = resources.getString(R.string.unsubscribe);
                _root.setBackgroundResource(R.drawable.background_button_accent);
            }
            else {
                _textSubscribe.text = resources.getString(R.string.subscribe);
                _root.setBackgroundResource(R.drawable.background_button_primary);
            }
            _root.visibility = VISIBLE;
        }
        else
            _root.visibility = INVISIBLE;

        isSubscribed = isSubcribed;
    }
}