package com.futo.platformplayer.tv.fragments

import com.futo.platformplayer.R
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.tv.views.TvUi

class TvHomeFragment : TvFeedFragment() {
    override val heading = R.string.tv_home
    override val hint = R.string.tv_home_hint

    override suspend fun fetch() = StatePlatform.instance.getHome()

    override fun configureHeader() {
        super.configureHeader()
        actions.addView(
            TvUi.iconButton(requireContext(), R.drawable.ic_sources, getString(R.string.tv_sources)) {
                host.selectTab(4)
            })
    }

    override fun emptyText() =
        if (StatePlatform.instance.getEnabledClients().isEmpty()) getString(R.string.tv_no_sources)
        else super.emptyText()
}
