package com.futo.platformplayer.api.media

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.getOrThrowNullable
import com.futo.polycentric.core.combineHashCodes
import okhttp3.internal.platform.Platform

@kotlinx.serialization.Serializable
class PlatformID {
    val platform: String;
    val value: String?;
    var pluginId: String? = null;
    var claimType: Int = 0;
    var claimFieldType: Int = -1;

   constructor(platform: String, id: String?, pluginId: String? = null, claimType: Int = 0, claimFieldType: Int = -1) {
        this.platform = platform;
        this.value = id;
        this.pluginId = pluginId;
        this.claimType = claimType;
       this.claimFieldType = claimFieldType;
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PlatformID) {
            return false
        }

        return platform == other.platform && value == other.value
    }

    override fun hashCode(): Int {
        return combineHashCodes(listOf(platform.hashCode(), value?.hashCode()))
    }

    override fun toString(): String {
        return "(platform: $platform, value: $value, pluginId: $pluginId, claimType: $claimType, claimFieldType: $claimFieldType)";
    }

    companion object {
        val NONE = PlatformID("Unknown", null);

        fun fromV8(config: SourcePluginConfig, value: V8ValueObject): PlatformID {
            val contextName = "PlatformID";
            return PlatformID(
                value.getOrThrow(config, "platform", contextName),
                value.getOrThrowNullable<String>(config, "value", contextName),
                config.id,
                value.getOrDefault(config, "claimType", contextName, 0) ?: 0,
                value.getOrDefault(config, "claimFieldType", contextName, -1) ?: -1);
        }

        fun asUrlID(url: String): PlatformID {
            return PlatformID("URL", url, null);
        }
    }
}