package com.futo.platformplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Looper
import android.os.OperationCanceledException
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Display
import android.view.View
import android.view.WindowInsetsController
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.PlatformVideoWithTime
import com.futo.platformplayer.others.PlatformLinkMovementMethod
import com.futo.platformplayer.states.StatePlatform
import org.json.JSONArray
import org.json.JSONObject
import userpackage.Protocol
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.min

private val _allowedCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ";
fun getRandomString(sizeOfRandomString: Int): String {
    val random = Random();
    val sb = StringBuilder(sizeOfRandomString);
    for (i in 0 until sizeOfRandomString)
        sb.append(_allowedCharacters[random.nextInt(_allowedCharacters.length)]);
    return sb.toString()
}

fun getRandomStringRandomLength(minLength: Int, maxLength: Int): String {
    if (maxLength == minLength)
        return getRandomString(minLength);
    return getRandomString(ThreadLocalRandom.current().nextInt(minLength, maxLength));
}

fun findNonRuntimeException(ex: Throwable?): Throwable? {
    if(ex == null)
        return null;
    if(ex is java.lang.RuntimeException)
        return findNonRuntimeException(ex.cause)
    else
        return ex;
}

fun ensureNotMainThread() {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Logger.e("Utility", "Throwing exception because a function that should not be called on main thread, is called on main thread")
        throw IllegalStateException("Cannot run on main thread")
    }
}

private val _regexHexColor = Regex("(#[a-fA-F0-9]{8})|(#[a-fA-F0-9]{6})|(#[a-fA-F0-9]{3})");
fun String.isHexColor(): Boolean {
    return _regexHexColor.matches(this);
}

fun IPlatformVideo.withTimestamp(sec: Long) =  PlatformVideoWithTime(this, sec);


fun loadBitmap(url: String): Bitmap {
    try {
        val client = ManagedHttpClient();
        val response = client.get(url);
        if (response.isOk && response.body != null) {
            val bitmapStream = response.body.byteStream();
            val bitmap = BitmapFactory.decodeStream(bitmapStream);
            return bitmap;
        } else {
            throw Exception("Failed to find data at URL.");
        }
    } catch (e: Throwable) {
        Logger.w("Utility", "Exception thrown while downloading bitmap.", e);
        throw e;
    }
}

fun TextView.setPlatformPlayerLinkMovementMethod(context: Context) {
    this.movementMethod = PlatformLinkMovementMethod(context);
}

fun InputStream.copyToOutputStream(outputStream: OutputStream, isCancelled: (() -> Boolean)? = null) {
    val buffer = ByteArray(16384);
    var n: Int;
    var total = 0;

    while (read(buffer).also { n = it } >= 0) {
        if (isCancelled != null && isCancelled()) {
            throw OperationCanceledException("Copy stream was cancelled.");
        }

        total += n;
        outputStream.write(buffer, 0, n);
    }
}

fun InputStream.copyToOutputStream(inputStreamLength: Long, outputStream: OutputStream, onProgress: (Float) -> Unit) {
    val buffer = ByteArray(16384);
    var n: Int;
    var total = 0;
    val inputStreamLengthFloat = inputStreamLength.toFloat();

    while (read(buffer).also { n = it } >= 0) {
        total += n;
        outputStream.write(buffer, 0, n);
        onProgress.invoke(total.toFloat() / inputStreamLengthFloat);
    }
}

fun Activity.setNavigationBarColorAndIcons() {
    window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    } else {
        val decorView = window.decorView;
        var systemUiVisibility = decorView.systemUiVisibility;
        systemUiVisibility = systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv();
        decorView.systemUiVisibility = systemUiVisibility;
    }
}

fun Int.dp(resources: Resources): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()
}

fun Int.sp(resources: Resources): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this.toFloat(), resources.displayMetrics).toInt()
}

fun File.share(context: Context) {
    val uri = FileProvider.getUriForFile(context, context.resources.getString(R.string.authority), this);

    val shareIntent = Intent();
    shareIntent.action = Intent.ACTION_SEND;
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    shareIntent.setDataAndType(uri, context.contentResolver.getType(uri));
    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);

    val chooserIntent = Intent.createChooser(shareIntent, "Share");
    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(chooserIntent);
}