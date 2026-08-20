package com.smartspend.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * Category-wise spend rows for the selected month, sorted by amount descending.
 */
class CategoryAdapter(
    private var categoryList: List<CategorySummaryItem> = emptyList()
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    fun updateData(newList: List<CategorySummaryItem>) {
        val sorted = newList.sortedByDescending { it.total }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = categoryList.size
            override fun getNewListSize() = sorted.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                categoryList[oldItemPosition].category == sorted[newItemPosition].category
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                categoryList[oldItemPosition] == sorted[newItemPosition]
        })
        categoryList = sorted
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categoryList[position])
    }

    override fun getItemCount(): Int = categoryList.size

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIcon: TextView = itemView.findViewById(R.id.tvCategoryIcon)
        private val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val tvCategoryAmount: TextView = itemView.findViewById(R.id.tvCategoryAmount)
        private val progressCategory: ProgressBar = itemView.findViewById(R.id.progressCategory)
        private val tvCategoryPercent: TextView = itemView.findViewById(R.id.tvCategoryPercent)
        private val tvTxCount: TextView = itemView.findViewById(R.id.tvCategoryTxCount)

        fun bind(item: CategorySummaryItem) {
            tvIcon.text = categoryEmoji(item.category)
            tvCategoryName.text = item.category
            tvCategoryAmount.text = "₹%.2f".format(item.total)
            val count = item.transaction_count
            tvTxCount.text = if (count == 1) "1 transaction" else "$count transactions"

            val hasBudget = item.budget_limit > 0
            val usedPct = if (hasBudget) item.budget_used_percent else item.percentage
            progressCategory.progress = usedPct.toInt().coerceIn(0, 100)

            val barColor = when {
                usedPct > 90 -> Color.parseColor("#EF4444")
                usedPct >= 60 -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#10B981")
            }
            progressCategory.progressTintList = ColorStateList.valueOf(barColor)

            tvCategoryPercent.text = if (hasBudget) {
                "${usedPct.toInt()}% of budget used"
            } else {
                "${item.percentage}% of monthly spend · no budget set"
            }
        }
    }
}
