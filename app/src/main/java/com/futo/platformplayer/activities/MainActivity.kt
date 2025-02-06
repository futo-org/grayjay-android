package com.futo.platformplayer.activities

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fragment.mainactivity.bottombar.MenuBottomBarFragment
import com.futo.platformplayer.fragment.mainactivity.main.BrowserFragment
import com.futo.platformplayer.fragment.mainactivity.main.BuyFragment
import com.futo.platformplayer.fragment.mainactivity.main.ChannelFragment
import com.futo.platformplayer.fragment.mainactivity.main.CommentsFragment
import com.futo.platformplayer.fragment.mainactivity.main.ContentSearchResultsFragment
import com.futo.platformplayer.fragment.mainactivity.main.CreatorSearchResultsFragment
import com.futo.platformplayer.fragment.mainactivity.main.CreatorsFragment
import com.futo.platformplayer.fragment.mainactivity.main.DownloadsFragment
import com.futo.platformplayer.fragment.mainactivity.main.HistoryFragment
import com.futo.platformplayer.fragment.mainactivity.main.HomeFragment
import com.futo.platformplayer.fragment.mainactivity.main.ImportPlaylistsFragment
import com.futo.platformplayer.fragment.mainactivity.main.ImportSubscriptionsFragment
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment
import com.futo.platformplayer.fragment.mainactivity.main.PlaylistFragment
import com.futo.platformplayer.fragment.mainactivity.main.PlaylistSearchResultsFragment
import com.futo.platformplayer.fragment.mainactivity.main.PlaylistsFragment
import com.futo.platformplayer.fragment.mainactivity.main.PostDetailFragment
import com.futo.platformplayer.fragment.mainactivity.main.RemotePlaylistFragment
import com.futo.platformplayer.fragment.mainactivity.main.SourceDetailFragment
import com.futo.platformplayer.fragment.mainactivity.main.SourcesFragment
import com.futo.platformplayer.fragment.mainactivity.main.SubscriptionGroupFragment
import com.futo.platformplayer.fragment.mainactivity.main.SubscriptionGroupListFragment
import com.futo.platformplayer.fragment.mainactivity.main.SubscriptionsFeedFragment
import com.futo.platformplayer.fragment.mainactivity.main.SuggestionsFragment
import com.futo.platformplayer.fragment.mainactivity.main.TutorialFragment
import com.futo.platformplayer.fragment.mainactivity.main.VideoDetailFragment
import com.futo.platformplayer.fragment.mainactivity.main.WatchLaterFragment
import com.futo.platformplayer.fragment.mainactivity.topbar.AddTopBarFragment
import com.futo.platformplayer.fragment.mainactivity.topbar.GeneralTopBarFragment
import com.futo.platformplayer.fragment.mainactivity.topbar.ImportTopBarFragment
import com.futo.platformplayer.fragment.mainactivity.topbar.NavigationTopBarFragment
import com.futo.platformplayer.fragment.mainactivity.topbar.SearchTopBarFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.ImportCache
import com.futo.platformplayer.models.UrlVideoWithTime
import com.futo.platformplayer.receivers.MediaButtonReceiver
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateBackup
import com.futo.platformplayer.states.StateDeveloper
import com.futo.platformplayer.states.StatePayment
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.platformplayer.stores.SubscriptionStorage
import com.futo.platformplayer.stores.v2.ManagedStore
import com.futo.platformplayer.views.ToastView
import com.futo.polycentric.core.ApiMethods
import com.google.gson.JsonParser
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue


class MainActivity : AppCompatActivity, IWithResultLauncher {

    //TODO: Move to dimensions
    private val HEIGHT_MENU_DP = 48f;
    private val HEIGHT_VIDEO_MINIMIZED_DP = 60f;

    //Containers
    lateinit var rootView: MotionLayout;

    private lateinit var _overlayContainer: FrameLayout;
    private lateinit var _toastView: ToastView;

    //Segment Containers
    private lateinit var _fragContainerTopBar: FragmentContainerView;
    private lateinit var _fragContainerMain: FragmentContainerView;
    private lateinit var _fragContainerBotBar: FragmentContainerView;
    private lateinit var _fragContainerVideoDetail: FragmentContainerView;
    private lateinit var _fragContainerOverlay: FrameLayout;

    //Views
    private lateinit var _buttonIncognito: ImageView;

    //Frags TopBar
    lateinit var _fragTopBarGeneral: GeneralTopBarFragment;
    lateinit var _fragTopBarSearch: SearchTopBarFragment;
    lateinit var _fragTopBarNavigation: NavigationTopBarFragment;
    lateinit var _fragTopBarImport: ImportTopBarFragment;
    lateinit var _fragTopBarAdd: AddTopBarFragment;

    //Frags BotBar
    lateinit var _fragBotBarMenu: MenuBottomBarFragment;

    //Frags Main
    lateinit var _fragMainHome: HomeFragment;
    lateinit var _fragPostDetail: PostDetailFragment;
    lateinit var _fragMainVideoSearchResults: ContentSearchResultsFragment;
    lateinit var _fragMainCreatorSearchResults: CreatorSearchResultsFragment;
    lateinit var _fragMainPlaylistSearchResults: PlaylistSearchResultsFragment;
    lateinit var _fragMainSuggestions: SuggestionsFragment;
    lateinit var _fragMainSubscriptions: CreatorsFragment;
    lateinit var _fragMainComments: CommentsFragment;
    lateinit var _fragMainSubscriptionsFeed: SubscriptionsFeedFragment;
    lateinit var _fragMainChannel: ChannelFragment;
    lateinit var _fragMainSources: SourcesFragment;
    lateinit var _fragMainTutorial: TutorialFragment;
    lateinit var _fragMainPlaylists: PlaylistsFragment;
    lateinit var _fragMainPlaylist: PlaylistFragment;
    lateinit var _fragMainRemotePlaylist: RemotePlaylistFragment;
    lateinit var _fragWatchlist: WatchLaterFragment;
    lateinit var _fragHistory: HistoryFragment;
    lateinit var _fragSourceDetail: SourceDetailFragment;
    lateinit var _fragDownloads: DownloadsFragment;
    lateinit var _fragImportSubscriptions: ImportSubscriptionsFragment;
    lateinit var _fragImportPlaylists: ImportPlaylistsFragment;
    lateinit var _fragBuy: BuyFragment;
    lateinit var _fragSubGroup: SubscriptionGroupFragment;
    lateinit var _fragSubGroupList: SubscriptionGroupListFragment;

    lateinit var _fragBrowser: BrowserFragment;

    //Frags Overlay
    lateinit var _fragVideoDetail: VideoDetailFragment;

    //State
    private val _queue: Queue<Pair<MainFragment, Any?>> = LinkedList();
    lateinit var fragCurrent: MainFragment private set;
    private var _parameterCurrent: Any? = null;

    var fragBeforeOverlay: MainFragment? = null; private set;

    val onNavigated = Event1<MainFragment>();

    private var _isVisible = true;
    private var _wasStopped = false;

    private val _urlQrCodeResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        scanResult?.let {
            val content = it.contents
            if (content == null) {
                UIDialogs.toast(this, getString(R.string.failed_to_scan_qr_code))
                return@let
            }

            try {
                runBlocking {
                    handleUrlAll(content)
                }
            } catch (e: Throwable) {
                Logger.i(TAG, "Failed to handle URL.", e)
                UIDialogs.toast(this, "Failed to handle URL: ${e.message}")
            }
        }
    }

    constructor() : super() {
        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        ApiMethods.UserAgent = "Grayjay Android (${BuildConfig.VERSION_CODE})";

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val writer = StringWriter();

            var excp = throwable;
            Logger.e("Application", "Uncaught", excp);

            //Resolve invocation chains
            while (excp is InvocationTargetException || excp is java.lang.RuntimeException) {
                val before = excp;

                if (excp is InvocationTargetException)
                    excp = excp.targetException ?: excp.cause ?: excp;
                else if (excp is java.lang.RuntimeException)
                    excp = excp.cause ?: excp;

                if (excp == before)
                    break;
            }
            writer.write((excp.message ?: "Empty error") + "\n\n");
            excp.printStackTrace(PrintWriter(writer));
            val message = writer.toString();
            Logger.e(TAG, message, excp);

            val exIntent = Intent(this, ExceptionActivity::class.java);
            exIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            exIntent.putExtra(ExceptionActivity.EXTRA_STACK, message);
            startActivity(exIntent);

            Runtime.getRuntime().exit(0);
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        Logger.i(TAG, "MainActivity.attachBaseContext")
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.i(TAG, "MainActivity Starting");
        StateApp.instance.setGlobalContext(this, lifecycleScope);
        StateApp.instance.mainAppStarting(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setNavigationBarColorAndIcons();
        if (Settings.instance.playback.allowVideoToGoUnderCutout)
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        runBlocking {
            StatePlatform.instance.updateAvailableClients(this@MainActivity);
        }

        //Preload common files to memory
        FragmentedStorage.get<SubscriptionStorage>();
        FragmentedStorage.get<Settings>();

        rootView = findViewById(R.id.rootView);
        _fragContainerTopBar = findViewById(R.id.fragment_top_bar);
        _fragContainerMain = findViewById(R.id.fragment_main);
        _fragContainerBotBar = findViewById(R.id.fragment_bottom_bar);
        _fragContainerVideoDetail = findViewById(R.id.fragment_overlay);
        _fragContainerOverlay = findViewById(R.id.fragment_overlay_container);
        _overlayContainer = findViewById(R.id.overlay_container);
        _toastView = findViewById(R.id.toast_view);

        //Initialize fragments

        //TopBars
        _fragTopBarGeneral = GeneralTopBarFragment.newInstance();
        _fragTopBarSearch = SearchTopBarFragment.newInstance();
        _fragTopBarNavigation = NavigationTopBarFragment.newInstance();
        _fragTopBarImport = ImportTopBarFragment.newInstance();
        _fragTopBarAdd = AddTopBarFragment.newInstance();

        //BotBars
        _fragBotBarMenu = MenuBottomBarFragment.newInstance();

        //Main
        _fragMainHome = HomeFragment.newInstance();
        _fragMainTutorial = TutorialFragment.newInstance()
        _fragMainSuggestions = SuggestionsFragment.newInstance();
        _fragMainVideoSearchResults = ContentSearchResultsFragment.newInstance();
        _fragMainCreatorSearchResults = CreatorSearchResultsFragment.newInstance();
        _fragMainPlaylistSearchResults = PlaylistSearchResultsFragment.newInstance();
        _fragMainSubscriptions = CreatorsFragment.newInstance();
        _fragMainComments = CommentsFragment.newInstance();
        _fragMainChannel = ChannelFragment.newInstance();
        _fragMainSubscriptionsFeed = SubscriptionsFeedFragment.newInstance();
        _fragMainSources = SourcesFragment.newInstance();
        _fragMainPlaylists = PlaylistsFragment.newInstance();
        _fragMainPlaylist = PlaylistFragment.newInstance();
        _fragMainRemotePlaylist = RemotePlaylistFragment.newInstance();
        _fragPostDetail = PostDetailFragment.newInstance();
        _fragWatchlist = WatchLaterFragment.newInstance();
        _fragHistory = HistoryFragment.newInstance();
        _fragSourceDetail = SourceDetailFragment.newInstance();
        _fragDownloads = DownloadsFragment();
        _fragImportSubscriptions = ImportSubscriptionsFragment.newInstance();
        _fragImportPlaylists = ImportPlaylistsFragment.newInstance();
        _fragBuy = BuyFragment.newInstance();
        _fragSubGroup = SubscriptionGroupFragment.newInstance();
        _fragSubGroupList = SubscriptionGroupListFragment.newInstance();

        _fragBrowser = BrowserFragment.newInstance();

        //Overlays
        _fragVideoDetail = VideoDetailFragment.newInstance();
        //Overlay Init
        _fragVideoDetail.onMinimize.subscribe { };
        _fragVideoDetail.onShownEvent.subscribe {
            _fragMainHome.setPreviewsEnabled(false);
            _fragMainVideoSearchResults.setPreviewsEnabled(false);
            _fragMainSubscriptionsFeed.setPreviewsEnabled(false);
        };


        _fragVideoDetail.onMinimize.subscribe {
            updateSegmentPaddings();
        };
        _fragVideoDetail.onTransitioning.subscribe {
            if (it || _fragVideoDetail.state != VideoDetailFragment.State.MINIMIZED)
                _fragContainerOverlay.elevation =
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15f, resources.displayMetrics);
            else
                _fragContainerOverlay.elevation =
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics);
        }

        _fragVideoDetail.onCloseEvent.subscribe {
            _fragMainHome.setPreviewsEnabled(true);
            _fragMainVideoSearchResults.setPreviewsEnabled(true);
            _fragMainSubscriptionsFeed.setPreviewsEnabled(true);
            _fragContainerVideoDetail.visibility = View.INVISIBLE;
            updateSegmentPaddings();
        };


        _buttonIncognito = findViewById(R.id.incognito_button);
        _buttonIncognito.elevation = -99f;
        _buttonIncognito.alpha = 0f;
        StateApp.instance.privateModeChanged.subscribe {
            //Messing with visibility causes some issues with layout ordering?
            if (it) {
                _buttonIncognito.elevation = 99f;
                _buttonIncognito.alpha = 1f;
            } else {
                _buttonIncognito.elevation = -99f;
                _buttonIncognito.alpha = 0f;
            }
        }
        _buttonIncognito.setOnClickListener {
            if (!StateApp.instance.privateMode)
                return@setOnClickListener;
            UIDialogs.showDialog(
                this, R.drawable.ic_disabled_visible_purple, "Disable Privacy Mode",
                "Do you want to disable privacy mode? New videos will be tracked again.", null, 0,
                UIDialogs.Action("Cancel", {
                    StateApp.instance.setPrivacyMode(true);
                }, UIDialogs.ActionStyle.NONE),
                UIDialogs.Action("Disable", {
                    StateApp.instance.setPrivacyMode(false);
                }, UIDialogs.ActionStyle.DANGEROUS)
            );
        };
        _fragVideoDetail.onFullscreenChanged.subscribe {
            Logger.i(TAG, "onFullscreenChanged ${it}");

            if (it) {
                _buttonIncognito.elevation = -99f;
                _buttonIncognito.alpha = 0f;
            } else {
                if (StateApp.instance.privateMode) {
                    _buttonIncognito.elevation = 99f;
                    _buttonIncognito.alpha = 1f;
                } else {
                    _buttonIncognito.elevation = -99f;
                    _buttonIncognito.alpha = 0f;
                }
            }
        }

        StatePlayer.instance.also {
            it.onQueueChanged.subscribe { shouldSwapCurrentItem ->
                if (!shouldSwapCurrentItem) {
                    return@subscribe;
                }

                if (_fragVideoDetail.state == VideoDetailFragment.State.CLOSED) {
                    if (fragCurrent !is VideoDetailFragment) {
                        val toPlay = StatePlayer.instance.getCurrentQueueItem();
                        navigate(_fragVideoDetail, toPlay);

                        if (!StatePlayer.instance.queueFocused)
                            _fragVideoDetail.minimizeVideoDetail();
                    }
                } else {
                    val toPlay = StatePlayer.instance.getCurrentQueueItem() ?: return@subscribe;
                    Logger.i(TAG, "Queue changed _fragVideoDetail.currentUrl=${_fragVideoDetail.currentUrl} toPlay.url=${toPlay.url}")
                    if (_fragVideoDetail.currentUrl == null || _fragVideoDetail.currentUrl != toPlay.url) {
                        navigate(_fragVideoDetail, toPlay);
                    }
                }
            };
        }

        onNavigated.subscribe {
            updateSegmentPaddings();
        }


        //Set top bars
        _fragMainHome.topBar = _fragTopBarGeneral;
        _fragMainSubscriptions.topBar = _fragTopBarGeneral;
        _fragMainComments.topBar = _fragTopBarGeneral;
        _fragMainSuggestions.topBar = _fragTopBarSearch;
        _fragMainVideoSearchResults.topBar = _fragTopBarSearch;
        _fragMainCreatorSearchResults.topBar = _fragTopBarSearch;
        _fragMainPlaylistSearchResults.topBar = _fragTopBarSearch;
        _fragMainChannel.topBar = _fragTopBarNavigation;
        _fragMainTutorial.topBar = _fragTopBarNavigation;
        _fragMainSubscriptionsFeed.topBar = _fragTopBarGeneral;
        _fragMainSources.topBar = _fragTopBarAdd;
        _fragMainPlaylists.topBar = _fragTopBarGeneral;
        _fragMainPlaylist.topBar = _fragTopBarNavigation;
        _fragMainRemotePlaylist.topBar = _fragTopBarNavigation;
        _fragPostDetail.topBar = _fragTopBarNavigation;
        _fragWatchlist.topBar = _fragTopBarNavigation;
        _fragHistory.topBar = _fragTopBarNavigation;
        _fragSourceDetail.topBar = _fragTopBarNavigation;
        _fragDownloads.topBar = _fragTopBarGeneral;
        _fragImportSubscriptions.topBar = _fragTopBarImport;
        _fragImportPlaylists.topBar = _fragTopBarImport;
        _fragSubGroupList.topBar = _fragTopBarAdd;

        _fragBrowser.topBar = _fragTopBarNavigation;

        fragCurrent = _fragMainHome;

        val defaultTab = Settings.instance.tabs.mapNotNull {
            val buttonDefinition =
                MenuBottomBarFragment.buttonDefinitions.firstOrNull { bd -> it.id == bd.id };
            if (buttonDefinition == null) {
                return@mapNotNull null;
            } else {
                return@mapNotNull Pair(it, buttonDefinition);
            }
        }.first { it.first.enabled }.second;

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_top_bar, _fragTopBarGeneral)
            .replace(R.id.fragment_main, _fragMainHome)
            .replace(R.id.fragment_bottom_bar, _fragBotBarMenu)
            .replace(R.id.fragment_overlay, _fragVideoDetail)
            .commitNow();

        defaultTab.action(_fragBotBarMenu);
        StateSubscriptions.instance;

        fragCurrent.onShown(null, false);

        //Other stuff
        rootView.progress = 0f;

        handleIntent(intent);

        if (Settings.instance.casting.enabled) {
            StateCasting.instance.start(this);
        }

        StatePlatform.instance.onDevSourceChanged.subscribe {
            Logger.i(TAG, "onDevSourceChanged")

            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    if (!_isVisible) {
                        val bringUpIntent = Intent(this@MainActivity, MainActivity::class.java);
                        bringUpIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        bringUpIntent.action = "TAB";
                        bringUpIntent.putExtra("TAB", "Sources");
                        startActivity(bringUpIntent);
                    } else {
                        _fragVideoDetail.closeVideoDetails();
                        navigate(_fragMainSources);
                    }
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to make sources front.", e);
                }
            }
        };

        StateApp.instance.mainAppStarted(this);

        //if(ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        //    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE), 123);
        //else
        StateApp.instance.mainAppStartedWithExternalFiles(this);

        //startActivity(Intent(this, TestActivity::class.java));

        // updates the requestedOrientation based on user settings
        _fragVideoDetail.updateOrientation()

        val sharedPreferences =
            getSharedPreferences("GrayjayFirstBoot", Context.MODE_PRIVATE)
        val isFirstBoot = sharedPreferences.getBoolean("IsFirstBoot", true)
        if (isFirstBoot) {
            UIDialogs.showConfirmationDialog(this, getString(R.string.do_you_want_to_see_the_tutorials_you_can_find_them_at_any_time_through_the_more_button), {
                navigate(_fragMainTutorial)
            })

            sharedPreferences.edit().putBoolean("IsFirstBoot", false).apply()
        }

        val submissionStatus = FragmentedStorage.get<StringStorage>("subscriptionSubmissionStatus")

        val numSubscriptions = StateSubscriptions.instance.getSubscriptionCount()

        val subscriptionsThreshold = 20

        if (
            submissionStatus.value == ""
            && StateApp.instance.getCurrentNetworkState() != StateApp.NetworkState.DISCONNECTED
            && numSubscriptions >= subscriptionsThreshold
        ) {

            UIDialogs.showDialog(
                this,
                R.drawable.ic_internet,
                getString(R.string.contribute_personal_subscriptions_list),
                getString(R.string.contribute_personal_subscriptions_list_description),
                null,
                0,
                UIDialogs.Action("Cancel", {
                    submissionStatus.setAndSave("dismissed")
                }, UIDialogs.ActionStyle.NONE),
                UIDialogs.Action("Upload", {
                    submissionStatus.setAndSave("submitted")

                    GlobalScope.launch(Dispatchers.IO) {
                        @Serializable
                        data class CreatorInfo(val pluginId: String, val url: String)

                        val subscriptions =
                            StateSubscriptions.instance.getSubscriptions().map { original ->
                                CreatorInfo(
                                    pluginId = original.channel.id.pluginId ?: "",
                                    url = original.channel.url
                                )
                            }

                        val json = Json.encodeToString(subscriptions)

                        val url = "https://data.grayjay.app/donate-subscription-list"
                        val client = ManagedHttpClient();
                        val headers = hashMapOf(
                            "Content-Type" to "application/json"
                        )
                        try {
                            val response = client.post(url, json, headers)
                            // if it failed retry one time
                            if (!response.isOk) {
                                client.post(url, json, headers)
                            }
                        } catch (e: Exception) {
                            Logger.i(TAG, "Failed to submit subscription list.", e)
                        }
                    }
                }, UIDialogs.ActionStyle.PRIMARY)
            )
        }
    }

    /*
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode != 123)
            return;

        if(grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            StateApp.instance.mainAppStartedWithExternalFiles(this);
        else {
            UIDialogs.showDialog(this, R.drawable.ic_help, "File Permissions", "Grayjay requires file permissions for exporting downloads and automatic backups", null, 0,
                UIDialogs.Action("Cancel", {}),
                UIDialogs.Action("Configure", {
                    startActivity(Intent().apply {
                        action = android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION;
                        data = Uri.fromParts("package", packageName, null)
                    });
                }, UIDialogs.ActionStyle.PRIMARY));
        }
            UIDialogs.toast(this, "No external file permissions\nExporting and auto backups will not work");
    }*/

    fun showUrlQrCodeScanner() {
        try {
            val integrator = IntentIntegrator(this)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.setPrompt(getString(R.string.scan_a_qr_code))
            integrator.setOrientationLocked(true);
            integrator.setCameraId(0)
            integrator.setBeepEnabled(false)
            integrator.setBarcodeImageEnabled(true)
            integrator.captureActivity = QRCaptureActivity::class.java
            _urlQrCodeResultLauncher.launch(integrator.createScanIntent())
        } catch (e: Throwable) {
            Logger.i(TAG, "Failed to handle show QR scanner.", e)
            UIDialogs.toast(this, "Failed to show QR scanner: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume();
        Logger.v(TAG, "onResume")
        _isVisible = true;
    }

    override fun onPause() {
        super.onPause();
        Logger.v(TAG, "onPause")
        _isVisible = false;
    }

    override fun onStop() {
        super.onStop()
        Logger.v(TAG, "_wasStopped = true");
        _wasStopped = true;
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null)
            return;
        Logger.i(TAG, "handleIntent started by " + intent.action);


        var targetData: String? = null;

        when (intent.action) {
            Intent.ACTION_SEND -> {
                targetData = intent.getStringExtra(Intent.EXTRA_STREAM)
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT);
                Logger.i(TAG, "Share Received: " + targetData);
            }

            Intent.ACTION_VIEW -> {
                targetData = intent.dataString

                if (!targetData.isNullOrEmpty()) {
                    Logger.i(TAG, "View Received: " + targetData);
                }
            }

            "VIDEO" -> {
                val url = intent.getStringExtra("VIDEO");
                navigate(_fragVideoDetail, url);
            }

            "IMPORT_OPTIONS" -> {
                UIDialogs.showImportOptionsDialog(this);
            }

            "ACTION" -> {
                val action = intent.getStringExtra("ACTION");
                StateDeveloper.instance.testState = "TestPlayback";
                StateDeveloper.instance.testPlayback();
            }

            "TAB" -> {
                when (intent.getStringExtra("TAB")) {
                    "Sources" -> {
                        runBlocking {
                            StatePlatform.instance.updateAvailableClients(this@MainActivity, true) //Ideally this is not needed..
                            navigate(_fragMainSources);
                        }
                    };
                    "BROWSE_PLUGINS" -> {
                        navigate(_fragBrowser, BrowserFragment.NavigateOptions("https://plugins.grayjay.app/phone.html", mapOf(
                            Pair("grayjay") { req ->
                                StateApp.instance.contextOrNull?.let {
                                    if (it is MainActivity) {
                                        runBlocking {
                                            it.handleUrlAll(req.url.toString());
                                        }
                                    }
                                };
                            }
                        )));
                    }
                }
            }
        }

        try {
            if (targetData != null) {
                runBlocking {
                    handleUrlAll(targetData)
                }
            }
        } catch (ex: Throwable) {
            UIDialogs.showGeneralErrorDialog(this, getString(R.string.failed_to_handle_file), ex);
        }
    }

    suspend fun handleUrlAll(url: String) {
        val uri = Uri.parse(url)
        when (uri.scheme) {
            "grayjay" -> {
                if (url.startsWith("grayjay://license/")) {
                    if (StatePayment.instance.setPaymentLicenseUrl(url)) {
                        UIDialogs.showDialogOk(this, R.drawable.ic_check, getString(R.string.your_license_key_has_been_set_an_app_restart_might_be_required));

                        if (fragCurrent is BuyFragment)
                            closeSegment(fragCurrent);
                    } else
                        UIDialogs.toast(getString(R.string.invalid_license_format));

                } else if (url.startsWith("grayjay://plugin/")) {
                    val intent = Intent(this, AddSourceActivity::class.java).apply {
                        data = Uri.parse(url.substring("grayjay://plugin/".length));
                    };
                    startActivity(intent);
                } else if (url.startsWith("grayjay://video/")) {
                    val videoUrl = url.substring("grayjay://video/".length);
                    navigate(_fragVideoDetail, videoUrl);
                } else if (url.startsWith("grayjay://channel/")) {
                    val channelUrl = url.substring("grayjay://channel/".length);
                    navigate(_fragMainChannel, channelUrl);
                }
            }

            "content" -> {
                if (!handleContent(url, intent.type)) {
                    UIDialogs.showSingleButtonDialog(
                        this,
                        R.drawable.ic_play,
                        getString(R.string.unknown_content_format) + " [${url}]\n[${intent.type}]",
                        "Ok",
                        { });
                }
            }

            "file" -> {
                if (!handleFile(url)) {
                    UIDialogs.showSingleButtonDialog(
                        this,
                        R.drawable.ic_play,
                        getString(R.string.unknown_file_format) + " [${url}]",
                        "Ok",
                        { });
                }
            }

            "polycentric" -> {
                if (!handlePolycentric(url)) {
                    UIDialogs.showSingleButtonDialog(
                        this,
                        R.drawable.ic_play,
                        getString(R.string.unknown_polycentric_format) + " [${url}]",
                        "Ok",
                        { });
                }
            }

            "fcast" -> {
                if (!handleFCast(url)) {
                    UIDialogs.showSingleButtonDialog(
                        this,
                        R.drawable.ic_cast,
                        "Unknown FCast format [${url}]",
                        "Ok",
                        { });
                }
            }

            else -> {
                if (!handleUrl(url)) {
                    UIDialogs.showSingleButtonDialog(
                        this,
                        R.drawable.ic_play,
                        getString(R.string.unknown_url_format) + " [${url}]",
                        "Ok",
                        { });
                }
            }
        }
    }

    suspend fun handleUrl(url: String, position: Int = 0): Boolean {
        Logger.i(TAG, "handleUrl(url=$url)")

        return withContext(Dispatchers.IO) {
            Logger.i(TAG, "handleUrl(url=$url) on IO");
            if (StatePlatform.instance.hasEnabledVideoClient(url)) {
                Logger.i(TAG, "handleUrl(url=$url) found video client");
                lifecycleScope.launch(Dispatchers.Main) {
                    if (position > 0)
                        navigate(_fragVideoDetail, UrlVideoWithTime(url, position.toLong(), true));
                    else
                        navigate(_fragVideoDetail, url);

                    _fragVideoDetail.maximizeVideoDetail(true);
                }
                return@withContext true;
            } else if (StatePlatform.instance.hasEnabledChannelClient(url)) {
                Logger.i(TAG, "handleUrl(url=$url) found channel client");
                lifecycleScope.launch(Dispatchers.Main) {
                    navigate(_fragMainChannel, url);
                    delay(100);
                    _fragVideoDetail.minimizeVideoDetail();
                };
                return@withContext true;
            } else if (StatePlatform.instance.hasEnabledPlaylistClient(url)) {
                Logger.i(TAG, "handleUrl(url=$url) found playlist client");
                lifecycleScope.launch(Dispatchers.Main) {
                    navigate(_fragMainRemotePlaylist, url);
                    delay(100);
                    _fragVideoDetail.minimizeVideoDetail();
                };
                return@withContext true;
            }
            return@withContext false;
        }
    }

    fun handleContent(file: String, mime: String? = null): Boolean {
        Logger.i(TAG, "handleContent(url=$file)");

        val data = readSharedContent(file);
        if (file.lowercase().endsWith(".json") || mime == "application/json") {
            var recon = String(data);
            if (!recon.trim().startsWith("["))
                return handleUnknownJson(recon);

            var reconLines = Json.decodeFromString<List<String>>(recon);
            val cacheStr =
                reconLines.find { it.startsWith("__CACHE:") }?.substring("__CACHE:".length);
            reconLines = reconLines.filter { !it.startsWith("__CACHE:") }; //TODO: constant prefix
            var cache: ImportCache? = null;
            try {
                if (cacheStr != null)
                    cache = Json.decodeFromString(cacheStr);
            } catch (ex: Throwable) {
                Logger.e(TAG, "Failed to deserialize cache");
            }


            recon = reconLines.joinToString("\n");
            Logger.i(TAG, "Opened shared playlist reconstruction\n${recon}");
            handleReconstruction(recon, cache);
            return true;
        } else if (file.lowercase().endsWith(".zip") || mime == "application/zip") {
            StateBackup.importZipBytes(this, lifecycleScope, data);
            return true;
        } else if (file.lowercase().endsWith(".txt") || mime == "text/plain") {
            return handleUnknownText(String(data));
        }
        return false;
    }

    fun handleFile(file: String): Boolean {
        Logger.i(TAG, "handleFile(url=$file)");
        if (file.lowercase().endsWith(".json")) {
            var recon = String(readSharedFile(file));
            if (!recon.startsWith("["))
                return handleUnknownJson(recon);

            var reconLines = Json.decodeFromString<List<String>>(recon);
            val cacheStr =
                reconLines.find { it.startsWith("__CACHE:") }?.substring("__CACHE:".length);
            reconLines = reconLines.filter { !it.startsWith("__CACHE:") }; //TODO: constant prefix
            var cache: ImportCache? = null;
            try {
                if (cacheStr != null)
                    cache = Json.decodeFromString(cacheStr);
            } catch (ex: Throwable) {
                Logger.e(TAG, "Failed to deserialize cache");
            }
            recon = reconLines.joinToString("\n");

            Logger.i(TAG, "Opened shared playlist reconstruction\n${recon}");
            handleReconstruction(recon, cache);
            return true;
        } else if (file.lowercase().endsWith(".zip")) {
            StateBackup.importZipBytes(this, lifecycleScope, readSharedFile(file));
            return true;
        } else if (file.lowercase().endsWith(".txt")) {
            return handleUnknownText(String(readSharedFile(file)));
        }
        return false;
    }

    fun handleReconstruction(recon: String, cache: ImportCache? = null) {
        val type = ManagedStore.getReconstructionIdentifier(recon);
        val store: ManagedStore<*> = when (type) {
            "Playlist" -> StatePlaylists.instance.playlistStore
            else -> {
                UIDialogs.toast(getString(R.string.unknown_reconstruction_type) + " ${type}", false);
                return;
            };
        };

        val name = when (type) {
            "Playlist" -> recon.split("\n")
                .filter { !it.startsWith(ManagedStore.RECONSTRUCTION_HEADER_OPERATOR) }
                .firstOrNull() ?: type;
            else -> type
        }


        if (!type.isNullOrEmpty()) {
            UIDialogs.showImportDialog(this, store, name, listOf(recon), cache) {

            }
        }
    }

    fun handleUnknownText(text: String): Boolean {
        try {
            if (text.startsWith("@/Subscription") || text.startsWith("Subscriptions")) {
                val lines = text.split("\n").map { it.trim() }.drop(1).filter { it.isNotEmpty() };
                navigate(_fragImportSubscriptions, lines);
                return true;
            }
        } catch (ex: Throwable) {
            Logger.e(TAG, ex.message, ex);
            UIDialogs.showGeneralErrorDialog(this, getString(R.string.failed_to_parse_text_file), ex);
        }
        return false;
    }

    fun handleUnknownJson(json: String): Boolean {

        val context = this;

        //TODO: Proper import selection
        try {
            val newPipeSubsParsed = JsonParser.parseString(json).asJsonObject;
            if (!newPipeSubsParsed.has("subscriptions") || !newPipeSubsParsed["subscriptions"].isJsonArray)
                return false;//throw IllegalArgumentException("Invalid NewPipe json structure found");

            StateBackup.importNewPipeSubs(this, newPipeSubsParsed);
        } catch (ex: Exception) {
            Logger.e(TAG, ex.message, ex);
            UIDialogs.showGeneralErrorDialog(context, getString(R.string.failed_to_parse_newpipe_subscriptions), ex);
        }

        /*
        lifecycleScope.launch(Dispatchers.Main) {
            UISlideOverlays.showOverlay(_overlayContainer, "Import Json", "", {},
                SlideUpMenuGroup(context, "What kind of json import is this?", "",
                    SlideUpMenuItem(context, 0, "NewPipe Subscriptions", "", "NewPipeSubs", {
                    }))
            );
        }*/


        return true;
    }


    fun handlePolycentric(url: String): Boolean {
        Logger.i(TAG, "handlePolycentric");
        startActivity(Intent(this, PolycentricImportProfileActivity::class.java).apply { putExtra("url", url) })
        return true;
    }

    fun handleFCast(url: String): Boolean {
        Logger.i(TAG, "handleFCast");

        try {
            StateCasting.instance.handleUrl(this, url)
            return true;
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse FCast URL '${url}'.", e)
        }

        return false
    }

    private fun readSharedContent(contentPath: String): ByteArray {
        return contentResolver.openInputStream(Uri.parse(contentPath))?.use {
            return it.readBytes();
        } ?: throw IllegalStateException("Opened content was not accessible");
    }

    private fun readSharedFile(filePath: String): ByteArray {
        val dataFile = File(filePath);
        if (!dataFile.exists())
            throw IllegalArgumentException("Opened file does not exist or not permitted");
        val data = dataFile.readBytes();
        return data;
    }

    override fun onBackPressed() {
        Logger.i(TAG, "onBackPressed")

        if (_fragBotBarMenu.onBackPressed())
            return;

        if (_fragVideoDetail.state == VideoDetailFragment.State.MAXIMIZED && _fragVideoDetail.onBackPressed())
            return;

        if (!fragCurrent.onBackPressed())
            closeSegment();
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint();
        Logger.i(TAG, "onUserLeaveHint")

        if (_fragVideoDetail.state == VideoDetailFragment.State.MAXIMIZED || _fragVideoDetail.state == VideoDetailFragment.State.MINIMIZED)
            _fragVideoDetail.onUserLeaveHint();
    }

    override fun onRestart() {
        super.onRestart();
        Logger.i(TAG, "onRestart");
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        val isStop: Boolean = lifecycle.currentState == Lifecycle.State.CREATED;
        Logger.v(TAG, "onPictureInPictureModeChanged isInPictureInPictureMode=$isInPictureInPictureMode isStop=$isStop")
        _fragVideoDetail.onPictureInPictureModeChanged(isInPictureInPictureMode, isStop, newConfig);
        Logger.v(TAG, "onPictureInPictureModeChanged Ready");
    }

    override fun onDestroy() {
        super.onDestroy();
        Logger.v(TAG, "onDestroy")
        StateApp.instance.mainAppDestroyed(this);
    }

    inline fun <reified T> isFragmentActive(): Boolean {
        return fragCurrent is T;
    }

    /**
     * Navigate takes a MainFragment, and makes them the current main visible view
     * A parameter can be provided which becomes available in the onShow of said fragment
     */
    @SuppressLint("CommitTransaction")
    fun navigate(segment: MainFragment, parameter: Any? = null, withHistory: Boolean = true, isBack: Boolean = false) {
        Logger.i(TAG, "Navigate to $segment (parameter=$parameter, withHistory=$withHistory, isBack=$isBack)")

        if (segment != fragCurrent) {

            if (segment is VideoDetailFragment) {
                if (_fragContainerVideoDetail.visibility != View.VISIBLE)
                    _fragContainerVideoDetail.visibility = View.VISIBLE;
                when (segment.state) {
                    VideoDetailFragment.State.MINIMIZED -> segment.maximizeVideoDetail()
                    VideoDetailFragment.State.CLOSED -> segment.maximizeVideoDetail()
                    else -> {}
                }
                segment.onShown(parameter, isBack);
                return;
            }

            fragCurrent.onHide();

            if (segment.isMainView) {
                var transaction = supportFragmentManager.beginTransaction();
                if (segment.topBar != null) {
                    if (segment.topBar != fragCurrent.topBar) {
                        transaction = transaction
                            .show(segment.topBar as Fragment)
                            .replace(R.id.fragment_top_bar, segment.topBar as Fragment);
                        fragCurrent.topBar?.onHide();
                    }
                } else if (fragCurrent.topBar != null)
                    transaction.hide(fragCurrent.topBar as Fragment);

                transaction = transaction.replace(R.id.fragment_main, segment);

                if (segment.hasBottomBar) {
                    if (!fragCurrent.hasBottomBar)
                        transaction = transaction.show(_fragBotBarMenu);
                } else {
                    if (fragCurrent.hasBottomBar)
                        transaction = transaction.hide(_fragBotBarMenu);
                }
                transaction.commitNow();
            } else {

                if (!segment.hasBottomBar) {
                    supportFragmentManager.beginTransaction()
                        .hide(_fragBotBarMenu)
                        .commitNow();
                }
            }

            if (fragCurrent.isHistory && withHistory && _queue.lastOrNull() != fragCurrent)
                _queue.add(Pair(fragCurrent, _parameterCurrent));

            if (segment.isOverlay && !fragCurrent.isOverlay && withHistory)// && fragCurrent.isHistory)
                fragBeforeOverlay = fragCurrent;


            fragCurrent = segment;
            _parameterCurrent = parameter;
        }

        segment.topBar?.onShown(parameter);
        segment.onShown(parameter, isBack);
        onNavigated.emit(segment);
    }

    /**
     * Called when the current segment (main) should be closed, if already at a root view (tab), close application
     * If called with a non-null fragment, it will only close if the current fragment is the provided one
     */
    fun closeSegment(fragment: MainFragment? = null) {
        if (fragment is VideoDetailFragment) {
            fragment.onHide();
            return;
        }

        if ((fragment?.isOverlay ?: false) && fragBeforeOverlay != null) {
            navigate(fragBeforeOverlay!!, null, false, true);
        } else {
            val last = _queue.lastOrNull();
            if (last != null) {
                _queue.remove(last);
                navigate(last.first, last.second, false, true);
            } else {
                if (_fragVideoDetail.state == VideoDetailFragment.State.CLOSED) {
                    finish();
                } else {
                    UIDialogs.showConfirmationDialog(this, "There is a video playing, are you sure you want to exit the app?", {
                        finish();
                    })
                }
            }
        }
    }

    /**
     * Provides the fragment instance for the provided fragment class
     */
    inline fun <reified T : Fragment> getFragment(): T {
        return when (T::class) {
            HomeFragment::class -> _fragMainHome as T;
            TutorialFragment::class -> _fragMainTutorial as T;
            ContentSearchResultsFragment::class -> _fragMainVideoSearchResults as T;
            CreatorSearchResultsFragment::class -> _fragMainCreatorSearchResults as T;
            SuggestionsFragment::class -> _fragMainSuggestions as T;
            VideoDetailFragment::class -> _fragVideoDetail as T;
            MenuBottomBarFragment::class -> _fragBotBarMenu as T;
            GeneralTopBarFragment::class -> _fragTopBarGeneral as T;
            SearchTopBarFragment::class -> _fragTopBarSearch as T;
            CreatorsFragment::class -> _fragMainSubscriptions as T;
            CommentsFragment::class -> _fragMainComments as T;
            SubscriptionsFeedFragment::class -> _fragMainSubscriptionsFeed as T;
            PlaylistSearchResultsFragment::class -> _fragMainPlaylistSearchResults as T;
            ChannelFragment::class -> _fragMainChannel as T;
            SourcesFragment::class -> _fragMainSources as T;
            PlaylistsFragment::class -> _fragMainPlaylists as T;
            PlaylistFragment::class -> _fragMainPlaylist as T;
            RemotePlaylistFragment::class -> _fragMainRemotePlaylist as T;
            PostDetailFragment::class -> _fragPostDetail as T;
            WatchLaterFragment::class -> _fragWatchlist as T;
            HistoryFragment::class -> _fragHistory as T;
            SourceDetailFragment::class -> _fragSourceDetail as T;
            DownloadsFragment::class -> _fragDownloads as T;
            ImportSubscriptionsFragment::class -> _fragImportSubscriptions as T;
            ImportPlaylistsFragment::class -> _fragImportPlaylists as T;
            BrowserFragment::class -> _fragBrowser as T;
            BuyFragment::class -> _fragBuy as T;
            SubscriptionGroupFragment::class -> _fragSubGroup as T;
            SubscriptionGroupListFragment::class -> _fragSubGroupList as T;
            else -> throw IllegalArgumentException("Fragment type ${T::class.java.name} is not available in MainActivity");
        }
    }


    private fun updateSegmentPaddings() {
        var paddingBottom = 0f;
        if (fragCurrent.hasBottomBar)
            paddingBottom += HEIGHT_MENU_DP;

        _fragContainerOverlay.setPadding(
            0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, paddingBottom - HEIGHT_MENU_DP, resources.displayMetrics)
                .toInt()
        );

        if (_fragVideoDetail.state == VideoDetailFragment.State.MINIMIZED)
            paddingBottom += HEIGHT_VIDEO_MINIMIZED_DP;

        _fragContainerMain.setPadding(
            0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, paddingBottom, resources.displayMetrics)
                .toInt()
        );
    }


    val notifPermission = "android.permission.POST_NOTIFICATIONS";
    val requestPermissionLauncher =  registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted)
            UIDialogs.toast(this, "Notification permission granted");
        else
            UIDialogs.toast(this, "Notification permission denied");
    }
    fun requestNotificationPermissions(reason: String) {
        when {
            ContextCompat.checkSelfPermission(this, notifPermission) == PackageManager.PERMISSION_GRANTED -> {

            }

            ActivityCompat.shouldShowRequestPermissionRationale(this, notifPermission) -> {
                UIDialogs.showDialog(
                    this, R.drawable.ic_notifications, "Notifications Required",
                    reason, null, 0,
                    UIDialogs.Action("Cancel", {}),
                    UIDialogs.Action("Enable", {
                        requestPermissionLauncher.launch(notifPermission);
                    }, UIDialogs.ActionStyle.PRIMARY)
                );
            }

            else -> {
                requestPermissionLauncher.launch(notifPermission);
            }
        }
    }

    private val _toastQueue = ConcurrentLinkedQueue<ToastView.Toast>();
    private var _toastJob: Job? = null;
    fun showAppToast(toast: ToastView.Toast) {
        synchronized(_toastQueue) {
            _toastQueue.add(toast);
            if (_toastJob?.isActive != true)
                _toastJob = lifecycleScope.launch(Dispatchers.Default) {
                    launchAppToastJob();
                };
        }
    }

    private suspend fun launchAppToastJob() {
        Logger.i(TAG, "Starting appToast loop");
        while (!_toastQueue.isEmpty()) {
            val toast = _toastQueue.poll() ?: continue;
            Logger.i(TAG, "Showing next toast (${toast.msg})");

            lifecycleScope.launch(Dispatchers.Main) {
                if (!_toastView.isVisible) {
                    Logger.i(TAG, "First showing toast");
                    _toastView.setToast(toast);
                    _toastView.show(true);
                } else {
                    _toastView.setToastAnimated(toast);
                }
            }
            if (toast.long)
                delay(5000);
            else
                delay(2500);
        }
        Logger.i(TAG, "Ending appToast loop");
        lifecycleScope.launch(Dispatchers.Main) {
            _toastView.hide(true) {
            };
        }
    }


    //TODO: Only calls last handler due to missing request codes on ActivityResultLaunchers.
    private var resultLauncherMap = mutableMapOf<Int, (ActivityResult) -> Unit>();
    private var requestCode: Int? = -1;
    private val resultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val handler = synchronized(resultLauncherMap) {
            resultLauncherMap.remove(requestCode);
        }
        if (handler != null)
            handler(result);
    };

    override fun launchForResult(intent: Intent, code: Int, handler: (ActivityResult) -> Unit) {
        synchronized(resultLauncherMap) {
            resultLauncherMap[code] = handler;
        }
        requestCode = code;
        resultLauncher.launch(intent);
    }

    companion object {
        private val TAG = "MainActivity"

        fun getTabIntent(context: Context, tab: String): Intent {
            val sourcesIntent = Intent(context, MainActivity::class.java);
            sourcesIntent.action = "TAB";
            sourcesIntent.putExtra("TAB", tab);
            sourcesIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return sourcesIntent;
        }

        fun getVideoIntent(context: Context, videoUrl: String): Intent {
            val sourcesIntent = Intent(context, MainActivity::class.java);
            sourcesIntent.action = "VIDEO";
            sourcesIntent.putExtra("VIDEO", videoUrl);
            sourcesIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return sourcesIntent;
        }

        fun getActionIntent(context: Context, action: String): Intent {
            val sourcesIntent = Intent(context, MainActivity::class.java);
            sourcesIntent.action = "ACTION";
            sourcesIntent.putExtra("ACTION", action);
            sourcesIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return sourcesIntent;
        }

        fun getImportOptionsIntent(context: Context): Intent {
            val sourcesIntent = Intent(context, MainActivity::class.java);
            sourcesIntent.action = "IMPORT_OPTIONS";
            sourcesIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return sourcesIntent;
        }
    }
}