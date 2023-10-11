package com.futo.platformplayer.api.media

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import androidx.core.graphics.drawable.toBitmap
import com.caverock.androidsvg.SVG
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.live.IPlatformLiveEvent
import com.futo.platformplayer.api.media.models.live.LiveEventComment
import com.futo.platformplayer.api.media.models.live.LiveEventDonation
import com.futo.platformplayer.api.media.models.live.LiveEventEmojis
import com.futo.platformplayer.api.media.platforms.js.models.JSLiveEventPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.BatchedTaskHandler
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.overlays.LiveChatOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LiveChatManager {
    private val _scope: CoroutineScope;
    private val _emojiCache: EmojiCache = EmojiCache();
    private val _pager: IPager<IPlatformLiveEvent>?;

    private val _history: ArrayList<IPlatformLiveEvent> = arrayListOf();

    private var _startCounter = 0;

    private val _followers: HashMap<Any, (List<IPlatformLiveEvent>) -> Unit> = hashMapOf();

    var viewCount: Long = 0
        private set;

    constructor(scope: CoroutineScope, pager: IPager<IPlatformLiveEvent>, initialViewCount: Long = 0) {
        _scope = scope;
        _pager = pager;
        viewCount = initialViewCount;
        handleEvents(listOf(LiveEventComment("SYSTEM", null, "Live chat is still under construction. While it is mostly functional, the experience still needs to be improved.\n")));
        handleEvents(pager.getResults());
    }

    fun start() {
        val counter = ++_startCounter;
        startLoop(counter);
    }

    fun stop() {
        _startCounter++;
    }

    fun getHistory(): List<IPlatformLiveEvent> {
        synchronized(_history) {
            return _history.toList();
        }
    }

    fun follow(tag: Any, eventHandler: (List<IPlatformLiveEvent>) -> Unit) {
        val before = synchronized(_history) {
            _history.toList();
        };
        synchronized(_followers) {
            _followers.put(tag, eventHandler);
        }
        eventHandler(before);
    }
    fun unfollow(tag: Any) {
        synchronized(_followers) {
            _followers.remove(tag);
        }
    }

    fun hasEmoji(emoji: String): Boolean {
        return _emojiCache.hasEmoji(emoji);
    }
    fun getEmoji(emoji: String, handler: (Drawable?)->Unit) {
        return _emojiCache.getEmojiDrawable(emoji, handler);
    }

    private fun startLoop(counter: Int) {
        _scope.launch(Dispatchers.IO) {
            try {
                while(_startCounter == counter) {
                    var nextInterval = 1000L;
                    try {
                        if(_pager == null || !_pager.hasMorePages())
                            return@launch;
                        _pager.nextPage();
                        val newEvents = _pager.getResults();
                        if(_pager is JSLiveEventPager)
                            nextInterval = _pager.nextRequest.coerceAtLeast(800).toLong();

                        if(newEvents.size > 0)
                            Logger.i(TAG, "New Live Events (${newEvents.size}) [${newEvents.map { it.type.name }.joinToString(", ")}]");
                        else
                            Logger.v(TAG, "No new Live Events");

                        _scope.launch(Dispatchers.Main) {
                            try {
                                handleEvents(newEvents);
                            } catch (e: Throwable) {
                                Logger.e(TAG, "Failed to handle new live events.", e);
                            }
                        }
                    }
                    catch(ex: Throwable) {
                        Logger.e(LiveChatOverlay.TAG, "Failed to load live events", ex);
                    }
                    delay(nextInterval);
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Live events loop crashed.", e);
            }
        }
    }
    fun handleEvents(events: List<IPlatformLiveEvent>) {
        for(event in events) {
            if(event is LiveEventEmojis)
                _emojiCache.setEmojis(event);
        }
        synchronized(_history) {
            _history.addAll(events);
        }
        val handlers = synchronized(_followers) { _followers.values.toList() };
        for(handler in handlers) {
            try {
                handler(events);
            }
            catch(ex: Throwable) {
                Logger.e(TAG, "Failed to chat handle events on handler", ex);
            }
        }
    }

    companion object {
        val TAG = "LiveChatManager";
    }


    class EmojiCache {
        private val _cache_lock = Object();
        private val _cache_drawables = HashMap<String, Drawable>(); //TODO: Replace with LRUCache
        private val _cache_urls = HashMap<String, String>();

        private val _client = ManagedHttpClient();
        private val _download_drawable =
            BatchedTaskHandler<String, Drawable?>(StateApp.instance.scope, { url ->
                val req = _client.get(url);
                if (req.isOk && req.body != null) {
                    val contentType = req.body.contentType();
                    return@BatchedTaskHandler when (contentType?.toString()) {
                        //TODO: Get scaling to work with drawable (no bitmap conversion)
                        "image/svg+xml" -> {
                            val bitmap = PictureDrawable(SVG.getFromString(req.body.string()).renderToPicture(150, 150)).toBitmap(150,150,null);
                            return@BatchedTaskHandler BitmapDrawable(bitmap)
                        };
                        //"image/svg+xml" -> PictureDrawable(SVG.getFromString(req.body.string()).renderToPicture(15, 15));
                        else -> {
                            val bytes = req.body.bytes();
                            BitmapDrawable(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                        }
                    }
                } else {
                    Logger.w(TAG, "Failed to request emoji (${req.code}) [${req.url}]");
                    return@BatchedTaskHandler null;
                }
            }, { url ->
                synchronized(_cache_lock) {
                    return@synchronized _cache_drawables[url];
                }
            }, { url, drawable ->
                if (drawable != null)
                    synchronized(_cache_lock) {
                        _cache_drawables[url] = drawable;
                    }
            });

        fun setEmojis(emojis: LiveEventEmojis) {
            synchronized(_cache_lock) {
                for(emoji in emojis.emojis) {
                    _cache_urls[emoji.key] = emoji.value;
                }
            }
        }

        fun hasEmoji(emoji: String): Boolean {
            synchronized(_cache_lock) {
                return _cache_urls.containsKey(emoji);
            }
        }

        fun getEmojiDrawable(emoji: String, cb: (drawable: Drawable?)->Unit) {
            var drawable: Drawable? = null;
            var url: String? = null;
            synchronized(_cache_lock) {
                url = _cache_urls[emoji];
                if(url != null)
                    drawable = _cache_drawables[url];
            }
            if(drawable != null)
                cb(drawable);
            else if(url != null){
                Logger.i(TAG, "Requesting [${emoji}] (${url})");
                _download_drawable.execute(url!!).invokeOnCompletion {
                    if(it == null) {
                        Logger.i(TAG, "Found emoji [${emoji}]")
                        cb(synchronized(_cache_lock) { _cache_drawables[url] });
                    }
                    else {
                        Logger.w(TAG, "Exception on emoji load [${emoji}]: ${it.message}", it);
                    }
                }
            }
        }
        fun getEmojiUrl(emoji: String): String? {
            synchronized(_cache_lock) {
                return _cache_urls[emoji];
            }
        }
    }

}