package com.futo.platformplayer.subscription

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateDownloads
import java.time.OffsetDateTime

/**
 * Evaluates newly discovered subscription content against the configured auto-download rules
 * (global defaults in Settings.autoDownload, optionally overridden per subscription) and queues
 * matching NEW videos for download.
 *
 * Only uploads newer than [Subscription.autoDownloadSince] are considered, which is armed to "now"
 * the first time auto-download becomes active for a subscription so the existing back-catalog is
 * never grabbed.
 */
object SubscriptionAutoDownloader {
    private const val TAG = "SubscriptionAutoDownloader";

    fun onNewContent(sub: Subscription, content: IPlatformContent) {
        try {
            if(!sub.isAutoDownloadEnabled())
                return;

            //Arm the baseline the first time auto-download becomes active so we never grab the back-catalog
            if(sub.autoDownloadSince == OffsetDateTime.MAX) {
                sub.autoDownloadSince = OffsetDateTime.now();
                sub.saveAsync();
                return;
            }

            if(content !is IPlatformVideo)
                return;
            if(!sub.shouldAutoDownload(content))
                return;

            //Skip if already downloaded or already queued
            if(StateDownloads.instance.getCachedVideo(content.id) != null)
                return;
            if(StateDownloads.instance.getDownloading().any { it.videoEither.url == content.url })
                return;

            Logger.i(TAG, "Auto-downloading [${content.name}] from subscription [${sub.channel.name}]");
            StateDownloads.instance.download(content, sub.getAutoDownloadQualityPixels(), null);
        } catch(ex: Throwable) {
            Logger.e(TAG, "Failed to evaluate auto-download for [${content.name}]", ex);
        }
    }
}
