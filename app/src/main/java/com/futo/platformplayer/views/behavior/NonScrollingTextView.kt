package com.futo.platformplayer.views.behavior

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Layout
import android.text.Spannable
import android.text.style.URLSpan
import android.util.AttributeSet
import android.view.MotionEvent
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.others.PlatformLinkMovementMethod
import com.futo.platformplayer.receivers.MediaControlReceiver
import com.futo.platformplayer.timestampRegex
import kotlinx.coroutines.runBlocking

class NonScrollingTextView : androidx.appcompat.widget.AppCompatTextView {
    private var _lastTouchedLinks: Array<URLSpan>? = null
    private var downX = 0f
    private var downY = 0f
    private var linkPressed = false
    private val touchSlop = 20

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    override fun scrollTo(x: Int, y: Int) {
        // do nothing
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val action = event?.actionMasked
        if (event == null) return super.onTouchEvent(event)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x.toInt()
                val y = event.y.toInt()

                val layout: Layout? = this.layout
                if (layout != null && this.text is Spannable) {
                    val offset = layout.getOffsetForHorizontal(layout.getLineForVertical(y), x.toFloat())
                    val text = this.text as Spannable
                    val links = text.getSpans(offset, offset, URLSpan::class.java)
                    if (links.isNotEmpty()) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        _lastTouchedLinks = links
                        downX = event.x
                        downY = event.y
                        linkPressed = true
                        return true
                    } else {
                        linkPressed = false
                        _lastTouchedLinks = null
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (linkPressed) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        linkPressed = false
                        _lastTouchedLinks = null
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return false
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (linkPressed && _lastTouchedLinks != null) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (Math.abs(dx) <= touchSlop && Math.abs(dy) <= touchSlop && isTouchInside(event)) {
                        runBlocking {
                            for (link in _lastTouchedLinks!!) {
                                Logger.i(PlatformLinkMovementMethod.TAG) { "Link clicked '${link.url}'." }
                                val c = context
                                if (c is MainActivity) {
                                    if (c.handleUrl(link.url)) continue
                                    if (timestampRegex.matches(link.url)) {
                                        val tokens = link.url.split(':')
                                        var time_s = -1L
                                        when (tokens.size) {
                                            2 -> time_s = tokens[0].toLong() * 60 + tokens[1].toLong()
                                            3 -> time_s = tokens[0].toLong() * 3600 +
                                                    tokens[1].toLong() * 60 +
                                                    tokens[2].toLong()
                                        }
                                        if (time_s != -1L) {
                                            MediaControlReceiver.onSeekToReceived.emit(time_s * 1000)
                                            continue
                                        }
                                    }
                                    c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                                } else {
                                    c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                                }
                            }
                        }
                        _lastTouchedLinks = null
                        linkPressed = false
                        return true
                    } else {
                        linkPressed = false
                        _lastTouchedLinks = null
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                linkPressed = false
                _lastTouchedLinks = null
            }
        }

        return false
    }

    private fun isTouchInside(event: MotionEvent): Boolean {
        return event.x >= 0 && event.x <= width && event.y >= 0 && event.y <= height
    }

    companion object {
        private const val TAG = "NonScrollingTextView"
    }
}
