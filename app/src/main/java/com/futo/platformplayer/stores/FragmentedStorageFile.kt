package com.futo.platformplayer.stores

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.constructs.BackgroundTaskHandler
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File


@Serializable
open class FragmentedStorageFile() {
    @kotlinx.serialization.Transient
    private val _lock = Object();

    private val TAG = "FragmentedStorageFile";

    @Transient
    private var _file: File? = null;

    @Transient
    private var _bakFile: File? = null;


    @Transient
    private val _backgroundSave = BackgroundTaskHandler(StateApp.instance.scope, {
        saveBlocking();
    });

    fun getUnderlyingFile(): File? {
        return _file;
    }

    fun withFile(file: File, bakFile: File?): FragmentedStorageFile {
        _file = file;
        _bakFile = bakFile;
        return this;
    }

    fun save() {
        _backgroundSave.run();
    }
    fun saveBlocking() {
        synchronized(_lock) {
            val file = _file;
            if (file == null) {
                Logger.w(TAG, "Failed to flush settings because file was null.")
                return;
            }

            if (file.exists()) {
                val bakFile = _bakFile;
                if (bakFile != null) {
                    file.copyTo(bakFile, true);
                }
            }

            val json = encode();
            file.writeText(json);
        }
    }

    fun delete() {
        if(_file?.exists() ?: false)
            _file?.delete();
        if(_bakFile?.exists() ?: false)
            _bakFile?.delete();
    }

    open fun encode(): String {
        return "{}";
    }
}