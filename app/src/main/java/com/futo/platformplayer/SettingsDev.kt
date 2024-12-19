package com.futo.platformplayer

import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.caoccao.javet.values.primitive.V8ValueInteger
import com.caoccao.javet.values.primitive.V8ValueString
import com.futo.platformplayer.activities.DeveloperActivity
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.activities.SettingsActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginDescriptor
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.background.BackgroundWorker
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.serializers.FlexibleBooleanSerializer
import com.futo.platformplayer.states.StateAnnouncement
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateCache
import com.futo.platformplayer.states.StateDeveloper
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.FragmentedStorageFileJson
import com.futo.platformplayer.views.fields.ButtonField
import com.futo.platformplayer.views.fields.FieldForm
import com.futo.platformplayer.views.fields.FormField
import com.futo.platformplayer.views.fields.FormFieldWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.util.stream.IntStream.range
import kotlin.system.measureTimeMillis

@Serializable()
class SettingsDev : FragmentedStorageFileJson() {

    @FormField(R.string.developer_mode, FieldForm.TOGGLE, -1, 0)
    @Serializable(with = FlexibleBooleanSerializer::class)
    var developerMode: Boolean = false;

    @FormField(R.string.development_server, FieldForm.GROUP,
        R.string.settings_related_to_development_server_be_careful_as_it_may_open_your_phone_to_security_vulnerabilities, 1)
    val devServerSettings: DeveloperServerFields = DeveloperServerFields();
    @Serializable
    class DeveloperServerFields {

        @FormField(R.string.start_server_on_boot, FieldForm.TOGGLE, -1, 0)
        @Serializable(with = FlexibleBooleanSerializer::class)
        var devServerOnBoot: Boolean = false;

        @FormField(R.string.start_server, FieldForm.BUTTON,
            R.string.starts_a_devServer_on_port_11337_may_expose_vulnerabilities, 1)
        fun startServer() {
            StateDeveloper.instance.runServer();
            StateApp.instance.contextOrNull?.let {
                UIDialogs.toast(it, "Dev Started", false);
            };
        }
    }

    @FormField(R.string.experimental, FieldForm.GROUP,
        R.string.settings_related_to_development_server_be_careful_as_it_may_open_your_phone_to_security_vulnerabilities, 2)
    val experimentalSettings: ExperimentalFields = ExperimentalFields();
    @Serializable
    class ExperimentalFields {

        @FormField(R.string.background_subscription_testing, FieldForm.TOGGLE, -1, 0)
        @Serializable(with = FlexibleBooleanSerializer::class)
        var backgroundSubscriptionFetching: Boolean = false;
    }


    @FormField(R.string.cache, FieldForm.GROUP, -1, 3)
    val cache: Cache = Cache();
    @Serializable
    class Cache {

        @FormField(R.string.subscriptions_cache_5000, FieldForm.BUTTON, -1, 1, "subscription_cache_button")
        fun subscriptionsCache5000() {
            Logger.i("SettingsDev", "Started caching 5000 sub items");
            UIDialogs.toast(
                SettingsActivity.getActivity()!!,
                "Started caching 5000 sub items"
            );
            val button = DeveloperActivity.getActivity()?.getField("subscription_cache_button");
            if(button is ButtonField)
                button.setButtonEnabled(false);
            StateApp.instance.scope.launch(Dispatchers.IO) {
                try {
                    val subsCache =
                        StateSubscriptions.instance.getSubscriptionsFeedWithExceptions(cacheScope = this).first;

                    var total = 0;
                    var page = 0;
                    var lastToast = System.currentTimeMillis();
                    while(subsCache.hasMorePages() && total < 5000) {
                        subsCache.nextPage();
                        total += subsCache.getResults().size;
                        page++;

                        if(page % 10 == 0)
                            withContext(Dispatchers.Main) {
                                val diff = System.currentTimeMillis() - lastToast;
                                lastToast = System.currentTimeMillis();
                                UIDialogs.toast(
                                    SettingsActivity.getActivity()!!,
                                    "Page: ${page}, Total: ${total}, Speed: ${diff}ms"
                                );
                            }
                        Thread.sleep(250);
                    }

                    withContext(Dispatchers.Main) {
                        UIDialogs.toast(
                            SettingsActivity.getActivity()!!,
                            "FINISHED Page: ${page}, Total: ${total}"
                        );
                    }
                }
                catch(ex: Throwable) {
                    Logger.e("SettingsDev", ex.message, ex);
                    Logger.i("SettingsDev", "Failed: ${ex.message}");
                }
                finally {
                    withContext(Dispatchers.Main) {
                        if(button is ButtonField)
                            button.setButtonEnabled(true);
                    }
                }
            }
        }

        @FormField(R.string.history_cache_100, FieldForm.BUTTON, -1, 1, "history_cache_button")
        fun historyCache100() {
            Logger.i("SettingsDev", "Started caching 100 history items (from home)");
            UIDialogs.toast(
                SettingsActivity.getActivity()!!,
                "Started caching 100 history items (from home)"
            );
            val button = DeveloperActivity.getActivity()?.getField("history_cache_button");
            if(button is ButtonField)
                button.setButtonEnabled(false);
            StateApp.instance.scope.launch(Dispatchers.IO) {
                try {
                    val subsCache = StatePlatform.instance.getHome();

                    var num = 0;
                    for(item in subsCache.getResults().filterIsInstance<IPlatformVideo>()) {
                        StateHistory.instance.getHistoryByVideo(item, true, OffsetDateTime.now().minusHours(num.toLong() * 4))
                        num++;
                    }

                    var total = 0;
                    var page = 0;
                    var lastToast = System.currentTimeMillis();
                    while(subsCache.hasMorePages() && total < 5000) {
                        subsCache.nextPage();
                        total += subsCache.getResults().size;
                        page++;

                        for(item in subsCache.getResults().filterIsInstance<IPlatformVideo>()) {
                            StateHistory.instance.getHistoryByVideo(item, true, OffsetDateTime.now().minusHours(num.toLong() * 4))
                            num++;
                        }

                        if(page % 4 == 0)
                            withContext(Dispatchers.Main) {
                                val diff = System.currentTimeMillis() - lastToast;
                                lastToast = System.currentTimeMillis();
                                UIDialogs.toast(
                                    SettingsActivity.getActivity()!!,
                                    "Page: ${page}, Total: ${total}, Speed: ${diff}ms"
                                );
                            }
                        Thread.sleep(500);
                    }

                    withContext(Dispatchers.Main) {
                        UIDialogs.toast(
                            SettingsActivity.getActivity()!!,
                            "FINISHED Page: ${page}, Total: ${total}"
                        );
                    }
                }
                catch(ex: Throwable) {
                    Logger.e("SettingsDev", ex.message, ex);
                    Logger.i("SettingsDev", "Failed: ${ex.message}");
                }
                finally {
                    withContext(Dispatchers.Main) {
                        if(button is ButtonField)
                            button.setButtonEnabled(true);
                    }
                }
            }
        }
    }

    @FormField(R.string.crash_me, FieldForm.BUTTON,
        R.string.crashes_the_application_on_purpose, 3)
    fun crashMe() {
        throw java.lang.IllegalStateException("This is an uncaught exception triggered on purpose!");
    }

    @FormField(R.string.delete_announcements, FieldForm.BUTTON,
        R.string.delete_all_announcements, 3)
    fun deleteAnnouncements() {
        StateAnnouncement.instance.deleteAllAnnouncements();
    }

    @FormField(R.string.clear_cookies, FieldForm.BUTTON,
        R.string.clear_all_cookies_from_the_cookieManager, 3)
    fun clearCookies() {
        val cookieManager: CookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null);
    }
    @FormField(R.string.test_background_worker, FieldForm.BUTTON,
        R.string.test_background_worker_description, 4)
    fun triggerBackgroundUpdate() {
        val act = SettingsActivity.getActivity()!!;
        try {
            UIDialogs.toast(SettingsActivity.getActivity()!!, "Starting test background worker");

            val wm = WorkManager.getInstance(act);
            val req = OneTimeWorkRequestBuilder<BackgroundWorker>()
                .setInputData(Data.Builder().putBoolean("bypassMainCheck", true).build())
                .build();
            wm.enqueue(req);
        } catch (e: Throwable) {
            UIDialogs.showGeneralErrorDialog(act, "Failed to trigger background update", e)
        }
    }
    @FormField(R.string.clear_channel_cache, FieldForm.BUTTON,
        R.string.test_background_worker_description, 4)
    fun clearChannelContentCache() {
        UIDialogs.toast(SettingsActivity.getActivity()!!, "Clearing cache");
        StateCache.instance.clearToday();
        UIDialogs.toast(SettingsActivity.getActivity()!!, "Cleared");
    }


    @Contextual
    @Transient
    @FormField(R.string.v8_benchmarks, FieldForm.GROUP,
        R.string.various_benchmarks_using_the_integrated_v8_engine, 4)
    val v8Benchmarks: V8Benchmarks = V8Benchmarks();
    class V8Benchmarks {
        @FormField(
            R.string.test_v8_creation_speed, FieldForm.BUTTON,
            R.string.tests_v8_creation_times_and_running, 1
        )
        fun testV8Creation() {
            var plugin: V8Plugin? = null;
            StateApp.instance.scopeOrNull!!.launch(Dispatchers.IO) {
                try {
                    val count = 1000;
                    val timeStart = System.currentTimeMillis();
                    for (i in range(0, count)) {
                        val v8 = V8Plugin(
                            StateApp.instance.context,
                            SourcePluginConfig("Test", "", "", "", "", ""),
                            "var i = 0; function test() { i = i + 1; return i; }"
                        );

                        v8.start();
                        if (v8.executeTyped<V8ValueInteger>("test()").value != 1)
                            throw java.lang.IllegalStateException("Test didn't properly respond");
                        v8.stop();
                    }
                    val timeEnd = System.currentTimeMillis();
                    val resp = "Restarted V8 ${count} times in ${(timeEnd - timeStart)}ms, ${(timeEnd - timeStart) / count}ms per instance\n(initializing, calling function with value, destroying)"
                    Logger.i("SettingsDev", resp);

                    withContext(Dispatchers.Main) {
                        StateApp.instance.contextOrNull?.let {
                            UIDialogs.toast(it, resp);
                        };
                    }
                } catch (ex: Exception) {
                    withContext(Dispatchers.Main) {
                        StateApp.withContext {
                            UIDialogs.toast(it, "Failed: " + ex.message);
                        };
                    }
                } finally {
                    plugin?.stop();
                }
            }
        }

        @FormField(
            R.string.test_v8_communication_speed, FieldForm.BUTTON,
            R.string.tests_v8_communication_speeds, 4
        )
        fun testV8RunSpeeds() {
            var plugin: V8Plugin? = null;
            StateApp.instance.scope.launch(Dispatchers.IO) {
                try {
                    val count = 10000;
                    var str = "012346789012346789012346789012346789012346789";
                    val v8 = V8Plugin(
                        StateApp.instance.context,
                        SourcePluginConfig("Test"),
                        "function test(str) { return str; }"
                    );
                    v8.start();
                    val timeStart = System.currentTimeMillis();
                    for (i in range(0, count)) {
                        if (v8.executeTyped<V8ValueString>("test(\"" + str + "\")").value != str)
                            throw java.lang.IllegalStateException("Test didn't properly respond");
                    }
                    val timeEnd = System.currentTimeMillis();
                    v8.stop();

                    val resp = "Ran V8 ${count} times in ${(timeEnd - timeStart)}ms, ${(timeEnd - timeStart) / count}ms per instance\n(passing a string[50] back and forth)";
                    Logger.i("SettingsDev", resp);
                    withContext(Dispatchers.Main) {
                        StateApp.withContext {
                            UIDialogs.toast(it, resp);
                        };
                    }
                } catch (ex: Exception) {
                    withContext(Dispatchers.Main) {
                        StateApp.withContext {
                            UIDialogs.toast(it, "Failed: " + ex.message);
                        };
                    }
                } finally {
                    plugin?.stop();
                }
            }
        }
    }

    @Contextual
    @Transient
    @FormField(R.string.v8_script_testing, FieldForm.GROUP, R.string.various_tests_against_a_custom_source, 4)
    val v8ScriptTests: V8ScriptTests = V8ScriptTests();
    class V8ScriptTests {
        @Contextual
        private var _currentPlugin : JSClient? = null;
        @FormField(R.string.inject, FieldForm.BUTTON, R.string.injects_a_test_source_config_local_into_v8, 1)
        fun testV8Init() {
            StateApp.instance.scope.launch(Dispatchers.IO) {
                try {
                    _currentPlugin =
                        getTestPlugin("http://192.168.1.132/Public/FUTO/TestConfig.json");

                    withContext(Dispatchers.Main) {
                        UIDialogs.toast(StateApp.instance.context, "TestPlugin injected");
                    }
                }
                catch(ex: Exception) {
                    toast(ex.message ?: "");
                }
            }
        }
        @FormField(R.string.getHome, FieldForm.BUTTON, R.string.attempts_to_fetch_2_pages_from_getHome, 2)
        fun testV8Home() {
            runTestPlugin(_currentPlugin) {
                var home: IPager<IPlatformContent>?;
                val resultPage1: String;
                val resultPage2: String;
                val page1Time = measureTimeMillis {
                    home = it.getHome();
                    val results = home!!.getResults();
                    resultPage1 = "Page1 Results=[${results.size}] HasMore=${home!!.hasMorePages()}\nResult[0]=${results.firstOrNull()?.name}";
                }
                toast(resultPage1);
                val page2Time = measureTimeMillis {
                    home!!.nextPage();
                    val results = home!!.getResults();
                    resultPage2 = "Page2 Results=[${results.size}] HasMore=${home!!.hasMorePages()}\nResult[0]=${results.firstOrNull()?.name}";
                }
                toast(resultPage2);
                toast("Page1: ${page1Time}ms, Page2: ${page2Time}ms");
            }
        }

        private fun toast(str: String, isLong: Boolean = false) {
            StateApp.instance.scope.launch(Dispatchers.Main) {
                try {
                    UIDialogs.toast(StateApp.instance.context, str, isLong);
                } catch (e: Throwable) {
                    Logger.e("SettingsDev", "Failed to show toast", e)
                }
            }
        }
        private fun runTestPlugin(plugin: JSClient?, handler: (JSClient) -> Unit) {
            StateApp.instance.scope.launch(Dispatchers.IO) {
                try {
                    if (plugin == null)
                        throw IllegalStateException("Test plugin not loaded, inject first");
                    else
                        handler(plugin);
                } catch (ex: Exception) {
                    Logger.e("ScriptTesting", ex.message ?: "", ex);
                    toast("Failed: " + ex.message, true);
                }
            }
        }
        private fun getTestPlugin(configUrl: String) : JSClient {
            val configResp =
                ManagedHttpClient().get(configUrl);
            if (!configResp.isOk || configResp.body == null)
                throw IllegalStateException("Failed to load config");
            val config = Json.decodeFromString<SourcePluginConfig>(configResp.body.string());

            val scriptResp = ManagedHttpClient().get(config.absoluteScriptUrl);
            if (!scriptResp.isOk || scriptResp.body == null)
                throw IllegalStateException("Failed to load script");
            val script = scriptResp.body.string();

            val client = JSClient(StateApp.instance.context, SourcePluginDescriptor(config), null, script);
            client.initialize();

            return client;
        }
    }


    @Contextual
    @Transient
    @FormField(R.string.other, FieldForm.GROUP, R.string.others_ellipsis, 5)
    val otherTests: OtherTests = OtherTests();
    class OtherTests {
        @FormField(R.string.unsubscribe_all, FieldForm.BUTTON, R.string.removes_all_subscriptions, -1)
        fun unsubscribeAll() {
            val toUnsub = StateSubscriptions.instance.getSubscriptions();
            UIDialogs.toast("Started unsubbing.. (${toUnsub.size})")
            toUnsub.forEach {
                StateSubscriptions.instance.removeSubscription(it.channel.url);
            };
            UIDialogs.toast("Finished unsubbing.. (${toUnsub.size})")
        }
        @FormField(R.string.clear_downloads, FieldForm.BUTTON, R.string.deletes_all_ongoing_downloads, 1)
        fun clearDownloads() {
            StateDownloads.instance.getDownloading().forEach {
                StateDownloads.instance.removeDownload(it);
            };
        }
        @FormField(R.string.clear_all_downloaded, FieldForm.BUTTON, R.string.deletes_all_downloaded_videos_and_related_files, 2)
        fun clearDownloaded() {
            StateDownloads.instance.getDownloadedVideos().forEach {
                StateDownloads.instance.deleteCachedVideo(it.id);
            };
        }
        @FormField(R.string.delete_unresolved, FieldForm.BUTTON, R.string.deletes_all_unresolved_source_files, 3)
        fun cleanupDownloads() {
            StateDownloads.instance.cleanupDownloads();
        }

        @FormField(R.string.fill_storage_till_error, FieldForm.BUTTON, R.string.writes_to_disk_till_no_space_is_left, 4)
        fun fillStorage(context: Context, scope: CoroutineScope?) {
            val gigabuffer = ByteArray(1024 * 1024 * 128);
            var count: Long = 0;

            UIDialogs.toast("Starting filling up space..");

            scope?.launch(Dispatchers.IO) {
                try {
                    do {
                        Logger.i("Developer", "Total: ${count}, Storage: ${(count * gigabuffer.size).toHumanBytesSize()}")
                        val tempFile = StateApp.instance.getTempFile();
                        tempFile.writeBytes(gigabuffer);
                        count++;

                        if(count % 50 == 0L) {
                            StateApp.instance.scopeOrNull?.launch (Dispatchers.Main) {
                                UIDialogs.toast(context, "Filled up ${(count * gigabuffer.size).toHumanBytesSize()}");
                            }
                        }
                    } while (true);
                } catch (ex: Throwable) {
                    withContext(Dispatchers.Main) {
                        UIDialogs.toast("Total: ${count},  Storage: ${(count * gigabuffer.size).toHumanBytesSize()}\nError: ${ex.message}");
                        UIDialogs.showGeneralErrorDialog(context, ex.message ?: "", ex);
                    }
                }
            }
        }


        @FormField(R.string.test_playback, FieldForm.BUTTON,
            R.string.test_playback, 1)
        fun testPlayback(context: Context) {
            context.startActivity(MainActivity.getActionIntent(context, "TEST_PLAYBACK"));
        }
    }



    @FormField(R.string.networking, FieldForm.GROUP, -1, 18)
    var networking = Networking();
    @Serializable
    class Networking {
        @FormField(R.string.allow_all_certificates, FieldForm.TOGGLE, -1, 0)
        @FormFieldWarning(R.string.allow_all_certificates_warning)
        var allowAllCertificates: Boolean = false;
    }


    @Contextual
    @Transient
    @FormField(R.string.info, FieldForm.GROUP, -1, 19)
    var info = Info();
    @Serializable
    class Info {
        @FormField(R.string.dev_info_channel_cache_size, FieldForm.READONLYTEXT, -1, 1, "channelCacheSize")
        var channelCacheStartupCount = StateCache.instance.channelCacheStartupCount;
    }



    //region BOILERPLATE
    override fun encode(): String {
        return Json.encodeToString(this);
    }

    companion object {
        val instance: SettingsDev get() {
            return FragmentedStorage.get<SettingsDev>();
        }
    }
    //endregion
}