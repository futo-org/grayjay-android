package com.futo.platformplayer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.activities.*
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.fragment.mainactivity.bottombar.MenuBottomBarFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.serializers.FlexibleBooleanSerializer
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import com.futo.platformplayer.states.*
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.FragmentedStorageFileJson
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.fields.DropdownFieldOptionsId
import com.futo.platformplayer.views.fields.FormField
import com.futo.platformplayer.views.fields.FieldForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.time.OffsetDateTime

@Serializable
data class MenuBottomBarSetting(val id: Int, var enabled: Boolean);

@Serializable()
class Settings : FragmentedStorageFileJson() {
    var didFirstStart: Boolean = false;

    @Serializable
    val tabs: MutableList<MenuBottomBarSetting> = MenuBottomBarFragment.buttonDefinitions.map { MenuBottomBarSetting(it.id, true) }.toMutableList()

    @Transient
    val onTabsChanged = Event0();

    @FormField(
        "Manage Polycentric identity", FieldForm.BUTTON,
        "Manage your Polycentric identity", -2
    )
    fun managePolycentricIdentity() {
        SettingsActivity.getActivity()?.let {
            if (StatePolycentric.instance.processHandle != null) {
                it.startActivity(Intent(it, PolycentricProfileActivity::class.java));
            } else {
                it.startActivity(Intent(it, PolycentricHomeActivity::class.java));
            }
        }
    }

    @FormField(
        "Submit feedback", FieldForm.BUTTON,
        "Give feedback on the application", -1
    )
    fun submitFeedback() {
        try {
            val i = Intent(Intent.ACTION_VIEW);
            val subject = "Feedback Grayjay";
            val body = "Hey,\n\nI have some feedback on the Grayjay app.\nVersion information (version_name = ${BuildConfig.VERSION_NAME}, version_code = ${BuildConfig.VERSION_CODE}, flavor = ${BuildConfig.FLAVOR}, build_type = ${BuildConfig.BUILD_TYPE}})\n\n";
            val data = Uri.parse("mailto:grayjay@futo.org?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body));
            i.data = data;

            StateApp.withContext { it.startActivity(i); };
        } catch (e: Throwable) {
            //Ignored
        }
    }

    @FormField(
        "Manage Tabs", FieldForm.BUTTON,
        "Change tabs visible on the home screen", -1
    )
    fun manageTabs() {
        try {
            SettingsActivity.getActivity()?.let {
                it.startActivity(Intent(it, ManageTabsActivity::class.java));
            }
        } catch (e: Throwable) {
            //Ignored
        }
    }

    @FormField("Home", "group", "Configure how your Home tab works and feels", 1)
    var home = HomeSettings();
    @Serializable
    class HomeSettings {
        @FormField("Feed Style", FieldForm.DROPDOWN, "", 5)
        @DropdownFieldOptionsId(R.array.feed_style)
        var homeFeedStyle: Int = 1;

        fun getHomeFeedStyle(): FeedStyle {
            if(homeFeedStyle == 0)
                return FeedStyle.PREVIEW;
            else
                return FeedStyle.THUMBNAIL;
        }
    }

    @FormField("Search", "group", "", 2)
    var search = SearchSettings();
    @Serializable
    class SearchSettings {
        @FormField("Search History", FieldForm.TOGGLE, "", 4)
        @Serializable(with = FlexibleBooleanSerializer::class)
        var searchHistory: Boolean = true;


        @FormField("Feed Style", FieldForm.DROPDOWN, "", 5)
        @DropdownFieldOptionsId(R.array.feed_style)
        var searchFeedStyle: Int = 1;


        fun getSearchFeedStyle(): FeedStyle {
            if(searchFeedStyle == 0)
                return FeedStyle.PREVIEW;
            else
                return FeedStyle.THUMBNAIL;
        }
    }

    @FormField("Subscriptions", "group", "Configure how your Subscriptions works and feels", 3)
    var subscriptions = SubscriptionsSettings();
    @Serializable
    class SubscriptionsSettings {
        @FormField("Feed Style", FieldForm.DROPDOWN, "", 5)
        @DropdownFieldOptionsId(R.array.feed_style)
        var subscriptionsFeedStyle: Int = 1;

        fun getSubscriptionsFeedStyle(): FeedStyle {
            if(subscriptionsFeedStyle == 0)
                return FeedStyle.PREVIEW;
            else
                return FeedStyle.THUMBNAIL;
        }

        @FormField("Background Update", FieldForm.DROPDOWN, "Experimental background update for subscriptions cache (requires restart)", 6)
        @DropdownFieldOptionsId(R.array.background_interval)
        var subscriptionsBackgroundUpdateInterval: Int = 0;

        fun getSubscriptionsBackgroundIntervalMinutes(): Int = when(subscriptionsBackgroundUpdateInterval) {
            0 -> 0;
            1 -> 15;
            2 -> 60;
            3 -> 60 * 3;
            4 -> 60 * 6;
            5 -> 60 * 12;
            6 -> 60 * 24;
            else -> 0
        };


        @FormField("Subscription Concurrency", FieldForm.DROPDOWN, "Specify how many threads are used to fetch channels (requires restart)", 7)
        @DropdownFieldOptionsId(R.array.thread_count)
        var subscriptionConcurrency: Int = 3;

        fun getSubscriptionsConcurrency() : Int {
            return threadIndexToCount(subscriptionConcurrency);
        }
    }

    @FormField("Player", "group", "Change behavior of the player", 4)
    var playback = PlaybackSettings();
    @Serializable
    class PlaybackSettings {
        @FormField("Primary Language", FieldForm.DROPDOWN, "", 0)
        @DropdownFieldOptionsId(R.array.languages)
        var primaryLanguage: Int = 0;

        fun getPrimaryLanguage(context: Context) = context.resources.getStringArray(R.array.languages)[primaryLanguage];

        @FormField("Default Playback Speed", FieldForm.DROPDOWN, "", 1)
        @DropdownFieldOptionsId(R.array.playback_speeds)
        var defaultPlaybackSpeed: Int = 3;
        fun getDefaultPlaybackSpeed(): Float = when(defaultPlaybackSpeed) {
            0 -> 0.25f;
            1 -> 0.5f;
            2 -> 0.75f;
            3 -> 1.0f;
            4 -> 1.25f;
            5 -> 1.5f;
            6 -> 1.75f;
            7 -> 2.0f;
            8 -> 2.25f;
            else -> 1.0f;
        };

        @FormField("Preferred Quality", FieldForm.DROPDOWN, "", 2)
        @DropdownFieldOptionsId(R.array.preferred_quality_array)
        var preferredQuality: Int = 0;

        @FormField("Preferred Metered Quality", FieldForm.DROPDOWN, "", 2)
        @DropdownFieldOptionsId(R.array.preferred_quality_array)
        var preferredMeteredQuality: Int = 0;
        fun getPreferredQualityPixelCount(): Int = preferedQualityToPixels(preferredQuality);
        fun getPreferredMeteredQualityPixelCount(): Int = preferedQualityToPixels(preferredMeteredQuality);
        fun getCurrentPreferredQualityPixelCount(): Int = if(!StateApp.instance.isCurrentMetered()) getPreferredQualityPixelCount() else getPreferredMeteredQualityPixelCount();

        @FormField("Preferred Preview Quality", FieldForm.DROPDOWN, "", 3)
        @DropdownFieldOptionsId(R.array.preferred_quality_array)
        var preferredPreviewQuality: Int = 5;
        fun getPreferredPreviewQualityPixelCount(): Int = preferedQualityToPixels(preferredPreviewQuality);

        @FormField("Auto-Rotate", FieldForm.DROPDOWN, "", 4)
        @DropdownFieldOptionsId(R.array.system_enabled_disabled_array)
        var autoRotate: Int = 2;

        fun isAutoRotate() = autoRotate == 1 || (autoRotate == 2 && StateApp.instance.getCurrentSystemAutoRotate());

        @FormField("Auto-Rotate Dead Zone", FieldForm.DROPDOWN, "Auto-rotate deadzone in degrees", 5)
        @DropdownFieldOptionsId(R.array.auto_rotate_dead_zone)
        var autoRotateDeadZone: Int = 0;

        fun getAutoRotateDeadZoneDegrees(): Int {
            return autoRotateDeadZone * 5;
        }

        @FormField("Background Behavior", FieldForm.DROPDOWN, "", 6)
        @DropdownFieldOptionsId(R.array.player_background_behavior)
        var backgroundPlay: Int = 2;

        fun isBackgroundContinue() = backgroundPlay == 1;
        fun isBackgroundPictureInPicture() = backgroundPlay == 2;

        @FormField("Resume After Preview", FieldForm.DROPDOWN, "When watching a video in preview mode, resume at the position when opening the video", 7)
        @DropdownFieldOptionsId(R.array.resume_after_preview)
        var resumeAfterPreview: Int = 1;


        @FormField("Live Chat Webview", FieldForm.TOGGLE, "Use the live chat web window when available over native implementation.", 8)
        var useLiveChatWindow: Boolean = true;

        fun shouldResumePreview(previewedPosition: Long): Boolean{
            if(resumeAfterPreview == 2)
                return true;
            if(resumeAfterPreview == 1 && previewedPosition > 10)
                return true;
            return false;
        }
    }

    @FormField("Downloads", "group", "Configure downloading of videos", 5)
    var downloads = Downloads();
    @Serializable
    class Downloads {

        @FormField("Download when", FieldForm.DROPDOWN, "Configure when videos should be downloaded", 0)
        @DropdownFieldOptionsId(R.array.when_download)
        var whenDownload: Int = 0;

        fun shouldDownload(): Boolean {
            return when (whenDownload) {
                0 -> !StateApp.instance.isCurrentMetered();
                1 -> StateApp.instance.isNetworkState(StateApp.NetworkState.WIFI, StateApp.NetworkState.ETHERNET);
                2 -> true;
                else -> false;
            }
        }

        @FormField("Default Video Quality", FieldForm.DROPDOWN, "", 2)
        @DropdownFieldOptionsId(R.array.preferred_video_download)
        var preferredVideoQuality: Int = 4;
        fun getDefaultVideoQualityPixels(): Int = preferedQualityToPixels(preferredVideoQuality);

        @FormField("Default Audio Quality", FieldForm.DROPDOWN, "", 3)
        @DropdownFieldOptionsId(R.array.preferred_audio_download)
        var preferredAudioQuality: Int = 1;
        fun isHighBitrateDefault(): Boolean = preferredAudioQuality > 0;

        @FormField("ByteRange Download", FieldForm.TOGGLE, "Attempt to utilize byte ranges, this can be combined with concurrency to bypass throttling", 4)
        @Serializable(with = FlexibleBooleanSerializer::class)
        var byteRangeDownload: Boolean = true;

        @FormField("ByteRange Concurrency", FieldForm.DROPDOWN, "Number of concurrent threads to multiply download speeds from throttled sources", 5)
        @DropdownFieldOptionsId(R.array.thread_count)
        var byteRangeConcurrency: Int = 3;
        fun getByteRangeThreadCount(): Int {
            return threadIndexToCount(byteRangeConcurrency);
        }
    }

    @FormField("Browsing", "group", "Configure browsing behavior", 6)
    var browsing = Browsing();
    @Serializable
    class Browsing {
        @FormField("Enable Video Cache", FieldForm.TOGGLE, "A cache to quickly load previously fetched videos", 0)
        @Serializable(with = FlexibleBooleanSerializer::class)
        var videoCache: Boolean = true;
    }

    @FormField("Casting", "group", "Configure casting", 7)
    var casting = Casting();
    @Serializable
    class Casting {
        @FormField("Enabled", FieldForm.TOGGLE, "Enable casting", 0)
        @Serializable(with = FlexibleBooleanSerializer::class)
        var enabled: Boolean = true;


        /*TODO: Should we have a different casting quality?
        @FormField("Preferred Casting Quality", FieldForm.DROPDOWN, "", 3)
        @DropdownFieldOptionsId(R.array.preferred_quality_array)
        var preferredQuality: Int = 4;
        fun getPreferredQualityPixelCount(): Int {
            when (preferredQuality) {
                0 -> return 1280 * 720;
                1 -> return 3840 * 2160;
                2 -> return 1920 * 1080;
                3 -> return 1280 * 720;
                4 -> return 640 * 480;
                else -> return 0;
            }
        }*/
    }


    @FormField("Logging", FieldForm.GROUP, "", 8)
    var logging = Logging();
    @Serializable
    class Logging {
        @FormField("Log Level", FieldForm.DROPDOWN, "", 0)
        @DropdownFieldOptionsId(R.array.log_levels)
        var logLevel: Int = 0;

        @FormField(
            "Submit logs", FieldForm.BUTTON,
            "Submit logs to help us narrow down issues", 1
        )
        fun submitLogs() {
            StateApp.instance.scopeGetter().launch(Dispatchers.IO) {
                try {
                    if (!Logger.submitLogs()) {
                        withContext(Dispatchers.Main) {
                            SettingsActivity.getActivity()?.let { UIDialogs.toast(it, "Please enable logging to submit logs") }
                        }
                    }
                } catch (e: Throwable) {
                    Logger.e("Settings", "Failed to submit logs.", e);
                }
            }
        }
    }



    @FormField("Announcement", FieldForm.GROUP, "", 10)
    var announcementSettings = AnnouncementSettings();
    @Serializable
    class AnnouncementSettings {
        @FormField(
            "Reset announcements", FieldForm.BUTTON,
            "Reset hidden announcements", 1
        )
        fun resetAnnouncements() {
            StateAnnouncement.instance.resetAnnouncements();
            UIDialogs.toast("Announcements reset.");
        }
    }

    @FormField("Plugins", FieldForm.GROUP, "", 11)
    @Transient
    var plugins = Plugins();
    @Serializable
    class Plugins {

        @FormField("Clear Cookies on Logout", FieldForm.TOGGLE, "Clears cookies when you log out, allowing you to change account.", 0)
        var clearCookiesOnLogout: Boolean = true;

        @FormField(
            "Clear Cookies", FieldForm.BUTTON,
            "Clears in-app browser cookies, especially useful for fully logging out of plugins.", 1
        )
        fun clearCookies() {
            val cookieManager: CookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(null);
        }
        @FormField(
            "Reinstall Embedded Plugins", FieldForm.BUTTON,
            "Also removes any data related plugin like login or settings (may not clear browser cache)", 1
        )
        fun reinstallEmbedded() {
            StateApp.instance.scopeOrNull!!.launch(Dispatchers.IO) {
                try {
                    StatePlugins.instance.reinstallEmbeddedPlugins(StateApp.instance.context);

                    withContext(Dispatchers.Main) {
                        StateApp.instance.contextOrNull?.let {
                            UIDialogs.toast(it, "Embedded plugins reinstalled, a reboot is recommended");
                        };
                    }
                } catch (ex: Exception) {
                    withContext(Dispatchers.Main) {
                        StateApp.withContext {
                            UIDialogs.toast(it, "Failed: " + ex.message);
                        };
                    }
                }
            }
        }
    }


    @FormField("Auto Update", "group", "Configure the auto updater", 12)
    var autoUpdate = AutoUpdate();
    @Serializable
    class AutoUpdate {
        @FormField("Check", FieldForm.DROPDOWN, "", 0)
        @DropdownFieldOptionsId(R.array.auto_update_when_array)
        var check: Int = 0;

        @FormField("Background download", FieldForm.DROPDOWN, "Configure if background download should be used", 1)
        @DropdownFieldOptionsId(R.array.background_download)
        var backgroundDownload: Int = 0;

        @FormField("Download when", FieldForm.DROPDOWN, "Configure when updates should be downloaded", 2)
        @DropdownFieldOptionsId(R.array.when_download)
        var whenDownload: Int = 0;

        fun shouldDownload(): Boolean {
            return when (whenDownload) {
                0 -> !StateApp.instance.isCurrentMetered();
                1 -> StateApp.instance.isNetworkState(StateApp.NetworkState.WIFI, StateApp.NetworkState.ETHERNET);
                2 -> true;
                else -> false;
            }
        }

        fun isAutoUpdateEnabled(): Boolean {
            return check == 0 && !BuildConfig.IS_PLAYSTORE_BUILD;
        }

        @FormField(
            "Manual check", FieldForm.BUTTON,
            "Manually check for updates", 3
        )
        fun manualCheck() {
            if (!BuildConfig.IS_PLAYSTORE_BUILD) {
                SettingsActivity.getActivity()?.let {
                    StateUpdate.instance.checkForUpdates(it, true);
                }
            } else {
                SettingsActivity.getActivity()?.let {
                    try {
                        it.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${it.packageName}")))
                    } catch (e: ActivityNotFoundException) {
                        UIDialogs.toast(it, "Failed to show store.");
                    }
                }
            }
        }

        @FormField(
            "View changelog", FieldForm.BUTTON,
            "Review the current and past changelogs", 4
        )
        fun viewChangelog() {
            UIDialogs.toast("Retrieving changelog");
            SettingsActivity.getActivity()?.let {
                StateApp.instance.scopeGetter().launch(Dispatchers.IO) {
                    try {
                        val version = StateUpdate.instance.downloadVersionCode(ManagedHttpClient()) ?: return@launch;
                        Logger.i(TAG, "Version retrieved $version");

                        withContext(Dispatchers.Main) {
                            UIDialogs.showChangelogDialog(it, version);
                        }
                    } catch (e: Throwable) {
                        Logger.e("Settings", "Failed to submit logs.", e);
                    }
                }
            };
        }

        @FormField(
            "Remove Cached Version", FieldForm.BUTTON,
            "Remove the last downloaded version", 5
        )
        fun removeCachedVersion() {
            StateApp.withContext {
                val outputDirectory = File(it.filesDir, "autoupdate");
                if (!outputDirectory.exists()) {
                    UIDialogs.toast("Directory does not exist");
                    return@withContext;
                }

                File(outputDirectory, "last_version.apk").delete();
                File(outputDirectory, "last_version.txt").delete();
                UIDialogs.toast("Removed downloaded version");
            }
        }
    }

    @FormField("Backup", FieldForm.GROUP, "", 13)
    var backup = Backup();
    @Serializable
    class Backup {
        @Serializable(with = OffsetDateTimeSerializer::class)
        var lastAutoBackupTime: OffsetDateTime = OffsetDateTime.MIN;
        var didAskAutoBackup: Boolean = false;
        var autoBackupPassword: String? = null;
        fun shouldAutomaticBackup() = autoBackupPassword != null;

        @FormField("Automatic Backup", FieldForm.READONLYTEXT, "", 0)
        val automaticBackupText get() = if(!shouldAutomaticBackup()) "None" else "Every Day";

        @FormField("Set Automatic Backup", FieldForm.BUTTON, "Configure daily backup in case of catastrophic failure. (Written to the external Grayjay directory)", 1)
        fun configureAutomaticBackup() {
            UIDialogs.showAutomaticBackupDialog(SettingsActivity.getActivity()!!);
        }
        @FormField("Restore Automatic Backup", FieldForm.BUTTON, "Restore a previous automatic backup", 2)
        fun restoreAutomaticBackup() {
            val activity = SettingsActivity.getActivity()!!

            if(!StateBackup.hasAutomaticBackup())
                UIDialogs.toast(activity, "You don't have any automatic backups", false);
            else
                UIDialogs.showAutomaticRestoreDialog(activity, activity.lifecycleScope);
        }


        @FormField("Export Data", FieldForm.BUTTON, "Creates a zip file with your data which can be imported by opening it with Grayjay", 3)
        fun export() {
            StateBackup.startExternalBackup();
        }
    }

    @FormField("Payment", FieldForm.GROUP, "", 14)
    var payment = Payment();
    @Serializable
    class Payment {
        @FormField("Payment Status", FieldForm.READONLYTEXT, "", 1)
        val paymentStatus: String get() = if (StatePayment.instance.hasPaid) "Paid" else "Not Paid";

        @FormField("Clear Payment", FieldForm.BUTTON, "Deletes license keys from app", 2)
        fun clearPayment() {
            StatePayment.instance.clearLicenses();
            SettingsActivity.getActivity()?.let {
                UIDialogs.toast(it, "Licenses cleared, might require app restart");
            }
        }
    }

    @FormField("Info", FieldForm.GROUP, "", 15)
    var info = Info();
    @Serializable
    class Info {
        @FormField("Version Code", FieldForm.READONLYTEXT, "", 1, "code")
        var versionCode = BuildConfig.VERSION_CODE;
        @FormField("Version Name", FieldForm.READONLYTEXT, "", 2)
        var versionName = BuildConfig.VERSION_NAME;
        @FormField("Version Type", FieldForm.READONLYTEXT, "", 3)
        var versionType = BuildConfig.BUILD_TYPE;
    }

    //region BOILERPLATE
    override fun encode(): String {
        return Json.encodeToString(this);
    }

    companion object {
        private const val TAG = "Settings";

        private var _isFirst = true;

        val instance: Settings get() {
            if(_isFirst) {
                Logger.i(TAG, "Initial Settings fetch");
                _isFirst = false;
            }
            return FragmentedStorage.get<Settings>();
        }

        fun replace(text: String) {
            FragmentedStorage.replace<Settings>(text, true);
        }


        private fun preferedQualityToPixels(q: Int): Int {
            when (q) {
                0 -> return 1280 * 720;
                1 -> return 3840 * 2160;
                2 -> return 2560 * 1440;
                3 -> return 1920 * 1080;
                4 -> return 1280 * 720;
                5 -> return 854 * 480;
                6 -> return 640 * 360;
                7 -> return 426 * 240;
                8 -> return 256 * 144;
                else -> return 0;
            }
        }


        private fun threadIndexToCount(index: Int): Int {
            return when(index) {
                0 -> 1;
                1 -> 2;
                2 -> 4;
                3 -> 6;
                4 -> 8;
                5 -> 10;
                6 -> 15;
                else -> 1
            }
        }
    }
    //endregion
}