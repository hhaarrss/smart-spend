package com.smartspend.app

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

class TransactionListDeserializer : JsonDeserializer<PaginatedTransactionResponse> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): PaginatedTransactionResponse {
        if (json == null || json.isJsonNull) {
            return PaginatedTransactionResponse(emptyList(), 0, 1, 50, false, 0)
        }
        if (json.isJsonArray) {
            val listType = object : TypeToken<List<TransactionData>>() {}.type
            val items: List<TransactionData> = context?.deserialize(json, listType) ?: emptyList()
            return PaginatedTransactionResponse(items, items.size, 1, items.size, false, 1)
        } else if (json.isJsonObject) {
            val obj = json.asJsonObject
            val listType = object : TypeToken<List<TransactionData>>() {}.type
            val itemsElement = obj.get("transactions")
            val items: List<TransactionData> = if (itemsElement != null && !itemsElement.isJsonNull) {
                context?.deserialize(itemsElement, listType) ?: emptyList()
            } else {
                emptyList()
            }
            val totalCount = obj.get("total_count")?.asInt ?: items.size
            val page = obj.get("page")?.asInt ?: 1
            val limit = obj.get("limit")?.asInt ?: (if (items.isNotEmpty()) items.size else 50)
            val hasMore = obj.get("has_more")?.asBoolean ?: false
            val totalPages = obj.get("total_pages")?.asInt ?: 1
            return PaginatedTransactionResponse(items, totalCount, page, limit, hasMore, totalPages)
        }
        return PaginatedTransactionResponse(emptyList(), 0, 1, 50, false, 0)
    }
}

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
    val is_transfer: Boolean = false,
    val transfer_to: String? = null,
    val created_at: String
)

data class RecategorizePayload(
    val transaction_id: Int,
    val merchant_raw: String,
    val new_category: String,
    val subcategory: String? = null,
    val display_name: String? = null
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
 * Data model for Budget Limit returned from /budget/ endpoint.
 */
data class BudgetLimitData(
    val id: Int,
    val user_id: Int,
    val category: String,
    val monthly_limit: Double,
    val alert_at_percent: Double?,
    val is_family_limit: Boolean?
)

/**
 * Payload for setting / updating category budget limit.
 */
data class BudgetSetPayload(
    val category: String,
    val monthly_limit: Double,
    val alert_at_percent: Double = 80.0,
    val is_family_limit: Boolean = false
)

/**
 * Payload for creating manual transaction.
 */
data class TransactionCreatePayload(
    val amount: Double,
    val type: String,
    val category: String,
    val merchant: String?,
    val date: String
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

data class TransactionUpdatePayload(
    val category: String? = null,
    val merchant: String? = null,
    val amount: Double? = null,
    val date: String? = null,
    val notes: String? = null
)

data class CategoriesResponse(
    val categories: List<String>
)

data class FcmTokenPayload(
    val fcm_token: String
)

data class NeedsReviewResponse(
    val count: Int,
    val transactions: List<TransactionData>,
    val message: String
)

data class CategorizePayload(
    val category: String,
    val merchant_alias: String? = null
)

/**
 * Retrofit service interface for all SmartSpend backend API calls.
 */
interface BackendService {

    /**
     * Fetch transactions requiring user review/categorization.
     */
    @GET("transactions/needs-review")
    suspend fun getNeedsReviewTransactions(
        @Header("Authorization") token: String
    ): Response<NeedsReviewResponse>

    /**
     * 1-click categorize transaction and record merchant learning.
     */
    @PATCH("transactions/{id}/categorize")
    suspend fun categorizeTransaction(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body payload: CategorizePayload
    ): Response<Map<String, Any>>

    /**
     * Register device FCM push notification token.
     */
    @POST("users/fcm-token")
    suspend fun registerFcmToken(
        @Header("Authorization") token: String,
        @Body payload: FcmTokenPayload
    ): Response<Map<String, String>>

    /**
     * Fetch single canonical category list.
     */
    @GET("categories")
    suspend fun getCategories(): Response<CategoriesResponse>

    /**
     * Partially edit a transaction (category, merchant, amount, date, notes).
     */
    @PATCH("transactions/{id}")
    suspend fun patchTransaction(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body payload: TransactionUpdatePayload
    ): Response<TransactionData>

    /**
     * Delete a transaction.
     */
    @retrofit2.http.DELETE("transactions/{id}")
    suspend fun deleteTransaction(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Map<String, String>>

    /**
     * Authenticate user and obtain a JWT access token.
     */
    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    /**
     * Send parsed SMS data to the backend for transaction ingestion.
     */
    @POST("transactions/ingest-sms")
    suspend fun ingestSms(
        @Header("Authorization") token: String,
        @Body payload: SmsPayload
    ): Response<SmsIngestionResponse>

    /**
     * Fetch user's transactions list.
     */
    @GET("transactions/")
    suspend fun getTransactions(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("month") month: Int? = null,
        @Query("year") year: Int? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("include_transfers") includeTransfers: Boolean? = null
    ): Response<PaginatedTransactionResponse>

    /**
     * Fetch monthly category spending summary with budget utilization.
     */
    @GET("transactions/monthly-category-summary")
    suspend fun getMonthlyCategorySummary(
        @Header("Authorization") token: String,
        @Query("month") month: Int,
        @Query("year") year: Int
    ): Response<MonthlyCategorySummaryResponse>

    /**
     * Fetch category totals summary for a given month (YYYY-MM).
     */
    @GET("transactions/summary")
    suspend fun getCategorySummary(
        @Header("Authorization") token: String,
        @Query("month") month: String
    ): Response<Map<String, Double>>

    /**
     * Fetch configured budget limits.
     */
    @GET("budget/")
    suspend fun getBudgets(
        @Header("Authorization") token: String
    ): Response<List<BudgetLimitData>>

    /**
     * Create or update category budget limit.
     */
    @POST("budget/")
    suspend fun setBudget(
        @Header("Authorization") token: String,
        @Body payload: BudgetSetPayload
    ): Response<BudgetLimitData>

    /**
     * Manually create a transaction.
     */
    @POST("transactions/")
    suspend fun createTransaction(
        @Header("Authorization") token: String,
        @Body payload: TransactionCreatePayload
    ): Response<TransactionData>

    /**
     * Re-categorize a transaction (updates category, review status, and saves user learning).
     */
    @PATCH("transactions/{id}/recategorize")
    suspend fun recategorizeTransaction(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body payload: RecategorizePayload
    ): Response<Map<String, Any>>

    /**
     * Fetch analytical financial insights summary.
     */
    @GET("insights/summary")
    suspend fun getInsightsSummary(
        @Header("Authorization") token: String
    ): Response<InsightsSummaryData>

    companion object {
        private const val BASE_URL = "https://expense-tracker-pk4d.onrender.com/"

        /**
         * Creates a configured Retrofit BackendService instance.
         */
        fun create(): BackendService {
            val gson = GsonBuilder()
                .registerTypeAdapter(PaginatedTransactionResponse::class.java, TransactionListDeserializer())
                .create()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(BackendService::class.java)
        }
    }
}
