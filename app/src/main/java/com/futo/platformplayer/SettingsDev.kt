package com.futo.platformplayer

import android.content.Context
import android.webkit.CookieManager
import com.caoccao.javet.values.primitive.V8ValueInteger
import com.caoccao.javet.values.primitive.V8ValueString
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginDescriptor
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.serializers.FlexibleBooleanSerializer
import com.futo.platformplayer.states.StateAnnouncement
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDeveloper
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.FragmentedStorageFileJson
import com.futo.platformplayer.views.fields.FieldForm
import com.futo.platformplayer.views.fields.FormField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.stream.IntStream.range
import kotlin.system.measureTimeMillis

@Serializable()
class SettingsDev : FragmentedStorageFileJson() {

    @FormField("Developer Mode", FieldForm.TOGGLE, "", 0)
    @Serializable(with = FlexibleBooleanSerializer::class)
    var developerMode: Boolean = false;

    @FormField("Development Server", FieldForm.GROUP,
        "Settings related to development server, be careful as it may open your phone to security vulnerabilities", 1)
    val devServerSettings: DeveloperServerFields = DeveloperServerFields();
    @Serializable
    class DeveloperServerFields {

        @FormField("Start Server on boot", FieldForm.TOGGLE, "", 0)
        @Serializable(with = FlexibleBooleanSerializer::class)
        var devServerOnBoot: Boolean = false;

        @FormField("Start Server", FieldForm.BUTTON,
            "Starts a DevServer on port 11337, may expose vulnerabilities.", 1)
        fun startServer() {
            StateDeveloper.instance.runServer();
            StateApp.instance.contextOrNull?.let {
                UIDialogs.toast(it, "Dev Started", false);
            };
        }
    }

    @FormField("Experimental", FieldForm.GROUP,
        "Settings related to development server, be careful as it may open your phone to security vulnerabilities", 2)
    val experimentalSettings: ExperimentalFields = ExperimentalFields();
    @Serializable
    class ExperimentalFields {

        @FormField("Background Subscription Testing", FieldForm.TOGGLE, "", 0)
        @Serializable(with = FlexibleBooleanSerializer::class)
        var backgroundSubscriptionFetching: Boolean = false;
    }

    @FormField("Crash Me", FieldForm.BUTTON,
        "Crashes the application on purpose", 2)
    fun crashMe() {
        throw java.lang.IllegalStateException("This is an uncaught exception triggered on purpose!");
    }

    @FormField("Delete Announcements", FieldForm.BUTTON,
        "Delete all announcements", 2)
    fun deleteAnnouncements() {
        StateAnnouncement.instance.deleteAllAnnouncements();
    }

    @FormField("Clear Cookies", FieldForm.BUTTON,
        "Clear all cook from the CookieManager", 2)
    fun clearCookies() {
        val cookieManager: CookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null);
    }

    @Contextual
    @Transient
    @FormField("V8 Benchmarks", FieldForm.GROUP,
        "Various benchmarks using the integrated V8 engine", 3)
    val v8Benchmarks: V8Benchmarks = V8Benchmarks();
    class V8Benchmarks {
        @FormField(
            "Test V8 Creation speed", FieldForm.BUTTON,
            "Tests V8 creation times and running", 1
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
            "Test V8 Communication speed", FieldForm.BUTTON,
            "Tests V8 communication speeds", 2
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
    @FormField("V8 Script Testing", FieldForm.GROUP, "Various tests against a custom source", 4)
    val v8ScriptTests: V8ScriptTests = V8ScriptTests();
    class V8ScriptTests {
        @Contextual
        private var _currentPlugin : JSClient? = null;
        @FormField("Inject", FieldForm.BUTTON, "Injects a test source config (local) into V8", 1)
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
        @FormField("getHome", FieldForm.BUTTON, "Attempts to fetch 2 pages from getHome", 2)
        fun testV8Home() {
            runTestPlugin(_currentPlugin) {
                var home: IPager<IPlatformContent>? = null;
                var resultPage1: String = "";
                var resultPage2: String = "";
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
    @FormField("Other", FieldForm.GROUP, "Others...", 5)
    val otherTests: OtherTests = OtherTests();
    class OtherTests {
        @FormField("Clear Downloads", FieldForm.BUTTON, "Deletes all ongoing downloads", 1)
        fun clearDownloads() {
            StateDownloads.instance.getDownloading().forEach {
                StateDownloads.instance.removeDownload(it);
            };
        }
        @FormField("Clear All Downloaded", FieldForm.BUTTON, "Deletes all downloaded videos and related files", 2)
        fun clearDownloaded() {
            StateDownloads.instance.getDownloadedVideos().forEach {
                StateDownloads.instance.deleteCachedVideo(it.id);
            };
        }
        @FormField("Delete Unresolved", FieldForm.BUTTON, "Deletes all unresolved source files", 3)
        fun cleanupDownloads() {
            StateDownloads.instance.cleanupDownloads();
        }

        @FormField("Fill storage till error", FieldForm.BUTTON, "Writes to disk till no space is left", 4)
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