package com.futo.platformplayer.states

import android.content.Context
import com.futo.platformplayer.SettingsDev
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.http.server.ManagedHttpServer
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.PlatformContentPager
import com.futo.platformplayer.developer.DeveloperEndpoints
import com.futo.platformplayer.engine.exceptions.ScriptExecutionException
import com.futo.platformplayer.fragment.mainactivity.main.VideoDetailView
import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

/***
 * Used for developer related calls
 */
class StateDeveloper {
    private var _server : ManagedHttpServer? = null;

    var currentDevID: String? = null
        private set;

    private var _devLogsIndex: Int = 0;
    private val _devLogs: MutableList<DevLog> = mutableListOf();
    private val _devHttpExchanges: MutableList<DevHttpExchange> = mutableListOf();

    var devProxy: DevProxySettings? = null;

    var testState: String? = null;
    val isPlaybackTesting: Boolean get() {
        return SettingsDev.instance.developerMode && testState == "TestPlayback";
    };


    fun initializeDev(id: String) {
        currentDevID = id;
        synchronized(_devLogs) {
            _devLogs.clear();
        }
    }
    inline fun <reified T> handleDevCall(devId: String, contextName: String, printResult: Boolean = false, handle: ()->T): T {
        var resp: T? = null;

        val time = measureTimeMillis {
            try {
                resp = handle();
            }
            catch (castEx: ClassCastException) {
                Logger.e("StateDeveloper", "Wrapped Exception: " + castEx.message, castEx);
                val exMsg =
                    "Call [${contextName}] returned incorrect type. Expected [${T::class.simpleName}].\nCastException: ${castEx.message}";
                logDevException(devId, exMsg);
                throw castEx;
            }
            catch (ex: ScriptExecutionException) {
                Logger.e("StateDeveloper", "Wrapped Exception: " + ex.message, ex);
                logDevException(
                    devId,
                    "Call [${contextName}] failed due to: (${ex::class.simpleName}) ${ex.message}" +
                            (if(ex.stack != null) "\n" + ex.stack else "")
                );
                throw ex;
            }
            catch (ex: Throwable) {
                Logger.e("StateDeveloper", "Wrapped Exception: " + ex.message, ex);
                logDevException(
                    devId,
                    "Call [${contextName}] failed due to: (${ex::class.simpleName}) ${ex.message}"
                );
                throw ex;
            }
        }
        var printValue = "";
        if(printResult) {
            if(resp is Boolean)
                printValue = resp.toString();
            else if(resp is List<*>)
                printValue = (resp as List<*>).size.toString();
        }

        logDevInfo(devId, "Call [${contextName}] succesful [${time}ms] ${printValue}");
        return resp!!;
    }
    fun logDevException(devId: String, msg: String) {
        currentDevID.let {
            if(it == devId)
                synchronized(_devLogs) {
                    _devLogsIndex++;
                    _devLogs.add(DevLog(_devLogsIndex, devId, "EXCEPTION", msg));
                }
        }
    }
    fun logDevInfo(devId: String, msg: String) {
        currentDevID.let {
            if(it == devId)
                synchronized(_devLogs) {
                    _devLogsIndex++;
                    _devLogs.add(DevLog(_devLogsIndex, devId, "INFO", msg));
                }
        }
    }
    fun getLogs(startIndex: Int) : List<DevLog> {
        synchronized(_devLogs) {
            val index = _devLogs.indexOfFirst { it.id == startIndex };
            return _devLogs.subList(index + 1, _devLogs.size);
        }
    }

    fun addDevHttpExchange(exchange: DevHttpExchange) {
        synchronized(_devHttpExchanges) {
            if(_devHttpExchanges.size > 15)
                _devHttpExchanges.removeAt(0);
            _devHttpExchanges.add(exchange);
        }
    }
    fun getHttpExchangesAndClear(): List<DevHttpExchange> {
        synchronized(_devHttpExchanges) {
            val data = _devHttpExchanges.toList();
            _devHttpExchanges.clear();
            return data;
        }
    }

    fun setDevClientSettings(settings: HashMap<String, String?>) {
        val client = StatePlatform.instance.getDevClient();
        client?.let {
            it.descriptor.settings = settings;
        };
    }


    fun runServer() {
        if(_server != null)
            return;
        UIDialogs.toast("DevServer Booted");
        _server = ManagedHttpServer(11337).apply {
            this.addBridgeHandlers(DeveloperEndpoints(StateApp.instance.context), "dev");
        };
        _server?.start();
    }
    fun stopServer() {
        _server?.stop();
        _server = null;
    }


    private var homePager: IPager<IPlatformContent>? = null;
    private var pagerIndex = 0;
    fun testPlayback(){
        val mainActivity = if(StateApp.instance.isMainActive) StateApp.instance.context as MainActivity else return;
        StateApp.instance.scope.launch(Dispatchers.IO) {
            if(homePager == null)
                homePager = StatePlatform.instance.getHome();
            var pager = homePager ?: return@launch;
            pagerIndex++;
            val video =  if(pager.getResults().size <= pagerIndex) {
                if(!pager.hasMorePages()) {
                    homePager = StatePlatform.instance.getHome();
                    pager = homePager as IPager<IPlatformContent>;
                }
                pager.nextPage();
                pagerIndex = 0;
                val results = pager.getResults();
                if(results.size <= 0)
                    null;
                else
                    results[0];
            }
            else
                pager.getResults()[pagerIndex];

            StateApp.instance.scope.launch(Dispatchers.Main) {
                mainActivity.navigate(mainActivity._fragVideoDetail, video);
            }
        }
    }

    companion object {
        const val DEV_ID = "DEV";

        private var _instance : StateDeveloper? = null;
        val instance : StateDeveloper
            get(){
            if(_instance == null)
                _instance = StateDeveloper();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
                it._server?.stop();
            }
        }

    }

    @kotlinx.serialization.Serializable
    data class DevLog(val id: Int, val devId: String, val type: String, val log: String);

    @kotlinx.serialization.Serializable
    data class DevHttpRequest(val method: String, val url: String, val headers: Map<String, String>, val body: String, val status: Int = 0);
    @kotlinx.serialization.Serializable
    data class DevHttpExchange(val request: DevHttpRequest, val response: DevHttpRequest);

    @kotlinx.serialization.Serializable
    data class DevProxySettings(val url: String, val port: Int)
}