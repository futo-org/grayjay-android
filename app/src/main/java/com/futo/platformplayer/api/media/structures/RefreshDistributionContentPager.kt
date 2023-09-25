package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import kotlinx.coroutines.Deferred

/**
 * A RefreshMultiPager that simply returns all respective pagers in equal distribution, optionally inserting PlaceholderPager results as provided for their respective promised pagers
 * (Eg. Pager A is completed, Pager [B,C,D] are promised/deferred. placeholderPagers [1,2,3] will map B=>1, C=>2, D=>3 until promised pagers are completed)
 * Uses wrapped MultiDistributionContentAsyncPager for inidivual pagers.
 */
class RefreshDistributionContentPager(pagers: List<IPager<IPlatformContent>>, pendingPagers: List<Deferred<IPager<IPlatformContent>?>>, placeholderPagers: List<IPager<IPlatformContent>>? = null)
    : MultiRefreshPager<IPlatformContent>(pagers, pendingPagers, placeholderPagers) {

    override fun recreatePager(pagers: List<IPager<IPlatformContent>>): IPager<IPlatformContent> {
        return MultiDistributionContentParallelPager(pagers.associateWith { 1f });
        //return MultiDistributionContentPager(pagers.associateWith { 1f });
    }
}