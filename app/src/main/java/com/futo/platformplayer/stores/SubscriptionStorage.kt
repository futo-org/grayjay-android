package com.futo.platformplayer.stores

import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

@Serializable()
class SubscriptionStorage : FragmentedStorageFileJson() {
    var version = StateSubscriptions.VERSION;
    var subscriptions = arrayListOf<Subscription>();

    fun addSubscription(channel: Subscription) : Subscription {
        subscriptions.add(channel);
        return channel;
    }

    fun removeSubscription(url : String) {
        val toRemove = subscriptions.firstOrNull { it.channel.url == url };
        subscriptions.removeIf { it.channel.url == url };
    }

    fun isSubscribedTo(channel: IPlatformChannel): Boolean = isSubscribedTo(channel.url);
    fun isSubscribedTo(channel: PlatformAuthorLink): Boolean = isSubscribedTo(channel.url);
    fun isSubscribedTo(url: String) : Boolean = subscriptions.any { u -> u.channel.url == url };

    override fun encode(): String {
        return Json.encodeToString(this);
    }
}