package com.smartspend.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.smartspend.app.databinding.ItemTransactionCardBinding
import java.util.Locale

/**
 * RecyclerView Adapter for displaying transaction cards matching the mobile design.
 */
class TransactionAdapter(
    private var transactions: List<TransactionData> = emptyList(),
    private val onItemClick: (TransactionData) -> Unit = {}
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    fun updateData(newTransactions: List<TransactionData>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TransactionViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    class TransactionViewHolder(
        private val binding: ItemTransactionCardBinding,
        private val onItemClick: (TransactionData) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tx: TransactionData) {
            binding.root.setOnClickListener { onItemClick(tx) }
            val merchantName = tx.merchant?.takeIf { it.isNotBlank() } ?: tx.category
            binding.tvMerchantName.text = merchantName

            // Subtitle: Food & Dining · HDFC · XX4521
            val bankStr = tx.bank?.uppercase(Locale.ROOT) ?: "BANK"
            val acctStr = if (!tx.account_last4.isNullOrEmpty()) "XX${tx.account_last4}" else ""
            binding.tvSubtitle.text = "${tx.category} · $bankStr · $acctStr".trim(' ', '·')

            // Category Icon
            binding.tvCategoryIcon.text = getCategoryEmoji(tx.category)

            // Date / Time format
            val rawDateStr = tx.date.take(16).replace("T", " ")
            binding.tvDateTime.text = rawDateStr

            // Amount formatting (-₹349.00 vs +₹85,000.00)
            val isCredit = tx.type.equals("credit", ignoreCase = true)
            if (isCredit) {
                binding.tvAmount.text = "+₹%.2f".format(tx.amount)
                binding.tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.emerald_success))
            } else {
                binding.tvAmount.text = "-₹%.2f".format(tx.amount)
                binding.tvAmount.setTextColor(Color.parseColor("#F8FAFC"))
            }

            // Confidence / Review status badge
            val status = tx.review_status ?: "auto_categorized"
            if (status.contains("needs_review", ignoreCase = true)) {
                binding.tvConfidenceBadge.text = "Needs Review"
                binding.tvConfidenceBadge.setTextColor(Color.parseColor("#F59E0B"))
            } else {
                binding.tvConfidenceBadge.text = "High"
                binding.tvConfidenceBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.emerald_success))
            }
        }

        private fun getCategoryEmoji(category: String): String {
            val catLower = category.lowercase(Locale.ROOT)
            return when {
                catLower.contains("food") || catLower.contains("dining") -> "🍴"
                catLower.contains("grocer") -> "🛍️"
                catLower.contains("transport") || catLower.contains("cab") -> "🚗"
                catLower.contains("fuel") -> "⛽"
                catLower.contains("entert") || catLower.contains("movie") -> "🎬"
                catLower.contains("util") || catLower.contains("bill") -> "⚡"
                catLower.contains("shop") -> "🛍️"
                catLower.contains("salary") -> "💰"
                else -> "💳"
            }
        }
    }
}
