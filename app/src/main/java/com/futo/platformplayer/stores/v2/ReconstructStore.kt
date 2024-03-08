package com.futo.platformplayer.stores.v2

import com.futo.platformplayer.models.ImportCache

abstract class ReconstructStore<T> {
    open val backupOnSave: Boolean = false;
    open val backupOnCreate: Boolean = true;

    val identifierName: String?;

    constructor(identifierName: String? = null) {
        this.identifierName = identifierName;
    }

    abstract fun toReconstruction(obj: T): String;
    abstract suspend fun toObject(id: String, backup: String, reconstructionBuilder: Builder, importCache: ImportCache? = null): T;

    fun toReconstructionWithHeader(obj: T, fallbackName: String): String {
        val identifier = identifierName ?: fallbackName;
        return "@/${identifier}\n${toReconstruction(obj)}";
    }

    suspend fun toObjectWithHeader(id: String, backup: String, builder: Builder, importCache: ImportCache? = null): T {
        if(backup.startsWith("@/") && backup.contains("\n"))
            return toObject(id, backup.substring(backup.indexOf("\n") + 1), builder, importCache);
        else
            return toObject(id, backup, builder, importCache);
    }



    class Builder {
        val messages: ArrayList<String> = arrayListOf();
    }
}