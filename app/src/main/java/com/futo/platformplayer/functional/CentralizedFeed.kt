package com.futo.platformplayer.functional

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.structures.ReusablePager
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2

//TODO: Integrate this better?
class CentralizedFeed {
    var lock = Object();
    var feed: ReusablePager<IPlatformContent>? = null;
    var isGlobalUpdating: Boolean = false;
    var exceptions: List<Throwable> = listOf();


    var lastProgress: Int = 0;
    var lastTotal: Int = 0;
    val onUpdateProgress = Event2<Int, Int>();
    val onUpdated = Event0();
    val onUpdatedOnce = Event1<Throwable?>();
    val onException = Event1<List<Throwable>>();
}