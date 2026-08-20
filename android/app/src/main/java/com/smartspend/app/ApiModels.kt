package com.smartspend.app

data class CategorySummaryItem(
    val category: String,
    val total: Double,
    val percentage: Double,
    val transaction_count: Int,
    val top_merchant: String,
    val budget_limit: Double,
    val budget_used_percent: Double
)

data class MonthlyCategorySummaryResponse(
    val month: Int,
    val year: Int,
    val total_spent: Double,
    val categories: List<CategorySummaryItem>,
    val previous_month_total: Double,
    val month_over_month_change: Double
)

data class PaginatedTransactionResponse(
    val transactions: List<TransactionData>,
    val total_count: Int,
    val page: Int,
    val limit: Int,
    val has_more: Boolean,
    val total_pages: Int
)
