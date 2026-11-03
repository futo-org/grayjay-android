package com.futo.platformplayer

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.states.StatePlatform

class TemporaryFixes {
    companion object {
        private const val PLUGIN_ID = "35ae969a-a7db-11ed-afa1-0242ac120002";

        // Remove once membership metadata and subscription filtering honor the plugin setting,
        // including for content already stored in the subscription cache.
        fun filterSubscriptionContent(results: List<IPlatformContent>): List<IPlatformContent> {
            val plugin = StatePlatform.instance.getClientOrNull(PLUGIN_ID) as? JSClient
                ?: return results;
            val allowMemberContent = plugin.descriptor.getSettingsWithDefaults()["allowMemberContent"].toBoolean();
            if (allowMemberContent)
                return results;

            return results.filterNot {
                it.id.pluginId == PLUGIN_ID && it.name.startsWith("[MEMBER]")
            };
        }
    }
}
