package com.futo.platformplayer.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.states.StateSubscriptionGroups
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.others.ToggleTagView
import com.futo.platformplayer.views.adapters.viewholders.SubscriptionBarViewHolder
import com.futo.platformplayer.views.adapters.viewholders.SubscriptionGroupBarViewHolder
import com.futo.platformplayer.views.subscriptions.SubscriptionExploreButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ToggleBar : LinearLayout {
    private val _tagsContainer: LinearLayout;

    private var allowLongPress: Boolean = false;

    override fun onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow();
        StateSubscriptionGroups.instance.onGroupsChanged.remove(this);
    }

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_toggle_bar, this);

        _tagsContainer = findViewById(R.id.container_tags);
    }

    fun setToggles(vararg buttons: Toggle) {
        _tagsContainer.removeAllViews();
        for(button in buttons) {
            _tagsContainer.addView(ToggleTagView(context).apply {
                if(button.icon > 0)
                    this.setInfo(button.icon, button.name, button.isActive, button.isButton, button.tag);
                else if(button.iconVariable != null)
                    this.setInfo(button.iconVariable, button.name, button.isActive, button.isButton, button.tag);
                else
                    this.setInfo(button.name, button.isActive, button.isButton, button.tag);
                this.onClick.subscribe({ view, enabled -> button.action(view, enabled); });
                if(allowLongPress) {
                    this.onLongClick.subscribe({ view, enabled ->
                        for (tagView in _tagsContainer.children.filter { it is ToggleTagView }) {
                            if (tagView != view && tagView is ToggleTagView && !tagView.isButton) {
                                if (enabled && !tagView.isActive) {
                                    tagView.handleClick();
                                } else if (!enabled && tagView.isActive) {
                                    tagView.handleClick();
                                }
                            }
                        }
                    })
                }
                else if(button.actionLong != null) {
                    this.onLongClick.subscribe({ view, enabled ->
                        val tags = _tagsContainer.children.filter { it is ToggleTagView }.map { it as ToggleTagView }.toList();
                        button.actionLong!!(view, tags, enabled);
                    });
                }
            });
        }
    }

    class Toggle {
        val name: String;
        val icon: Int;
        val iconVariable: ImageVariable?;
        val action: (ToggleTagView, Boolean)->Unit;
        val actionLong: ((ToggleTagView, List<ToggleTagView>, Boolean) -> Unit)?;
        val isActive: Boolean;
        var isButton: Boolean = false
            private set;
        var tag: String? = null;

        constructor(name: String, icon: ImageVariable?, isActive: Boolean = false, action: (ToggleTagView, Boolean)->Unit, actionLong: ((ToggleTagView, List<ToggleTagView>, Boolean)->Unit)? = null) {
            this.name = name;
            this.icon = 0;
            this.iconVariable = icon;
            this.action = action;
            this.actionLong = actionLong;
            this.isActive = isActive;
        }
        constructor(name: String, icon: Int, isActive: Boolean = false, action: (ToggleTagView, Boolean)->Unit) {
            this.name = name;
            this.icon = icon;
            this.iconVariable = null;
            this.action = action;
            this.actionLong = null;
            this.isActive = isActive;
        }
        constructor(name: String, isActive: Boolean = false, action: (ToggleTagView, Boolean)->Unit) {
            this.name = name;
            this.icon = 0;
            this.iconVariable = null;
            this.action = action;
            this.actionLong = null;
            this.isActive = isActive;
        }

        fun asButton(): Toggle{
            isButton = true;
            return this;
        }
        fun withTag(str: String): Toggle {
            tag = str;
            return this;
        }
    }
}