import kotlin.text.Regex

fun main() {
    val sms = "ICICI Bank Acct XX598 debited for Rs 1.00 on 21-Sep-26; RABADIYA HARSH  credited. UPI:110727772460. Call 18002662 for dispute. SMS BLOCK 598 to 9215676766."
    val pat = Regex("(?i)(?:upi\\s*(?:ref(?:erence)?\\s*(?:no\\.?|num\\.?|number)?|id|no\\.?)?|imps\\s*(?:ref(?:erence)?\\s*(?:no\\.?)?)?|rrn\\s*[:\\s-]*|ref(?:erence)?\\s*(?:no\\.?|num)?\\s*[:\\s-]*)\\s*([A-Za-z0-9]{8,22})")
    val match = pat.find(sms)
    println("Match: " + match?.groupValues?.get(1))
}
