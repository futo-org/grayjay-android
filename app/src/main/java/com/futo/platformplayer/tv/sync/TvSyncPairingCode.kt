package com.futo.platformplayer.tv.sync

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.sync.internal.SyncDeviceInfo
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.net.NetworkInterface
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object TvSyncPairingCode {
    fun url(publicKey: String, pairingCode: String): String {
        val addresses = NetworkInterface.getNetworkInterfaces()
            .toList()
            .flatMap { it.inetAddresses.toList() }
            .filter { !it.isLoopbackAddress && it.address.size == 4 }
            .mapNotNull { it.hostAddress }
            .distinct()
        val info = SyncDeviceInfo(publicKey, addresses.toTypedArray(), StateSync.PORT, pairingCode)
        val encoded = Base64.encodeToString(
            Json.encodeToString(info).toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return "grayjay://sync/$encoded"
    }

    fun bitmap(url: String, size: Int): Bitmap {
        val matrix = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
    }
}
