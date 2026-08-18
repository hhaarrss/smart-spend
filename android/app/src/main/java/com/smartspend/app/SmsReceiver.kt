package com.smartspend.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * BroadcastReceiver that intercepts incoming SMS messages from bank senders or containing transaction keywords,
 * and forwards them to the SmartSpend backend for automatic transaction ingestion.
 *
 * Includes an Offline Queue to retry failed SMS syncs when internet connectivity restores,
 * and posts a native status bar Notification on successful sync.
 */
class SmsReceiver : BroadcastReceiver() {

    private val bankKeywords = listOf(
        "icici", "hdfc", "sbi", "axis", "kotak", "yes", "pnb", "indus", "canara",
        "paytm", "pytm", "gpay", "bhim", "cred", "idfc", "union", "bob", "rbl",
        "citi", "fed", "amex", "slice", "jupiter", "fi", "onecard", "niyo", "upi", "bank"
    )

    private val transactionKeywords = listOf(
        "debited", "credited", "transferred", "spent", "paid", "withdrawn",
        "received", "vpa", "upi", "a/c", "inr", "rs.", "rs "
    )

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.originatingAddress ?: continue
                val messageBody = sms.messageBody ?: continue

                Log.d("SmsReceiver", "Received SMS from: $sender | Body: $messageBody")

                if (isTransactionalSms(sender, messageBody)) {
                    Log.d("SmsReceiver", "Transactional SMS detected! Forwarding to backend...")
                    sendToBackend(context, sender, messageBody)
                } else {
                    Log.d("SmsReceiver", "SMS ignored (not a bank/transactional SMS)")
                }
            }
        }
    }

    private fun isTransactionalSms(sender: String, body: String): Boolean {
        val sLower = sender.lowercase()
        val bLower = body.lowercase()

        val isSenderMatch = bankKeywords.any { sLower.contains(it) }
        val isBodyMatch = transactionKeywords.any { bLower.contains(it) }

        return isSenderMatch || isBodyMatch
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
                flushOfflineQueue(context, token)

                val response = service.ingestSms("Bearer $token", SmsPayload(body, sender))
                if (response.isSuccessful) {
                    val respBody = response.body()
                    if (respBody != null && respBody.success) {
                        val tx = respBody.transaction
                        Log.d("SmsReceiver", "Successfully ingested SMS! Transaction ID: ${tx?.id}")
                        sharedPrefs.edit().apply {
                            putString("last_sms", body)
                            putInt("total_synced", sharedPrefs.getInt("total_synced", 0) + 1)
                            apply()
                        }
                        showSyncNotification(context, tx?.amount, tx?.merchant, tx?.category)
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

    private fun showSyncNotification(context: Context, amount: Double?, merchant: String?, category: String?) {
        try {
            val channelId = "smartspend_sms_sync"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "SMS Auto-Sync Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies when a payment SMS is auto-synced to SmartSpend"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val amtStr = if (amount != null) "₹%.2f".format(amount) else "Payment"
            val merchStr = merchant ?: "Merchant"
            val catStr = category ?: "General"

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("💳 Payment Auto-Synced!")
                .setContentText("Synced $amtStr to $merchStr ($catStr)")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Failed to show notification", e)
        }
    }

    companion object {
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
