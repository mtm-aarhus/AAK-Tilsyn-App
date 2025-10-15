package com.aak.tilsynsapp

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNyFakturaScreen(
    row: VejmanKassenRow,
    onBack: () -> Unit,
    onSubmit: (updatedRow: VejmanKassenRow, newStatus: String?) -> Unit
) {
    val context = LocalContext.current
    val status = row.fakturaStatus ?: "Ny"
    val isEditable = status == "Ny"

    val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    val backendFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    val parsedStartDate = remember(row.startdato) {
        try { row.startdato?.let { backendFormat.parse(it) } } catch (_: Exception) { null }
    }

    var kvadratmeter by remember { mutableStateOf(row.kvadratmeter?.toString() ?: "") }
    var tilladelsestype by remember { mutableStateOf(row.tilladelsestype ?: "") }

    var slutdatoText by remember {
        mutableStateOf(row.slutdato?.let {
            runCatching { formatter.format(backendFormat.parse(it)!!) }.getOrDefault("")
        } ?: "")
    }
    var isDateValid by remember { mutableStateOf(true) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    fun markUnsaved() { hasUnsavedChanges = true }

    BackHandler {
        if (hasUnsavedChanges && isEditable) showUnsavedDialog = true else onBack()
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Gem ændringer?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    val updated = row.copy(
                        kvadratmeter = kvadratmeter.replace(",", ".").toFloatOrNull(),
                        tilladelsestype = tilladelsestype,
                        slutdato = runCatching {
                            backendFormat.format(formatter.parse(slutdatoText)!!)
                        }.getOrNull()
                    )
                    onSubmit(updated, null)
                }) { Text("Ja") }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false; onBack() }) {
                    Text("Nej")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rediger tilladelse") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges && isEditable) showUnsavedDialog = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tilbage")
                    }
                },
                actions = {
                    if (isEditable && hasUnsavedChanges) {
                        IconButton(onClick = {
                            val updated = row.copy(
                                kvadratmeter = kvadratmeter.replace(",", ".").toFloatOrNull(),
                                tilladelsestype = tilladelsestype,
                                slutdato = runCatching {
                                    backendFormat.format(formatter.parse(slutdatoText)!!)
                                }.getOrNull()
                            )
                            onSubmit(updated, null)
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Gem kladde")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(padding)
                .padding(16.dp)
        ) {
            listOf(
                "Adresse" to row.adresse,
                "Forseelse" to row.forseelse,
                "Firmanavn" to row.firmanavn,
                "CVR" to row.cvr?.toString(),
                "HenstillingId" to row.henstillingId
            ).forEach { (label, value) ->
                OutlinedTextField(
                    value = value ?: "-",
                    onValueChange = {},
                    label = { Text(label) },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            OutlinedTextField(
                value = parsedStartDate?.let { formatter.format(it) } ?: "-",
                onValueChange = {},
                label = { Text("Startdato") },
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            if (status == "Ny") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = slutdatoText,
                        onValueChange = {
                            slutdatoText = it
                            isDateValid = runCatching { formatter.parse(it) }.isSuccess
                            markUnsaved()
                        },
                        isError = !isDateValid,
                        label = { Text("Slutdato (dag-måned-år)") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        val calendar = Calendar.getInstance()

                        val parsedSlutdato = runCatching { formatter.parse(slutdatoText) }.getOrNull()
                        val parsedStartdato = parsedStartDate

                        if (parsedSlutdato == null || (parsedStartdato != null && parsedSlutdato == parsedStartdato)) {
                            calendar.time = Date()
                        } else {
                            calendar.time = parsedSlutdato
                        }

                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                calendar.set(y, m, d)
                                slutdatoText = formatter.format(calendar.time)
                                isDateValid = true
                                markUnsaved()
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Vælg dato")
                    }

                }
            } else {
                OutlinedTextField(
                    value = slutdatoText,
                    onValueChange = {},
                    label = { Text("Slutdato") },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            var isKvadratmeterValid by remember { mutableStateOf(true) }

            OutlinedTextField(
                value = kvadratmeter,
                onValueChange = {
                    val filtered = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                    kvadratmeter = filtered
                    isKvadratmeterValid = filtered.replace(",", ".").toFloatOrNull() != null
                    if (isEditable) markUnsaved()
                },
                isError = !isKvadratmeterValid,
                label = { Text("Kvadratmeter") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = {
                    if (!isKvadratmeterValid)
                        Text("Ugyldigt tal – kun decimaltal tilladt (brug . eller ,)",
                            color = MaterialTheme.colorScheme.error)
                },
                readOnly = !isEditable,
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            if (status == "Ny") {
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = tilladelsestype,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Vælg tilladelsestype") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        tilladelsestyper.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    tilladelsestype = type
                                    markUnsaved()
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = tilladelsestype,
                    onValueChange = {},
                    label = { Text("Tilladelsestype") },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))

            when (status) {
                "Ny" -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val updated = row.copy(
                                        kvadratmeter = kvadratmeter.replace(",", ".").toFloatOrNull(),
                                        tilladelsestype = tilladelsestype,
                                        slutdato = runCatching {
                                            backendFormat.format(formatter.parse(slutdatoText)!!)
                                        }.getOrNull()
                                    )
                                    onSubmit(updated, "Fakturer ikke")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Fakturer ikke")
                            }

                            Button(
                                onClick = {
                                    val updated = row.copy(
                                        kvadratmeter = kvadratmeter.replace(",", ".").toFloatOrNull(),
                                        tilladelsestype = tilladelsestype,
                                        slutdato = runCatching {
                                            backendFormat.format(formatter.parse(slutdatoText)!!)
                                        }.getOrNull()
                                    )
                                    onSubmit(updated, "Til fakturering")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Send til fakturering")
                            }

                        }

                    }
                }

                "Til fakturering" -> {
                    Button(
                        onClick = {
                            val updated = row.copy(fakturaStatus = "Ny")
                            onSubmit(updated, "Ny")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fortryd fakturering")
                    }
                }

                "Fakturer ikke" -> {
                    Button(
                        onClick = {
                            val updated = row.copy(fakturaStatus = "Ny")
                            onSubmit(updated, "Ny")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Markér som ny igen")
                    }
                }

                "Faktureret" -> {
                    // No buttons
                }
            }
        }
    }
}

val tilladelsestyper = listOf(
    "Henstilling Stillads m2", "Henstilling Byggeplads m2", "Henstilling Bygninger m2",
    "Henstilling Container m2", "Henstilling Kran m2", "Henstilling Lift m2",
    "Henstilling Materiel m2", "Henstilling Skurvogn m2", "Henstilling Afmærkning m2"
)