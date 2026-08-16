package com.smartspend.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/**
 * RecyclerView Adapter for displaying list of synced transactions in the mobile app feed.
 */
class TransactionAdapter(
    private var transactions: List<TransactionData> = emptyList()
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    fun updateData(newTransactions: List<TransactionData>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategoryIcon: TextView = itemView.findViewById(R.id.tvCategoryIcon)
        private val tvMerchant: TextView = itemView.findViewById(R.id.tvMerchant)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        private val tvDateBank: TextView = itemView.findViewById(R.id.tvDateBank)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvReviewBadge: TextView = itemView.findViewById(R.id.tvReviewBadge)

        fun bind(tx: TransactionData) {
            val merchantName = tx.merchant?.takeIf { it.isNotBlank() } ?: tx.category
            tvMerchant.text = merchantName
            tvCategory.text = tx.category

            // Emoji icon mapping
            tvCategoryIcon.text = getCategoryEmoji(tx.category)

            // Bank and Date formatting
            val bankStr = tx.bank?.uppercase(Locale.ROOT) ?: "BANK"
            val accountStr = if (!tx.account_last4.isNullOrEmpty()) "XX${tx.account_last4}" else ""
            val rawDateStr = tx.date.take(10)
            tvDateBank.text = "$rawDateStr • $bankStr $accountStr".trim()

            // Amount formatting
            val isCredit = tx.type.equals("credit", ignoreCase = true)
            if (isCredit) {
                tvAmount.text = "+ ₹%.2f".format(tx.amount)
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.emerald_success))
            } else {
                tvAmount.text = "- ₹%.2f".format(tx.amount)
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.rose_error))
            }

            // Review status badge
            val status = tx.review_status ?: "auto_categorized"
            if (status.contains("needs_review", ignoreCase = true)) {
                tvReviewBadge.text = "⚠️ Needs Review"
                tvReviewBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.amber_warning))
            } else {
                tvReviewBadge.text = "✅ Auto"
                tvReviewBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.emerald_success))
            }
        }

        private fun getCategoryEmoji(category: String): String {
            val catLower = category.lowercase(Locale.ROOT)
            return when {
                catLower.contains("grocer") || catLower.contains("supermarket") -> "🛒"
                catLower.contains("food") || catLower.contains("dining") || catLower.contains("restaurant") -> "🍔"
                catLower.contains("shop") || catLower.contains("e-commerce") -> "🛍️"
                catLower.contains("transport") || catLower.contains("cab") || catLower.contains("fuel") -> "🚗"
                catLower.contains("util") || catLower.contains("bill") || catLower.contains("recharge") -> "💡"
                catLower.contains("health") || catLower.contains("pharmacy") || catLower.contains("med") -> "🏥"
                catLower.contains("entert") || catLower.contains("movie") || catLower.contains("subscr") -> "🎬"
                else -> "💳"
            }
        }
    }
}
