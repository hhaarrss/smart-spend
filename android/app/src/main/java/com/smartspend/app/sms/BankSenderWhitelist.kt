package com.smartspend.app.sms

import java.util.Locale

/**
 * Whitelist of known Indian bank and financial institution SMS sender tokens.
 *
 * Source: Consolidates sender tokens from SmsFilter.kt and identify_bank() in backend/utils/sms_parser.py.
 * Senders in India arrive with prefixes such as "AD-HDFCBK", "VM-ICICIB", "BZ-SBIPSG", "VK-AXISBK", etc.
 */
object BankSenderWhitelist {

    /**
     * Exact bank sender tokens whitelisted across the app and backend.
     */
    val WHITELISTED_SENDER_TOKENS = listOf(
        "HDFCBK", "HDFC",
        "ICICIB", "ICICI",
        "SBIPSG", "SBIINB", "SBIBNK", "SBI",
        "AXISBK", "AXIS",
        "KOTAKB", "KOTAK",
        "YESBNK", "YESBK", "YES",
        "PNBSMS", "PNB", "PUNJAB",
        "BOBSMS", "BOB", "BARODA",
        "CANBNK", "CANARA",
        "INDBNK",
        "IDFCFB", "IDFC",
        "RBLBNK", "RBL",
        "CITIBK", "CITI",
        "AMEXIN", "AMEX",
        "ONECRD",
        "FEDBNK", "FEDERAL",
        "UNIONB", "UNION",
        "PAYTMB", "PAYTM", "PYTM",
        "GPAY",
        "BHIM",
        "AD-BANK"
    )

    /**
     * Checks whether an incoming SMS sender address belongs to a whitelisted bank/financial institution.
     * Rejects personal phone numbers (+91..., 10-digit numbers) and non-banking entities.
     *
     * @param sender Header/originating address of the SMS (e.g., "AD-HDFCBK", "VK-AXISBK", "+919876543210")
     * @return true if whitelisted, false otherwise.
     */
    fun isWhitelisted(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val sUpper = sender.uppercase(Locale.ROOT)
        return WHITELISTED_SENDER_TOKENS.any { token ->
            sUpper.contains(token)
        }
    }

    /**
     * Identifies the institution name from sender or body, mirroring identify_bank() from backend/utils/sms_parser.py.
     */
    fun identifyBank(sender: String, body: String): String {
        val sUpper = sender.uppercase(Locale.ROOT)
        val bUpper = body.uppercase(Locale.ROOT)

        return when {
            "HDFC" in sUpper || "HDFC" in bUpper -> "HDFC"
            "SBI" in sUpper || "SBI" in bUpper -> "SBI"
            "ICICI" in sUpper || "ICICI" in bUpper -> "ICICI"
            "AXIS" in sUpper || "AXIS" in bUpper -> "Axis"
            "KOTAK" in sUpper || "KOTAK" in bUpper -> "Kotak"
            "YESBK" in sUpper || "YES" in bUpper -> "Yes Bank"
            "PNB" in sUpper || "PUNJAB" in bUpper -> "PNB"
            "PAYTM" in sUpper || "PYTM" in bUpper -> "Paytm"
            "IDFC" in sUpper || "IDFC" in bUpper -> "IDFC"
            "UNION" in sUpper || "UNION" in bUpper -> "Union Bank"
            "BOB" in sUpper || "BARODA" in bUpper -> "Bank of Baroda"
            "CANARA" in sUpper || "CANARA" in bUpper -> "Canara Bank"
            "RBL" in sUpper || "RBL" in bUpper -> "RBL Bank"
            "CITI" in sUpper || "CITI" in bUpper -> "Citi Bank"
            "FED" in sUpper || "FEDERAL" in bUpper -> "Federal Bank"
            "AMEX" in sUpper || "AMERICAN EXPRESS" in bUpper -> "AmEx"
            else -> "BANK"
        }
    }
}
