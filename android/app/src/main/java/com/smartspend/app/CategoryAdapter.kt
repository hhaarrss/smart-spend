package com.smartspend.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/**
 * RecyclerView Adapter for displaying Category Breakdown items in the mobile dashboard.
 */
class CategoryAdapter(
    private var categoryList: List<Pair<String, Double>> = emptyList(),
    private var totalMonthSpend: Double = 1.0
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    fun updateData(newCategoryMap: Map<String, Double>) {
        val list = newCategoryMap.toList().sortedByDescending { it.second }
        categoryList = list
        totalMonthSpend = list.sumOf { it.second }.coerceAtLeast(1.0)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val item = categoryList[position]
        holder.bind(item.first, item.second, totalMonthSpend)
    }

    override fun getItemCount(): Int = categoryList.size

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val tvCategoryAmount: TextView = itemView.findViewById(R.id.tvCategoryAmount)
        private val progressCategory: ProgressBar = itemView.findViewById(R.id.progressCategory)
        private val tvCategoryPercent: TextView = itemView.findViewById(R.id.tvCategoryPercent)

        fun bind(name: String, amount: Double, total: Double) {
            tvCategoryName.text = name
            tvCategoryAmount.text = "₹%.2f".format(amount)

            val percent = ((amount / total) * 100).toInt().coerceIn(1, 100)
            progressCategory.progress = percent
            tvCategoryPercent.text = "$percent% of monthly total spending"
        }
    }
}
