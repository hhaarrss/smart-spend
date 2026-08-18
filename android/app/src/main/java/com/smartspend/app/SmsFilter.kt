package com.smartspend.app

import java.util.Locale

object SmsFilter {
    private val bankKeywords = listOf(
        "icici", "hdfc", "sbi", "axis", "kotak", "yes", "pnb", "indus", "canara",
        "paytm", "pytm", "gpay", "bhim", "cred", "idfc", "union", "bob", "rbl",
        "citi", "fed", "amex", "slice", "jupiter", "fi", "onecard", "niyo", "upi", "bank"
    )

    private val transactionKeywords = listOf(
        "debited", "credited", "transferred", "spent", "paid", "withdrawn",
        "received", "vpa", "upi", "a/c", "inr", "rs.", "rs "
    )

    fun isTransactional(sender: String, body: String): Boolean {
        val sLower = sender.lowercase(Locale.ROOT)
        val bLower = body.lowercase(Locale.ROOT)

        val isSenderMatch = bankKeywords.any { sLower.contains(it) }
        val isBodyMatch = transactionKeywords.any { bLower.contains(it) }

        return isSenderMatch || isBodyMatch
    }
}
