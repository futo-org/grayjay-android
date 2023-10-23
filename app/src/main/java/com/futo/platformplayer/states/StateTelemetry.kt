package com.futo.platformplayer.states

import android.content.Context
import android.os.Build
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Telemetry
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

class StateTelemetry {
    private val _id = FragmentedStorage.get<StringStorage>("id");

    fun initialize() {
        if (_id.value.isEmpty()) {
            _id.setAndSave(UUID.randomUUID().toString());
        }
    }

    fun upload() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val telemetry = Telemetry(
                    _id.value,
                    BuildConfig.APPLICATION_ID,
                    BuildConfig.VERSION_CODE.toString(),
                    BuildConfig.VERSION_NAME,
                    BuildConfig.BUILD_TYPE,
                    BuildConfig.DEBUG,
                    BuildConfig.IS_UNSTABLE_BUILD,
                    Build.BRAND,
                    Build.MANUFACTURER,
                    Build.MODEL,
                    Build.VERSION.SDK_INT
                );

                val headers = hashMapOf(
                    "Content-Type" to "application/json"
                );

                val json = Json.encodeToString(telemetry);
                val url = "https://logs.grayjay.app/telemetry";
                //val url = "http://10.0.0.5:5413/telemetry";
                val client = ManagedHttpClient();
                val response = client.post(url, json, headers);
                if (response.isOk) {
                    Logger.i(TAG, "Launch telemetry submitted.");
                } else {
                    Logger.w(TAG, "Failed to submit launch telemetry (${response.code}): '${response.body?.string()}'.");
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to submit launch telemetry.", e);
            }
        }
    }

    companion object {
        private var _instance: StateTelemetry? = null;
        val instance: StateTelemetry
            get(){
                if(_instance == null)
                    _instance = StateTelemetry();
                return _instance!!;
            };

        private const val TAG = "StateTelemetry";
    }
}