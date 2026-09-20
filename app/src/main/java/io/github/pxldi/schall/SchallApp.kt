package io.github.pxldi.schall

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.pxldi.schall.data.LiveUpdates
import io.github.pxldi.schall.data.SessionStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** What lives for the whole process: one HTTP client, the stored session, the
 * event stream, and the image loader covers are fetched through. */
class SchallApp : Application() {
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    val sessions: SessionStore by lazy { SessionStore(this) }
    val live: LiveUpdates by lazy { LiveUpdates(http) }

    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
                .build()
        }
    }
}
