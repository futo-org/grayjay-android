package com.futo.platformplayer.fragment.mainactivity.bottombar

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.dp
import com.futo.platformplayer.fragment.mainactivity.MainActivityFragment
import com.futo.platformplayer.fragment.mainactivity.main.*
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePayment
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.pills.RoundButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

class MenuBottomBarFragment : MainActivityFragment() {
    private var _view: MenuBottomBarView? = null;

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = MenuBottomBarView(this, inflater);
        _view = view;
        return view;
    }

    override fun onResume() {
        super.onResume()
        _view?.updateAllButtonVisibility()
    }

    override fun onDestroyView() {
        super.onDestroyView();

        _view?.cleanup();
        _view = null;
    }

    fun onBackPressed() : Boolean {
        return _view?.onBackPressed() ?: false;
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        _view?.updateAllButtonVisibility()
    }

        @SuppressLint("ViewConstructor")
    class MenuBottomBarView : LinearLayout {
        private val _fragment: MenuBottomBarFragment;
        private val _inflater: LayoutInflater;
        private val _subscribedActivity: MainActivity?;

        private val _containerMoreHeader: ConstraintLayout;
        private val _toggleAirplaneMode: RoundButton;
        private val _togglePrivacy: RoundButton;

        private var _overlayMore: FrameLayout;
        private var _overlayMoreBackground: FrameLayout;
        private var _layoutMoreButtons: RecyclerView;
        private val _layoutMoreButtonItems = arrayListOf<MenuButtonItem>();
        private var _layoutMoreButtonsAdapter: AnyAdapterView<MenuButtonItem, MenuButtonItemViewHolder>;
        private var _layoutBottomBarButtons: LinearLayout;

        private var _moreVisible = false;
        private var _moreVisibleAnimating = false;

        private var _bottomButtons = arrayListOf<MenuButton>();
        private var _moreButtons = arrayListOf<MenuButton>();

        private var _buttonsVisible = 0;
        private var _subscriptionsVisible = true;

        private var currentButtonDefinitions: List<ButtonDefinition>? = null;

        constructor(fragment: MenuBottomBarFragment, inflater: LayoutInflater) : super(inflater.context) {
            _fragment = fragment;
            _inflater = inflater;
            inflater.inflate(R.layout.fragment_overview_bottom_bar, this);

            _containerMoreHeader = findViewById(R.id.container_more_options);
            _toggleAirplaneMode = findViewById(R.id.toggle_airplane);
            _togglePrivacy = findViewById(R.id.toggle_privacy);

            _toggleAirplaneMode.visibility = GONE;
            _toggleAirplaneMode.icon.setImageResource(R.drawable.ic_library);
            _toggleAirplaneMode.onClick.subscribe {
                if(StateApp.instance.privateMode) {
                    _togglePrivacy.icon.setImageResource(R.drawable.ic_disabled_visible);
                    //StateApp.instance.setPrivacyMode(false);
                    UIDialogs.appToast("Airplane mode disabled");
                }
                else {
                    _togglePrivacy.icon.setImageResource(R.drawable.ic_disabled_visible_purple);
                    //StateApp.instance.setPrivacyMode(true);
                    UIDialogs.appToast("Airplane mode enabled");
                }
            }
            _togglePrivacy.icon.setImageResource(R.drawable.ic_disabled_visible)
            _togglePrivacy.onClick.subscribe {
                if(StateApp.instance.privateMode) {
                    _togglePrivacy.icon.setImageResource(R.drawable.ic_disabled_visible);
                    StateApp.instance.setPrivacyMode(false);
                    UIDialogs.appToast("Privacy mode disabled");
                }
                else {
                    _togglePrivacy.icon.setImageResource(R.drawable.ic_disabled_visible_purple);
                    StateApp.instance.setPrivacyMode(true);
                    UIDialogs.appToast("Privacy mode enabled");
                }
            }

            _overlayMore = findViewById(R.id.more_overlay);
            _overlayMoreBackground = findViewById(R.id.more_overlay_background);
            _layoutMoreButtons = findViewById(R.id.more_menu_buttons);
            _layoutBottomBarButtons = findViewById(R.id.bottom_bar_buttons);

            val totalWidthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density;
            val columns = MenuButtonItemViewHolder.getAutoSizeColumns(totalWidthDp);
            _layoutMoreButtonsAdapter = _layoutMoreButtons.asAny<MenuButtonItem, MenuButtonItemViewHolder>(_layoutMoreButtonItems,
                RecyclerView.VERTICAL, false, { button ->
                button.setAutoSize(totalWidthDp);
                button.parentFragment = this@MenuBottomBarView._fragment;
                button.onClick.subscribe {
                    setMoreVisible(false);
                }
            })
            val layoutManager = GridLayoutManager(context, columns);
            _layoutMoreButtons.layoutManager = layoutManager;

            _overlayMoreBackground.setOnClickListener { setMoreVisible(false); };

            _subscribedActivity = fragment.activity as MainActivity?
            _subscribedActivity?.onNavigated?.subscribe(this) {
                updateMenuIcons();
            }

            registerUpdateButtonEvents();
            updateButtonDefinitions();
        }

        fun cleanup() {
            _subscribedActivity?.onNavigated?.remove(this)
            unregisterUpdateButtonEvents();
        }

        fun onBackPressed() : Boolean {
            if(_moreVisible) {
                setMoreVisible(false);
                return true;
            }
            return false;
        }

        private fun setMoreVisible(visible: Boolean) {

            //TODO: issues with these bools
            if (_moreVisibleAnimating) {
                return
            }

            if (_moreVisible == visible) {
                return
            }


/*
            val height = _moreButtons.firstOrNull()?.let {
                it.height.toFloat() + (it.layoutParams as MarginLayoutParams).bottomMargin
            } ?: return
            */

            _moreVisibleAnimating = true
            val moreOverlayBackground = _overlayMoreBackground
            val moreOverlay = _overlayMore
            val duration: Long = 300
            val staggerFactor = 3.0f

            if (visible) {
                moreOverlay.visibility = VISIBLE
                val animations = arrayListOf<Animator>()
                animations.add(ObjectAnimator.ofFloat(moreOverlayBackground, "alpha", 0.0f, 1.0f).setDuration(duration))
                animations.add(ObjectAnimator.ofFloat(_layoutMoreButtons, "alpha", 0.0f, 1.0f).setDuration(duration))
                animations.add(ObjectAnimator.ofFloat(_containerMoreHeader, "alpha", 0.0f, 1.0f).setDuration(duration))
                _bottomButtons.find { it.definition.id == 99 }?.let {
                    animations.add(ObjectAnimator.ofFloat(it, "alpha", 0.5f, 1.0f)
                        .setDuration(duration));
                }

                animations.add(ObjectAnimator.ofFloat(_layoutMoreButtons, "translationY", resources.displayMetrics.heightPixels.toFloat(), 0.0f).setDuration(duration))
                for ((index, button) in _moreButtons.withIndex()) {
                    val i = _moreButtons.size - index
                    //animations.add(ObjectAnimator.ofFloat(button, "translationY", height * staggerFactor * (i + 1), 0.0f).setDuration(duration))
                }

                val animatorSet = AnimatorSet()
                animatorSet.doOnEnd {
                    _moreVisibleAnimating = false
                    _moreVisible = true
                }
                animatorSet.playTogether(animations)
                animatorSet.start()
            } else {
                val animations = arrayListOf<Animator>()
                animations
                    .add(ObjectAnimator.ofFloat(moreOverlayBackground, "alpha", 1.0f, 0.0f)
                    .setDuration(duration))
                animations
                    .add(ObjectAnimator.ofFloat(_layoutMoreButtons, "alpha", 1.0f, 0.0f)
                        .setDuration(duration))
                animations
                    .add(ObjectAnimator.ofFloat(_containerMoreHeader, "alpha", 1.0f, 0.0f)
                        .setDuration(duration))
                _bottomButtons.find { it.definition.id == 99 }?.let {
                    animations.add(ObjectAnimator.ofFloat(it, "alpha", 1.0f, 0.5f)
                        .setDuration(duration));
                }

                animations.add(ObjectAnimator.ofFloat(_layoutMoreButtons, "translationY", 0.0f, resources.displayMetrics.heightPixels.toFloat()).setDuration(duration))
                for ((index, button) in _moreButtons.withIndex()) {
                    val i = _moreButtons.size - index
                    //animations.add(ObjectAnimator.ofFloat(button, "translationY", 0.0f, height * staggerFactor * (i + 1)).setDuration(duration))
                }

                val animatorSet = AnimatorSet()
                animatorSet.doOnEnd {
                    _moreVisibleAnimating = false
                    _moreVisible = false
                    moreOverlay.visibility = INVISIBLE
                }
                animatorSet.playTogether(animations)
                animatorSet.start()
            }

        }

        private fun updateBottomMenuButtons(buttons: MutableList<ButtonDefinition>, hasMore: Boolean) {
            if (hasMore) {
                buttons.add(ButtonDefinition(99, R.drawable.ic_more, R.drawable.ic_more, R.string.more, canToggle = false, { false }, { setMoreVisible(!_moreVisible) }))
            }

            _bottomButtons.clear();
            //_bottomButtonImages.clear();
            _layoutBottomBarButtons.removeAllViews();

            _layoutBottomBarButtons.addView(Space(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            })

            for ((index, button) in buttons.withIndex()) {
                val menuButton = MenuButton(context, button, _fragment, false);
                menuButton.setOnClickListener {
                    updateMenuIcons()
                    button.action(_fragment)
                    setMoreVisible(false);
                }

                _layoutBottomBarButtons.addView(menuButton)
                if (index < buttonDefinitions.size - 1) {
                    _layoutBottomBarButtons.addView(Space(context).apply {
                        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                    })
                }

                _bottomButtons.add(menuButton)
            }

            _layoutBottomBarButtons.addView(Space(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            })
        }

        private fun updateMoreButtons(buttons: MutableList<ButtonDefinition>) {
            //_moreButtonImages.clear();
            _moreButtons.clear();
            _layoutMoreButtons.removeAllViews();

            var insertedButtons = 0;
            //Force buy to be on top for more buttons
            val buyIndex = buttons.indexOfFirst { b -> b.id == 98 };
            if (buyIndex != -1) {
                val button = buttons[buyIndex]
                buttons.removeAt(buyIndex)
                buttons.add(0, button)
                insertedButtons++;
            }
            //Force faq to be second
            val faqIndex = buttons.indexOfFirst { b -> b.id == 97 };
            if (faqIndex != -1) {
                val button = buttons[faqIndex]
                buttons.removeAt(faqIndex)
                buttons.add(if (insertedButtons == 1) 1 else 0, button)
                insertedButtons++;
            }
            //Force privacy to be third
            val privacyIndex = buttons.indexOfFirst { b -> b.id == 96 };
            if (privacyIndex != -1) {
                val button = buttons[privacyIndex]
                buttons.removeAt(privacyIndex)
                buttons.add(if (insertedButtons == 2) 2 else (if(insertedButtons == 1) 1 else 0), button)
                insertedButtons++;
            }

            val newButtons = mutableListOf<MenuButtonItem>();
            for (data in buttons) {
                /*
                val button = MenuButton(context, data, _fragment, true);
                button.setOnClickListener {
                    updateMenuIcons()
                    data.action(_fragment)
                    setMoreVisible(false);
                };

                _moreButtons.add(button);
                _layoutMoreButtons.addView(button);
                */
                val buttonItem = MenuButtonItem(data);
                newButtons.add(buttonItem);
            }
            _layoutMoreButtonsAdapter.setData(newButtons);
            _layoutMoreButtonsAdapter.notifyContentChanged();
        }

        private fun updateMenuIcons() {
            for(button in _bottomButtons.toList())
                button.updateActive(_fragment);
            for(button in _moreButtons.toList())
                button.updateActive(_fragment, true);
        }

        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)

            updateAllButtonVisibility()
        }

        fun updateAllButtonVisibility() {
            // if the more fly-out menu is open the we should close it
            if(_moreVisible) {
                setMoreVisible(false)
            }

            val defs = currentButtonDefinitions?.toMutableList() ?: return
            val metrics = resources.displayMetrics
            _buttonsVisible = floor(metrics.widthPixels.toDouble() / 65.dp(resources).toDouble()).roundToInt();
            if (_buttonsVisible >= defs.size) {
                updateBottomMenuButtons(defs.toMutableList(), false);
            } else if (_buttonsVisible > 0) {
                updateBottomMenuButtons(defs.take(_buttonsVisible - 1).toMutableList(), true);
                updateMoreButtons(defs.drop(_buttonsVisible - 1).toMutableList());
            } else {
                updateBottomMenuButtons(mutableListOf(), false)
                updateMoreButtons(defs.toMutableList())
            }
        }

        private fun registerUpdateButtonEvents() {
            /*
            _subscriptionsVisible = StateSubscriptions.instance.getSubscriptionCount() > 0;
            StateSubscriptions.instance.onSubscriptionsChanged.subscribe(this) { subs, _ ->
                _subscriptionsVisible = subs.isNotEmpty();
                updateButtonDefinitions()
            }*/

            StatePayment.instance.hasPaidChanged.subscribe(this) {
                _fragment.lifecycleScope.launch(Dispatchers.Main) {
                    updateButtonDefinitions()
                }
            };

            Settings.instance.onTabsChanged.subscribe(this) {
                updateButtonDefinitions()
            }
        }

        private fun unregisterUpdateButtonEvents() {
            StateSubscriptions.instance.onSubscriptionsChanged.remove(this);
            Settings.instance.onTabsChanged.remove(this)
            StatePayment.instance.hasPaidChanged.remove(this)
        }

        private fun updateButtonDefinitions() {
            val newCurrentButtonDefinitions = Settings.instance.tabs.filter { it.enabled }.mapNotNull {
                if (it.id == 1 && !_subscriptionsVisible) {
                    return@mapNotNull null
                }

                buttonDefinitions.find { d -> d.id == it.id }
            }.toMutableList()

            //Add unconfigured tabs with default values
            buttonDefinitions.forEach { buttonDefinition ->
                if (!Settings.instance.tabs.any { it.id == buttonDefinition.id }) {
                    newCurrentButtonDefinitions.add(buttonDefinition)
                }
            }

            if (!StatePayment.instance.hasPaid) {
                newCurrentButtonDefinitions.add(ButtonDefinition(98, R.drawable.ic_paid, R.drawable.ic_paid_filled, R.string.buy, canToggle = false, { it.currentMain is BuyFragment }, { it.navigate<BuyFragment>(withHistory = true) }))
            }

            //Add conditional buttons here, when you add a conditional button, be sure to add the register and unregister events for when the button needs to be updated

            currentButtonDefinitions = newCurrentButtonDefinitions
            updateAllButtonVisibility()
        }


        class MenuButtonItem(val def: ButtonDefinition);
        class MenuButtonItemViewHolder(private val _viewGroup: ViewGroup): AnyAdapter.AnyViewHolder<MenuButtonItem>(
            LayoutInflater.from(_viewGroup.context).inflate(R.layout.list_menu_tile,
                _viewGroup, false)) {

            val onClick = Event1<MenuButtonItem>();

            val root: ConstraintLayout;
            val imageIcon: ImageView;
            val textName: TextView;


            var button: MenuButtonItem? = null;

            var parentFragment: MenuBottomBarFragment? = null;

            init {
                root = _view.findViewById(R.id.root);
                imageIcon = _view.findViewById(R.id.image_icon);
                textName = _view.findViewById(R.id.text_name);

                root.setOnClickListener {
                    button?.let {
                        it.def.action(parentFragment ?: return@let);
                        onClick.emit(it);
                    }
                }
            }


            override fun bind(value: MenuButtonItem) {
                button = value;
                textName.text = _view.context.getString(value.def.string);
                imageIcon.setImageResource(value.def.iconActive);
            }


            fun setWidth(dp: Int) {
                root.updateLayoutParams {
                    this.width = (dp - 12).dp(_viewGroup.context.resources);
                    this.height = (dp - 12).dp(_viewGroup.context.resources);
                }
                imageIcon.updateLayoutParams {
                    this.width = (dp - 46).dp(_viewGroup.context.resources);
                    this.height = (dp - 46).dp(_viewGroup.context.resources);
                }
            }

            fun setAutoSize(totalWidth: Float) {
                val dpWidth = totalWidth;
                val columns = Math.max(((dpWidth) / viewWidthDp).toInt(), 1);
                val remainder = dpWidth - columns * viewWidthDp;
                val targetSize = viewWidthDp + (remainder / columns).toInt();
                setWidth(targetSize);
            }

            companion object {
                val viewWidthDp = 90;
                fun getAutoSizeColumns(totalWidth: Float): Int {
                    val dpWidth = totalWidth;
                    val columns = Math.max(((dpWidth) / viewWidthDp).toInt(), 1);
                    return columns;
                }
            }
        }
        class MenuButton: LinearLayout {
            val definition: ButtonDefinition;

            private val _buttonImage: ImageView;
            private val _textButton: TextView;

            constructor(context: Context, def: ButtonDefinition, fragment: MenuBottomBarFragment, isMore: Boolean): super(context) {
                inflate(context, if(isMore) R.layout.view_bottom_more_menu_button else R.layout.view_bottom_menu_button, this);
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

                this.definition = def;

                _buttonImage = findViewById(R.id.image_button);
                //_buttonImage.setImageResource(if (def.isActive(fragment)) def.iconActive else def.icon);
                _buttonImage.setImageResource(definition.iconActive);
                if(definition.isActive(fragment) || isMore) {
                    this.alpha = 1f;
                }
                else {
                    this.alpha = 0.5f;
                }

                _textButton = findViewById(R.id.text_button);
                _textButton.text = resources.getString(def.string);

                val root = findViewById<LinearLayout>(R.id.root);
                root.setOnClickListener {
                    this.performClick();
                }
            }

            fun updateActive(fragment: MenuBottomBarFragment, isMore: Boolean = false, overrideValue: Boolean? = null) {
                //_buttonImage.setImageResource(if (definition.isActive(fragment)) definition.iconActive else definition.icon);
                _buttonImage.setImageResource(definition.iconActive);
                val isActive = overrideValue ?: definition.isActive(fragment) || isMore
                if(isActive) {
                    this.alpha = 1f;
                }
                else {
                    this.alpha = 0.5f;
                }
            }
        }
    }

    companion object {
        private const val TAG = "MenuBottomBarFragment";

        fun newInstance() = MenuBottomBarFragment().apply { }

        @UnstableApi
        //Add configurable buttons here
        var buttonDefinitions = listOf(
            ButtonDefinition(0, R.drawable.ic_home, R.drawable.ic_home_filled, R.string.home, canToggle = true, { it.currentMain is HomeFragment }, {
                val currentMain = it.currentMain
                if (currentMain is HomeFragment) {
                    currentMain.scrollToTop(false)
                    currentMain.reloadFeed()
                } else {
                    it.navigate<HomeFragment>(withHistory = false)
                }
            }),
            ButtonDefinition(1, R.drawable.ic_subscriptions, R.drawable.ic_subscriptions_filled, R.string.subscriptions, canToggle = true, { it.currentMain is SubscriptionsFeedFragment }, { it.navigate<SubscriptionsFeedFragment>(withHistory = false) }),
            ButtonDefinition(12, R.drawable.ic_library, R.drawable.ic_library, R.string.library, canToggle = false, { it.currentMain is LibraryFragment }, { it.navigate<LibraryFragment>(withHistory = false) }),
            ButtonDefinition(2, R.drawable.ic_creators, R.drawable.ic_creators_filled, R.string.creators, canToggle = false, { it.currentMain is CreatorsFragment }, { it.navigate<CreatorsFragment>(withHistory = false) }),
            ButtonDefinition(3, R.drawable.ic_sources, R.drawable.ic_sources_filled, R.string.sources, canToggle = false, { it.currentMain is SourcesFragment }, { it.navigate<SourcesFragment>(withHistory = false) }),
            ButtonDefinition(4, R.drawable.ic_playlist, R.drawable.ic_playlist_filled, R.string.playlists, canToggle = false, { it.currentMain is PlaylistsFragment }, { it.navigate<PlaylistsFragment>(withHistory = false) }),
            ButtonDefinition(11, R.drawable.ic_smart_display, R.drawable.ic_smart_display_filled, R.string.shorts, canToggle = true, { it.currentMain is ShortsFragment && !(it.currentMain as ShortsFragment).isChannelShortsMode }, { it.navigate<ShortsFragment>(withHistory = false) }),
            ButtonDefinition(5, R.drawable.ic_history, R.drawable.ic_history, R.string.history, canToggle = false, { it.currentMain is HistoryFragment }, { it.navigate<HistoryFragment>(withHistory = false) }),
            ButtonDefinition(6, R.drawable.ic_download, R.drawable.ic_download, R.string.downloads, canToggle = false, { it.currentMain is DownloadsFragment }, { it.navigate<DownloadsFragment>(withHistory = false) }),
            ButtonDefinition(8, R.drawable.ic_chat, R.drawable.ic_chat_filled, R.string.comments, canToggle = true, { it.currentMain is CommentsFragment }, { it.navigate<CommentsFragment>(withHistory = false) }),
            ButtonDefinition(9, R.drawable.ic_subscriptions, R.drawable.ic_subscriptions_filled, R.string.subscription_group_menu, canToggle = true, { it.currentMain is SubscriptionGroupListFragment }, { it.navigate<SubscriptionGroupListFragment>(withHistory = false) }),
            ButtonDefinition(10, R.drawable.ic_help_square, R.drawable.ic_help_square_fill, R.string.tutorials, canToggle = true, { it.currentMain is TutorialFragment }, { it.navigate<TutorialFragment>(withHistory = false) }),
            ButtonDefinition(7, R.drawable.ic_settings, R.drawable.ic_settings_filled, R.string.settings, canToggle = false, { false }, {
                it.navigate<SettingsFragment>();
                /*
                val c = it.context ?: return@ButtonDefinition;
                Logger.i(TAG, "settings preventPictureInPicture()");
                it.requireFragment<VideoDetailFragment>().preventPictureInPicture();
                val intent = Intent(c, SettingsActivity::class.java);
                c.startActivity(intent);
                if (c is Activity) {
                    c.overridePendingTransition(R.anim.slide_in_up, R.anim.slide_darken);
                }*/
            }),
            ButtonDefinition(96, R.drawable.ic_disabled_visible, R.drawable.ic_disabled_visible, R.string.privacy_mode, canToggle = true, { false }, {
                UIDialogs.showDialog(it.context ?: return@ButtonDefinition, R.drawable.ic_disabled_visible_purple, "Privacy Mode",
                    "All requests will be processed anonymously (any logins will be disabled except for the personalized home page), local playback and history tracking will also be disabled.\n\nTap the icon to disable.", null, 0,
                    UIDialogs.Action("Cancel", {
                        StateApp.instance.setPrivacyMode(false);
                    }, UIDialogs.ActionStyle.NONE),
                    UIDialogs.Action("Enable", {
                        StateApp.instance.setPrivacyMode(true);
                    }, UIDialogs.ActionStyle.PRIMARY));
            }),
            ButtonDefinition(97, R.drawable.ic_quiz, R.drawable.ic_quiz_fill, R.string.faq, canToggle = true, { false }, {
                it.navigate<BrowserFragment>(Settings.URL_FAQ, withHistory = false);
            })
            //96 is reserved for privacy button
            //98 is reserved for buy button
            //99 is reserved for more button
        );
    }

    data class ButtonDefinition(
        val id: Int,
        val icon: Int,
        val iconActive: Int,
        val string: Int,
        val canToggle: Boolean,
        val isActive: (fragment: MenuBottomBarFragment) -> Boolean,
        val action: (fragment: MenuBottomBarFragment) -> Unit);
}