package com.smartspend.app.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartspend.app.HomeBudgetSnapshotData
import com.smartspend.app.HomeCategoryData
import com.smartspend.app.HomeData
import com.smartspend.app.HomeRecentTransactionData
import com.smartspend.app.RetrofitClient
import com.smartspend.app.ui.permission.rememberSmsPermissionsGranted
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

private sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(val data: HomeData) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit = {},
    onBudget: () -> Unit = {},
    onCategories: () -> Unit = {},
    onTrends: () -> Unit = {},
    onAccount: () -> Unit = {},
    onEnableAutoSync: () -> Unit = {}
) {
    var state by remember { mutableStateOf<HomeUiState>(HomeUiState.Loading) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        state = HomeUiState.Loading
        state = try {
            val response = RetrofitClient.apiService.getHomeData()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                HomeUiState.Loaded(body)
            } else {
                HomeUiState.Error("Home data failed to load (${response.code()}).")
            }
        } catch (e: Exception) {
            HomeUiState.Error(e.localizedMessage ?: "Unable to reach SmartSpend.")
        }
    }

    Scaffold(
        containerColor = Color(0xFFF7F8FB),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val current = state) {
                HomeUiState.Loading -> HomeSkeleton()
                is HomeUiState.Loaded -> HomeContent(
                    data = current.data,
                    onBudget = onBudget,
                    onCategories = onCategories,
                    onTrends = onTrends,
                    onAccount = onAccount,
                    onEnableAutoSync = onEnableAutoSync
                )
                is HomeUiState.Error -> HomeError(
                    message = current.message,
                    onRetry = { refreshKey++ }
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    onBudget: () -> Unit,
    onCategories: () -> Unit,
    onTrends: () -> Unit,
    onAccount: () -> Unit,
    onEnableAutoSync: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { TopBar(data.user.full_name, data.user.email, onAccount) }
        item { HeroSection(data, onBudget) }
        item { AutoSyncCard(onEnableAutoSync) }
        item { ModeButtons(onTrends = onTrends, onCategories = onCategories) }
        if (data.overview.needs_review_count > 0) {
            item { NeedsReviewPill(data.overview.needs_review_count) }
        }
        item {
            SectionCard(
                title = "Recent transactions",
                action = "See all"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.recent_transactions.take(5).forEach { TransactionRow(it) }
                }
            }
        }
        item {
            SectionCard(
                title = "Top categories",
                action = "See all"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.top_categories.take(4).forEach { CategoryRow(it, data.overview.total_spent) }
                }
            }
        }
        item {
            SectionCard(title = "Budget snapshot") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    data.budget_snapshot.take(4).forEach { BudgetRow(it) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(fullName: String?, email: String?, onAccount: () -> Unit) {
    val displayName = fullName?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore("@")?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        ?: "SmartSpend"
    val initial = displayName.firstOrNull()?.uppercase() ?: "S"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1F8A70))
                .clickable { onAccount() },
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Good morning", style = MaterialTheme.typography.labelMedium, color = Color(0xFF667085))
            Text(
                displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(enabled = false, onClick = {}) {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF98A2B3))
        }
    }
}

@Composable
private fun HeroSection(data: HomeData, onBudget: () -> Unit) {
    val mom = data.overview.mom_change_percent
    val momLabel = if (mom >= 0) "+${mom.formatPercent()} MoM" else "${mom.formatPercent()} MoM"

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF101828), Color(0xFF1F8A70), Color(0xFFEF9F35))
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total spend", color = Color(0xFFE6F4EF), style = MaterialTheme.typography.labelLarge)
                    Text(
                        money(data.overview.total_spent),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Badge(containerColor = if (mom <= 0) Color(0xFFDCFCE7) else Color(0xFFFFF7ED)) {
                    Text(momLabel, color = if (mom <= 0) Color(0xFF166534) else Color(0xFF9A3412))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Income", color = Color(0xFFD1FAE5), style = MaterialTheme.typography.labelMedium)
                        Text(money(data.overview.total_income), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Button(onClick = onBudget, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Text("Set limit")
                }
            }
        }
    }
}

@Composable
private fun AutoSyncCard(onEnableAutoSync: () -> Unit) {
    val autoSyncActive = rememberSmsPermissionsGranted()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("SMS auto-sync", fontWeight = FontWeight.Bold)
                Text(
                    if (autoSyncActive) "Active — transactions are detected automatically" else "Off — add transactions manually",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF667085)
                )
            }
            if (autoSyncActive) {
                Badge(containerColor = Color(0xFFDCFCE7)) {
                    Text("Active", color = Color(0xFF166534))
                }
            } else {
                Button(onClick = onEnableAutoSync) {
                    Text("Enable auto-sync")
                }
            }
        }
    }
}

@Composable
private fun ModeButtons(
    onTrends: () -> Unit,
    onCategories: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(selected = false, onClick = onTrends, label = { Text("Trends") })
        FilterChip(selected = false, onClick = onCategories, label = { Text("Categories") })
    }
}

@Composable
private fun NeedsReviewPill(count: Int) {
    AssistChip(
        enabled = false,
        onClick = {},
        label = { Text("$count transactions need review") }
    )
}

@Composable
private fun SectionCard(
    title: String,
    action: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (action != null) {
                    TextButton(enabled = false, onClick = {}) {
                        Text(action)
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun TransactionRow(tx: HomeRecentTransactionData) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (tx.type.equals("credit", true)) Color(0xFFE7F8EF) else Color(0xFFFFF2E5)),
            contentAlignment = Alignment.Center
        ) {
            Text(tx.category.take(1).uppercase(), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.merchant ?: tx.category, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("${tx.category} • ${formatDate(tx.date)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085))
        }
        Text(
            text = if (tx.type.equals("credit", true)) "+${money(tx.amount)}" else money(tx.amount),
            color = if (tx.type.equals("credit", true)) Color(0xFF047857) else Color(0xFF101828),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CategoryRow(category: HomeCategoryData, totalSpend: Double) {
    val pct = if (totalSpend > 0.0) (category.spent / totalSpend).coerceIn(0.0, 1.0).toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(category.category, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(money(category.spent), color = Color(0xFF475467))
        }
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = Color(0xFF1F8A70),
            trackColor = Color(0xFFE4E7EC)
        )
    }
}

@Composable
private fun BudgetRow(budget: HomeBudgetSnapshotData) {
    val pct = (budget.percent_used / 100.0).coerceIn(0.0, 1.0).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(budget.category, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("${budget.percent_used.toInt()}% used", color = budgetTextColor(budget.percent_used))
        }
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = budgetBarColor(budget.percent_used),
            trackColor = Color(0xFFE4E7EC)
        )
        Text("${money(budget.spent)} of ${money(budget.limit)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085))
    }
}

@Composable
private fun HomeSkeleton() {
    val transition = rememberInfiniteTransition(label = "home-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(7) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (index == 1) 170.dp else 82.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE4E7EC).copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun HomeError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Could not load Home", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(message, color = Color(0xFF667085))
                OutlinedButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

private fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).format(value.absoluteValue)

private fun budgetBarColor(percentUsed: Double): Color = when {
    percentUsed > 100.0 -> Color(0xFFE5484D)
    percentUsed >= 80.0 -> Color(0xFFF59E0B)
    else -> Color(0xFF1F8A70)
}

private fun budgetTextColor(percentUsed: Double): Color = when {
    percentUsed > 100.0 -> Color(0xFFB42318)
    percentUsed >= 80.0 -> Color(0xFFB54708)
    else -> Color(0xFF475467)
}

private fun Double.formatPercent(): String = "%.1f%%".format(this)

private fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return "Recent"
    return try {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd MMM"))
    } catch (_: Exception) {
        value.take(10)
    }
}
