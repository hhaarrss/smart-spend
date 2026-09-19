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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartspend.app.HomeBudgetSnapshotData
import com.smartspend.app.HomeCategoryData
import com.smartspend.app.HomeData
import com.smartspend.app.HomeRecentTransactionData
import com.smartspend.app.RetrofitClient
import com.smartspend.app.ui.components.MerchantAvatar
import com.smartspend.app.ui.permission.rememberSmsPermissionsGranted
import com.smartspend.app.ui.theme.SmartSpendTheme
import com.smartspend.app.ui.theme.TabularAmount
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

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
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                containerColor = SmartSpendTheme.colors.accent,
                contentColor = SmartSpendTheme.colors.onAccent,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add", fontWeight = FontWeight.Black)
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
        // Generous bottom padding so the FAB never covers the last row.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { GreetingRow(data.user.full_name, data.user.email, onAccount) }
        item { SpendHero(data) }
        item { InsightStrip(data) }

        if (data.overview.needs_review_count > 0) {
            item { NeedsReviewBanner(data.overview.needs_review_count, onCategories) }
        }

        item { AutoSyncCard(onEnableAutoSync) }

        if (data.top_categories.isNotEmpty()) {
            item {
                SectionCard(title = "Where it's going", actionLabel = "Categories", onAction = onCategories) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val biggest = data.top_categories.maxOf { it.spent }.coerceAtLeast(0.01)
                        data.top_categories.take(4).forEach { CategoryRow(it, biggest) }
                    }
                }
            }
        }

        if (data.recent_transactions.isNotEmpty()) {
            item {
                SectionCard(title = "Recent", actionLabel = "Trends", onAction = onTrends) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        data.recent_transactions.take(5).forEach { TransactionRow(it) }
                    }
                }
            }
        }

        if (data.budget_snapshot.isNotEmpty()) {
            item {
                SectionCard(title = "Budgets", actionLabel = "Manage", onAction = onBudget) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        data.budget_snapshot.take(4).forEach { BudgetRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingRow(fullName: String?, email: String?, onAccount: () -> Unit) {
    val displayName = fullName?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore("@")?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        ?: "there"
    val initial = displayName.firstOrNull()?.uppercase() ?: "S"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                greetingFor(LocalTime.now()).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = SmartSpendTheme.colors.inkMuted
            )
            Text(
                displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onAccount),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initial,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/**
 * The one number the screen exists to show. Everything else on Home is context for it,
 * so it gets the display size and the only saturated surface on the page.
 */
@Composable
private fun SpendHero(data: HomeData) {
    val overview = data.overview
    val onHero = MaterialTheme.colorScheme.onPrimary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "SPENT IN ${monthLabel(data.month, data.year).uppercase(Locale.getDefault())}",
                style = MaterialTheme.typography.labelMedium,
                color = onHero.copy(alpha = 0.75f)
            )
            Text(
                moneyWhole(overview.total_spent),
                style = MaterialTheme.typography.displayLarge,
                color = onHero,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            MomPill(overview.mom_change_percent, overview.total_spent)

            if (overview.total_income > 0.0) {
                Spacer(Modifier.height(2.dp))
                IncomeUsageBar(
                    spent = overview.total_spent,
                    income = overview.total_income,
                    onHero = onHero
                )
            }
        }
    }
}

/**
 * A raw "-12.4% MoM" makes the reader do the work. The rupee difference plus a plain-word
 * comparison is the same fact in the form people actually think about it.
 */
@Composable
private fun MomPill(momPercent: Double, totalSpent: Double) {
    if (momPercent == 0.0) return

    val spendingLess = momPercent < 0
    // total_spent is this month at (100 + mom)% of last month; recover the rupee gap.
    val previous = if (momPercent > -100.0) totalSpent / (1 + momPercent / 100.0) else 0.0
    val delta = (totalSpent - previous).absoluteValue

    val tint = if (spendingLess) SmartSpendTheme.colors.positive else SmartSpendTheme.colors.negative
    val verb = if (spendingLess) "less" else "more"

    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.22f)
    ) {
        Text(
            text = "${if (spendingLess) "▼" else "▲"}  ${moneyWhole(delta)} $verb than last month",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
    }
}

/** How much of what came in has gone back out — the fastest read on whether this month is fine. */
@Composable
private fun IncomeUsageBar(spent: Double, income: Double, onHero: Color) {
    val ratio = (spent / income).coerceIn(0.0, 1.0).toFloat()
    val remaining = (income - spent).coerceAtLeast(0.0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape),
            color = onHero,
            trackColor = onHero.copy(alpha = 0.25f),
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "${(ratio * 100).roundToInt()}% of income used",
                style = MaterialTheme.typography.bodySmall,
                color = onHero.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            Text(
                "${moneyWhole(remaining)} left",
                style = MaterialTheme.typography.bodySmall,
                color = onHero,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Burn rate and runway — the two derived numbers that change behaviour mid-month. */
@Composable
private fun InsightStrip(data: HomeData) {
    val today = LocalDate.now()
    val isCurrentMonth = today.year == data.year && today.monthValue == data.month
    val daysInMonth = runCatching { YearMonth.of(data.year, data.month).lengthOfMonth() }
        .getOrDefault(30)
    val daysElapsed = if (isCurrentMonth) today.dayOfMonth else daysInMonth
    val daysLeft = (daysInMonth - daysElapsed).coerceAtLeast(0)
    val perDay = if (daysElapsed > 0) data.overview.total_spent / daysElapsed else 0.0

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatTile(
            modifier = Modifier.weight(1f),
            value = moneyWhole(perDay),
            label = "a day, on average"
        )
        StatTile(
            modifier = Modifier.weight(1f),
            value = if (isCurrentMonth) "$daysLeft" else "$daysInMonth",
            label = if (isCurrentMonth) "days left this month" else "days in this month"
        )
    }
}

@Composable
private fun StatTile(modifier: Modifier = Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = SmartSpendTheme.colors.inkMuted
            )
        }
    }
}

@Composable
private fun NeedsReviewBanner(count: Int, onReview: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = SmartSpendTheme.colors.cautionContainer)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onReview)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 transaction needs a category" else "$count transactions need a category",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Tag them once and we'll remember next time",
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartSpendTheme.colors.inkMuted
                )
            }
            Text(
                "Review",
                style = MaterialTheme.typography.labelLarge,
                color = SmartSpendTheme.colors.caution
            )
        }
    }
}

@Composable
private fun AutoSyncCard(onEnableAutoSync: () -> Unit) {
    val autoSyncActive = rememberSmsPermissionsGranted()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (autoSyncActive) {
                SmartSpendTheme.colors.positiveContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (autoSyncActive) "Auto-sync is on" else "Auto-sync is off",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (autoSyncActive) {
                        "Bank SMS get logged the moment they arrive"
                    } else {
                        "Log bank SMS automatically instead of by hand"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartSpendTheme.colors.inkMuted
                )
            }
            if (!autoSyncActive) {
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onEnableAutoSync,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Turn on", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (actionLabel != null && onAction != null) {
                    Text(
                        actionLabel,
                        modifier = Modifier.clickable(onClick = onAction),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun TransactionRow(tx: HomeRecentTransactionData) {
    val isCredit = tx.type.equals("credit", ignoreCase = true)

    Row(verticalAlignment = Alignment.CenterVertically) {
        MerchantAvatar(category = tx.category, merchant = tx.merchant, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.merchant?.takeIf { it.isNotBlank() } ?: tx.category,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${tx.category} · ${formatDate(tx.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = SmartSpendTheme.colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (isCredit) "+${moneyWhole(tx.amount)}" else moneyWhole(tx.amount),
            style = MaterialTheme.typography.titleMedium.merge(TabularAmount),
            color = if (isCredit) SmartSpendTheme.colors.positive else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Bars are scaled against the biggest category rather than total spend: against the total
 * every bar is short and they all look alike, which defeats the comparison.
 */
@Composable
private fun CategoryRow(category: HomeCategoryData, biggestSpend: Double) {
    val colors = SmartSpendTheme.categories[category.category]
    val ratio = (category.spent / biggestSpend).coerceIn(0.0, 1.0).toFloat()

    Row(verticalAlignment = Alignment.CenterVertically) {
        MerchantAvatar(category = category.category, size = 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row {
                Text(
                    category.category,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    moneyWhole(category.spent),
                    style = MaterialTheme.typography.titleMedium.merge(TabularAmount),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = colors.accent,
                trackColor = SmartSpendTheme.colors.subtleSurface,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
private fun BudgetRow(budget: HomeBudgetSnapshotData) {
    val percent = budget.percent_used
    val ratio = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    val barColor = when {
        percent > 100.0 -> SmartSpendTheme.colors.negative
        percent >= 80.0 -> SmartSpendTheme.colors.caution
        else -> SmartSpendTheme.colors.positive
    }
    val overspend = budget.spent - budget.limit

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                budget.category,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${percent.roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = barColor
            )
        }
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = barColor,
            trackColor = SmartSpendTheme.colors.subtleSurface,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        Text(
            text = if (overspend > 0) {
                "${moneyWhole(overspend)} over ${moneyWhole(budget.limit)}"
            } else {
                "${moneyWhole(budget.spent)} of ${moneyWhole(budget.limit)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (overspend > 0) SmartSpendTheme.colors.negative else SmartSpendTheme.colors.inkMuted
        )
    }
}

@Composable
private fun HomeSkeleton() {
    val transition = rememberInfiniteTransition(label = "home-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(6) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (index == 0) 190.dp else 86.dp)
                    .clip(
                        if (index == 0) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large
                    )
                    .background(SmartSpendTheme.colors.subtleSurface.copy(alpha = alpha))
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
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Couldn't load your spending",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SmartSpendTheme.colors.inkMuted
                )
                OutlinedButton(onClick = onRetry, shape = MaterialTheme.shapes.small) {
                    Text("Try again", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Whole rupees. Paise widen every amount on screen and tell the user nothing. */
private val inrWhole: NumberFormat =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())
        .apply { maximumFractionDigits = 0 }

private fun moneyWhole(value: Double): String = inrWhole.format(value.absoluteValue)

private fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private fun monthLabel(month: Int, year: Int): String = runCatching {
    YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MMMM"))
}.getOrDefault("this month")

private fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return "Recent"
    return try {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMM"))
    } catch (_: Exception) {
        value.take(10)
    }
}
