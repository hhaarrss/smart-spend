package com.smartspend.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.smartspend.app.databinding.ItemDateHeaderBinding
import com.smartspend.app.databinding.ItemTransactionCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class TxListItem {
    data class Header(val label: String) : TxListItem()
    data class Row(val tx: TransactionData) : TxListItem()
}

/**
 * RecyclerView adapter with DiffUtil, day headers, and latest-first rows.
 */
class TransactionAdapter(
    private val onItemClick: (TransactionData) -> Unit = {},
    private val onItemLongClick: (TransactionData) -> Unit = {},
    private val onBadgeClick: (TransactionData) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<TxListItem> = emptyList()

    fun submitList(newItems: List<TxListItem>) {
        val diff = DiffUtil.calculateDiff(TxDiffCallback(items, newItems))
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TxListItem.Header -> VIEW_HEADER
            is TxListItem.Row -> VIEW_ROW
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_HEADER) {
            HeaderViewHolder(ItemDateHeaderBinding.inflate(inflater, parent, false))
        } else {
            TransactionViewHolder(
                ItemTransactionCardBinding.inflate(inflater, parent, false),
                onItemClick,
                onItemLongClick,
                onBadgeClick
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TxListItem.Header -> (holder as HeaderViewHolder).bind(item.label)
            is TxListItem.Row -> (holder as TransactionViewHolder).bind(item.tx)
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(
        private val binding: ItemDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(label: String) {
            binding.tvDateHeader.text = label
        }
    }

    class TransactionViewHolder(
        private val binding: ItemTransactionCardBinding,
        private val onItemClick: (TransactionData) -> Unit,
        private val onItemLongClick: (TransactionData) -> Unit,
        private val onBadgeClick: (TransactionData) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tx: TransactionData) {
            binding.root.setOnClickListener { onItemClick(tx) }
            binding.root.setOnLongClickListener {
                onItemLongClick(tx)
                true
            }
            val isTransfer = tx.is_transfer || tx.category.equals("Transfer", ignoreCase = true)
            val merchantName = if (isTransfer && !tx.transfer_to.isNullOrBlank()) {
                "Transfer to ${tx.transfer_to}"
            } else {
                tx.merchant?.takeIf { it.isNotBlank() } ?: tx.category
            }
            binding.tvMerchantName.text = merchantName

            val bankStr = tx.bank?.uppercase(Locale.ROOT) ?: "BANK"
            val acctStr = if (!tx.account_last4.isNullOrEmpty()) "XX${tx.account_last4}" else ""
            binding.tvSubtitle.text = "${tx.category} · $bankStr · $acctStr".trim(' ', '·')

            binding.tvCategoryIcon.text = if (isTransfer) "👤" else categoryEmoji(tx.category)

            val justNow = DateUtils.isJustNow(tx)
            if (justNow) {
                binding.tvDateTime.text = "JUST NOW"
                binding.tvDateTime.setTextColor(ContextCompat.getColor(itemView.context, R.color.emerald_success))
            } else {
                val millis = DateUtils.parseIsoMillis(tx.date)
                binding.tvDateTime.text = if (millis > 0) {
                    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(millis))
                } else {
                    tx.date.take(16).replace("T", " ")
                }
                binding.tvDateTime.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_tertiary))
            }

            val isCredit = tx.type.equals("credit", ignoreCase = true)
            if (isCredit) {
                binding.tvAmount.text = "+₹%.2f".format(tx.amount)
                binding.tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.emerald_success))
            } else {
                binding.tvAmount.text = "-₹%.2f".format(tx.amount)
                binding.tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.rose_error))
            }

            val status = tx.review_status ?: "auto_categorized"
            if (status.contains("needs_review", ignoreCase = true)) {
                binding.tvConfidenceBadge.text = "Needs Review"
                binding.tvConfidenceBadge.setTextColor(Color.parseColor("#F59E0B"))
            } else if (justNow) {
                binding.tvConfidenceBadge.text = "JUST NOW"
                binding.tvConfidenceBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.emerald_success))
            } else {
                // "High" = AI categorized with high confidence. Tap to override.
                binding.tvConfidenceBadge.text = "High ✏"
                binding.tvConfidenceBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.emerald_success))
            }

            // Make the badge always clickable to change category
            binding.tvConfidenceBadge.isClickable = true
            binding.tvConfidenceBadge.setOnClickListener { onBadgeClick(tx) }
        }
    }

    private class TxDiffCallback(
        private val oldList: List<TxListItem>,
        private val newList: List<TxListItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return when {
                oldItem is TxListItem.Header && newItem is TxListItem.Header ->
                    oldItem.label == newItem.label
                oldItem is TxListItem.Row && newItem is TxListItem.Row ->
                    oldItem.tx.id == newItem.tx.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    companion object {
        private const val VIEW_HEADER = 0
        private const val VIEW_ROW = 1
    }
}
