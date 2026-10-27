package com.futo.platformplayer.views.video.thumbnails

import androidx.annotation.MainThread
import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal class SeekThumbnailLoader {
    private data class Provider(val source: ISeekThumbnailProvider, val resolutionMs: Long)
    private data class Loaded(val model: Any, val frames: List<SeekThumbnailFrame>?, val positionMs: Long)

    private val _scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate);
    private val _revisionState = MutableStateFlow(0L);
    val revision = _revisionState.asStateFlow();

    private var _provider: Provider? = null;
    private var _pending: Deferred<Unit>? = null;
    private var _loaded: Loaded? = null;
    private var _lastAttemptMs: Long? = null;
    private var _attachedViews = 0;

    val hasProvider: Boolean
        get() {
            return _provider != null;
        }

    fun setProvider(provider: ISeekThumbnailProvider?) {
        val replacement = provider?.let {
            val resolution = it.seekResolutionMs;
            require(resolution > 0) { "Seek resolution must be positive" };
            Provider(it, resolution.toLong());
        };
        clearCache();
        _provider = replacement;
        _revisionState.value++;
    }

    fun requestPosition(positionMs: Long): Long {
        val resolution = _provider?.resolutionMs ?: 1L;
        val position = positionMs.coerceAtLeast(0);
        return position - position % resolution;
    }

    fun attach() {
        _attachedViews++;
    }

    fun detach() {
        check(_attachedViews > 0);
        if(--_attachedViews == 0)
            clearCache();
    }

    suspend fun getThumbnail(positionMs: Long): SeekThumbnailRequest? {
        return withContext(Dispatchers.Main.immediate) {
            val current = _provider ?: return@withContext null;
            val position = requestPosition(positionMs);
            while(_provider === current) {
                _loaded?.let { cached ->
                    val frames = cached.frames;
                    if(frames == null && cached.positionMs == position)
                        return@withContext SeekThumbnailRequest(cached.model);
                    val frame = frames?.firstOrNull { it.contains(position, current.resolutionMs) };
                    if(frame != null)
                        return@withContext SeekThumbnailRequest(cached.model, frame);
                };
                if(_lastAttemptMs == position)
                    return@withContext null;

                val request = _pending ?: _scope.async(start = CoroutineStart.LAZY) {
                    load(current, position);
                }.also { _pending = it; };
                try {
                    request.await();
                }
                finally {
                    if(_pending === request && request.isCompleted)
                        _pending = null;
                }
            }
            null;
        };
    }

    private suspend fun load(current: Provider, positionMs: Long) {
        try {
            Logger.i("SeekThumbnailLoader", "getSeekThumbnail ${positionMs}");
            val thumbnail = withContext(Dispatchers.IO) { current.source.getThumbnail(positionMs); };
            val result = when(thumbnail) {
                is SeekThumbnail.Url -> Loaded(thumbnail.url, thumbnail.frames, positionMs);
                is SeekThumbnail.Image -> Loaded(thumbnail.bitmap, thumbnail.frames, positionMs);
                null -> null;
            };
            currentCoroutineContext().ensureActive();
            if(_provider === current && result != null)
                _loaded = result;
        }
        catch (ex: CancellationException) {
            throw ex;
        }
        catch (ex: Exception) {
            Logger.w("SeekThumbnailLoader", "Failed to get seek thumbnail", ex);
        }
        currentCoroutineContext().ensureActive();
        if(_provider === current)
            _lastAttemptMs = positionMs;
    }

    private fun clearCache() {
        _pending?.cancel();
        _pending = null;
        _loaded = null;
        _lastAttemptMs = null;
    }
}
