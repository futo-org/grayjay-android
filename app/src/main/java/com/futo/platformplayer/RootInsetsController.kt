package com.futo.platformplayer

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import kotlin.math.max

class RootInsetsController private constructor(
    private val activity: Activity,
    private val window: Window,
    private val root: ViewGroup
) {
    private val controller by lazy { WindowInsetsControllerCompat(window, root) }

    private val basePaddingLeft = root.paddingLeft
    private val basePaddingTop = root.paddingTop
    private val basePaddingRight = root.paddingRight
    private val basePaddingBottom = root.paddingBottom

    private var currentInsets: WindowInsetsCompat = WindowInsetsCompat.CONSUMED
    private var fullscreen = false

    init {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            currentInsets = insets
            applyPadding()
            insets
        }

        root.doOnAttach { ViewCompat.requestApplyInsets(root) }
    }

    private fun effectiveInsets(): Insets {
        if (fullscreen) return Insets.NONE

        val sys = currentInsets.getInsets(Type.systemBars())
        val cut = currentInsets.getInsetsIgnoringVisibility(Type.displayCutout())
        val portrait = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

        val top = if (portrait) max(sys.top, cut.top) else sys.top
        return Insets.of(sys.left, top, sys.right, sys.bottom)
    }


    private fun applyPadding() {
        val e = effectiveInsets()
        root.updatePadding(
            left = basePaddingLeft + e.left,
            top = basePaddingTop + e.top,
            right = basePaddingRight + e.right,
            bottom = basePaddingBottom + e.bottom
        )
    }

    private fun forceRelayoutAndInsets() {
        root.post {
            ViewCompat.requestApplyInsets(root)
            applyPadding()
            root.post {
                ViewCompat.requestApplyInsets(root)
                applyPadding()
            }
        }
    }

    fun enterFullscreen(allowCutoutShortEdges: Boolean = true) {
        fullscreen = true
        if (allowCutoutShortEdges) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        controller.hide(Type.systemBars())
        forceRelayoutAndInsets()
    }

    fun exitFullscreen() {
        fullscreen = false
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        controller.show(Type.systemBars())
        forceRelayoutAndInsets()
    }

    fun onConfigurationChanged() {
        forceRelayoutAndInsets()
    }

    fun setLightSystemBarAppearance(lightStatus: Boolean, lightNav: Boolean) {
        controller.isAppearanceLightStatusBars = lightStatus
        controller.isAppearanceLightNavigationBars = lightNav
    }

    companion object {
        fun attach(activity: Activity, root: ViewGroup): RootInsetsController {
            return RootInsetsController(activity, activity.window, root)
        }
    }
}
