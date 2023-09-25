package com.futo.platformplayer.fragment.mainactivity.topbar

import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.fragment.mainactivity.MainActivityFragment

abstract class TopFragment : MainActivityFragment() {

    open fun onShown(parameter: Any? = null) {}
    open fun onHide() {}

    fun close() {
        isValidMainActivity();
        return (activity as MainActivity).closeSegment();
    }
}