package com.smartspend.app

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class SmsPayload(
    val raw_sms: String,
    val sender: String
)

interface BackendService {
    @POST("/transactions/ingest-sms")
    suspend fun ingestSms(
        @Header("Authorization") token: String,
        @Body payload: SmsPayload
    ): Response<Unit>

    companion object {
        private const val BASE_URL = "http://10.160.29.167:8000" // For real device or specific IP

        fun create(): BackendService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BackendService::class.java)
        }
    }
}
