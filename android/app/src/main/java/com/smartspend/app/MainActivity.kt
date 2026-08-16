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
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartspend.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main entry point for the SmartSpend Android companion app.
 *
 * Features:
 * - Tab 1 (Insights & Analytics): Monthly Spend Total, Category Breakdown, MoM AI Insights.
 * - Tab 2 (Transactions Feed): Scrollable list of all synced bank expenses.
 * - Tab 3 (Sync Hub): Device pairing, SMS listener status, test sync trigger.
 */
class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefs: SharedPreferences

    private val transactionAdapter = TransactionAdapter()
    private val categoryAdapter = CategoryAdapter()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "total_synced" || key == "last_sms" || key == "jwt_token") {
            updateSyncHubStats()
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

        setupRecyclerViews()
        setupTabNavigation()
        checkPermissions()
        setupListeners()
        navigateToCorrectScreen()
    }

    private fun setupRecyclerViews() {
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = transactionAdapter

        binding.rvCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCategories.adapter = categoryAdapter
    }

    // ──────────────── Tab Navigation ────────────────

    private fun setupTabNavigation() {
        binding.tabAnalytics.setOnClickListener { switchTab(0) }
        binding.tabFeed.setOnClickListener { switchTab(1) }
        binding.tabSyncHub.setOnClickListener { switchTab(2) }
    }

    private fun switchTab(tabIndex: Int) {
        val activeColor = ContextCompat.getColor(this, R.color.indigo_light)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)

        binding.tabAnalytics.setTextColor(if (tabIndex == 0) activeColor else inactiveColor)
        binding.tabFeed.setTextColor(if (tabIndex == 1) activeColor else inactiveColor)
        binding.tabSyncHub.setTextColor(if (tabIndex == 2) activeColor else inactiveColor)

        binding.viewAnalytics.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        binding.viewFeed.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        binding.viewSyncHub.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE

        // Refresh data when switching to Analytics or Feed
        if (tabIndex == 0 || tabIndex == 1) {
            fetchDashboardData()
        }
    }

    // ──────────────── Screen Navigation ────────────────

    private fun navigateToCorrectScreen() {
        val token = sharedPrefs.getString("jwt_token", null)
        if (token.isNullOrEmpty()) {
            showLogin()
        } else {
            showDashboard()
        }
    }

    private fun showLogin() {
        binding.loginSection.visibility = View.VISIBLE
        binding.dashboardSection.visibility = View.GONE
        binding.etEmail.text?.clear()
        binding.etPassword.text?.clear()
        binding.tvLoginError.visibility = View.GONE
    }

    private fun showDashboard() {
        binding.loginSection.visibility = View.GONE
        binding.dashboardSection.visibility = View.VISIBLE
        updateSyncHubStats()
        switchTab(0) // Default to Analytics Tab
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener { performLogin() }
        binding.btnTestSms.setOnClickListener { sendTestSms() }
        binding.btnLogout.setOnClickListener { performLogout() }
    }

    // ──────────────── Login ────────────────

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            showLoginError("Please enter both email and password")
            return
        }

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

    private fun showLoginError(message: String) {
        binding.tvLoginError.text = message
        binding.tvLoginError.visibility = View.VISIBLE
    }

    // ──────────────── Data Fetching & Sync ────────────────

    private fun fetchDashboardData() {
        val token = sharedPrefs.getString("jwt_token", "") ?: return
        val authHeader = "Bearer $token"
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        lifecycleScope.launch {
            try {
                // 1. Fetch Transactions List
                val txResponse = RetrofitClient.apiService.getTransactions(authHeader, limit = 50)
                if (txResponse.isSuccessful && txResponse.body() != null) {
                    val txList = txResponse.body()!!
                    runOnUiThread {
                        transactionAdapter.updateData(txList)
                        binding.tvFeedCount.text = "${txList.size} Transactions"
                    }
                }

                // 2. Fetch Category Summary for Current Month
                val catResponse = RetrofitClient.apiService.getCategorySummary(authHeader, currentMonth)
                if (catResponse.isSuccessful && catResponse.body() != null) {
                    val catMap = catResponse.body()!!
                    val totalSpend = catMap.values.sum()
                    runOnUiThread {
                        categoryAdapter.updateData(catMap)
                        binding.tvTotalMonthlySpend.text = "₹%.2f".format(totalSpend)
                    }
                }

                // 3. Fetch Insights Summary
                val insightsResponse = RetrofitClient.apiService.getInsightsSummary(authHeader)
                if (insightsResponse.isSuccessful && insightsResponse.body() != null) {
                    val insights = insightsResponse.body()!!
                    val changes = insights.spending_changes
                    runOnUiThread {
                        if (!changes.isNullOrEmpty()) {
                            val first = changes[0]
                            val dirSymbol = if (first.direction == "up") "📈" else "📉"
                            binding.tvInsightsText.text = "$dirSymbol Your ${first.category} spend is ${first.direction} by ${first.change_percent.toInt()}% compared to last month."
                        } else {
                            binding.tvInsightsText.text = "✅ Spending is balanced across all categories this month."
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore transient errors while fetching
            }
        }
    }

    private fun updateSyncHubStats() {
        val lastSms = sharedPrefs.getString("last_sms", getString(R.string.label_no_sms))
        val userEmail = sharedPrefs.getString("user_email", "—")

        binding.tvUserEmail.text = userEmail
        binding.tvLastSms.text = lastSms
    }

    // ──────────────── Test SMS ────────────────

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

                            // Immediately re-fetch analytics to sync mobile and web views
                            fetchDashboardData()
                            updateSyncHubStats()
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
        val token = sharedPrefs.getString("jwt_token", null)
        if (token.isNullOrEmpty() && binding.dashboardSection.visibility == View.VISIBLE) {
            showLogin()
        } else if (!token.isNullOrEmpty() && binding.dashboardSection.visibility == View.VISIBLE) {
            fetchDashboardData()
            updateSyncHubStats()
            lifecycleScope.launch {
                SmsReceiver.flushOfflineQueue(this@MainActivity, token)
            }
        }
    }
}
