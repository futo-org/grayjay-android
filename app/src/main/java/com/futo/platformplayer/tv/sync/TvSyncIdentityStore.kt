package com.futo.platformplayer.tv.sync

import android.content.Context

object TvSyncIdentityStore {
    @Synchronized
    fun all(context: Context): Set<String> = preferences(context).getStringSet("keys", emptySet()).orEmpty().toSet()

    @Synchronized
    fun remember(context: Context, publicKey: String) {
        preferences(context).edit().putStringSet("keys", all(context) + publicKey).apply()
    }

    @Synchronized
    fun forget(context: Context, publicKey: String) {
        preferences(context).edit().putStringSet("keys", all(context) - publicKey).apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences("tv-sync-identities", Context.MODE_PRIVATE)
}
