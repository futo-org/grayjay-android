package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.dp
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.states.StateSubscriptionGroups
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.SearchView
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.viewholders.CreatorBarViewHolder
import com.futo.platformplayer.views.overlays.ImageVariableOverlay
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
            _view?.setGroup(parameter);
        else
            _view?.setGroup(null);
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = SubscriptionGroupView(requireContext(), this);
        _view = view;
        return view;
    }

    companion object {
        private const val TAG = "SourcesFragment";
        fun newInstance() = SubscriptionGroupFragment().apply {}
    }


    private class SubscriptionGroupView: ConstraintLayout {
        private val _fragment: SubscriptionGroupFragment;

        private val _textGroupTitleContainer: LinearLayout;
        private val _textGroupTitle: TextView;
        private val _imageGroup: ShapeableImageView;
        private val _imageGroupBackground: ImageView;
        private val _buttonEditImage: LinearLayout;
        private val _searchBar: SearchView;

        private val _textGroupMeta: TextView;

        private val _buttonSettings: ImageView;

        private val _enabledCreators: ArrayList<IPlatformChannel> = arrayListOf();
        private val _disabledCreators: ArrayList<IPlatformChannel> = arrayListOf();
        private val _enabledCreatorsFiltered: ArrayList<IPlatformChannel> = arrayListOf();
        private val _disabledCreatorsFiltered: ArrayList<IPlatformChannel> = arrayListOf();

        private val _containerEnabled: LinearLayout;
        private val _containerDisabled: LinearLayout;

        private val _recyclerCreatorsEnabled: AnyAdapterView<IPlatformChannel, CreatorBarViewHolder>;
        private val _recyclerCreatorsDisabled: AnyAdapterView<IPlatformChannel, CreatorBarViewHolder>;

        private val _overlay: FrameLayout;

        private var _group: SubscriptionGroup? = null;

        constructor(context: Context, fragment: SubscriptionGroupFragment): super(context) {
            inflate(context, R.layout.fragment_subscriptions_group, this);
            _fragment = fragment;

            _overlay = findViewById(R.id.overlay);
            _searchBar = findViewById(R.id.search_bar);
            _textGroupTitleContainer = findViewById(R.id.text_group_title_container);
            _textGroupTitle = findViewById(R.id.text_group_title);
            _imageGroup = findViewById(R.id.image_group);
            _imageGroupBackground = findViewById(R.id.group_image_background);
            _buttonEditImage = findViewById(R.id.button_edit_image);
            _textGroupMeta = findViewById(R.id.text_group_meta);
            _buttonSettings = findViewById(R.id.button_settings);
            _imageGroup.setBackgroundColor(Color.GRAY);

            val dp6 = 6.dp(resources);
            _imageGroup.shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, dp6.toFloat())
                .build()

            _containerEnabled = findViewById(R.id.container_enabled);
            _containerDisabled = findViewById(R.id.container_disabled);
            _recyclerCreatorsEnabled = findViewById<RecyclerView>(R.id.recycler_creators_enabled).asAny(_enabledCreatorsFiltered) {
                it.itemView.setPadding(0, dp6, 0, dp6);
                it.onClick.subscribe { channel ->
                    disableCreator(channel);
                };
            }
            _recyclerCreatorsDisabled = findViewById<RecyclerView>(R.id.recycler_creators_disabled).asAny(_disabledCreatorsFiltered) {
                it.itemView.setPadding(0, dp6, 0, dp6);
                it.onClick.subscribe { channel ->
                    enableCreator(channel);
                };
            }
            _recyclerCreatorsEnabled.view.layoutManager = GridLayoutManager(context, 5).apply {
                this.orientation = LinearLayoutManager.VERTICAL;
            };
            _recyclerCreatorsDisabled.view.layoutManager = GridLayoutManager(context, 5).apply {
                this.orientation = LinearLayoutManager.VERTICAL;
            };

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

            _searchBar.onSearchChanged.subscribe {
                filterCreators();
            }

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


        fun setGroup(group: SubscriptionGroup?) {
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

        @SuppressLint("NotifyDataSetChanged")
        private fun reloadCreators(group: SubscriptionGroup?) {
            _enabledCreators.clear();
            _disabledCreators.clear();

            if(group != null) {
                val urls = group.urls.toList();
                val subs = StateSubscriptions.instance.getSubscriptions().map { it.channel }
                _enabledCreators.addAll(subs.filter { urls.contains(it.url) });
                _disabledCreators.addAll(subs.filter { !urls.contains(it.url) });
            }
            filterCreators();
        }

        private fun filterCreators() {
            val query = _searchBar.textSearch.text.toString().lowercase();
            val filteredEnabled = _enabledCreators.filter { it.name.lowercase().contains(query) };
            val filteredDisabled = _disabledCreators.filter { it.name.lowercase().contains(query) };

            //Optimize
            _enabledCreatorsFiltered.clear();
            _enabledCreatorsFiltered.addAll(filteredEnabled);
            _disabledCreatorsFiltered.clear();
            _disabledCreatorsFiltered.addAll(filteredDisabled);

            _recyclerCreatorsEnabled.notifyContentChanged();
            _recyclerCreatorsDisabled.notifyContentChanged();
        }

        private fun enableCreator(channel: IPlatformChannel) {
            val index = _disabledCreatorsFiltered.indexOf(channel);
            if (index >= 0) {
                _disabledCreators.remove(channel)
                _disabledCreatorsFiltered.remove(channel);
                _recyclerCreatorsDisabled.adapter.notifyItemRangeRemoved(index);

                _enabledCreators.add(channel);
                _enabledCreatorsFiltered.add(channel);
                _recyclerCreatorsEnabled.adapter.notifyItemInserted(_enabledCreatorsFiltered.size - 1);

                _group?.let {
                    it.urls.remove(channel.url);
                    save();
                }
                updateMeta();
            }
        }
        private fun disableCreator(channel: IPlatformChannel) {
            val index = _enabledCreatorsFiltered.indexOf(channel);
            if (index >= 0) {
                _enabledCreators.remove(channel)
                _enabledCreatorsFiltered.removeAt(index);
                _recyclerCreatorsEnabled.adapter.notifyItemRangeRemoved(index);

                _disabledCreators.add(channel);
                _disabledCreatorsFiltered.add(channel);
                _recyclerCreatorsDisabled.adapter.notifyItemInserted(_disabledCreatorsFiltered.size - 1);

                _group?.let {
                    it.urls.remove(channel.url);
                    save();
                }
                updateMeta();
            }
        }

        private fun updateMeta() {
            _textGroupMeta.text = "${_enabledCreators.size} creators";
        }
    }
}