package com.futo.platformplayer.polycentric

import com.futo.platformplayer.encryption.GEncryptionProviderV1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.polycentric.core.ProcessSecret
import com.futo.polycentric.core.PublicKey
import com.futo.polycentric.core.base64ToByteArray
import com.futo.polycentric.core.toBase64
import userpackage.Protocol

class PolycentricStorage {
    private val _processSecrets = FragmentedStorage.get<StringArrayStorage>("processSecrets");

    fun addProcessSecret(processSecret: ProcessSecret) {
        _processSecrets.addDistinct(GEncryptionProviderV1.instance.encrypt(processSecret.toProto().toByteArray()).toBase64())
        _processSecrets.saveBlocking()
    }

    fun getProcessSecrets(): List<ProcessSecret> {
        val processSecrets = arrayListOf<ProcessSecret>()
        for (p in _processSecrets.getAllValues()) {
            try {
                processSecrets.add(ProcessSecret.fromProto(Protocol.StorageTypeProcessSecret.parseFrom(GEncryptionProviderV1.instance.decrypt(p.base64ToByteArray()))))
            } catch (e: Throwable) {
                Logger.i(TAG, "Failed to decrypt process secret", e);
            }
        }
        return processSecrets
    }

    fun removeProcessSecret(publicKey: PublicKey) {
        for(p in _processSecrets.getAllValues()){
            try {
                val key = ProcessSecret.fromProto(Protocol.StorageTypeProcessSecret.parseFrom(GEncryptionProviderV1.instance.decrypt(p.base64ToByteArray())));
                if(key.system.publicKey.equals(publicKey))
                    _processSecrets.remove(p);
            } catch (e: Throwable) {
                Logger.i(TAG, "Failed to decrypt process secret", e);
            }
        }
    }

    companion object {
        val TAG = "PolycentricStorage";
        private var _instance : PolycentricStorage? = null;
        val instance : PolycentricStorage
            get(){
                if(_instance == null)
                    _instance = PolycentricStorage();
                return _instance!!;
            };
    }
}