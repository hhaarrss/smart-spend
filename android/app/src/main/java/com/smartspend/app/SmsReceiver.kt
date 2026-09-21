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
import com.smartspend.app.sms.BankSenderWhitelist
import com.smartspend.app.sms.SmsTransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * BroadcastReceiver that intercepts incoming SMS messages from whitelisted bank senders,
 * parses them locally using SmsTransactionParser, and forwards only structured transaction fields to the backend.
 *
 * Raw SMS text is never transmitted or stored.
 *
 * Includes an Offline Queue to retry failed SMS syncs when internet connectivity restores,
 * and posts a native status bar Notification on successful sync.
 */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.originatingAddress ?: continue
                val messageBody = sms.messageBody ?: continue
                val timestamp = sms.timestampMillis

                Log.d("SmsReceiver", "Received SMS from: $sender")

                if (!BankSenderWhitelist.isWhitelisted(sender)) {
                    Log.d("SmsReceiver", "SMS ignored (sender not in bank whitelist): $sender")
                    continue
                }

                val parsed = SmsTransactionParser.parse(messageBody, sender, timestamp)
                if (parsed != null) {
                    Log.d("SmsReceiver", "Transactional SMS detected! Forwarding structured payload on-device...")
                    sendToBackend(context, parsed.toSmsPayload())
                } else {
                    Log.d("SmsReceiver", "SMS ignored because parsing did not produce a valid transaction")
                }
            }
        }
    }

    private fun isTransactionalSms(sender: String, body: String): Boolean =
        BankSenderWhitelist.isWhitelisted(sender) && SmsTransactionParser.parse(body, sender) != null

    private fun sendToBackend(context: Context, payload: SmsPayload) {
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
                val response = service.ingestSms("Bearer $token", payload)
                if (response.isSuccessful) {
                    val respBody = response.body()
                    if (respBody != null && (respBody.success || respBody.message == "Duplicate transaction detected")) {
                        val tx = respBody.transaction
                        Log.d("SmsReceiver", "Successfully ingested SMS or was duplicate! Transaction ID: ${tx?.id}")
                        sharedPrefs.edit().apply {
                            putString("last_sms", "${payload.transaction_type} ${payload.amount} from ${payload.bank_sender_id}")
                            putInt("total_synced", sharedPrefs.getInt("total_synced", 0) + 1)
                            commit() // Use commit() for synchronous write before process dies
                        }
                        showSyncNotification(context, tx?.amount ?: payload.amount, tx?.merchant ?: payload.merchant_raw, tx?.category)
                    } else {
                        Log.w("SmsReceiver", "Backend rejected SMS: ${respBody?.message ?: "Unknown error"}")
                        queueOfflineSms(context, payload)
                    }
                } else if (response.code() == 401) {
                    Log.w("SmsReceiver", "Received 401 Unauthorized — clearing stored token")
                    sharedPrefs.edit().remove("jwt_token").remove("user_email").commit()
                } else {
                    Log.e("SmsReceiver", "Server error ${response.code()} — queuing SMS for retry")
                    queueOfflineSms(context, payload)
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Network error ingesting SMS — queuing offline", e)
                queueOfflineSms(context, payload)
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
        fun queueOfflineSms(context: Context, payload: SmsPayload) {
            val sharedPrefs = context.getSharedPreferences("smart_spend_prefs", Context.MODE_PRIVATE)
            val queueJsonStr = sharedPrefs.getString("offline_sms_queue", "[]") ?: "[]"
            try {
                val queueArray = JSONArray(queueJsonStr)
                val item = JSONObject().apply {
                    put("amount", payload.amount)
                    put("transaction_type", payload.transaction_type)
                    put("merchant_raw", payload.merchant_raw)
                    put("bank_sender_id", payload.bank_sender_id)
                    put("account_last4", payload.account_last4)
                    put("date", payload.date)
                    put("upi_ref", payload.upi_ref)
                    put("timestamp", System.currentTimeMillis())
                }
                queueArray.put(item)
                sharedPrefs.edit().putString("offline_sms_queue", queueArray.toString()).commit() // Use commit() to ensure disk write
                Log.d("SmsReceiver", "Queued structured SMS payload offline. Queue size: ${queueArray.length()}")
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Failed to queue offline SMS payload", e)
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
                    val payload = SmsPayload(
                        amount = obj.getDouble("amount"),
                        transaction_type = obj.getString("transaction_type"),
                        merchant_raw = optNullableString(obj, "merchant_raw"),
                        bank_sender_id = optNullableString(obj, "bank_sender_id"),
                        account_last4 = optNullableString(obj, "account_last4"),
                        date = obj.getString("date"),
                        upi_ref = optNullableString(obj, "upi_ref")
                    )

                    try {
                        val response = service.ingestSms("Bearer $token", payload)
                        if (response.isSuccessful && (response.body()?.success == true || response.body()?.message == "Duplicate transaction detected")) {
                            Log.d("SmsReceiver", "Flushed offline SMS successfully")
                            val newCount = sharedPrefs.getInt("total_synced", 0) + 1
                            sharedPrefs.edit().putInt("total_synced", newCount).commit()
                        } else if (response.code() == 401) {
                            break
                        } else {
                            remainingQueue.put(obj)
                        }
                    } catch (e: Exception) {
                        remainingQueue.put(obj)
                    }
                }
                sharedPrefs.edit().putString("offline_sms_queue", remainingQueue.toString()).commit()
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error flushing offline SMS queue", e)
            }
        }

        private fun optNullableString(obj: JSONObject, key: String): String? {
            if (!obj.has(key) || obj.isNull(key)) {
                return null
            }
            return obj.optString(key).takeIf { it.isNotBlank() && it != "null" }
        }
    }
}
