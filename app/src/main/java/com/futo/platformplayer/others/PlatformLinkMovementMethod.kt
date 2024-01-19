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

class PlatformLinkMovementMethod : LinkMovementMethod {
    private val _context: Context;

    constructor(context: Context) : super() {
        _context = context;
    }

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val action = event.action;
        if (action == MotionEvent.ACTION_UP) {
            val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX;
            val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY;

            val layout = widget.layout;
            val line = layout.getLineForVertical(y);
            val off = layout.getOffsetForHorizontal(line, x.toFloat());
            val links = buffer.getSpans(off, off, URLSpan::class.java);

            if (links.isNotEmpty()) {
                runBlocking {
                    for (link in links) {
                        Logger.i(TAG) { "Link clicked '${link.url}'." };

                        if (_context is MainActivity) {
                            if (_context.handleUrl(link.url)) {
                                continue;
                            }

                            if (timestampRegex.matches(link.url)) {
                                val tokens = link.url.split(':');

                                var time_s = -1L;
                                if (tokens.size == 2) {
                                    time_s = tokens[0].toLong() * 60 + tokens[1].toLong();
                                } else if (tokens.size == 3) {
                                    time_s =
                                        tokens[0].toLong() * 60 * 60 + tokens[1].toLong() * 60 + tokens[2].toLong();
                                }

                                if (time_s != -1L) {
                                    MediaControlReceiver.onSeekToReceived.emit(time_s * 1000);
                                    continue;
                                }
                            }
                        }


                        _context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)));
                    }
                }

                return true;
            }
        }

        return super.onTouchEvent(widget, buffer, event);
    }

    companion object {
        val TAG = "PlatformLinkMovementMethod";
    }
}