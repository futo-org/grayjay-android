package com.futo.platformplayer.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.tv.fragments.*
import com.futo.platformplayer.tv.sync.TvSyncSetupReceiver
import com.futo.platformplayer.tv.views.TvNavigationRail
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

class TvActivity : AppCompatActivity() {
    private val mainId = UUID.randomUUID().toString()
    private lateinit var rail: TvNavigationRail
    private lateinit var content: FrameLayout
    private var selectedTab = 0
    private var updatePrompt: TvUpdatePrompt? = null
    var currentVideo: IPlatformVideoDetails? = null
    var ready = CompletableDeferred<Unit>()
        private set

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StateApp.instance.setGlobalContext(this, lifecycleScope, mainId)
        StateApp.instance.mainAppStarting(this)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val root = FrameLayout(this).apply { setBackgroundColor(TvUi.background) }
        content = FrameLayout(this).apply {
            id = R.id.tv_content
            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1).apply { marginStart = dp(80) })
        rail = TvNavigationRail(this) { selectTab(it) }
        root.addView(rail, FrameLayout.LayoutParams(dp(80), -1))
        setContentView(root)
        updatePrompt = TvUpdatePrompt(this)
        selectedTab = savedInstanceState?.getInt("tab") ?: 0
        rail.selectTab(selectedTab)
        supportFragmentManager.addOnBackStackChangedListener { updateRail() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val fragment = supportFragmentManager.findFragmentById(R.id.tv_content)
                    if (fragment is TvPlayerFragment && fragment.hideControls()) return
                    if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStackImmediate()
                    else if (selectedTab != 0) selectTab(0) else finish()
                }
            })
        if (savedInstanceState == null) selectTab(0)
        initialize(savedInstanceState == null)
        updateRail()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val player = supportFragmentManager.findFragmentById(R.id.tv_content) as? TvPlayerFragment
        return player?.handleKey(event) == true || super.dispatchKeyEvent(event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", selectedTab)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        StateApp.instance.mainAppDestroyed(this, mainId)
    }

    suspend fun awaitReady() {
        if (ready.isCancelled) {
            ready = CompletableDeferred()
            initialize()
        }
        ready.await()
    }

    fun selectTab(index: Int) {
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.popBackStackImmediate(
            null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        selectedTab = index
        rail.selectTab(index)
        val fragment = when (index) {
            1 -> TvSearchFragment()
            2 -> TvSubscriptionsFragment()
            3 -> TvWatchLaterFragment()
            4 -> TvSourcesFragment()
            5 -> TvHistoryFragment()
            6 -> TvSyncFragment()
            7 -> TvPlaylistsFragment()
            else -> TvHomeFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.tv_content, fragment).commit()
        rail.visibility = View.VISIBLE
        content.post { if (!content.hasFocus()) content.requestFocus() }
    }

    fun navigate(fragment: Fragment) {
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.beginTransaction().replace(R.id.tv_content, fragment).addToBackStack(null).commit()
        content.post { if (!content.hasFocus()) content.requestFocus() }
    }

    fun openVideo(url: String) =
        navigate(
            TvPlayerFragment().apply {
                arguments = Bundle().apply { putString("url", url) }
            })

    fun openChannel(url: String, name: String? = null, thumbnail: String? = null) =
        navigate(
            TvChannelFragment().apply {
                arguments = Bundle().apply {
                    putString("url", url)
                    putString("name", name)
                    putString("thumbnail", thumbnail)
                }
            })

    fun play(video: IPlatformVideoDetails, position: Long = 0) {
        currentVideo = video
        val current = supportFragmentManager.findFragmentById(R.id.tv_content)
        if (current is TvVideoDetailsFragment && current.arguments?.getBoolean("fromPlayer") == true) {
            supportFragmentManager.setFragmentResult("tv_resume", Bundle().apply { putLong("position", position) })
            supportFragmentManager.popBackStackImmediate()
            return
        }
        navigate(
            TvPlayerFragment().apply {
                arguments = Bundle().apply {
                    putString("url", video.url)
                    putLong("position", position)
                }
            })
    }

    fun currentFocusInRail(): Boolean = rail.contains(currentFocus)

    fun focusNavigation() {
        rail.focusSelected()
    }

    private fun initialize(openIntent: Boolean = false) {
        lifecycleScope.launch {
            try {
                StatePlatform.instance.updateAvailableClients(this@TvActivity)
                StateApp.instance.mainAppStarted(this@TvActivity)
                ready.complete(Unit)
                if (openIntent) handleIntent(intent)
                updatePrompt?.showIfReady()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ready.completeExceptionally(error)
            }
        }
    }

    private fun updateRail() {
        val playing = supportFragmentManager.findFragmentById(R.id.tv_content) is TvPlayerFragment
        rail.visibility = if (playing) View.GONE else View.VISIBLE
        (content.layoutParams as FrameLayout.LayoutParams).apply {
            marginStart = if (playing) 0 else dp(80)
            content.layoutParams = this
        }
        updatePrompt?.showIfReady()
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == TvSyncSetupReceiver.ACTION_OPEN)
            selectTab(6)
        else
            intent.data?.toString()?.let { openVideo(it) }
    }
}
