package com.smartspend.app

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smartspend.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Main entry point for the SmartSpend Android companion app.
 *
 * Manages two UI states via ViewBinding:
 * - Login screen: email/password form that authenticates against the FastAPI backend
 * - Dashboard: shows sync stats, connection status, and actions (test SMS, logout)
 *
 * JWT tokens are persisted in SharedPreferences. If a valid token exists on launch,
 * the login screen is skipped entirely.
 */
class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefs: SharedPreferences

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "total_synced" || key == "last_sms" || key == "jwt_token") {
            updateDashboard()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Toast.makeText(this, "SMS Permissions Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "SMS Permissions Denied — auto-sync won't work", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefs = getSharedPreferences("smart_spend_prefs", Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefsListener)

        checkPermissions()
        setupListeners()
        navigateToCorrectScreen()
    }

    // ──────────────── Navigation ────────────────

    /**
     * Checks SharedPreferences for an existing JWT token.
     * If found, skips login and shows the dashboard directly.
     */
    private fun navigateToCorrectScreen() {
        val token = sharedPrefs.getString("jwt_token", null)
        if (token.isNullOrEmpty()) {
            showLogin()
        } else {
            showDashboard()
        }
    }

    /**
     * Switches the visible section to the login form.
     */
    private fun showLogin() {
        binding.loginSection.visibility = View.VISIBLE
        binding.dashboardSection.visibility = View.GONE
        // Clear input fields
        binding.etEmail.text?.clear()
        binding.etPassword.text?.clear()
        binding.tvLoginError.visibility = View.GONE
    }

    /**
     * Switches the visible section to the dashboard and refreshes displayed data.
     */
    private fun showDashboard() {
        binding.loginSection.visibility = View.GONE
        binding.dashboardSection.visibility = View.VISIBLE
        updateDashboard()
    }

    // ──────────────── Listeners ────────────────

    /**
     * Wires up click listeners for all interactive elements.
     */
    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        binding.btnTestSms.setOnClickListener {
            sendTestSms()
        }

        binding.btnLogout.setOnClickListener {
            performLogout()
        }
    }

    // ──────────────── Login ────────────────

    /**
     * Validates input fields and calls the /auth/login endpoint.
     * On success, stores the JWT token and user email in SharedPreferences
     * and transitions to the dashboard.
     */
    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            showLoginError("Please enter both email and password")
            return
        }

        // Disable button and show loading state
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = getString(R.string.btn_logging_in)
        binding.tvLoginError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.login(email, password)

                runOnUiThread {
                    if (response.isSuccessful) {
                        val loginResponse = response.body()
                        if (loginResponse != null) {
                            val oldEmail = sharedPrefs.getString("user_email", "")
                            val edit = sharedPrefs.edit()
                                .putString("jwt_token", loginResponse.access_token)
                                .putString("user_email", email)
                            
                            if (oldEmail != email) {
                                edit.putInt("total_synced", 0)
                                    .putString("last_sms", getString(R.string.label_no_sms))
                            }
                            edit.apply()

                            Toast.makeText(this@MainActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                            showDashboard()
                        } else {
                            showLoginError("Empty response from server")
                        }
                    } else {
                        val errorMsg = when (response.code()) {
                            401 -> "Incorrect email or password"
                            422 -> "Invalid request format"
                            else -> "Login failed: ${response.code()}"
                        }
                        showLoginError(errorMsg)
                    }

                    // Reset button state
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = getString(R.string.btn_login)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showLoginError("Connection error: ${e.localizedMessage}")
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = getString(R.string.btn_login)
                }
            }
        }
    }

    /**
     * Displays an error message on the login form.
     */
    private fun showLoginError(message: String) {
        binding.tvLoginError.text = message
        binding.tvLoginError.visibility = View.VISIBLE
    }

    // ──────────────── Dashboard ────────────────

    /**
     * Reads stats from SharedPreferences and updates all dashboard UI elements.
     */
    private fun updateDashboard() {
        val lastSms = sharedPrefs.getString("last_sms", getString(R.string.label_no_sms))
        val totalSynced = sharedPrefs.getInt("total_synced", 0)
        val userEmail = sharedPrefs.getString("user_email", "—")
        val token = sharedPrefs.getString("jwt_token", "")

        binding.tvLastSms.text = lastSms
        binding.tvTotalSynced.text = totalSynced.toString()
        binding.tvUserEmail.text = userEmail

        // Connection status based on token presence
        if (!token.isNullOrEmpty()) {
            binding.tvStatus.text = getString(R.string.status_connected)
            binding.viewStatusDot.setBackgroundColor(
                ContextCompat.getColor(this, R.color.emerald_success)
            )
        } else {
            binding.tvStatus.text = getString(R.string.status_disconnected)
            binding.viewStatusDot.setBackgroundColor(
                ContextCompat.getColor(this, R.color.rose_error)
            )
        }
    }

    // ──────────────── Test SMS ────────────────

    /**
     * Sends a hardcoded HDFC bank SMS to the backend using the stored JWT token.
     * Handles 401 by clearing the token and switching back to login.
     */
    private fun sendTestSms() {
        val token = sharedPrefs.getString("jwt_token", "") ?: ""

        if (token.isEmpty()) {
            Toast.makeText(this, "Not authenticated — please login first", Toast.LENGTH_SHORT).show()
            showLogin()
            return
        }

        val randomAmount = (50..2500).random()
        val merchants = listOf("Blinkit", "Swiggy", "Zomato", "Amazon", "Uber", "DMart", "BigBasket")
        val randomMerchant = merchants.random()
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        val testSms = "ICICI Bank Acct XX373 debited for Rs $randomAmount.00 on 10-Aug-26; $randomMerchant credited. UPI:$timestamp."
        val testSender = "AD-ICICIB"

        binding.btnTestSms.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.ingestSms(
                    "Bearer $token",
                    SmsPayload(testSms, testSender)
                )
                runOnUiThread {
                    if (response.isSuccessful) {
                        val respBody = response.body()
                        if (respBody != null && respBody.success) {
                            val newCount = sharedPrefs.getInt("total_synced", 0) + 1
                            sharedPrefs.edit()
                                .putString("last_sms", testSms)
                                .putInt("total_synced", newCount)
                                .apply()

                            val cat = respBody.transaction?.category ?: "Expense"
                            Toast.makeText(
                                this@MainActivity,
                                "Synced ₹$randomAmount at $randomMerchant ($cat)! ✅",
                                Toast.LENGTH_LONG
                            ).show()
                            updateDashboard()
                        } else {
                            val errMsg = respBody?.message ?: "Sync rejected by server"
                            Toast.makeText(this@MainActivity, errMsg, Toast.LENGTH_LONG).show()
                        }
                    } else if (response.code() == 401) {
                        Toast.makeText(this@MainActivity, "Session expired — please login again", Toast.LENGTH_LONG).show()
                        sharedPrefs.edit().remove("jwt_token").remove("user_email").apply()
                        showLogin()
                    } else {
                        Toast.makeText(this@MainActivity, "Server Error (${response.code()})", Toast.LENGTH_LONG).show()
                    }
                    binding.btnTestSms.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connection Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    binding.btnTestSms.isEnabled = true
                }
            }
        }
    }

    // ──────────────── Logout ────────────────

    /**
     * Clears all stored credentials and navigates back to the login screen.
     */
    private fun performLogout() {
        sharedPrefs.edit()
            .remove("jwt_token")
            .remove("user_email")
            .remove("total_synced")
            .remove("last_sms")
            .apply()

        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
        showLogin()
    }

    // ──────────────── Permissions ────────────────

    /**
     * Requests SMS permissions if not already granted.
     */
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

    override fun onDestroy() {
        super.onDestroy()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onResume() {
        super.onResume()
        // Re-check token validity on resume (token may have been cleared by SmsReceiver)
        val token = sharedPrefs.getString("jwt_token", null)
        if (token.isNullOrEmpty() && binding.dashboardSection.visibility == View.VISIBLE) {
            showLogin()
        } else if (!token.isNullOrEmpty() && binding.dashboardSection.visibility == View.VISIBLE) {
            updateDashboard()
        }
    }
}
