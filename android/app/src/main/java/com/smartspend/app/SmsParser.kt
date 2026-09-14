package com.smartspend.app

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object SmsParser {
    private val amountPatterns = listOf(
        Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.\d{1,2})?)"""),
        Regex("""(?i)([0-9,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr)""")
    )

    private val accountPatterns = listOf(
        Regex("""(?i)(?:a/c|acct|account|card)\s*(?:no\.?)?\s*(?:x+|xx|\*+)?\s*(\d{4})"""),
        Regex("""(?i)(?:x{2,}|\*{2,})(\d{4})""")
    )

    private val datePatterns = listOf(
        Regex("""(?i)\b(\d{1,2}[-/][A-Za-z]{3}[-/]\d{2,4})\b"""),
        Regex("""\b(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\b"""),
        Regex("""\b(\d{4}[-/]\d{1,2}[-/]\d{1,2})\b""")
    )

    private val merchantPatterns = listOf(
        Regex("""(?i)\b(?:at|to|by|from)\s+([A-Za-z0-9 .&@_-]{2,80}?)(?=\s+(?:on|via|upi|ref|rrn|a/c|acct|card|avl|available|if\b)|[.;,]|$)"""),
        Regex("""(?i)\binfo[:\s]+([A-Za-z0-9 .&@_-]{2,80}?)(?=\s+(?:on|via|upi|ref|rrn|avl|available|if\b)|[.;,]|$)""")
    )

    private val upiRefPattern = Regex("""(?i)\b(?:upi|ref|rrn)(?:\s*(?:no|id))?[:\s-]*([A-Z0-9]{6,})\b""")

    fun parse(rawSms: String, sender: String): SmsPayload? {
        if (!SmsFilter.isTransactional(sender, rawSms)) {
            return null
        }

        val sms = rawSms.replace(Regex("""\s+"""), " ").trim()
        val lower = sms.lowercase(Locale.ROOT)
        val amount = amountPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(sms)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        } ?: return null

        val transactionType = when {
            listOf("credited", "received", "deposited", "refund").any { lower.contains(it) } -> "credit"
            listOf("debited", "spent", "paid", "withdrawn", "sent to", "transferred").any { lower.contains(it) } -> "debit"
            else -> return null
        }

        return SmsPayload(
            amount = amount,
            transaction_type = transactionType,
            merchant_raw = extractMerchant(sms),
            bank_sender_id = sender.take(50),
            account_last4 = extractAccountLast4(sms),
            date = extractDate(sms),
            upi_ref = upiRefPattern.find(sms)?.groupValues?.get(1)?.take(100)
        )
    }

    private fun extractMerchant(sms: String): String? {
        return merchantPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(sms)?.groupValues?.get(1)?.trim(' ', '.', ',', ';')
        }?.takeIf { it.isNotBlank() }?.take(255)
    }

    private fun extractAccountLast4(sms: String): String? {
        return accountPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(sms)?.groupValues?.get(1)
        }
    }

    private fun extractDate(sms: String): String {
        val rawDate = datePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(sms)?.groupValues?.get(1)
        }

        val localDate = rawDate?.let { parseDate(it) } ?: LocalDate.now()
        return localDate.atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    private fun parseDate(raw: String): LocalDate? {
        val normalized = normalizeYear(raw.replace("/", "-"))
        val formatters = listOf(
            DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE
        )

        return formatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDate.parse(normalized, formatter) }.getOrNull()
        } ?: runCatching { OffsetDateTime.parse(raw).toLocalDate() }.getOrNull()
    }

    private fun normalizeYear(raw: String): String {
        val parts = raw.split("-")
        if (parts.size != 3 || parts[2].length != 2) {
            return raw
        }
        return "${parts[0]}-${parts[1]}-20${parts[2]}"
    }
}
