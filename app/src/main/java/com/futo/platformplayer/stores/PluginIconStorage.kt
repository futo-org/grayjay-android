package com.futo.platformplayer.stores

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.ImageVariable
import java.io.File

class PluginIconStorage : FragmentedStorageDirectory() {

    fun hasIcon(name: String) : Boolean {
        val ref = getFileReference(name);
        return ref.exists();
    }

    fun getIconBinary(name: String) : ImageVariable {
        return ImageVariable.fromFile(getFileOrThrow(name));
    }
    fun saveIconBinary(name: String, binary: ByteArray) {
        val file = getFileReference(name);
        try {
            file.writeBytes(binary);
        }
        catch(ex: Throwable) {
            Logger.e("Failed to save icon", ex.message, ex);
            file.delete();
        }
        finally {
        }
    }
    fun deleteIconBinary(name: String) {
        val file = getFileReference(name);
        if(file.exists())
            file.delete();
    }


    fun getFileOrThrow(name: String) : File {
        val ref = getFileReference(name);
        if(!ref.exists())
            throw IllegalArgumentException("File does not exist [${name}]");
        return ref;
    }
}