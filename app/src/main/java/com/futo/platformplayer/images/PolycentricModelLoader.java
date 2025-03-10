package com.futo.platformplayer.images;

import static com.futo.platformplayer.Extensions_PolycentricKt.getDataLinkFromUrl;

import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import com.futo.polycentric.core.ApiMethods;

import kotlin.Unit;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.Dispatchers;
import userpackage.Protocol;

import java.lang.Exception;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;

public class PolycentricModelLoader implements ModelLoader<String, ByteBuffer> {

    @Override
    public boolean handles(String model) {
        return model.startsWith("polycentric://");
    }

    @Override
    public ModelLoader.LoadData<ByteBuffer> buildLoadData(@NonNull String model, int width, int height, @NonNull Options options) {
        return new ModelLoader.LoadData<ByteBuffer>(new ObjectKey(model), new Fetcher(model));
    }

    public static class Factory implements ModelLoaderFactory<String, ByteBuffer> {
        @NonNull
        @Override
        public ModelLoader<String, ByteBuffer> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new PolycentricModelLoader();
        }

        @Override
        public void teardown() { }
    }

    public static class Fetcher implements DataFetcher<ByteBuffer> {
        private final String _model;
        private Deferred<ByteBuffer> _deferred;

        public Fetcher(String model) {
            this._model = model;
        }

        @NonNull
        @Override
        public DataSource getDataSource() {
            return DataSource.REMOTE;
        }

        @Override
        public void loadData(@NonNull Priority priority, @NonNull DataFetcher.DataCallback<? super ByteBuffer> callback) {
            Log.i("PolycentricModelLoader", this._model);

            Protocol.URLInfoDataLink dataLink = getDataLinkFromUrl(_model);
            if (dataLink == null) {
                callback.onLoadFailed(new Exception("Data link cannot be null"));
                return;
            }

            _deferred = ApiMethods.Companion.getDataFromServerAndReassemble(CoroutineScopeKt.CoroutineScope(Dispatchers.getIO()), dataLink);
            _deferred.invokeOnCompletion(throwable -> {
                if (throwable != null) {
                    Log.e("PolycentricModelLoader", "getDataAsync failed throwable: " + throwable.toString());
                    callback.onLoadFailed(new Exception(throwable));
                    return Unit.INSTANCE;
                }

                Deferred<ByteBuffer> deferred = _deferred;
                if (deferred == null) {
                    Log.e("PolycentricModelLoader", "getDataAsync failed deferred is null");
                    callback.onLoadFailed(new Exception("Deferred is null"));
                    return Unit.INSTANCE;
                }

                ByteBuffer completed = deferred.getCompleted();
                if (completed != null) {
                    Log.e("PolycentricModelLoader", "getDataAsync success loaded " + completed.remaining() + " bytes");
                    callback.onDataReady(completed);
                } else {
                    Log.e("PolycentricModelLoader", "getDataAsync failed completed is null");
                    callback.onLoadFailed(new Exception("Completed is null"));
                }
                return Unit.INSTANCE;
            });
        }

        @Override
        public void cancel() {
            if (_deferred != null) {
                _deferred.cancel(new CancellationException("Cancelled by Fetcher."));
            }
        }

        @Override
        public void cleanup() {
            _deferred = null;
        }

        @NonNull
        @Override
        public Class<ByteBuffer> getDataClass() {
            return ByteBuffer.class;
        }
    }
}
