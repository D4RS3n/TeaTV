package com.teatv.app

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.teatv.app.chat.points.PointsRepository
import okhttp3.Cache
import okhttp3.OkHttpClient

/** Единый ImageLoader + инициализация репозитория баллов. */
class TeaApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        PointsRepository.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .cache(Cache(cacheDir.resolve("coil_http"), 50L * 1024 * 1024))
                    .build()
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_emotes"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .build()
}
