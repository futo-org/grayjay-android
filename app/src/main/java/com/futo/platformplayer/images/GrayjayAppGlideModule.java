package com.futo.platformplayer.images;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

@GlideModule
public class GrayjayAppGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        Log.i("GrayjayAppGlideModule", "registerComponents called");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            registry.prepend(String.class, InputStream.class, new MediaStoreThumbnailLoader.InputStreamFactory());
        }
    }
}