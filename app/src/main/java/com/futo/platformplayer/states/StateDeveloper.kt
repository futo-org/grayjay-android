package com.futo.platformplayer.states

import android.content.Context
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.http.server.ManagedHttpServer
import com.futo.platformplayer.developer.DeveloperEndpoints
import com.futo.platformplayer.engine.exceptions.ScriptExecutionException
import com.futo.platformplayer.logging.Logger
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
}