package com.smartspend.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * BroadcastReceiver that intercepts incoming SMS messages from known bank senders,
 * and forwards them to the SmartSpend backend for automatic transaction ingestion.
 *
 * Includes an Offline Queue to retry failed SMS syncs when internet connectivity restores.
 */
class SmsReceiver : BroadcastReceiver() {
    private val bankSenders = setOf("ICICI", "HDFC", "SBI", "AXIS", "KOTAK", "YES", "PNB", "INDUS", "CANARA")
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
     * Checks if the SMS sender matches a known bank sender ID.
     */
    private fun isBankSender(sender: String): Boolean {
        return bankSenders.any { sender.contains(it, ignoreCase = true) }
    }

    /**
     * Forwards the SMS to the backend API for ingestion.
     * Queues SMS offline if network request fails.
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
                // First try to flush any previously queued offline SMS
                flushOfflineQueue(context, token)

                val response = service.ingestSms("Bearer $token", SmsPayload(body, sender))
                if (response.isSuccessful) {
                    val respBody = response.body()
                    if (respBody != null && respBody.success) {
                        Log.d("SmsReceiver", "Successfully ingested SMS")
                        sharedPrefs.edit().apply {
                            putString("last_sms", body)
                            putInt("total_synced", sharedPrefs.getInt("total_synced", 0) + 1)
                            apply()
                        }
                    } else {
                        Log.w("SmsReceiver", "Backend rejected SMS: ${respBody?.message ?: "Unknown error"}")
                    }
                } else if (response.code() == 401) {
                    Log.w("SmsReceiver", "Received 401 Unauthorized — clearing stored token")
                    sharedPrefs.edit().remove("jwt_token").remove("user_email").apply()
                } else {
                    Log.e("SmsReceiver", "Server error ${response.code()} — queuing SMS for retry")
                    queueOfflineSms(context, sender, body)
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Network error ingesting SMS — queuing offline", e)
                queueOfflineSms(context, sender, body)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /**
         * Saves a failed SMS sync to offline storage for future auto-retry.
         */
        fun queueOfflineSms(context: Context, sender: String, body: String) {
            val sharedPrefs = context.getSharedPreferences("smart_spend_prefs", Context.MODE_PRIVATE)
            val queueJsonStr = sharedPrefs.getString("offline_sms_queue", "[]") ?: "[]"
            try {
                val queueArray = JSONArray(queueJsonStr)
                val item = JSONObject().apply {
                    put("sender", sender)
                    put("body", body)
                    put("timestamp", System.currentTimeMillis())
                }
                queueArray.put(item)
                sharedPrefs.edit().putString("offline_sms_queue", queueArray.toString()).apply()
                Log.d("SmsReceiver", "Queued SMS offline. Queue size: ${queueArray.length()}")
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Failed to queue offline SMS", e)
            }
        }

        /**
         * Flushes all offline queued SMS to backend once internet/server connection is restored.
         */
        suspend fun flushOfflineQueue(context: Context, token: String) {
            val sharedPrefs = context.getSharedPreferences("smart_spend_prefs", Context.MODE_PRIVATE)
            val queueJsonStr = sharedPrefs.getString("offline_sms_queue", "[]") ?: "[]"
            if (queueJsonStr == "[]") return

            try {
                val queueArray = JSONArray(queueJsonStr)
                if (queueArray.length() == 0) return

                Log.d("SmsReceiver", "Flushing ${queueArray.length()} offline queued SMS...")
                val remainingQueue = JSONArray()
                val service = RetrofitClient.apiService

                for (i in 0 until queueArray.length()) {
                    val obj = queueArray.getJSONObject(i)
                    val sender = obj.getString("sender")
                    val body = obj.getString("body")

                    try {
                        val response = service.ingestSms("Bearer $token", SmsPayload(body, sender))
                        if (response.isSuccessful && response.body()?.success == true) {
                            Log.d("SmsReceiver", "Flushed offline SMS successfully")
                            val newCount = sharedPrefs.getInt("total_synced", 0) + 1
                            sharedPrefs.edit().putInt("total_synced", newCount).apply()
                        } else if (response.code() == 401) {
                            // Stop flushing on auth failure
                            break
                        } else {
                            remainingQueue.put(obj)
                        }
                    } catch (e: Exception) {
                        remainingQueue.put(obj)
                    }
                }
                sharedPrefs.edit().putString("offline_sms_queue", remainingQueue.toString()).apply()
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error flushing offline SMS queue", e)
            }
        }
    }
}
