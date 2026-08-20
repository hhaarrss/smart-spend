package com.smartspend.app

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {

    fun parseIsoMillis(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val trimmed = raw.trim()
        try {
            return Instant.parse(trimmed).toEpochMilli()
        } catch (_: DateTimeParseException) {
        } catch (_: Exception) {
        }
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val parsed = sdf.parse(trimmed.replace("Z", ""))
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    fun isJustNow(tx: TransactionData, now: Long = System.currentTimeMillis()): Boolean {
        val created = parseIsoMillis(tx.created_at).takeIf { it > 0 } ?: parseIsoMillis(tx.date)
        if (created <= 0) return false
        return now - created <= TimeUnit.MINUTES.toMillis(5)
    }

    fun dayHeader(tx: TransactionData): String {
        val millis = parseIsoMillis(tx.date)
        if (millis <= 0) return tx.date.take(10)
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            isSameDay(cal, today) -> "TODAY"
            isSameDay(cal, yesterday) -> "YESTERDAY"
            else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis)).uppercase(Locale.getDefault())
        }
    }

    fun formatMonthYear(month: Int, year: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.YEAR, year)
        return SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(cal.time)
    }

    fun daysInMonth(month: Int, year: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun sortLatestFirst(transactions: List<TransactionData>): List<TransactionData> {
        return transactions.sortedWith(
            compareByDescending<TransactionData> { parseIsoMillis(it.date) }
                .thenByDescending { parseIsoMillis(it.created_at) }
        )
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}

fun categoryEmoji(category: String): String {
    val catLower = category.lowercase(Locale.getDefault())
    return when {
        catLower.contains("food") || catLower.contains("dining") -> "🍴"
        catLower.contains("grocer") -> "🛒"
        catLower.contains("transport") || catLower.contains("cab") -> "🚗"
        catLower.contains("fuel") -> "⛽"
        catLower.contains("entert") || catLower.contains("movie") -> "🎬"
        catLower.contains("util") || catLower.contains("bill") -> "⚡"
        catLower.contains("shop") -> "🛍️"
        catLower.contains("health") || catLower.contains("fitness") -> "💊"
        catLower.contains("edu") -> "🎓"
        catLower.contains("travel") || catLower.contains("hotel") -> "✈️"
        catLower.contains("salary") || catLower.contains("finance") -> "💰"
        catLower.contains("telecom") || catLower.contains("recharge") -> "📱"
        catLower.contains("subscr") -> "🔁"
        catLower.contains("review") -> "❓"
        else -> "💳"
    }
}
