package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.viewholders.CreatorBarViewHolder
import com.futo.platformplayer.views.overlays.ImageVariableOverlay
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuTextInput
import com.google.android.material.imageview.ShapeableImageView

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
        private val _buttonEditImage: LinearLayout;

        private val _textGroupMeta: TextView;

        private val _buttonSettings: ImageView;

        private val _enabledCreators: ArrayList<IPlatformChannel> = arrayListOf();
        private val _disabledCreators: ArrayList<IPlatformChannel> = arrayListOf();

        private val _containerEnabled: LinearLayout;
        private val _containerDisabled: LinearLayout;

        private val _recyclerCreatorsEnabled: AnyAdapterView<IPlatformChannel, CreatorBarViewHolder>;
        private val _recyclerCreatorsDisabled: AnyAdapterView<IPlatformChannel, CreatorBarViewHolder>;

        private val _overlay: FrameLayout;

        private var _group: SubscriptionGroup? = null;

        private val _editNameOverlayField: SlideUpMenuTextInput;


        constructor(context: Context, fragment: SubscriptionGroupFragment): super(context) {
            inflate(context, R.layout.fragment_subscriptions_group, this);
            _fragment = fragment;

            _editNameOverlayField = SlideUpMenuTextInput(context, "Group name");

            _overlay = findViewById(R.id.overlay);
            _textGroupTitleContainer = findViewById(R.id.text_group_title_container);
            _textGroupTitle = findViewById(R.id.text_group_title);
            _imageGroup = findViewById(R.id.image_group);
            _buttonEditImage = findViewById(R.id.button_edit_image);
            _textGroupMeta = findViewById(R.id.text_group_meta);
            _buttonSettings = findViewById(R.id.button_settings);

            _containerEnabled = findViewById(R.id.container_enabled);
            _containerDisabled = findViewById(R.id.container_disabled);
            _recyclerCreatorsEnabled = findViewById<RecyclerView>(R.id.recycler_creators_enabled).asAny(_enabledCreators) {
                it.onClick.subscribe { channel ->
                    disableCreator(channel);
                };
            }
            _recyclerCreatorsDisabled = findViewById<RecyclerView>(R.id.recycler_creators_disabled).asAny(_disabledCreators) {
                it.onClick.subscribe { channel ->
                    enableCreator(channel);
                };
            }

            _textGroupTitleContainer.setOnClickListener {
                _group?.let { editName(it) };
            };
            _imageGroup.setOnClickListener {
                _group?.let { editImage(it) };
            };
            _buttonEditImage.setOnClickListener {
                _group?.let { editImage(it) }
            };

            setGroup(null);
        }

        fun editName(group: SubscriptionGroup) {
            UISlideOverlays.showOverlay(_overlay, "Edit name", "Save", {
                val text = _editNameOverlayField.text;
                if(!text.isNullOrEmpty()) {
                    group.name = text;
                    _textGroupTitle.text = text;
                    //TODO: Save
                }
            }, _editNameOverlayField);
        }
        fun editImage(group: SubscriptionGroup) {
            val overlay = ImageVariableOverlay(context);
            val view = UISlideOverlays.showOverlay(_overlay, "Temp", null, {},
                overlay);
            overlay.onSelected.subscribe {
                view.hide(true);
                group.image = it;
                it.setImageView(_imageGroup);
                //TODO: Save
            };
        }


        fun setGroup(group: SubscriptionGroup?) {
            _group = group;
            _textGroupTitle.text = group?.name;

            val image = group?.image;
            if(image != null)
                image.setImageView(_imageGroup);
            else
                _imageGroup.setImageResource(0);
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
        }

        private fun enableCreator(channel: IPlatformChannel) {
            val index = _disabledCreators.indexOf(channel);
            if (index >= 0) {
                _disabledCreators.removeAt(index)
                _recyclerCreatorsDisabled.adapter.notifyItemRangeRemoved(index);

                _enabledCreators.add(channel);
                _recyclerCreatorsEnabled.adapter.notifyItemInserted(_enabledCreators.size - 1);

                _group?.let {
                    it.urls.remove(channel.url);
                    //TODO: Save
                }
                updateMeta();
            }
        }
        private fun disableCreator(channel: IPlatformChannel) {
            val index = _disabledCreators.indexOf(channel);
            if (index >= 0) {
                _disabledCreators.removeAt(index)
                _recyclerCreatorsDisabled.adapter.notifyItemRangeRemoved(index);

                _enabledCreators.add(channel);
                _recyclerCreatorsEnabled.adapter.notifyItemInserted(_enabledCreators.size - 1);

                _group?.let {
                    it.urls.remove(channel.url);
                    //TODO: Save
                }
                updateMeta();
            }
        }

        private fun updateMeta() {
            _textGroupMeta.text = "${_group?.urls?.size} creators";
        }
    }
}