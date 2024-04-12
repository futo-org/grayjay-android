package com.futo.platformplayer.engine.packages

import android.util.Base64
import com.caoccao.javet.annotations.V8Function
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.google.common.hash.Hashing.md5
import java.security.MessageDigest
import java.util.UUID


class PackageUtilities : V8Package {
    @Transient
    private val _config: IV8PluginConfig;

    override val name: String get() = "Utilities";
    override val variableName: String get() = "utility";

    constructor(plugin: V8Plugin, config: IV8PluginConfig): super(plugin) {
        _config = config;
    }

    @V8Function
    fun toBase64(arr: ByteArray): String {
        return Base64.encodeToString(arr, Base64.NO_WRAP);
    }

    @V8Function
    fun fromBase64(str: String): ByteArray {
        return Base64.decode(str, Base64.DEFAULT)
    }

    @V8Function
    fun md5(arr: ByteArray): ByteArray {
        return MessageDigest.getInstance("MD5").digest(arr);
    }

    @V8Function
    fun randomUUID(): String {
        return UUID.randomUUID().toString();
    }
}