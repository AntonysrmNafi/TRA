package com.blockveil.tracker.remover.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** One shared OkHttp client for the whole app, so sockets/threads get reused. */
object HttpClientProvider {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            // We resolve redirects ourselves, one hop at a time, so we can
            // stop early (max hop count) and inspect each Location header.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
