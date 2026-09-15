package com.smartspend.app.ui.addtransaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartspend.app.CategoryListsResponse
import com.smartspend.app.RetrofitClient
import com.smartspend.app.TransactionCreatePayload
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun AddTransactionScreen(onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf(CategoryListsResponse(emptyList(), emptyList())) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var type by remember { mutableStateOf("debit") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var paidTo by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val response = RetrofitClient.apiService.getCategoryLists()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                categories = body
                category = body.debit.firstOrNull().orEmpty()
            } else {
                error = "Could not load categories (${response.code()})."
            }
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Could not load categories."
        } finally {
            loading = false
        }
    }

    fun resetForm(keepType: Boolean = true) {
        amount = ""
        date = LocalDate.now().toString()
        paidTo = ""
        notes = ""
        if (!keepType) type = "debit"
        category = if (type == "debit") categories.debit.firstOrNull().orEmpty() else categories.credit.firstOrNull().orEmpty()
    }

    fun save(addAnother: Boolean) {
        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null || amountValue <= 0.0 || date.isBlank() || paidTo.isBlank() || category.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("Amount, date, paid-to, and category are required.") }
            return
        }
        saving = true
        scope.launch {
            try {
                val payload = TransactionCreatePayload(
                    amount = amountValue,
                    type = type,
                    category = category,
                    merchant = paidTo.trim(),
                    date = "${date.trim()}T12:00:00Z",
                    notes = notes.takeIf { it.isNotBlank() }
                )
                val response = RetrofitClient.apiService.createTransactionNoAuth(payload)
                if (response.isSuccessful) {
                    snackbarHostState.showSnackbar("Transaction saved.")
                    if (addAnother) resetForm() else onBack()
                } else {
                    snackbarHostState.showSnackbar("Save failed (${response.code()}).")
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(e.localizedMessage ?: "Save failed.")
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFFF7F8FB),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(title = "Add transaction", onBack = onBack)
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = type == "debit",
                            onClick = {
                                type = "debit"
                                category = categories.debit.firstOrNull().orEmpty()
                            },
                            label = { Text("Debit") }
                        )
                        FilterChip(
                            selected = type == "credit",
                            onClick = {
                                type = "credit"
                                category = categories.credit.firstOrNull().orEmpty()
                            },
                            label = { Text("Credit") }
                        )
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Amount") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Date") },
                        supportingText = { Text("YYYY-MM-DD") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = paidTo,
                        onValueChange = { paidTo = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (type == "credit") "Received from" else "Paid to") },
                        singleLine = true
                    )
                    CategoryDropdown(
                        category = category,
                        categories = if (type == "debit") categories.debit else categories.credit,
                        onCategoryChange = { category = it }
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes") },
                        minLines = 3
                    )
                    if (loading) Text("Loading categories...", color = Color(0xFF667085))
                    if (error != null) Text(error.orEmpty(), color = Color(0xFFB42318))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            enabled = !saving && !loading,
                            onClick = { save(addAnother = false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (saving) "Saving" else "Save")
                        }
                        OutlinedButton(
                            enabled = !saving && !loading,
                            onClick = { save(addAnother = true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save & Add Another")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    category: String,
    categories: List<String>,
    onCategoryChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = category,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            label = { Text("Category") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onCategoryChange(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Manual entry", color = Color(0xFF667085))
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
