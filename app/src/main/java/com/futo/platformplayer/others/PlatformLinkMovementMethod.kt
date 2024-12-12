package com.futo.platformplayer.others

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.MotionEvent
import android.widget.TextView
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.receivers.MediaControlReceiver
import com.futo.platformplayer.timestampRegex
import kotlinx.coroutines.runBlocking

class PlatformLinkMovementMethod(private val _context: Context) : LinkMovementMethod() {

    private var pressedLinks: Array<URLSpan>? = null
    private var linkPressed = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = 20

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val action = event.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val links = findLinksAtTouchPosition(widget, buffer, event)
                if (links.isNotEmpty()) {
                    pressedLinks = links
                    linkPressed = true
                    downX = event.x
                    downY = event.y
                    widget.parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                } else {
                    linkPressed = false
                    pressedLinks = null
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (linkPressed) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        linkPressed = false
                        pressedLinks = null
                        widget.parent?.requestDisallowInterceptTouchEvent(false)
                        return false
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (linkPressed && pressedLinks != null) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (Math.abs(dx) <= touchSlop && Math.abs(dy) <= touchSlop && isTouchInside(widget, event)) {
                        runBlocking {
                            for (link in pressedLinks!!) {
                                Logger.i(TAG) { "Link clicked '${link.url}'." }

                                if (_context is MainActivity) {
                                    if (_context.handleUrl(link.url)) continue
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
                                }
                                _context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                            }
                        }
                        pressedLinks = null
                        linkPressed = false
                        return true
                    } else {
                        pressedLinks = null
                        linkPressed = false
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                linkPressed = false
                pressedLinks = null
            }
        }

        return false
    }

    private fun findLinksAtTouchPosition(widget: TextView, buffer: Spannable, event: MotionEvent): Array<URLSpan> {
        val x = (event.x - widget.totalPaddingLeft + widget.scrollX).toInt()
        val y = (event.y - widget.totalPaddingTop + widget.scrollY).toInt()

        val layout = widget.layout ?: return emptyArray()
        val line = layout.getLineForVertical(y)
        val off = layout.getOffsetForHorizontal(line, x.toFloat())
        return buffer.getSpans(off, off, URLSpan::class.java)
    }

    private fun isTouchInside(widget: TextView, event: MotionEvent): Boolean {
        return event.x >= 0 && event.x <= widget.width && event.y >= 0 && event.y <= widget.height
    }

    companion object {
        const val TAG = "PlatformLinkMovementMethod"
    }
}
