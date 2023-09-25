package com.futo.platformplayer.views.subscriptions

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.others.ToggleTagView
import com.futo.platformplayer.views.adapters.viewholders.SubscriptionBarViewHolder

class SubscriptionBar : LinearLayout {
    private var _adapterView: AnyAdapterView<Subscription, SubscriptionBarViewHolder>? = null;
    private val _tagsContainer: LinearLayout;

    val onClickChannel = Event1<SerializedChannel>();



    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_subscription_bar, this);

        val subscriptions = StateSubscriptions.instance.getSubscriptions();
        _adapterView = findViewById<RecyclerView>(R.id.recycler_creators).asAny(subscriptions, orientation = RecyclerView.HORIZONTAL) {
            it.onClick.subscribe { c ->
                onClickChannel.emit(c.channel);
            };
        };
        _tagsContainer = findViewById(R.id.container_tags);
    }


    fun setToggles(vararg buttons: Toggle) {
        _tagsContainer.removeAllViews();
        for(button in buttons) {
            _tagsContainer.addView(ToggleTagView(context).apply {
                this.setInfo(button.name, button.isActive);
                this.onClick.subscribe { button.action(it); };
            });
        }
    }

    class Toggle {
        val name: String;
        val icon: Int;
        val action: (Boolean)->Unit;
        val isActive: Boolean;

        constructor(name: String, icon: Int, isActive: Boolean = false, action: (Boolean)->Unit) {
            this.name = name;
            this.icon = icon;
            this.action = action;
            this.isActive = isActive;
        }
        constructor(name: String, isActive: Boolean = false, action: (Boolean)->Unit) {
            this.name = name;
            this.icon = 0;
            this.action = action;
            this.isActive = isActive;
        }
    }
}