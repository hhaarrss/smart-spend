package com.smartspend.app

/**
 * Singleton Retrofit client providing the shared BackendService instance.
 * Dynamically retrieves configured service instance.
 */
object RetrofitClient {
    val apiService: BackendService by lazy {
        BackendService.create()
    }
}
