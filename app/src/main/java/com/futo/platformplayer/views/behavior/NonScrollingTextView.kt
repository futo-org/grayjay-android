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
    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    override fun scrollTo(x: Int, y: Int) {
        //do nothing
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val action = event?.action
        Logger.i(TAG, "onTouchEvent (action = $action)");

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            val x = event.x.toInt()
            val y = event.y.toInt()

            val layout: Layout? = this.layout
            if (layout != null) {
                val line = layout.getLineForVertical(y)
                val offset = layout.getOffsetForHorizontal(line, x.toFloat())

                val text = this.text
                if (text is Spannable) {
                    val links = text.getSpans(offset, offset, URLSpan::class.java)
                    if (links.isNotEmpty()) {
                        runBlocking {
                            for (link in links) {
                                Logger.i(PlatformLinkMovementMethod.TAG) { "Link clicked '${link.url}'." };

                                val c = context;
                                if (c is MainActivity) {
                                    if (c.handleUrl(link.url)) {
                                        continue;
                                    }

                                    if (timestampRegex.matches(link.url)) {
                                        val tokens = link.url.split(':');

                                        var time_s = -1L;
                                        if (tokens.size == 2) {
                                            time_s = tokens[0].toLong() * 60 + tokens[1].toLong();
                                        } else if (tokens.size == 3) {
                                            time_s = tokens[0].toLong() * 60 * 60 + tokens[1].toLong() * 60 + tokens[2].toLong();
                                        }

                                        if (time_s != -1L) {
                                            MediaControlReceiver.onSeekToReceived.emit(time_s * 1000);
                                            continue;
                                        }
                                    }

                                    c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)));
                                }
                            }
                        }

                        return true
                    }
                }
            }
        }

        super.onTouchEvent(event)
        return false
    }

    companion object {
        private const val TAG = "NonScrollingTextView"
    }
}