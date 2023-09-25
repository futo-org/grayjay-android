package com.futo.platformplayer.images;

import android.content.Context;
import android.util.Log;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;
import java.nio.ByteBuffer;

@GlideModule
public class GrayjayAppGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        Log.i("GrayjayAppGlideModule", "registerComponents called");
        registry.prepend(String.class, ByteBuffer.class, new PolycentricModelLoader.Factory());
    }
}