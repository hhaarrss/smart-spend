package com.smartspend.app.ui.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.smartspend.app.BuildConfig

private const val PRIVACY_POLICY_URL = "https://hhaarrss.github.io/smart-spend/privacy-policy.html"
private const val TERMS_OF_SERVICE_URL = "https://hhaarrss.github.io/smart-spend/terms.html"
private const val SUPPORT_FAQ_URL = "https://hhaarrss.github.io/smart-spend/support.html"
private const val SUPPORT_EMAIL = "smartspend4support@gmail.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("smart_spend_prefs", Context.MODE_PRIVATE)
    }

    val userEmail = remember {
        sharedPrefs.getString("user_email", "user@smartspend.app") ?: "user@smartspend.app"
    }
    val jwtToken = remember {
        sharedPrefs.getString("jwt_token", null)
    }

    var notificationsEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_notifications_enabled", true))
    }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMailto(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, "SmartSpend Support Request")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No email app found to contact $email", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleLogout() {
        sharedPrefs.edit()
            .remove("jwt_token")
            .remove("user_email")
            .remove("fcm_token")
            .apply()
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}
        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
        onLogout()
    }

    Scaffold(
        containerColor = Color(0xFFF7F8FB),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Account & Profile",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF101828),
                    navigationIconContentColor = Color(0xFF101828)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Profile Section
            SectionHeader(title = "PROFILE")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1F8A70), Color(0xFF101828))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userEmail.firstOrNull()?.uppercase() ?: "S",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userEmail.substringBefore("@")
                                    .replaceFirstChar { it.uppercase() }
                                    .ifEmpty { "SmartSpend User" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF101828)
                            )
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF667085)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF2F4F7))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone",
                            tint = Color(0xFF667085),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Phone Number",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF667085)
                            )
                            Text(
                                "+91 98765 43210 (Stub)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF101828)
                            )
                        }
                        Badge(containerColor = Color(0xFFF2F4F7)) {
                            Text("Stub user", color = Color(0xFF475467), fontSize = 11.sp)
                        }
                    }
                }
            }

            // 2. Preferences Section
            SectionHeader(title = "PREFERENCES")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color(0xFF1F8A70),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Push Notifications",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF101828)
                            )
                            Text(
                                "Budget alerts & daily spend summaries",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF667085)
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = {
                                notificationsEnabled = it
                                sharedPrefs.edit().putBoolean("pref_notifications_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF1F8A70)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF2F4F7))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Theme",
                            tint = Color(0xFF667085),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Theme",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF101828)
                            )
                            Text(
                                "System Default (Non-functional stub)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF667085)
                            )
                        }
                        Badge(containerColor = Color(0xFFFFF4E5)) {
                            Text("Stub", color = Color(0xFFB54708), fontSize = 11.sp)
                        }
                    }
                }
            }

            // 3. Privacy & Data Section (Compliance)
            SectionHeader(title = "PRIVACY & DATA (PLAY STORE COMPLIANCE)")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    ActionRowItem(
                        icon = Icons.Default.Lock,
                        iconTint = Color(0xFF1F8A70),
                        title = "Privacy Policy",
                        subtitle = "Review how your SMS & transaction data is handled",
                        onClick = { openUrl(PRIVACY_POLICY_URL) }
                    )

                    HorizontalDivider(color = Color(0xFFF2F4F7))

                    ActionRowItem(
                        icon = Icons.Default.Info,
                        iconTint = Color(0xFF1F8A70),
                        title = "Terms of Service",
                        subtitle = "SmartSpend terms and conditions",
                        onClick = { openUrl(TERMS_OF_SERVICE_URL) }
                    )

                    HorizontalDivider(color = Color(0xFFF2F4F7))

                    ActionRowItem(
                        icon = Icons.Default.Share,
                        iconTint = Color(0xFF98A2B3),
                        title = "Export my data (CSV)",
                        subtitle = "Download all personal transactions as CSV",
                        badge = "Missing backend endpoint",
                        badgeColor = Color(0xFFFEE2E2),
                        badgeTextColor = Color(0xFF991B1B),
                        enabled = false,
                        onClick = {
                            Toast.makeText(
                                context,
                                "CSV Export endpoint is missing on backend. Flagged for review.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )

                    HorizontalDivider(color = Color(0xFFF2F4F7))

                    ActionRowItem(
                        icon = Icons.Default.Delete,
                        iconTint = Color(0xFFDC2626),
                        title = "Delete my account",
                        titleColor = Color(0xFFDC2626),
                        subtitle = "Permanently purge all data from SmartSpend servers",
                        onClick = { showDeleteDialog = true }
                    )
                }
            }

            // 4. Support Section
            SectionHeader(title = "SUPPORT")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    ActionRowItem(
                        icon = Icons.Default.Info,
                        iconTint = Color(0xFF1F8A70),
                        title = "Help & FAQ",
                        subtitle = "Frequently asked questions and guides",
                        onClick = { openUrl(SUPPORT_FAQ_URL) }
                    )

                    HorizontalDivider(color = Color(0xFFF2F4F7))

                    ActionRowItem(
                        icon = Icons.Default.Email,
                        iconTint = Color(0xFF1F8A70),
                        title = "Contact Support",
                        subtitle = SUPPORT_EMAIL,
                        onClick = { openMailto(SUPPORT_EMAIL) }
                    )
                }
            }

            // 5. About Section
            SectionHeader(title = "ABOUT")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SmartSpend",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF101828)
                        )
                        Spacer(Modifier.weight(1f))
                        Badge(containerColor = Color(0xFFDCFCE7)) {
                            Text(
                                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                color = Color(0xFF166534),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        "SMS Auto-Sync & Expense Analytics for Android",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF667085)
                    )
                    Text(
                        "All SMS parsing happens locally on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1F8A70),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 6. Logout Button
            OutlinedButton(
                onClick = { handleLogout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "Log Out",
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            jwtToken = jwtToken,
            onDismiss = { showDeleteDialog = false },
            onDeleted = {
                showDeleteDialog = false
                sharedPrefs.edit().clear().apply()
                try {
                    FirebaseAuth.getInstance().signOut()
                } catch (_: Exception) {}
                Toast.makeText(context, "Account permanently deleted.", Toast.LENGTH_LONG).show()
                onLogout()
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF667085),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun ActionRowItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    titleColor: Color = Color(0xFF101828),
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = Color(0xFFF2F4F7),
    badgeTextColor: Color = Color(0xFF475467),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) titleColor else Color(0xFF98A2B3)
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Badge(containerColor = badgeColor) {
                        Text(badge, color = badgeTextColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF667085)
            )
        }
        if (enabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Navigate",
                tint = Color(0xFF98A2B3),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
