package com.futo.platformplayer.views.subscriptions

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.states.StateSubscriptionGroups
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.others.ToggleTagView
import com.futo.platformplayer.views.adapters.viewholders.SubscriptionBarViewHolder
import com.futo.platformplayer.views.adapters.viewholders.SubscriptionGroupBarViewHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubscriptionBar : LinearLayout {
    private var _adapterView: AnyAdapterView<Subscription, SubscriptionBarViewHolder>? = null;
    private var _subGroups: AnyAdapterView<SubscriptionGroup, SubscriptionGroupBarViewHolder>;
    private var _subGroupsExplore: SubscriptionExploreButton;
    private val _tagsContainer: LinearLayout;

    private val _groups: ArrayList<SubscriptionGroup>;
    private var _group: SubscriptionGroup? = null;

    val onClickChannel = Event1<SerializedChannel>();
    val onToggleGroup = Event1<SubscriptionGroup?>();
    val onHoldGroup = Event1<SubscriptionGroup>();

    override fun onAttachedToWindow() {
        super.onAttachedToWindow();
        StateSubscriptionGroups.instance.onGroupsChanged.subscribe(this) {
            findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.Main) {
                reloadGroups();
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow();
        StateSubscriptionGroups.instance.onGroupsChanged.remove(this);
    }

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_subscription_bar, this);

        val subscriptions = ArrayList(StateSubscriptions.instance.getSubscriptions().sortedByDescending { it.playbackSeconds });
        _adapterView = findViewById<RecyclerView>(R.id.recycler_creators).asAny(subscriptions, orientation = RecyclerView.HORIZONTAL) {
            it.onClick.subscribe { c ->
                onClickChannel.emit(c.channel);
            };
        };
        _groups = ArrayList(getGroups());
        _subGroups = findViewById<RecyclerView>(R.id.recycler_subgroups).asAny(_groups, orientation = RecyclerView.HORIZONTAL) {
            it.onClick.subscribe(::groupClicked);
            it.onClickLong.subscribe { g ->
                onHoldGroup.emit(g);
            }
        }
        _subGroupsExplore = findViewById(R.id.subgroup_explore);
        _tagsContainer = findViewById(R.id.container_tags);

        _subGroupsExplore.onClick.subscribe {
            UIDialogs.showDialog(context, R.drawable.ic_subscriptions, "Subscription Groups",
                "Subscription groups are an easy way to navigate your subscriptions.\n\nDefine your own subsets, and in the near future share them with others.", null, 0,
                UIDialogs.Action("Hide Bar", {
                    Settings.instance.subscriptions.showSubscriptionGroups = false;
                    Settings.instance.save();
                    reloadGroups();
                    
                    UIDialogs.showDialogOk(context, R.drawable.ic_quiz, "Subscription groups can be re-enabled in settings")
                }),
                UIDialogs.Action("Create", {
                    onToggleGroup.emit(SubscriptionGroup.Add()); //Shortcut..
                }, UIDialogs.ActionStyle.PRIMARY))
        };

        updateExplore();
    }

    private fun groupClicked(g: SubscriptionGroup) {
        if(g is SubscriptionGroup.Add) {
            onToggleGroup.emit(g);
            return;
        }
        val isSame = _group == g;
            _group?.let {
                if (it is SubscriptionGroup.Selectable) {
                    it.selected = false;
                    val index = _groups.indexOf(it);
                    if (index >= 0)
                        _subGroups.notifyContentChanged(index);
                }
            }

        if(isSame) {
            _group = null;
            onToggleGroup.emit(null);
        }
        else {
            _group = g;
            if(g is SubscriptionGroup.Selectable)
                g.selected = true;
            _subGroups.notifyContentChanged(_groups.indexOf(g));
            onToggleGroup.emit(g);
        }
    }

    private fun reloadGroups() {
        val results = getGroups();
        _groups.clear();
        _groups.addAll(results);
        _subGroups.notifyContentChanged();

        updateExplore();
    }
    private fun getGroups(): List<SubscriptionGroup> {
        return if(Settings.instance.subscriptions.showSubscriptionGroups)
            (StateSubscriptionGroups.instance.getSubscriptionGroups()
                .sortedBy { it.priority }
                .map {  SubscriptionGroup.Selectable(it, it.id == _group?.id) } +
                    listOf(SubscriptionGroup.Add()));
        else listOf();
    }

    fun updateExplore() {
        val show = Settings.instance.subscriptions.showSubscriptionGroups &&
                _groups.all { it is SubscriptionGroup.Add };
        if(show) {
            _subGroupsExplore.visibility = View.VISIBLE;
            _subGroups.view.visibility = View.GONE;
        }
        else {
            _subGroupsExplore.visibility = View.GONE;
            _subGroups.view.visibility = View.VISIBLE;
        }
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