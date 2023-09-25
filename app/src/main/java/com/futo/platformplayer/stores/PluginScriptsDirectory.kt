package com.futo.platformplayer.stores

class PluginScriptsDirectory : FragmentedStorageDirectory() {
    fun getScript(id: String) : String? {
        if(hasFile(id))
            return getFileOrCreate<FragmentedStorageFileString>(id).value;
        return null;
    }
    fun setScript(id: String, script: String) {
        val file = getFileOrCreate<FragmentedStorageFileString>(id);
        file.value = script;
        file.saveBlocking();
    }
    fun removeScript(id: String) {
        deleteFile(id);
    }
}