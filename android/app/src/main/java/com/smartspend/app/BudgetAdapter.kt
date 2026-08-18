package com.smartspend.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartspend.app.databinding.ItemBudgetCardBinding

/**
 * RecyclerView Adapter for category Budget Limits list.
 */
class BudgetAdapter(
    private val onEditClick: (BudgetLimitData) -> Unit
) : RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder>() {

    private var budgets: List<BudgetLimitData> = emptyList()
    private var spendingMap: Map<String, Double> = emptyMap()

    fun updateData(newBudgets: List<BudgetLimitData>, newSpending: Map<String, Double>) {
        budgets = newBudgets
        spendingMap = newSpending
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val binding = ItemBudgetCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BudgetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        holder.bind(budgets[position], spendingMap, onEditClick)
    }

    override fun getItemCount(): Int = budgets.size

    class BudgetViewHolder(private val binding: ItemBudgetCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            budget: BudgetLimitData,
            spendingMap: Map<String, Double>,
            onEditClick: (BudgetLimitData) -> Unit
        ) {
            binding.tvCategoryName.text = budget.category
            binding.tvCategoryIcon.text = getCategoryIcon(budget.category)

            val spent = spendingMap[budget.category] ?: 0.0
            val limit = budget.monthly_limit
            val percent = if (limit > 0) ((spent / limit) * 100).toInt() else 0

            binding.tvSpentAmount.text = "₹%.2f".format(spent)
            binding.tvLimitAmount.text = "₹%.2f".format(limit)

            binding.pbBudgetProgress.progress = percent.coerceAtMost(100)
            binding.tvPercentUsed.text = "$percent% used"

            if (percent >= 100) {
                binding.tvStatusBadge.visibility = View.VISIBLE
                binding.tvStatusBadge.text = "Over limit"
                binding.tvStatusBadge.setTextColor(Color.parseColor("#EF4444"))
                binding.tvPercentUsed.setTextColor(Color.parseColor("#EF4444"))
                
                binding.boxExceededAlert.visibility = View.VISIBLE
                val exceeded = spent - limit
                binding.tvExceededText.text = "ⓘ Exceeded by ₹%.2f".format(exceeded)
            } else if (percent >= 80) {
                binding.tvStatusBadge.visibility = View.VISIBLE
                binding.tvStatusBadge.text = "Near limit"
                binding.tvStatusBadge.setTextColor(Color.parseColor("#F59E0B"))
                binding.tvPercentUsed.setTextColor(Color.parseColor("#F59E0B"))
                binding.boxExceededAlert.visibility = View.GONE
            } else {
                binding.tvStatusBadge.visibility = View.GONE
                binding.tvPercentUsed.setTextColor(Color.parseColor("#16803C"))
                binding.boxExceededAlert.visibility = View.GONE
            }

            binding.btnEditLimit.setOnClickListener {
                onEditClick(budget)
            }
        }

        private fun getCategoryIcon(category: String): String {
            return when (category.lowercase()) {
                "food & dining", "food and dining" -> "🍴"
                "groceries" -> "🛍️"
                "transportation" -> "🚗"
                "shopping" -> "🛍️"
                "utilities & bills", "utilities" -> "⚡"
                "entertainment" -> "🎬"
                "education" -> "📚"
                "fuel" -> "⛽"
                "health & fitness" -> "💊"
                "salary" -> "💰"
                else -> "💳"
            }
        }
    }
}
