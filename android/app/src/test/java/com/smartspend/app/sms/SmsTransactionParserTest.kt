package com.smartspend.app.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTransactionParserTest {

    @Test
    fun testNonWhitelistedSenderRejected() {
        // Non-whitelisted senders must never reach parser or produce parsed transactions
        val nonBankSender = "+919876543210"
        val promoSender = "AD-SHOPDEAL"
        val sms = "Your account is debited for Rs 500.00 on 27-May-26 at Local Store"

        assertFalse(BankSenderWhitelist.isWhitelisted(nonBankSender))
        assertFalse(BankSenderWhitelist.isWhitelisted(promoSender))

        val parsedNonBank = SmsTransactionParser.parse(sms, nonBankSender)
        assertNull(parsedNonBank)

        val parsedPromo = SmsTransactionParser.parse(sms, promoSender)
        assertNull(parsedPromo)
    }

    @Test
    fun testSpamMessageRejected() {
        // Promotional/loan spam must be rejected even from bank sender
        val sender = "AD-HDFCBK"
        val spamSms = "Congratulations! Get pre-approved personal loan up to Rs 5,00,000 at no extra charge. Apply now."

        val parsed = SmsTransactionParser.parse(spamSms, sender)
        assertNull(parsed)
    }

    @Test
    fun testRealCase1_IciciUpiDebit() {
        // Real test fixture from backend/tests/test_full_suite.py line 82
        val sender = "AD-ICICIB"
        val sms = "ICICI Bank Acct XX1234 debited for Rs 450.00 on 27-May-26; Swiggy credited. UPI:412356789012. Call 18002662 for dispute."

        assertTrue(BankSenderWhitelist.isWhitelisted(sender))
        val parsed = SmsTransactionParser.parse(sms, sender)
        assertNotNull(parsed)
        parsed!!

        assertEquals(450.00, parsed.amount, 0.001)
        assertEquals("debit", parsed.transactionType)
        assertEquals("Swiggy", parsed.merchantRaw)
        assertEquals("1234", parsed.accountLast4)
        assertEquals("412356789012", parsed.upiRef)
        assertEquals("ICICI", parsed.bank)
    }

    @Test
    fun testRealCase2_HdfcDebitGroceries() {
        // Real test fixture from backend/tests/test_full_suite.py line 107
        val sender = "AD-HDFCBK"
        val sms = "HDFC Bank: Rs.1200.00 debited from A/c XX5678 on 27-05-26. Info: DMART SUPERMARKET. Avl Bal: Rs.15340.00"

        assertTrue(BankSenderWhitelist.isWhitelisted(sender))
        val parsed = SmsTransactionParser.parse(sms, sender)
        assertNotNull(parsed)
        parsed!!

        assertEquals(1200.00, parsed.amount, 0.001)
        assertEquals("debit", parsed.transactionType)
        assertEquals("DMART SUPERMARKET", parsed.merchantRaw)
        assertEquals("5678", parsed.accountLast4)
        assertEquals("HDFC", parsed.bank)
    }

    @Test
    fun testRealCase3_AxisDebitTravel() {
        // Real test fixture from backend/tests/test_full_suite.py line 118
        val sender = "AD-AXISBK"
        val sms = "Rs.8500.00 debited from Axis Bank A/c XX9012 on 28-May-26. Info: MAKEMYTRIP. Avl Bal: Rs.21450.00"

        assertTrue(BankSenderWhitelist.isWhitelisted(sender))
        val parsed = SmsTransactionParser.parse(sms, sender)
        assertNotNull(parsed)
        parsed!!

        assertEquals(8500.00, parsed.amount, 0.001)
        assertEquals("debit", parsed.transactionType)
        assertEquals("MAKEMYTRIP", parsed.merchantRaw)
        assertEquals("9012", parsed.accountLast4)
        assertEquals("Axis", parsed.bank)
    }

    @Test
    fun testRealCase4_SbiCreditCashback() {
        // Real test fixture from backend/tests/test_full_suite.py line 169
        val sender = "AD-SBIBNK"
        val sms = "Rs.100.00 credited to A/c XX5678 on 29-May-26 towards GPAY CASHBACK. Ref 987654321"

        assertTrue(BankSenderWhitelist.isWhitelisted(sender))
        val parsed = SmsTransactionParser.parse(sms, sender)
        assertNotNull(parsed)
        parsed!!

        assertEquals(100.00, parsed.amount, 0.001)
        assertEquals("credit", parsed.transactionType)
        assertEquals("GPAY CASHBACK", parsed.merchantRaw)
        assertEquals("5678", parsed.accountLast4)
        assertEquals("987654321", parsed.upiRef)
        assertEquals("SBI", parsed.bank)
    }

    @Test
    fun testRealCase5_IciciCreditCardUsage() {
        // Real test fixture from backend/tests/test_full_suite.py line 95
        val sender = "AD-ICICIB"
        val sms = "ICICI Bank Credit Card XX9087 has been used for a transaction of Rs 1850.00 on 27-May-26 at BARBEQUE NATION. If not done by you call 18002662."

        assertTrue(BankSenderWhitelist.isWhitelisted(sender))
        val parsed = SmsTransactionParser.parse(sms, sender)
        assertNotNull(parsed)
        parsed!!

        assertEquals(1850.00, parsed.amount, 0.001)
        assertEquals("debit", parsed.transactionType)
        assertEquals("BARBEQUE NATION", parsed.merchantRaw)
        assertEquals("9087", parsed.accountLast4)
        assertEquals("ICICI", parsed.bank)
    }

    @Test
    fun testRealCase6_HdfcSalaryCredit() {
        // Real test fixture from backend/tests/test_full_suite.py line 145
        val sender = "AD-HDFCBK"
        val sms = "HDFC Bank: Rs 45000.00 credited to A/c XX9876 on 30-May-26. Info: TCS SALARY. Avl Bal Rs 52000.00"

        assertTrue(BankSenderWhitelist.isWhitelisted(sender))
        val parsed = SmsTransactionParser.parse(sms, sender)
        assertNotNull(parsed)
        parsed!!

        assertEquals(45000.00, parsed.amount, 0.001)
        assertEquals("credit", parsed.transactionType)
        assertEquals("TCS SALARY", parsed.merchantRaw)
        assertEquals("9876", parsed.accountLast4)
        assertEquals("HDFC", parsed.bank)
    }

    @Test
    fun testToSmsPayloadContainsNoRawSms() {
        val sender = "AD-HDFCBK"
        val sms = "HDFC Bank: Rs.1200.00 debited from A/c XX5678 on 27-05-26. Info: DMART SUPERMARKET. Avl Bal: Rs.15340.00"
        val parsed = SmsTransactionParser.parse(sms, sender)
        assertNotNull(parsed)

        val payload = parsed!!.toSmsPayload()
        assertEquals(1200.00, payload.amount, 0.001)
        assertEquals("debit", payload.transaction_type)
        assertEquals("DMART SUPERMARKET", payload.merchant_raw)
        assertEquals("5678", payload.account_last4)
        assertEquals("AD-HDFCBK", payload.bank_sender_id)
        assertNotNull(payload.date)
        // Verify via reflection/fields that SmsPayload contains only structured fields
        val fieldNames = payload.javaClass.declaredFields.map { it.name }
        assertFalse("SmsPayload must NOT have raw_sms field", fieldNames.contains("raw_sms"))
        assertFalse("SmsPayload must NOT have rawSms field", fieldNames.contains("rawSms"))
        assertFalse("SmsPayload must NOT have body field", fieldNames.contains("body"))
    }
}
