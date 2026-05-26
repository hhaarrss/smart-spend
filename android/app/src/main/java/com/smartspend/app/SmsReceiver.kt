package com.smartspend.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val bankSenders = setOf("HDFCBK", "SBIINB", "ICICIB", "AXISBK", "KOTAKB", "YESBNK", "PNBSMS")
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
                }
            }
        }
    }

    private fun isBankSender(sender: String): Boolean {
        // Banks often have senders like "AD-HDFCBK" or just "HDFCBK"
        return bankSenders.any { sender.contains(it, ignoreCase = true) }
    }

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
                    Log.d("SmsReceiver", "Successfully ingested SMS")
                    // Update shared prefs for dashboard
                    sharedPrefs.edit().apply {
                        putString("last_sms", body)
                        putInt("total_synced", sharedPrefs.getInt("total_synced", 0) + 1)
                        apply()
                    }
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
