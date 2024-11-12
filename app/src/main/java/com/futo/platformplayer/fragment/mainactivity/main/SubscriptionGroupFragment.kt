package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.dp
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.states.StateSubscriptionGroups
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.SearchView
import com.futo.platformplayer.views.adapters.viewholders.CreatorBarViewHolder
import com.futo.platformplayer.views.overlays.CreatorSelectOverlay
import com.futo.platformplayer.views.overlays.ImageVariableOverlay
import com.futo.platformplayer.views.overlays.OverlayTopbar
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuTextInput
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel

class SubscriptionGroupFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = false;
    override val hasBottomBar: Boolean get() = true;

    private var _view: SubscriptionGroupView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);

        if(parameter is SubscriptionGroup)
            _view?.setGroup(StateSubscriptionGroups.instance.getSubscriptionGroup(parameter.id) ?: parameter);
        else
            _view?.setGroup(null);
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = SubscriptionGroupView(requireContext(), this);
        _view = view;
        return view;
    }

    override fun onHide() {
        super.onHide();
        _view?.onHide();
    }

    companion object {
        private const val TAG = "SourcesFragment";
        fun newInstance() = SubscriptionGroupFragment().apply {}
    }


    private class SubscriptionGroupView: ConstraintLayout {
        private val _fragment: SubscriptionGroupFragment;

        private val _topbar: OverlayTopbar;
        private val _textGroupTitleContainer: LinearLayout;
        private val _textGroupTitle: TextView;
        private val _imageGroup: ShapeableImageView;
        private val _imageGroupBackground: ImageView;
        private val _buttonEditImage: LinearLayout;
        private val _searchBar: SearchView;

        private val _textGroupMeta: TextView;

        private val _buttonSettings: ImageButton;
        private val _buttonDelete: ImageButton;

        private val _buttonAddCreator: FrameLayout;

        private val _enabledCreators: ArrayList<IPlatformChannel> = arrayListOf();
        private val _enabledCreatorsFiltered: ArrayList<IPlatformChannel> = arrayListOf();

        private val _recyclerCreatorsEnabled: AnyAdapterView<IPlatformChannel, CreatorBarViewHolder>;

        private val _overlay: FrameLayout;

        private var _group: SubscriptionGroup? = null;

        private var _didDelete: Boolean = false;

        constructor(context: Context, fragment: SubscriptionGroupFragment): super(context) {
            inflate(context, R.layout.fragment_subscriptions_group, this);
            _fragment = fragment;

            _overlay = findViewById(R.id.overlay);
            _topbar = findViewById(R.id.topbar);
            _searchBar = findViewById(R.id.search_bar);
            _textGroupTitleContainer = findViewById(R.id.text_group_title_container);
            _textGroupTitle = findViewById(R.id.text_group_title);
            _imageGroup = findViewById(R.id.image_group);
            _imageGroupBackground = findViewById(R.id.group_image_background);
            _buttonEditImage = findViewById(R.id.button_edit_image);
            _textGroupMeta = findViewById(R.id.text_group_meta);
            _buttonSettings = findViewById(R.id.button_settings);
            _buttonDelete = findViewById(R.id.button_delete);
            _buttonAddCreator = findViewById(R.id.button_creator_add);
            _imageGroup.setBackgroundColor(Color.GRAY);

            _topbar.onClose.subscribe {
                fragment.close(true);
            }

            _buttonAddCreator.setOnClickListener {
                addCreators();
            }

            val dp6 = 6.dp(resources);
            _imageGroup.shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, dp6.toFloat())
                .build()

            _recyclerCreatorsEnabled = findViewById<RecyclerView>(R.id.recycler_creators_enabled).asAny(_enabledCreatorsFiltered) {
                it.itemView.setPadding(0, dp6, 0, dp6);
                it.onClick.subscribe { channel ->
                    //disableCreator(channel);
                    UIDialogs.showDialog(context, R.drawable.ic_trash, "Delete", "Are you sure you want to delete\n[${channel.name}]?", null, 0,
                        UIDialogs.Action("Cancel", {}),
                        UIDialogs.Action("Delete", {
                            _group?.let {
                                it.urls.remove(channel.url);
                                save();
                                reloadCreators(it);
                            }
                        }, UIDialogs.ActionStyle.DANGEROUS))
                };
            }
            /*
            _recyclerCreatorsDisabled = findViewById<RecyclerView>(R.id.recycler_creators_disabled).asAny(_disabledCreatorsFiltered) {
                it.itemView.setPadding(0, dp6, 0, dp6);
                it.onClick.subscribe { channel ->
                    enableCreator(channel);
                };
            }*/
            _recyclerCreatorsEnabled.view.layoutManager = GridLayoutManager(context, 5).apply {
                this.orientation = LinearLayoutManager.VERTICAL;
            };
            /*
            _recyclerCreatorsDisabled.view.layoutManager = GridLayoutManager(context, 5).apply {
                this.orientation = LinearLayoutManager.VERTICAL;
            };*/

            _textGroupTitleContainer.setOnClickListener {
                _group?.let { editName(it) };
            };
            _textGroupMeta.setOnClickListener {
                _group?.let { editName(it) };
            };
            _imageGroup.setOnClickListener {
                _group?.let { editImage(it) };
            };
            _buttonEditImage.setOnClickListener {
                _group?.let { editImage(it) }
            };
            _buttonSettings.setOnClickListener {

            }
            _buttonDelete.setOnClickListener {
                _group?.let { g ->
                    UIDialogs.showDialog(context, R.drawable.ic_trash, "Delete Group", "Are you sure you want to this group?\n[${g.name}]?", null, 0,
                        UIDialogs.Action("Cancel", {}),
                        UIDialogs.Action("Delete", {
                            StateSubscriptionGroups.instance.deleteSubscriptionGroup(g.id, true);
                            _didDelete = true;
                            fragment.close(true);
                        }, UIDialogs.ActionStyle.DANGEROUS))
                };
            }
            _buttonSettings.visibility = View.GONE;

            _searchBar.onSearchChanged.subscribe {
                filterCreators();
            }

            _topbar.setButtons(
                Pair(R.drawable.ic_share) {
                    UIDialogs.toast(context, "Coming soon");
                }
            );

            setGroup(null);
        }

        fun save() {
            _group?.let {
                StateSubscriptionGroups.instance.updateSubscriptionGroup(it);
            };
        }

        fun editName(group: SubscriptionGroup) {
            val editView = SlideUpMenuTextInput(context, "Group name");
            editView.text = group.name;
            UISlideOverlays.showOverlay(_overlay, "Edit name", "Save", {
                editView.deactivate();
                val text = editView.text;
                if(!text.isNullOrEmpty()) {
                    group.name = text;
                    _textGroupTitle.text = text;
                    save();
                }
            }, editView).onCancel.subscribe {
                editView.deactivate();
            }
            editView.activate();
        }
        fun editImage(group: SubscriptionGroup) {
            val overlay = ImageVariableOverlay(context, _enabledCreators.map { it.url });
            _overlay.removeAllViews();
            _overlay.addView(overlay);
            _overlay.alpha = 0f
            _overlay.visibility = View.VISIBLE;
            _overlay.animate().alpha(1f).setDuration(300).start();
            overlay.onSelected.subscribe {
                group.image = it;
                it.setImageView(_imageGroup);
                it.setImageView(_imageGroupBackground);
                save();
            };
            overlay.onClose.subscribe {
                _overlay.visibility = View.GONE;
                overlay.removeAllViews();
            }
        }
        fun addCreators() {
            val overlay = CreatorSelectOverlay(context, _enabledCreators.map { it.url });
            _overlay.removeAllViews();
            _overlay.addView(overlay);
            _overlay.alpha = 0f
            _overlay.visibility = View.VISIBLE;
            _overlay.animate().alpha(1f).setDuration(300).start();
            overlay.onSelected.subscribe {
                _group?.let { g ->
                    if(g.urls.isEmpty() && g.image == null) {
                        //Obtain image
                        for(sub in it) {
                            val sub = StateSubscriptions.instance.getSubscription(sub) ?: StateSubscriptions.instance.getSubscriptionOther(sub);
                            if(sub != null && sub.channel.thumbnail != null) {
                                g.image = ImageVariable.fromUrl(sub.channel.thumbnail!!);
                                g.image?.setImageView(_imageGroup);
                                g.image?.setImageView(_imageGroupBackground);
                                break;
                            }
                        }
                    }
                    for(url in it) {
                        if(!g.urls.contains(url))
                            g.urls.add(url);
                    }
                    save();
                    reloadCreators(g);
                }
            };
            overlay.onClose.subscribe {
                _overlay.visibility = View.GONE;
                overlay.removeAllViews();
            }
        }


        fun setGroup(group: SubscriptionGroup?) {
            _didDelete = false;
            _group = group;
            _textGroupTitle.text = group?.name;

            val image = group?.image;
            if(image != null) {
                image.setImageView(_imageGroupBackground);
                image.setImageView(_imageGroup);
            }
            else {
                _imageGroupBackground.setImageResource(0);
                _imageGroup.setImageResource(0);
            }
            updateMeta();
            reloadCreators(group);
        }

        fun onHide() {
            if(!_didDelete && _group != null && StateSubscriptionGroups.instance.getSubscriptionGroup(_group!!.id) === null) {
                UIDialogs.toast(context, "Group creation cancelled");
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        private fun reloadCreators(group: SubscriptionGroup?) {
            _enabledCreators.clear();
            //_disabledCreators.clear();

            if(group != null) {
                val urls = group.urls.toList();
                val subs = urls.map {
                    (StateSubscriptions.instance.getSubscription(it) ?: StateSubscriptions.instance.getSubscriptionOther(it))?.channel
                }.filterNotNull();
                _enabledCreators.addAll(subs);
            }
            updateMeta();
            filterCreators();
        }

        private fun filterCreators() {
            val query = _searchBar.textSearch.text.toString().lowercase();
            val filteredEnabled = _enabledCreators.filter { it.name.lowercase().contains(query) };

            //Optimize
            _enabledCreatorsFiltered.clear();
            _enabledCreatorsFiltered.addAll(filteredEnabled);

            _recyclerCreatorsEnabled.notifyContentChanged();
        }

        private fun updateMeta() {
            _textGroupMeta.text = "${_group?.urls?.size} creators";
        }
    }
}