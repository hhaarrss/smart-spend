package com.smartspend.app

import java.util.Locale

object SmsFilter {
    private val bankKeywords = listOf(
        "icici", "hdfc", "sbi", "axis", "kotak", "yes", "pnb", "indus", "canara",
        "paytm", "pytm", "gpay", "bhim", "cred", "idfc", "union", "bob", "rbl",
        "citi", "fed", "amex", "slice", "jupiter", "fi", "onecard", "niyo", "upi", "bank"
    )

    private val spamKeywords = listOf(
        "save rs", "earn up to", "cashback every", "apply now", "pre-approved",
        "pre approved", "loan offer", "get up to", "win up to", "lifetime free",
        "at no extra charge", "pro pass", "voucher", "coupon", "discount on",
        "mandate collect request", "request for blocking of funds",
        "otp", "verification code", "do not share", "claim now", "offer ends",
        "congratulations", "credit card limit", "personal loan"
    )

    private val actionKeywords = listOf(
        "debited", "credited", "transferred", "spent", "paid", "withdrawn",
        "deposited", "sent to", "received from", "received rs", "credited with", "refund"
    )

    private val currencyKeywords = listOf(
        "rs.", "rs ", "inr", "₹"
    )

    fun isTransactional(sender: String, body: String): Boolean {
        val sLower = sender.lowercase(Locale.ROOT)
        val bLower = body.lowercase(Locale.ROOT)

        // 1. Immediately reject spam, promotional, OTP, or mandate request messages
        if (spamKeywords.any { bLower.contains(it) }) {
            return false
        }

        // 2. Must contain an amount indicator (Rs., INR, ₹)
        val hasCurrency = currencyKeywords.any { bLower.contains(it) }
        if (!hasCurrency) {
            return false
        }

        // 3. Must contain a concrete transaction action (debited, credited, paid, etc.)
        val hasAction = actionKeywords.any { bLower.contains(it) }
        if (!hasAction) {
            return false
        }

        // 4. Must originate from a bank or mention an account / UPI / bank context
        val isBankSender = bankKeywords.any { sLower.contains(it) }
        val hasBankContext = bLower.contains("a/c") || bLower.contains("acct") || 
                             bLower.contains("account") || bLower.contains("upi") || 
                             bLower.contains("vpa") || bLower.contains("bank") ||
                             bLower.contains("card")

        return isBankSender || hasBankContext
    }
}

