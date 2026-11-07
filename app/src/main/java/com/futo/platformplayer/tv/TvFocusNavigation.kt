package com.futo.platformplayer.tv

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.SeekBar
import androidx.core.view.children
import androidx.core.view.doOnAttach
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TimeBar
import com.futo.platformplayer.R
import com.futo.platformplayer.fragment.mainactivity.main.VideoDetailFragment
import com.futo.platformplayer.fragment.mainactivity.main.VideoDetailView
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.video.FutoVideoPlayer

/** Adds remote focus to existing click targets without replacing their styling or listeners. */
@UnstableApi
class TvFocusNavigation private constructor(private val root: ViewGroup) {
    private val ring = FocusRing(root)
    private var scope: ViewGroup = root

    init {
        root.overlay.add(ring)
        root.viewTreeObserver.addOnGlobalLayoutListener { refresh() }
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, _ -> ring.invalidateSelf() }
        root.viewTreeObserver.addOnWindowFocusChangeListener { focused ->
            if (focused) refresh() else root.post { attachDialogWindows() }
        }
        root.post { refresh() }
    }

    fun dispatchKeyEvent(event: KeyEvent, dispatch: (KeyEvent) -> Boolean): Boolean {
        refresh()
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> return dispatch(event)
        }
        // Let RecyclerView and editable controls handle their own scrolling and cursor movement.
        if (scope === root) return dispatch(event)
        val focused = root.findFocus()
        if ((direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) &&
            (focused is EditText || focused is SeekBar || focused is TimeBar))
            return dispatch(event)
        if (scope is FutoVideoPlayer && scope.findViewById<View>(R.id.layout_controls).visibility != View.VISIBLE)
            return dispatch(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val next = focused?.focusSearch(direction)
        if (next != null && isInScope(next)) return dispatch(event)
        FocusFinder.getInstance().findNextFocus(scope, focused, direction)?.takeIf { isInScope(it) }?.requestFocus()
        return true
    }

    private fun refresh() {
        prepare(root)
        scope = modalScope(root) ?: root
        ring.setBounds(0, 0, root.width, root.height)
        ring.invalidateSelf()
        val player = scope as? FutoVideoPlayer
        if (root.hasWindowFocus() &&
            player != null &&
            root.findFocus() === player &&
            player.findViewById<View>(R.id.layout_controls).isShown) {
            TvPlayback.focusPlayPause(player)
        }
        if (root.hasWindowFocus() && !isInScope(root.findFocus(), checkAlpha = false)) {
            scope.getFocusables(View.FOCUS_FORWARD).firstOrNull { usable(it) }?.requestFocus()
        }
    }

    private fun prepare(view: View) {
        if (view is FutoVideoPlayer) view.isFocusable = true
        if (view is TimeBar) view.isFocusable = true
        if (view.hasOnClickListeners() || view.isLongClickable) {
            // Scrims dismiss overlays by touch; the visible Cancel/Back actions handle the remote.
            val isScrim = view.id == R.id.more_overlay_background || view.id == R.id.overlay_slide_up_menu_background
            view.isFocusable = !isScrim
        }
        if (view is ViewGroup) view.children.forEach(::prepare)
    }

    private fun modalScope(view: View): ViewGroup? {
        if (!usable(view)) return null
        if (view is ViewGroup) {
            view.children.toList().asReversed().forEach { child ->
                modalScope(child)?.let {
                    return it
                }
            }
            if (view is SlideUpMenuOverlay && view.isVisible) return view
            if (view.id == R.id.more_overlay) return view
            if (view.id == R.id.overlay_container && view.childCount > 0) return view
            if (view is FutoVideoPlayer && view.isFullScreen) return view
            if (view is VideoDetailView && view.fragment.state == VideoDetailFragment.State.MAXIMIZED) return view
        }
        return null
    }

    private fun isInScope(view: View?, checkAlpha: Boolean = true): Boolean {
        if (view == null || !usable(view, checkAlpha)) return false
        var parent: View? = view
        while (parent != null) {
            if (parent === scope) return true
            parent = parent.parent as? View
        }
        return false
    }

    companion object {
        fun attach(window: Window?) {
            if (window == null || !TvMode.isEnabled(window.context)) return
            val navigation = attachRoot(window.decorView) ?: return
            val callback = window.callback ?: return
            if (callback is RemoteWindowCallback) return
            window.callback = RemoteWindowCallback(callback, navigation)
        }

        fun attach(view: View) {
            if (!TvMode.isEnabled(view.context)) return
            view.doOnAttach { attachRoot(it.rootView) }
        }

        private fun attachRoot(view: View): TvFocusNavigation? {
            val root = view as? ViewGroup ?: return null
            val existing = root.getTag(R.id.tv_focus_navigation) as? TvFocusNavigation
            if (existing != null) return existing
            return TvFocusNavigation(root).also { root.setTag(R.id.tv_focus_navigation, it) }
        }

        private fun attachDialogWindows() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WindowInspector.getGlobalWindowViews().forEach { attachRoot(it) }
            }
        }

        private fun usable(view: View, checkAlpha: Boolean = true): Boolean {
            if (!view.isShown || !view.isEnabled) return false
            if (!checkAlpha) return true
            var current: View? = view
            while (current != null) {
                if (current.alpha <= 0.05f) return false
                current = current.parent as? View
            }
            return true
        }
    }

    private class FocusRing(private val root: ViewGroup) : Drawable() {
        private val rect = Rect()
        private val rootRect = Rect()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3 * root.resources.displayMetrics.density
        }

        override fun draw(canvas: Canvas) {
            val focused = root.findFocus() ?: return
            if (focused is FutoVideoPlayer) return
            if (!root.hasWindowFocus() || !usable(focused) || !focused.getGlobalVisibleRect(rect)) return
            root.getGlobalVisibleRect(rootRect)
            rect.offset(-rootRect.left, -rootRect.top)
            val bounds = RectF(rect)
            bounds.inset(paint.strokeWidth / 2, paint.strokeWidth / 2)
            canvas.drawRoundRect(bounds, 6f, 6f, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class RemoteWindowCallback(
        private val original: Window.Callback,
        private val navigation: TvFocusNavigation
    ) : Window.Callback by original {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            navigation.dispatchKeyEvent(event, original::dispatchKeyEvent)
    }
}
