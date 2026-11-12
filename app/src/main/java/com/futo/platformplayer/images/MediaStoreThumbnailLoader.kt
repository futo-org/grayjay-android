package com.futo.platformplayer.images

import android.content.ContentResolver
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.LocalUriFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.futo.platformplayer.states.StateApp
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException

@RequiresApi(Build.VERSION_CODES.Q)
class MediaStoreThumbnailLoader private constructor() : ModelLoader<String, InputStream> {

    override fun handles(model: String): Boolean = isMediaStoreAudioUri(model)

    private fun isMediaStoreAudioUri(uri: String): Boolean {
        try {
            val parsed = Uri.parse(uri);
            return ContentResolver.SCHEME_CONTENT == parsed.scheme
                    && MediaStore.AUTHORITY == parsed.authority
                    && "audio" in parsed.pathSegments
        }
        catch(ex: MalformedURLException) {
            return false;
        }
    }

    override fun buildLoadData(model: String, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        val diskCacheKey = ObjectKey(model)
        val resolver = StateApp.instance.contextOrNull?.contentResolver ?: return null;
        val fetcher = InputStreamFetcher(resolver, Uri.parse(model), width, height)
        return ModelLoader.LoadData(diskCacheKey, fetcher)
    }

    class InputStreamFactory() : ModelLoaderFactory<String, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, InputStream> = MediaStoreThumbnailLoader()

        override fun teardown() {
            // Do nothing.
        }
    }

    private class InputStreamFetcher(resolver: ContentResolver, uri: Uri, private val width: Int, private val height: Int) : LocalUriFetcher<InputStream>(resolver, uri) {

        override fun getDataClass(): Class<InputStream> = InputStream::class.java

        @Throws(FileNotFoundException::class)
        override fun loadResource(uri: Uri, contentResolver: ContentResolver): InputStream {
            val optimalSizeOptions = Bundle(1)
            optimalSizeOptions.putParcelable(ContentResolver.EXTRA_SIZE, Point(width, height))

            return contentResolver.openTypedAssetFile(uri, "image/*", optimalSizeOptions, null)
                ?.createInputStream()
                ?: throw FileNotFoundException("FileDescriptor is null for: $uri")
        }

        @Throws(IOException::class)
        override fun close(data: InputStream) {
            data.close()
        }
    }
}