package com.futo.platformplayer.views.video

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.TimeBar
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.futo.platformplayer.R
import com.futo.platformplayer.views.video.thumbnails.SeekThumbnailLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class SeekThumbnailView : FrameLayout, TimeBar.OnScrubListener {
    private val _controller: PlayerControlView;
    private val _timeBar: DefaultTimeBar;
    private val _thumbnails: SeekThumbnailLoader;
    private val _durationProvider: () -> Long;

    private val _thumbnail: ImageView;
    private val _imageTarget: BitmapImageViewTarget;
    private val _barBounds = Rect();
    private val _visibleBounds = Rect();
    private val _edgeMargin = dp(8);
    private val _scrubberInset = (context.getDrawable(R.drawable.player_thumb)?.intrinsicWidth ?: 0) / 2;
    private var _scrubFraction = 0f;
    private val _requestedPosition = MutableStateFlow<Long?>(null);
    private var _thumbnailJob: Job? = null;

    private constructor(controller: PlayerControlView, thumbnails: SeekThumbnailLoader, durationProvider: () -> Long) : super(controller.context) {
        _controller = controller;
        _timeBar = controller.findViewById(R.id.time_progress);
        _thumbnails = thumbnails;
        _durationProvider = durationProvider;

        LayoutInflater.from(context).inflate(R.layout.view_seek_thumbnail, this, true);
        _thumbnail = findViewById(R.id.seek_thumbnail_image);
        _imageTarget = object : BitmapImageViewTarget(_thumbnail) {
            override fun setResource(resource: Bitmap?) {
                _thumbnail.scaleType = if(resource == null) ImageView.ScaleType.CENTER else ImageView.ScaleType.CENTER_CROP;
                super.setResource(resource);
            }
        };
        visibility = INVISIBLE;
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow();
        _timeBar.addListener(this);
        _thumbnails.attach();
        _thumbnailJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            combine(_requestedPosition, _thumbnails.revision) { position, revision ->
                position?.let { _thumbnails.requestPosition(it) } to revision;
            }.distinctUntilChanged().collectLatest { (position, revision) ->
                if(!_thumbnails.hasProvider) {
                    hidePreview();
                    return@collectLatest;
                }
                clearThumbnail();
                if(position != null) {
                    val request = _thumbnails.getThumbnail(position);
                    if(_requestedPosition.value?.let { _thumbnails.requestPosition(it) } == position &&
                        _thumbnails.revision.value == revision && visibility == VISIBLE) {
                        request?.request(Glide.with(_thumbnail))
                            ?.into(_imageTarget);
                    }
                }
            };
        };
    }

    override fun onDetachedFromWindow() {
        _timeBar.removeListener(this);
        _thumbnailJob?.cancel();
        _thumbnailJob = null;
        _thumbnails.detach();
        hidePreview();
        super.onDetachedFromWindow();
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility);
        if(changedView !== this && visibility != VISIBLE) {
            hidePreview();
        }
    }

    override fun onScrubStart(timeBar: TimeBar, position: Long) {
        showPreview(position);
    }

    override fun onScrubMove(timeBar: TimeBar, position: Long) {
        showPreview(position);
    }

    override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
        hidePreview();
    }

    private fun showPreview(positionMs: Long) {
        val durationMs = _durationProvider();
        if(!_thumbnails.hasProvider || durationMs <= 0 || !_timeBar.isShown) {
            hidePreview();
            return;
        }

        val previewPositionMs = positionMs.coerceIn(0, durationMs);
        _scrubFraction = (previewPositionMs.toDouble() / durationMs).toFloat();
        if(_requestedPosition.value == null)
            clearThumbnail();
        visibility = VISIBLE;
        // The exact duration has no frame; preview the final millisecond at the right edge.
        _requestedPosition.value = previewPositionMs.coerceAtMost(durationMs - 1);
        positionPreview();
    }

    private fun clearThumbnail() {
        Glide.with(context.applicationContext).clear(_imageTarget);
        _thumbnail.scaleType = ImageView.ScaleType.CENTER;
        _thumbnail.setImageResource(0);
    }

    private fun hidePreview() {
        visibility = INVISIBLE;
        _requestedPosition.value = null;
        clearThumbnail();
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom);
        if(visibility == VISIBLE)
            positionPreview();
    }

    private fun positionPreview() {
        if(width == 0 || !_controller.getLocalVisibleRect(_visibleBounds))
            return;

        _timeBar.getDrawingRect(_barBounds);
        _controller.offsetDescendantRectToMyCoords(_timeBar, _barBounds);
        val start = _barBounds.left + _timeBar.paddingLeft + _scrubberInset;
        val end = _barBounds.right - _timeBar.paddingRight - _scrubberInset;
        val minX = (_visibleBounds.left + _edgeMargin).toFloat();
        val maxX = (_visibleBounds.right - _edgeMargin - width).toFloat().coerceAtLeast(minX);
        x = (start + (end - start) * _scrubFraction - width / 2f).coerceIn(minX, maxX);
        y = (_barBounds.top - dp(18) - height).toFloat()
            .coerceAtLeast((_visibleBounds.top + _edgeMargin).toFloat());
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt();
    }

    companion object {
        internal fun attachTo(controller: PlayerControlView, thumbnails: SeekThumbnailLoader = SeekThumbnailLoader(), durationProvider: () -> Long) {
            val preview = SeekThumbnailView(controller, thumbnails, durationProvider);
            controller.addView(preview, LayoutParams(preview.dp(160), LayoutParams.WRAP_CONTENT));
        }
    }
}
