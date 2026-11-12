package com.futo.platformplayer.polycentric

import android.content.Context
import org.futo.polycentric.core.IContentRepository
import org.futo.polycentric.core.IEventAckRepository
import org.futo.polycentric.core.IEventRepository
import org.futo.polycentric.core.IKeysRepository
import org.futo.polycentric.core.IStorageDriver
import org.futo.polycentric.core.SqliteStorageDriver

/**
 * Composite [IStorageDriver] which uses the Polycentric core library's
 * implementations for the various content storage repositories, but which
 * substitutes Grayjay's key storage repository for signing keys (which will
 * keep them encrypted at rest).
 */
class PolycentricStorageDriver(context: Context) : IStorageDriver {
    private val sqlite = SqliteStorageDriver(context)

    override fun createEventRepository(): IEventRepository = sqlite.createEventRepository()
    override fun createContentRepository(): IContentRepository = sqlite.createContentRepository()
    override fun createEventAckRepository(): IEventAckRepository = sqlite.createEventAckRepository()
    override fun createKeysRepository(): IKeysRepository = PolycentricKeyRepository()

    override suspend fun saveActiveIdentityKey(publicKey: ByteArray, identityKey: String?) =
        sqlite.saveActiveIdentityKey(publicKey, identityKey)

    override suspend fun loadActiveIdentityKey(publicKey: ByteArray): String? =
        sqlite.loadActiveIdentityKey(publicKey)

    override suspend fun saveActiveSession(identityKey: String?) =
        sqlite.saveActiveSession(identityKey)

    override suspend fun loadActiveSession(): String? = sqlite.loadActiveSession()
}
