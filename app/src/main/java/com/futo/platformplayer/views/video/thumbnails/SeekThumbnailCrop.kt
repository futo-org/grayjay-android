package com.futo.platformplayer.views.video.thumbnails

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.ByteBuffer
import java.security.MessageDigest

internal class SeekThumbnailCrop : BitmapTransformation {
    val x: Int;
    val y: Int;
    val width: Int;
    val height: Int;

    constructor(x: Int, y: Int, width: Int, height: Int) : super() {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        require(x <= toTransform.width - width && y <= toTransform.height - height) {
            "Thumbnail rectangle exceeds image bounds";
        };
        val result = pool.get(width, height, Bitmap.Config.ARGB_8888);
        result.setHasAlpha(toTransform.hasAlpha());
        val canvas = Canvas(result);
        canvas.drawBitmap(toTransform, Rect(x, y, x + width, y + height), Rect(0, 0, width, height), null);
        canvas.setBitmap(null);
        return result;
    }

    override fun equals(other: Any?): Boolean {
        return other is SeekThumbnailCrop &&
            x == other.x && y == other.y && width == other.width && height == other.height;
    }

    override fun hashCode(): Int {
        return ((x * 31 + y) * 31 + width) * 31 + height;
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("com.futo.platformplayer.SeekThumbnailCrop.v1".toByteArray(Charsets.UTF_8));
        messageDigest.update(ByteBuffer.allocate(16).putInt(x).putInt(y).putInt(width).putInt(height).array());
    }
}
