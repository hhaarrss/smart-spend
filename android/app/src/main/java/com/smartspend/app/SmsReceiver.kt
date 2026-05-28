package com.smartspend.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that intercepts incoming SMS messages from known bank senders,
 * and forwards them to the SmartSpend backend for automatic transaction ingestion.
 *
 * Reads the JWT token from SharedPreferences. If a 401 Unauthorized is received,
 * the stored token is cleared so the user is prompted to re-login on next app open.
 */
class SmsReceiver : BroadcastReceiver() {
    private val bankSenders = setOf("ICICI", "HDFC", "SBI", "AXIS", "KOTAK", "YES", "PNB")
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.originatingAddress ?: continue
                val messageBody = sms.messageBody ?: continue

                Log.d("SmsReceiver", "Received SMS from: $sender")

                if (isBankSender(sender)) {
                    sendToBackend(context, sender, messageBody)
                } else {
                    Log.d("SmsReceiver", "SMS from $sender ignored (not a recognized bank sender)")
                }
            }
        }
    }

    /**
     * Checks if the SMS sender matches a known Indian bank sender ID.
     * Banks often have senders like "AD-HDFCBK" or just "HDFCBK".
     */
    private fun isBankSender(sender: String): Boolean {
        return bankSenders.any { sender.contains(it, ignoreCase = true) }
    }

    /**
     * Forwards the SMS to the backend API for ingestion.
     * Reads JWT from SharedPreferences. On 401, clears the token
     * so the user is prompted to re-login on next app open.
     */
    private fun sendToBackend(context: Context, sender: String, body: String) {
        val sharedPrefs = context.getSharedPreferences("smart_spend_prefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("jwt_token", "") ?: ""

        if (token.isEmpty()) {
            Log.e("SmsReceiver", "No JWT token found, cannot ingest SMS")
            return
        }

        val service = RetrofitClient.apiService
        val pendingResult = goAsync()

        scope.launch {
            try {
                val response = service.ingestSms("Bearer $token", SmsPayload(body, sender))
                if (response.isSuccessful) {
                    val respBody = response.body()
                    if (respBody != null && respBody.success) {
                        Log.d("SmsReceiver", "Successfully ingested SMS")
                        // Update shared prefs for dashboard stats
                        sharedPrefs.edit().apply {
                            putString("last_sms", body)
                            putInt("total_synced", sharedPrefs.getInt("total_synced", 0) + 1)
                            apply()
                        }
                    } else {
                        Log.w("SmsReceiver", "Backend rejected SMS: ${respBody?.message ?: "Unknown error"}")
                    }
                } else if (response.code() == 401) {
                    // Token expired or invalid — clear it so user must re-login
                    Log.w("SmsReceiver", "Received 401 Unauthorized — clearing stored token")
                    sharedPrefs.edit().remove("jwt_token").remove("user_email").apply()
                } else {
                    Log.e("SmsReceiver", "Failed to ingest SMS: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error ingesting SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
