package com.futo.platformplayer.stores

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.CastingDeviceInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
class CastingDeviceInfoStorage : FragmentedStorageFileJson() {
    var deviceInfos = mutableListOf<CastingDeviceInfo>();

    @Synchronized
    fun getDevicesCount(): Int {
        return deviceInfos.size;
    }

    @Synchronized
    fun getDevices() : List<CastingDeviceInfo> {
        return deviceInfos.toList();
    }

    @Synchronized
    fun addDevice(castingDeviceInfo: CastingDeviceInfo): Boolean {
        if (deviceInfos.any { d -> d.name == castingDeviceInfo.name }) {
            Logger.i("CastingDeviceInfoStorage", "Device '${castingDeviceInfo.name}' already existed in device storage.")
            return false;
        }

        if (deviceInfos.size >= 5) {
            deviceInfos.removeAt(0);
        }

        deviceInfos.add(castingDeviceInfo);
        save();
        return true;
    }

    @Synchronized
    fun removeDevice(name: String) {
        deviceInfos.removeIf { d -> d.name == name };
        save();
    }

    override fun encode(): String {
        return Json.encodeToString(this);
    }
}