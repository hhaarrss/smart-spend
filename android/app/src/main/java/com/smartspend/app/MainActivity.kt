package com.smartspend.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smartspend.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions Denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        updateUI()

        binding.btnTestSms.setOnClickListener {
            sendTestSms()
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    private fun updateUI() {
        val sharedPrefs = getSharedPreferences("smart_spend_prefs", Context.MODE_PRIVATE)
        val lastSms = sharedPrefs.getString("last_sms", "No SMS received yet")
        val totalSynced = sharedPrefs.getInt("total_synced", 0)
        val token = sharedPrefs.getString("jwt_token", "")

        binding.tvLastSms.text = lastSms
        binding.tvTotalSynced.text = "Total Transactions Synced: $totalSynced"
        binding.tvLoginStatus.text = if (token.isNullOrEmpty()) "Login: Not logged in" else "Login: Logged in"
        binding.tvStatus.text = "Status: Connected" // Simple static status for now
    }

    private fun sendTestSms() {
        val sharedPrefs = getSharedPreferences("smart_spend_prefs", Context.MODE_PRIVATE)

        // Always set fresh token
        val freshToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwidXNlcl9pZCI6MSwiZXhwIjoxNzc5Nzk1NDg3fQ.Z9o5Vnkqrys_QnhmtGCr1O87-CTEs2Oayy_4hTihE_Q"
        sharedPrefs.edit().putString("jwt_token", freshToken).apply()

        val testSms = "HDFC Bank: Rs.450.00 debited from A/c XX1234 on 26-05-26. Info: SWIGGY. Avl Bal: Rs.12,340.00"
        val testSender = "HDFCBK"

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.ingestSms(
                    "Bearer $freshToken",
                    SmsPayload(testSms, testSender)
                )
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "Success! ${response.message()}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed: ${response.code()} ${response.message()}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
