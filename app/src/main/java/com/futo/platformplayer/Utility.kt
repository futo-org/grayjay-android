package com.futo.platformplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.icu.util.Output
import android.os.Build
import android.os.Looper
import android.os.OperationCanceledException
import android.util.TypedValue
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.PlatformMultiClientPool
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.PlatformVideoWithTime
import com.futo.platformplayer.others.PlatformLinkMovementMethod
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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

fun warnIfMainThread(context: String) {
    if(BuildConfig.DEBUG && Looper.myLooper() == Looper.getMainLooper())
        Logger.w(V8Plugin.TAG, "JAVASCRIPT ON MAIN THREAD\nAt: ${context}\n" + Thread.currentThread().stackTrace.joinToString { it.toString() });
}

fun ensureNotMainThread() {
    val isMainLooper = try {
        Looper.myLooper() == Looper.getMainLooper()
    } catch (e: Throwable) {
        //Ignore, for unit tests where its not mocked
        false
    }

    if (isMainLooper) {
        Logger.e("Utility", "Throwing exception because a function that should not be called on main thread, is called on main thread")
        throw IllegalStateException("Cannot run on main thread")
    }
}

private val _regexUrl = Regex("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=]*)");
fun String.isHttpUrl(): Boolean {
    return _regexUrl.matchEntire(this) != null;
}


private val _regexHexColor = Regex("(#[a-fA-F0-9]{8})|(#[a-fA-F0-9]{6})|(#[a-fA-F0-9]{3})");
fun String.isHexColor(): Boolean {
    return _regexHexColor.matches(this);
}

fun IPlatformClient.fromPool(pool: PlatformMultiClientPool) = pool.getClientPooled(this);

fun IPlatformVideo.withTimestamp(sec: Long) =  PlatformVideoWithTime(this, sec);

fun DocumentFile.getInputStream(context: Context) = context.contentResolver.openInputStream(this.uri);
fun DocumentFile.getOutputStream(context: Context) = context.contentResolver.openOutputStream(this.uri);
fun DocumentFile.copyTo(context: Context, file: DocumentFile) = this.getInputStream(context).use { input ->
    file.getOutputStream(context)?.use { output -> input?.copyTo(output) }
};
fun DocumentFile.readBytes(context: Context) = this.getInputStream(context).use { input -> input?.readBytes() };
fun DocumentFile.writeBytes(context: Context, byteArray: ByteArray) = context.contentResolver.openOutputStream(this.uri)?.use {
    it.write(byteArray);
    it.flush();
};

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

@Suppress("DEPRECATION")
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

fun DocumentFile.share(context: Context) {
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

fun String.decodeUnicode(): String {
    val sb = StringBuilder()
    var i = 0

    while (i < this.length) {
        var ch = this[i]

        if (ch == '\\' && i + 1 < this.length) {
            i++
            ch = this[i]
            when (ch) {
                '\\' -> sb.append('\\')
                't' -> sb.append('\t')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                'f' -> sb.append('\u000C')
                'b' -> sb.append('\b')
                '"' -> sb.append('"')
                '\'' -> sb.append('\'')
                'u' -> {
                    if (i + 4 < this.length) {
                        val unicode = this.substring(i + 1, i + 5)
                        try {
                            sb.append(unicode.toInt(16).toChar())
                        } catch (e: NumberFormatException) {
                            throw IOException("Invalid Unicode sequence: $unicode")
                        }
                        i += 4
                    } else {
                        throw IOException("Incomplete Unicode sequence")
                    }
                }
                in '0'..'7' -> {
                    val end = (i + 3).coerceAtMost(this.length)
                    val octal = this.substring(i, end).takeWhile { it in '0'..'7' }
                    try {
                        sb.append(octal.toInt(8).toChar())
                        i += octal.length - 1
                    } catch (e: NumberFormatException) {
                        throw IOException("Invalid Octal sequence: $octal")
                    }
                }
                else -> sb.append(ch)
            }
        } else {
            sb.append(ch)
        }

        i++
    }
    return sb.toString()
}

fun <T> smartMerge(targetArr: List<T>, toMerge: List<T>) : List<T>{
    val missingToMerge = toMerge.filter { !targetArr.contains(it) }.toList();
    val newArrResult = targetArr.toMutableList();

    for(missing in missingToMerge) {
        val newIndex = findNewIndex(toMerge, newArrResult, missing);
        newArrResult.add(newIndex, missing);
    }

    return newArrResult;
}
fun <T> findNewIndex(originalArr: List<T>, newArr: List<T>, item: T): Int{
    var originalIndex = originalArr.indexOf(item);
    var newIndex = -1;

    for(i in originalIndex-1 downTo 0) {
        val previousItem = originalArr[i];
        val indexInNewArr = newArr.indexOfFirst { it == previousItem };
        if(indexInNewArr >= 0) {
            newIndex = indexInNewArr + 1;
            break;
        }
    }
    if(newIndex < 0) {
        for(i in originalIndex+1 until originalArr.size) {
            val previousItem = originalArr[i];
            val indexInNewArr = newArr.indexOfFirst { it == previousItem };
            if(indexInNewArr >= 0) {
                newIndex = indexInNewArr - 1;
                break;
            }
        }
    }
    if(newIndex < 0)
        return newArr.size;
    else
        return newIndex;
}

fun ByteBuffer.toUtf8String(): String {
    val remainingBytes = ByteArray(remaining())
    get(remainingBytes)
    return String(remainingBytes, Charsets.UTF_8)
}

fun generateReadablePassword(length: Int): String {
    val validChars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
    val secureRandom = SecureRandom()
    val randomBytes = ByteArray(length)
    secureRandom.nextBytes(randomBytes)
    val sb = StringBuilder(length)
    for (byte in randomBytes) {
        val index = (byte.toInt() and 0xFF) % validChars.length
        sb.append(validChars[index])
    }
    return sb.toString()
}

fun ByteArray.toGzip(): ByteArray {
    if (this == null || this.isEmpty()) return ByteArray(0)

    val gzipTimeStart = OffsetDateTime.now();

    val outputStream = ByteArrayOutputStream()
    GZIPOutputStream(outputStream).use { gzip ->
        gzip.write(this)
    }
    val result = outputStream.toByteArray();
    Logger.i("Utility", "Gzip compression time: ${gzipTimeStart.getNowDiffMiliseconds()}ms");
    return result;
}

fun ByteArray.fromGzip(): ByteArray {
    if (this == null || this.isEmpty()) return ByteArray(0)

    val inputStream = ByteArrayInputStream(this)
    val outputStream = ByteArrayOutputStream()

    GZIPInputStream(inputStream).use { gzip ->
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (gzip.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
    }
    return outputStream.toByteArray()
}

fun findCandidateAddresses(): List<InetAddress> {
    val candidates = NetworkInterface.getNetworkInterfaces()
        .toList()
        .asSequence()
        .filter(::isUsableInterface)
        .flatMap { nif ->
            nif.interfaceAddresses
                .asSequence()
                .mapNotNull { ia ->
                    ia.address.takeIf(::isUsableAddress)?.let { addr ->
                        nif to ia
                    }
                }
        }
        .toList()

    return candidates
        .sortedWith(
            compareBy<Pair<NetworkInterface, InterfaceAddress>>(
                { addressScore(it.second.address) },
                { interfaceScore(it.first) },
                { -it.second.networkPrefixLength.toInt() },
                { -it.first.mtu }
            )
        ).map { it.second.address }
}

fun findPreferredAddress(): InetAddress? {
    val candidates = NetworkInterface.getNetworkInterfaces()
        .toList()
        .asSequence()
        .filter(::isUsableInterface)
        .flatMap { nif ->
            nif.interfaceAddresses
                .asSequence()
                .mapNotNull { ia ->
                    ia.address.takeIf(::isUsableAddress)?.let { addr ->
                        nif to ia
                    }
                }
        }
        .toList()

    return candidates
        .minWithOrNull(
            compareBy<Pair<NetworkInterface, InterfaceAddress>>(
                { addressScore(it.second.address) },
                { interfaceScore(it.first) },
                { -it.second.networkPrefixLength.toInt() },
                { -it.first.mtu }
            )
        )?.second?.address
}

private fun isUsableInterface(nif: NetworkInterface): Boolean {
    val name = nif.name.lowercase()
    return try {
        // must be up, not loopback/virtual/PtP, have a MAC, not Docker/tun/etc.
        nif.isUp
            && !nif.isLoopback
            && !nif.isPointToPoint
            && !nif.isVirtual
            && !name.startsWith("docker")
            && !name.startsWith("veth")
            && !name.startsWith("br-")
            && !name.startsWith("virbr")
            && !name.startsWith("vmnet")
            && !name.startsWith("tun")
            && !name.startsWith("tap")
    } catch (e: SocketException) {
        false
    }
}

private fun isUsableAddress(addr: InetAddress): Boolean {
    return when {
        addr.isAnyLocalAddress -> false // 0.0.0.0 / ::
        addr.isLoopbackAddress -> false
        addr.isLinkLocalAddress -> false // 169.254.x.x or fe80::/10
        addr.isMulticastAddress -> false
        else -> true
    }
}

private fun interfaceScore(nif: NetworkInterface): Int {
    val name = nif.name.lowercase()
    return when {
        name.matches(Regex("^(eth|enp|eno|ens|em)\\d+")) -> 0
        name.startsWith("eth") || name.contains("ethernet") -> 0
        name.matches(Regex("^(wlan|wlp)\\d+")) -> 1
        name.contains("wi-fi") || name.contains("wifi") -> 1
        else -> 2
    }
}

fun addressScore(addr: InetAddress): Int {
    return when (addr) {
        is Inet4Address -> {
            val octets = addr.address.map { it.toInt() and 0xFF }
            when {
                octets[0] == 10 -> 0  // 10/8
                octets[0] == 192 && octets[1] == 168 -> 0  // 192.168/16
                octets[0] == 172 && octets[1] in 16..31 -> 0  // 172.16–31/12
                else -> 1  // public IPv4
            }
        }
        is Inet6Address -> {
            // ULA (fc00::/7) vs global vs others
            val b0 = addr.address[0].toInt() and 0xFF
            when {
                (b0 and 0xFE) == 0xFC -> 2  // ULA
                (b0 and 0xE0) == 0x20 -> 3  // global
                else -> 4
            }
        }
        else -> Int.MAX_VALUE
    }
}

fun <T> Enumeration<T>.toList(): List<T> = Collections.list(this)