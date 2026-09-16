package com.smartspend.app.ui.trends

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartspend.app.CategoriesResponse
import com.smartspend.app.InsightsSummaryData
import com.smartspend.app.RetrofitClient
import com.smartspend.app.SpendingChangeItem
import com.smartspend.app.TransactionData
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.max

private enum class TrendPeriod(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    THREE_MONTHS("3mo"),
    SIX_MONTHS("6mo"),
    YEAR("1yr")
}

private data class TrendFilters(
    val showBy: String = SHOW_BY_ALL,
    val categories: Set<String> = emptySet()
)

private data class ChartBucket(val label: String, val amount: Double)

private const val SHOW_BY_ALL = "All spending"
private const val SHOW_BY_CATEGORY = "Selected categories"

@Composable
private fun FilterIcon(tint: Color = Color(0xFF1D2939)) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        // Funnel top line
        drawLine(tint, Offset(w * 0.12f, h * 0.22f), Offset(w * 0.88f, h * 0.22f), stroke, StrokeCap.Round)
        // Middle line
        drawLine(tint, Offset(w * 0.28f, h * 0.50f), Offset(w * 0.72f, h * 0.50f), stroke, StrokeCap.Round)
        // Bottom line
        drawLine(tint, Offset(w * 0.44f, h * 0.78f), Offset(w * 0.56f, h * 0.78f), stroke, StrokeCap.Round)
    }
}

@Composable
fun TrendsScreen(
    onBack: () -> Unit,
    onBudget: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var showFilterPanel by remember { mutableStateOf(false) }
    var period by remember { mutableStateOf(TrendPeriod.MONTH) }
    var appliedFilters by remember { mutableStateOf(TrendFilters()) }
    var categoryLists by remember { mutableStateOf(CategoriesResponse()) }

    LaunchedEffect(Unit) {
        // GET /categories → {"debit":[...],"credit":[...]}
        runCatching { RetrofitClient.apiService.getCategories() }
            .getOrNull()
            ?.body()
            ?.let { categoryLists = it }
    }

    if (showFilterPanel) {
        BackHandler { showFilterPanel = false }
        FilterPanel(
            lists = categoryLists,
            initial = appliedFilters,
            onClear = { appliedFilters = TrendFilters() },
            onApply = {
                appliedFilters = it
                showFilterPanel = false
            },
            onClose = { showFilterPanel = false }
        )
        return
    }

    Scaffold(containerColor = Color(0xFFF7F8FB)) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF1D2939))
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trends", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Spend over time and insights", color = Color(0xFF667085))
                }
                if (tab == 0) {
                    IconButton(onClick = { showFilterPanel = true }) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            FilterIcon(tint = Color(0xFF1D2939))
                            if (appliedFilters.categories.isNotEmpty()) {
                                Canvas(modifier = Modifier.size(8.dp)) {
                                    drawCircle(Color(0xFF047857))
                                }
                            }
                        }
                    }
                }
            }

            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.White,
                contentColor = Color(0xFF047857),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[tab]),
                        color = Color(0xFF047857)
                    )
                }
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = {
                        Text(
                            "Chart",
                            fontWeight = if (tab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (tab == 0) Color(0xFF047857) else Color(0xFF667085)
                        )
                    }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = {
                        Text(
                            "Insights",
                            fontWeight = if (tab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (tab == 1) Color(0xFF047857) else Color(0xFF667085)
                        )
                    }
                )
            }

            if (tab == 0) {
                ChartTab(
                    period = period,
                    onPeriodChange = { period = it },
                    filters = appliedFilters,
                    onBudget = onBudget
                )
            } else {
                InsightsTab(onBudget = onBudget)
            }
        }
    }
}

@Composable
private fun ChartTab(
    period: TrendPeriod,
    onPeriodChange: (TrendPeriod) -> Unit,
    filters: TrendFilters,
    onBudget: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var buckets by remember { mutableStateOf<List<ChartBucket>>(emptyList()) }

    LaunchedEffect(period, filters) {
        loading = true
        error = null
        try {
            val range = periodRange(period)
            val txs = fetchPeriodTransactions(range.first, range.second)
            val filtered = txs.filter { matchesFilters(it, filters) }
            buckets = bucketTransactions(filtered, period, range.first, range.second)
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Could not load trend chart."
            buckets = emptyList()
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrendPeriod.entries.forEach { option ->
                FilterChip(
                    selected = period == option,
                    onClick = { onPeriodChange(option) },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF047857),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(chartTitle(period, filters), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF1D2939))
                when {
                    loading -> Text("Loading chart…", color = Color(0xFF667085))
                    error != null -> Text(error.orEmpty(), color = Color(0xFFB42318))
                    buckets.all { it.amount == 0.0 } -> EmptyState("No spending recorded in this period for the current filters.")
                    else -> PeriodBarChart(buckets)
                }
            }
        }

        Button(
            onClick = onBudget,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF047857),
                contentColor = Color.White
            )
        ) {
            Text("Set monthly budget", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun InsightsTab(onBudget: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<InsightsSummaryData?>(null) }

    LaunchedEffect(Unit) {
        // Reuses GET /insights/summary — compare_month_spending/get_mom_change,
        // detect_recurring, detect_anomalies, get_budget_alerts. No new aggregates.
        loading = true
        try {
            val response = RetrofitClient.apiService.getInsightsSummaryNoAuth()
            if (response.isSuccessful) {
                data = response.body()
            } else {
                error = "Insights failed to load (${response.code()})."
            }
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Could not load insights."
        } finally {
            loading = false
        }
    }

    val movers = remember(data) { categoryMovers(data?.spending_changes.orEmpty()) }
    val spikes = remember(data) { spikeAnomalies(data?.anomalies.orEmpty()) }
    val recurring = data?.recurring.orEmpty()
    val alertCount = data?.budget_alerts.orEmpty().size
    val ready = !loading && error == null

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (loading) item { EmptyState("Loading insights…") }
        if (error != null) item { Text(error.orEmpty(), color = Color(0xFFB42318)) }
        if (!ready) return@LazyColumn

        // Budget-attention banner: count + link only — do not duplicate spent/limit numbers
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (alertCount > 0) Color(0xFFFFF7ED) else Color(0xFFF0FDF4)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBudget() }
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (alertCount == 0) "All budgets on track"
                            else "$alertCount ${if (alertCount == 1) "category" else "categories"} near/over limit",
                            fontWeight = FontWeight.Bold,
                            color = if (alertCount > 0) Color(0xFF9A3412) else Color(0xFF166534),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Open Budget →",
                            color = if (alertCount > 0) Color(0xFFC2410C) else Color(0xFF15803D),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = if (alertCount == 0) "Open Budget to review or change monthly limits."
                        else "Open Budget to review those categories. Limits and spent amounts live there only.",
                        color = if (alertCount > 0) Color(0xFFB45309) else Color(0xFF166534),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Category movers: top 3-5 by MoM % change, respecting MIN_MEANINGFUL_BASELINE
        item {
            InsightSection(title = "Category movers") {
                if (movers.isEmpty()) {
                    EmptyState("Not enough data to compare categories month-over-month.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        movers.forEach { MoverRow(it) }
                    }
                }
            }
        }

        // Recurring & Subscriptions list
        item {
            InsightSection(title = "Recurring & subscriptions") {
                if (recurring.isEmpty()) {
                    EmptyState("No recurring merchants detected yet.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recurring.forEach { RecurringRow(it) }
                    }
                }
            }
        }

        // Spike & Anomaly Notifications: labeled distinctly from budget warnings
        item {
            InsightSection(title = "Spike & anomaly notifications") {
                if (spikes.isEmpty()) {
                    EmptyState("No spending spikes flagged this month.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        spikes.forEach { SpikeRow(it) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(
    lists: CategoriesResponse,
    initial: TrendFilters,
    onClear: () -> Unit,
    onApply: (TrendFilters) -> Unit,
    onClose: () -> Unit
) {
    var showBy by remember { mutableStateOf(initial.showBy) }
    var selected by remember { mutableStateOf(initial.categories) }
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(SHOW_BY_ALL, SHOW_BY_CATEGORY)

    Scaffold(containerColor = Color(0xFFF7F8FB)) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Show trends by", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1D2939))
                    Text("Filter the Chart tab", color = Color(0xFF667085))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF1D2939))
                }
            }

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = showBy,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    label = { Text("Show trends by") },
                    readOnly = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1D2939),
                        unfocusedTextColor = Color(0xFF1D2939)
                    ),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = Color(0xFF1D2939)) },
                            onClick = {
                                showBy = option
                                if (option == SHOW_BY_ALL) {
                                    selected = emptySet()
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }

            Text("Debit categories", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFF1D2939))
            if (lists.debit.isEmpty()) {
                EmptyState("Debit category list is empty. Pull from GET /categories after the backend is reachable.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lists.debit.forEach { name ->
                        FilterChip(
                            selected = name in selected,
                            enabled = showBy == SHOW_BY_CATEGORY,
                            onClick = {
                                selected = toggle(selected, name)
                            },
                            label = { Text(name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF047857),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            Text("Credit categories", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFF1D2939))
            if (lists.credit.isEmpty()) {
                EmptyState("Credit category list is empty. Pull from GET /categories after the backend is reachable.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lists.credit.forEach { name ->
                        FilterChip(
                            selected = name in selected,
                            enabled = showBy == SHOW_BY_CATEGORY,
                            onClick = {
                                selected = toggle(selected, name)
                            },
                            label = { Text(name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF047857),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        showBy = SHOW_BY_ALL
                        selected = emptySet()
                        onClear()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Clear", color = Color(0xFF344054), fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = { onApply(TrendFilters(showBy, selected)) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF047857),
                        contentColor = Color.White
                    )
                ) { Text("Apply", fontWeight = FontWeight.Bold, color = Color.White) }
            }
        }
    }
}

@Composable
private fun InsightSection(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF1D2939))
            content()
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Text(message, color = Color(0xFF667085), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun MoverRow(item: SpendingChangeItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.category, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF1D2939))
            Text(
                if (item.not_enough_data || item.change_percent == null) "Baseline spending too low"
                else if (item.direction == "up") "Spending up vs last month"
                else "Spending down vs last month",
                color = Color(0xFF667085),
                style = MaterialTheme.typography.bodySmall
            )
        }
        val percent = item.change_percent
        if (item.not_enough_data || percent == null || percent.isNaN() || percent.isInfinite() || percent.absoluteValue > 999.0) {
            Text("Not enough data", color = Color(0xFF667085), fontWeight = FontWeight.Medium)
        } else {
            val color = if (item.direction == "up") Color(0xFFB42318) else Color(0xFF047857)
            val sign = if (item.direction == "up") "+" else "−"
            Text("$sign${"%.1f".format(percent)}%", color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecurringRow(raw: Map<String, Any>) {
    val merchant = raw["merchant"]?.toString() ?: "Unknown merchant"
    val amount = numberOf(raw["amount"])
    val frequency = raw["frequency"]?.toString()?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: "Recurring"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(merchant, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF1D2939))
            Text(frequency, color = Color(0xFF667085), style = MaterialTheme.typography.bodySmall)
        }
        Text(money(amount), fontWeight = FontWeight.Bold, color = Color(0xFF1D2939))
    }
}

@Composable
private fun SpikeRow(raw: Map<String, Any>) {
    val category = raw["category"]?.toString() ?: "Category"
    val amount = numberOf(raw["amount"])
    val avg = numberOf(raw["avg"])
    val merchant = raw["merchant"]?.toString() ?: "$category spending spike"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "UNUSUAL SPIKE",
                color = Color(0xFFB54708),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall
            )
            Text("·", color = Color(0xFF98A2B3))
            Text(
                "Not a budget warning",
                color = Color(0xFF667085),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(merchant, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D2939))
        Text(
            "${money(amount)} vs typical average of ${money(avg)} in $category",
            color = Color(0xFF475467),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PeriodBarChart(buckets: List<ChartBucket>) {
    val maxAmount = buckets.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
    val totalAmount = buckets.sumOf { it.amount }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "Total Spent",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF667085)
            )
            Text(
                money(totalAmount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D2939)
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val count = buckets.size
            val gap = 6f
            val barWidth = ((size.width - gap * (count + 1)) / count).coerceIn(4f, 48f)
            val totalBarsWidth = count * barWidth + (count - 1) * gap
            val startX = (size.width - totalBarsWidth) / 2f

            // Draw subtle horizontal grid lines
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = size.height * (i.toFloat() / gridLines)
                drawLine(
                    color = Color(0xFFF2F4F7),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            buckets.forEachIndexed { index, bucket ->
                val barHeight = ((bucket.amount / maxAmount) * (size.height - 12f)).toFloat().coerceAtLeast(if (bucket.amount > 0) 6f else 2f)
                val left = startX + index * (barWidth + gap)
                val top = size.height - barHeight
                drawRoundRect(
                    color = if (bucket.amount > 0) Color(0xFF047857) else Color(0xFFE4E7EC),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            buckets.filterIndexed { index, _ ->
                buckets.size <= 8 || index == 0 || index == buckets.lastIndex || index % max(1, buckets.size / 4) == 0
            }.forEach { Text(it.label, color = Color(0xFF667085), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

private fun categoryMovers(items: List<SpendingChangeItem>): List<SpendingChangeItem> {
    // Only rank categories with a real MoM % from get_mom_change().
    // MIN_MEANINGFUL_BASELINE cases arrive as not_enough_data / null percent — never as a huge %.
    return items
        .filter { item ->
            !item.not_enough_data &&
                item.change_percent != null &&
                !item.change_percent.isNaN() &&
                !item.change_percent.isInfinite() &&
                item.change_percent.absoluteValue <= 999.0
        }
        .sortedByDescending { it.change_percent?.absoluteValue ?: 0.0 }
        .take(5)
}

private fun spikeAnomalies(items: List<Map<String, Any>>): List<Map<String, Any>> {
    return items.filter { item ->
        val kind = item["kind"]?.toString()?.lowercase(Locale.getDefault())
        kind != "budget" && item["merchant"]?.toString()?.contains("Over Budget", ignoreCase = true) != true
    }
}

private suspend fun fetchPeriodTransactions(start: LocalDate, end: LocalDate): List<TransactionData> {
    // Chart buckets are derived from existing GET /transactions pages — no new aggregate endpoint.
    val all = mutableListOf<TransactionData>()
    var page = 1
    while (page <= 20) {
        val response = RetrofitClient.apiService.getTransactionsNoAuth(
            page = page,
            limit = 50,
            startDate = start.toString(),
            endDate = end.toString(),
            includeTransfers = false
        )
        val body = response.body() ?: break
        all.addAll(body.transactions)
        if (!body.has_more) break
        page++
    }
    return all
}

private fun matchesFilters(tx: TransactionData, filters: TrendFilters): Boolean {
    if (filters.showBy == SHOW_BY_CATEGORY && filters.categories.isNotEmpty()) {
        return filters.categories.any { it.equals(tx.category, ignoreCase = true) }
    }
    return tx.type.equals("debit", ignoreCase = true)
}

private fun periodRange(period: TrendPeriod): Pair<LocalDate, LocalDate> {
    val end = LocalDate.now()
    val start = when (period) {
        TrendPeriod.WEEK -> end.minusDays(6)
        TrendPeriod.MONTH -> end.minusDays(29)
        TrendPeriod.THREE_MONTHS -> end.minusMonths(3).plusDays(1)
        TrendPeriod.SIX_MONTHS -> end.minusMonths(6).plusDays(1)
        TrendPeriod.YEAR -> end.minusYears(1).plusDays(1)
    }
    return start to end
}

private fun bucketTransactions(
    txs: List<TransactionData>,
    period: TrendPeriod,
    start: LocalDate,
    end: LocalDate
): List<ChartBucket> {
    val amounts = mutableMapOf<String, Double>()
    txs.forEach { tx ->
        val day = parseTxDate(tx.date) ?: return@forEach
        if (day.isBefore(start) || day.isAfter(end)) return@forEach
        val key = bucketKey(day, period)
        amounts[key] = (amounts[key] ?: 0.0) + tx.amount
    }
    return bucketKeys(period, start, end).map { ChartBucket(labelForKey(it, period), amounts[it] ?: 0.0) }
}

private fun bucketKey(day: LocalDate, period: TrendPeriod): String = when (period) {
    TrendPeriod.WEEK, TrendPeriod.MONTH -> day.toString()
    TrendPeriod.THREE_MONTHS -> day.minusDays((day.dayOfWeek.value % 7).toLong()).toString()
    TrendPeriod.SIX_MONTHS, TrendPeriod.YEAR -> YearMonth.from(day).toString()
}

private fun bucketKeys(period: TrendPeriod, start: LocalDate, end: LocalDate): List<String> {
    return when (period) {
        TrendPeriod.WEEK, TrendPeriod.MONTH -> {
            val days = ChronoUnit.DAYS.between(start, end).toInt()
            (0..days).map { start.plusDays(it.toLong()).toString() }
        }
        TrendPeriod.THREE_MONTHS -> {
            generateSequence(start) { it.plusWeeks(1).takeIf { next -> !next.isAfter(end) } }.map { bucketKey(it, period) }.distinct().toList()
        }
        TrendPeriod.SIX_MONTHS, TrendPeriod.YEAR -> {
            generateSequence(YearMonth.from(start)) { current ->
                current.plusMonths(1).takeIf { !it.isAfter(YearMonth.from(end)) }
            }.map { it.toString() }.toList()
        }
    }
}

private fun labelForKey(key: String, period: TrendPeriod): String = when (period) {
    TrendPeriod.WEEK, TrendPeriod.MONTH -> runCatching { LocalDate.parse(key).format(DateTimeFormatter.ofPattern("d")) }.getOrDefault(key.takeLast(2))
    TrendPeriod.THREE_MONTHS -> runCatching { LocalDate.parse(key).format(DateTimeFormatter.ofPattern("d MMM")) }.getOrDefault(key)
    TrendPeriod.SIX_MONTHS, TrendPeriod.YEAR -> runCatching { YearMonth.parse(key).format(DateTimeFormatter.ofPattern("MMM")) }.getOrDefault(key)
}

private fun chartTitle(period: TrendPeriod, filters: TrendFilters): String {
    val scope = if (filters.showBy == SHOW_BY_CATEGORY && filters.categories.isNotEmpty()) {
        filters.categories.take(2).joinToString() + if (filters.categories.size > 2) " +" else ""
    } else {
        "All spending"
    }
    return "${period.label} · $scope"
}

private fun parseTxDate(raw: String): LocalDate? {
    return runCatching { OffsetDateTime.parse(raw).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
}

private fun toggle(current: Set<String>, value: String): Set<String> =
    if (value in current) current - value else current + value

private fun numberOf(value: Any?): Double = when (value) {
    is Number -> value.toDouble()
    is String -> value.toDoubleOrNull() ?: 0.0
    else -> 0.0
}

private fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).format(value)
