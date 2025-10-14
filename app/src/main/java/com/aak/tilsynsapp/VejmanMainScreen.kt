package com.aak.tilsynsapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VejmanMainScreen(viewModel: VejmanViewModel, onNavigateToRegelrytteren: () -> Unit) {
    val context = LocalContext.current

    val rows by viewModel.rows.collectAsState()
    val loading by viewModel.loadingStatus.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedRow = remember { mutableStateOf<VejmanKassenRow?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val searchQuery = remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    var filterExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("Ny") }
    val filterOptions = listOf("Ny", "Til fakturering", "Fakturer ikke", "Faktureret")

    val filteredRows = rows.filter { row ->
        val query = searchQuery.value.trim().lowercase()
        if (query.isEmpty()) return@filter true

        listOfNotNull(
            row.henstillingId,
            row.forseelse,
            row.cvr?.toString(),
            row.firmanavn,
            row.adresse
        ).any { fieldValue ->
            fieldValue.lowercase().contains(query)
        }
    }


    val pullRefreshState = rememberPullToRefreshState()
    val onRefresh = {
        if (!isRefreshing) {
            coroutineScope.launch {
                viewModel.refreshDataAsync()
            }
        }
    }

    if (loginState != LoginState.LoggedIn) {
        LoginScreen(viewModel)
        return
    }

    LaunchedEffect(Unit) {
        viewModel.preloadAndMaybeRefresh()
    }

    if (selectedRow.value != null) {
        EditNyFakturaScreen(
            row = selectedRow.value!!,
            onBack = { selectedRow.value = null },
            onSubmit = { updatedRow, newStatus ->
                coroutineScope.launch {
                    val success = viewModel.updateRow(context, updatedRow, newStatus)
                    selectedRow.value = null
                    if (success) {
                        viewModel.preloadAndMaybeRefresh(force = true)
                        snackbarHostState.showSnackbar("Rækken er opdateret")
                    } else {
                        snackbarHostState.showSnackbar("Fejl under opdatering", withDismissAction = true)
                    }
                }
            }
        )
    } else {
        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        snackbarData = data
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = true,
                        onClick = {},
                        icon = { Icon(Icons.Default.Receipt, contentDescription = "Fakturering") },
                        label = { Text("Fakturering") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToRegelrytteren,
                        icon = { Icon(Icons.Default.Route, contentDescription = "RegelRytteren") },
                        label = { Text("RegelRytteren") }
                    )
                }
            }
        ) { innerPadding ->
            PullToRefreshBox(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                onRefresh = onRefresh,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        isRefreshing = isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search + Filter Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery.value,
                            onValueChange = { searchQuery.value = it },
                            label = { Text("Søg") },
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        ExposedDropdownMenuBox(
                            expanded = filterExpanded,
                            onExpandedChange = {
                                if (!isRefreshing) filterExpanded = !filterExpanded
                            }
                        ) {
                            OutlinedTextField(
                                value = selectedFilter,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Status") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(type = MenuAnchorType.PrimaryEditable)
                                    .width(180.dp)
                                    .height(64.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = filterExpanded,
                                onDismissRequest = { filterExpanded = false }
                            ) {
                                filterOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedFilter = option
                                            viewModel.setActiveFilter(option)
                                            filterExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (loading != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = loading ?: "")
                        }
                    } else if (filteredRows.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Der er ingen fakturaer i \"$selectedFilter\"")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = onRefresh) {
                                    Text("Opdater data")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp)
                        ) {
                            items(filteredRows) { row ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .clickable { selectedRow.value = row },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    ListItem(
                                        headlineContent = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = row.adresse ?: "Ukendt adresse",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .weight(1f) // 👈 gives it flexible space but keeps date visible
                                                        .padding(end = 8.dp)
                                                )
                                                Text(
                                                    text = formatDate(row.startdato),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Clip // 👈 no ellipsis needed for date
                                                )
                                            }
                                        },
                                        supportingContent = {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    row.forseelse ?: "-",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    row.firmanavn ?: "-",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "-"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val output = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        output.format(parser.parse(raw)!!)
    } catch (_: Exception) {
        "-"
    }
}

