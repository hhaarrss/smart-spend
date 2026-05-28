package com.smartspend.app

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Payload for SMS ingestion endpoint.
 */
data class SmsPayload(
    val raw_sms: String,
    val sender: String
)

/**
 * Response model for the /auth/login endpoint.
 */
data class LoginResponse(
    val access_token: String,
    val token_type: String
)

/**
 * Response model for the /transactions/ingest-sms endpoint.
 */
data class SmsIngestionResponse(
    val success: Boolean,
    val message: String
)

/**
 * Retrofit service interface for all SmartSpend backend API calls.
 */
interface BackendService {

    /**
     * Authenticate user and obtain a JWT access token.
     * Uses form-urlencoded body matching FastAPI's OAuth2PasswordRequestForm.
     */
    @FormUrlEncoded
    @POST("/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    /**
     * Send parsed SMS data to the backend for transaction ingestion.
     */
    @POST("/transactions/ingest-sms")
    suspend fun ingestSms(
        @Header("Authorization") token: String,
        @Body payload: SmsPayload
    ): Response<SmsIngestionResponse>

    companion object {
        private const val BASE_URL = "http://10.214.158.167:8000"

        /**
         * Creates a configured Retrofit BackendService instance.
         */
        fun create(): BackendService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BackendService::class.java)
        }
    }
}
