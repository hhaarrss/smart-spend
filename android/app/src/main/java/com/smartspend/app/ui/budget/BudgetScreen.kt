package com.smartspend.app.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartspend.app.BudgetLimitData
import com.smartspend.app.BudgetSetPayload
import com.smartspend.app.BudgetUtilizationData
import com.smartspend.app.OverallBudgetPayload
import com.smartspend.app.RetrofitClient
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BudgetScreen(onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var budgets by remember { mutableStateOf<List<BudgetLimitData>>(emptyList()) }
    var utilization by remember { mutableStateOf<List<BudgetUtilizationData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }
    var overallBudget by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var newLimit by remember { mutableStateOf("") }

    suspend fun refresh() {
        loading = true
        try {
            val budgetResponse = RetrofitClient.apiService.getBudgetsNoAuth()
            val utilizationResponse = RetrofitClient.apiService.getBudgetUtilization()
            val overallResponse = RetrofitClient.apiService.getOverallBudget()
            if (budgetResponse.isSuccessful) budgets = budgetResponse.body().orEmpty()
            if (utilizationResponse.isSuccessful) utilization = utilizationResponse.body().orEmpty()
            if (overallResponse.isSuccessful) {
                overallBudget = overallResponse.body()?.monthly_limit?.let { plainNumber(it) }.orEmpty()
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun addBudget() {
        val limit = newLimit.toDoubleOrNull()
        if (newCategory.isBlank() || limit == null || limit <= 0.0) {
            scope.launch { snackbarHostState.showSnackbar("Category and positive limit are required.") }
            return
        }
        scope.launch {
            val response = RetrofitClient.apiService.setBudgetNoAuth(
                BudgetSetPayload(category = newCategory.trim(), monthly_limit = limit)
            )
            if (response.isSuccessful) {
                newCategory = ""
                newLimit = ""
                showAdd = false
                refresh()
                snackbarHostState.showSnackbar("Budget saved.")
            } else {
                snackbarHostState.showSnackbar("Budget save failed (${response.code()}).")
            }
        }
    }

    fun saveOverallBudget() {
        val limit = overallBudget.toDoubleOrNull()
        if (limit == null || limit <= 0.0) {
            scope.launch { snackbarHostState.showSnackbar("Enter a positive overall budget.") }
            return
        }
        scope.launch {
            val response = RetrofitClient.apiService.setOverallBudget(OverallBudgetPayload(monthly_limit = limit))
            if (response.isSuccessful) {
                overallBudget = response.body()?.monthly_limit?.let { plainNumber(it) } ?: overallBudget
                snackbarHostState.showSnackbar("Overall budget saved.")
            } else {
                snackbarHostState.showSnackbar("Overall budget save failed (${response.code()}).")
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFFF7F8FB),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = !showAdd }) {
                Icon(Icons.Default.Add, contentDescription = "Add category budget")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header("Budget", onBack)
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Overall budget", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = overallBudget,
                        onValueChange = { overallBudget = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Monthly limit") },
                        supportingText = { Text("Independent from category budgets") },
                        singleLine = true
                    )
                    Button(onClick = { saveOverallBudget() }) {
                        Text("Save overall budget")
                    }
                }
            }
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Per-category budgets", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showAdd = !showAdd }) {
                            Icon(Icons.Default.Add, contentDescription = "Add category budget")
                        }
                    }
                    if (showAdd) {
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Category") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newLimit,
                            onValueChange = { newLimit = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Limit") },
                            singleLine = true
                        )
                        TextButton(onClick = { addBudget() }) { Text("Save category budget") }
                    }
                    if (loading) Text("Loading budgets...", color = Color(0xFF667085))
                    utilization.forEach { item -> BudgetRow(item) }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(item: BudgetUtilizationData) {
    val progress = (item.percent_used / 100.0).coerceIn(0.0, 1.0).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.category, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(
                "${item.percent_used.toInt()}% used",
                color = budgetTextColor(item.percent_used)
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = budgetBarColor(item.percent_used),
            trackColor = Color(0xFFE4E7EC)
        )
        Text("${money(item.spent)} of ${money(item.limit)}", color = Color(0xFF667085))
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Limits and utilization", color = Color(0xFF667085))
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

private fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).format(value)

private fun plainNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

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
