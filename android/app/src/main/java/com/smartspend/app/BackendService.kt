package com.smartspend.app

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

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
 * Transaction data model returned inside API responses.
 */
data class TransactionData(
    val id: Int,
    val user_id: Int,
    val amount: Double,
    val type: String,
    val category: String,
    val merchant: String?,
    val subcategory: String?,
    val bank: String?,
    val account_last4: String?,
    val date: String,
    val source: String?,
    val confidence: String?,
    val review_status: String?,
    val created_at: String
)

/**
 * Response model for the /transactions/ingest-sms endpoint.
 */
data class SmsIngestionResponse(
    val success: Boolean,
    val transaction: TransactionData?,
    val message: String
)

/**
 * Data model for spending change item inside Insights summary.
 */
data class SpendingChangeItem(
    val category: String,
    val change_percent: Double,
    val direction: String
)

/**
 * Response model for the /insights/summary endpoint.
 */
data class InsightsSummaryData(
    val spending_changes: List<SpendingChangeItem>?,
    val anomalies: List<Map<String, Any>>?,
    val recurring: List<Map<String, Any>>?,
    val budget_alerts: List<Map<String, Any>>?
)

/**
 * Retrofit service interface for all SmartSpend backend API calls.
 */
interface BackendService {

    /**
     * Authenticate user and obtain a JWT access token.
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

    /**
     * Fetch user's transactions list.
     */
    @GET("/transactions/")
    suspend fun getTransactions(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<TransactionData>>

    /**
     * Fetch category totals summary for a given month (YYYY-MM).
     */
    @GET("/transactions/summary")
    suspend fun getCategorySummary(
        @Header("Authorization") token: String,
        @Query("month") month: String
    ): Response<Map<String, Double>>

    /**
     * Fetch analytical financial insights summary.
     */
    @GET("/insights/summary")
    suspend fun getInsightsSummary(
        @Header("Authorization") token: String
    ): Response<InsightsSummaryData>

    companion object {
        private const val BASE_URL = "https://expense-tracker-pk4d.onrender.com/"

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
