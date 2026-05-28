package com.smartspend.app

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Singleton Retrofit client providing the shared BackendService instance.
 * Includes HTTP logging for debug builds.
 */
object RetrofitClient {
    val apiService: BackendService by lazy {
        BackendService.create()
    }
}
