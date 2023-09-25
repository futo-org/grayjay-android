package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.constructs.Event2

interface IReplacerPager<T> {
    val onReplaced: Event2<T, T>;
}