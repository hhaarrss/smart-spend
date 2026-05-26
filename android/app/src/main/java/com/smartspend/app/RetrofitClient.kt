package com.smartspend.app

object RetrofitClient {
    val apiService: BackendService by lazy {
        BackendService.create()
    }
}
