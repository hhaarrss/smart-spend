package com.smartspend.app.ui.categories

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartspend.app.CategorySummaryItem
import com.smartspend.app.MerchantData
import com.smartspend.app.RetrofitClient
import com.smartspend.app.TransactionData
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val now = remember { LocalDate.now() }
    var tab by remember { mutableStateOf(0) }
    var categories by remember { mutableStateOf<List<CategorySummaryItem>>(emptyList()) }
    var merchants by remember { mutableStateOf<List<MerchantData>>(emptyList()) }
    var txs by remember { mutableStateOf<List<TransactionData>>(emptyList()) }
    var selected by remember { mutableStateOf<CategorySummaryItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val summary = RetrofitClient.apiService.getMonthlyCategorySummaryNoAuth(now.monthValue, now.year)
            val merchantResponse = RetrofitClient.apiService.getMerchants()
            val txResponse = RetrofitClient.apiService.getTransactionsNoAuth(
                page = 1,
                limit = 50,
                month = now.monthValue,
                year = now.year,
                type = "debit",
                includeTransfers = false
            )
            if (summary.isSuccessful) categories = summary.body()?.categories.orEmpty()
            if (merchantResponse.isSuccessful) merchants = merchantResponse.body().orEmpty()
            if (txResponse.isSuccessful) txs = txResponse.body()?.transactions.orEmpty()
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Could not load categories."
        } finally {
            loading = false
        }
    }

    Scaffold(containerColor = Color(0xFFF7F8FB)) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header("Categories", onBack)
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Categories") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Merchants") })
            }
            if (loading) Text("Loading...", color = Color(0xFF667085))
            if (error != null) Text(error.orEmpty(), color = Color(0xFFB42318))
            if (tab == 0) {
                selected?.let { selectedCategory ->
                    CategoryDrillDown(
                        category = selectedCategory,
                        transactions = txs.filter { matchesCategory(it.category, selectedCategory.category) },
                        onBack = { selected = null }
                    )
                } ?: CategoryOverview(categories = categories, onSelect = { selected = it })
            } else {
                MerchantList(merchants)
            }
        }
    }
}

@Composable
private fun CategoryOverview(
    categories: List<CategorySummaryItem>,
    onSelect: (CategorySummaryItem) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PieChart(categories)
                }
            }
        }
        items(categories) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.category, fontWeight = FontWeight.Bold)
                        Text("${item.transaction_count} transactions • ${item.percentage}%", color = Color(0xFF667085))
                    }
                    Text(money(item.total), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CategoryDrillDown(
    category: CategorySummaryItem,
    transactions: List<TransactionData>,
    onBack: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.category, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(money(category.total), color = Color(0xFF667085))
                }
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Monthly spend", fontWeight = FontWeight.Bold)
                    BarChart(category.total, category.budget_limit)
                }
            }
        }
        items(transactions) { tx ->
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tx.merchant ?: tx.category, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatDate(tx.date), color = Color(0xFF667085))
                    }
                    Text(money(tx.amount), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MerchantList(merchants: List<MerchantData>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(merchants) { merchant ->
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(merchant.name, fontWeight = FontWeight.Bold)
                        Text(merchant.category ?: "Uncategorized", color = Color(0xFF667085))
                    }
                    Text("${merchant.count}", color = Color(0xFF475467))
                }
            }
        }
    }
}

@Composable
private fun PieChart(categories: List<CategorySummaryItem>) {
    val colors = listOf(Color(0xFF1F8A70), Color(0xFFEF9F35), Color(0xFFE5484D), Color(0xFF4E79A7), Color(0xFF8E6C88))
    val total = categories.sumOf { it.total }.coerceAtLeast(1.0)
    Box(modifier = Modifier.height(220.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.height(180.dp).fillMaxWidth()) {
            val diameter = size.minDimension
            val topLeft = Offset((size.width - diameter) / 2f, 0f)
            var startAngle = -90f
            categories.forEachIndexed { index, item ->
                val sweep = ((item.total / total) * 360f).toFloat()
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = 42f)
                )
                startAngle += sweep
            }
        }
        Text(money(total), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BarChart(spent: Double, limit: Double) {
    val max = maxOf(spent, limit, 1.0)
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        val barWidth = size.width * 0.28f
        val baseline = size.height
        val spentHeight = (spent / max).toFloat() * size.height
        val limitHeight = (limit / max).toFloat() * size.height
        drawRoundRect(
            color = Color(0xFF1F8A70),
            topLeft = Offset(size.width * 0.18f, baseline - spentHeight),
            size = Size(barWidth, spentHeight)
        )
        drawRoundRect(
            color = Color(0xFFE4E7EC),
            topLeft = Offset(size.width * 0.55f, baseline - limitHeight),
            size = Size(barWidth, limitHeight)
        )
    }
    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        Text("Spent ${money(spent)}")
        Text("Limit ${money(limit)}")
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Monthly breakdown", color = Color(0xFF667085))
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

private fun matchesCategory(raw: String, display: String): Boolean {
    val source = raw.lowercase()
    val target = display.lowercase()
    return source == target ||
        (source == "food" && target.contains("food")) ||
        (source == "travel" && target.contains("transport")) ||
        (source == "utilities" && target.contains("utilities")) ||
        (source == "shopping" && target.contains("shopping")) ||
        (source == "entertainment" && target.contains("entertainment")) ||
        (source == "healthcare" && target.contains("health"))
}

private fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).format(value)

private fun formatDate(value: String): String =
    try {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    } catch (_: Exception) {
        value.take(10)
    }
