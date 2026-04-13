package com.aak.tilsynsapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: TilsynViewModel,
    onNavigateToTilsyn: () -> Unit,
    onNavigateToRegelrytteren: () -> Unit
) {
    val items by viewModel.historyItems.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val loading by viewModel.loadingStatus.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshDataAsync()
    }

    val pullRefreshState = rememberPullToRefreshState()
    val onRefresh = {
        viewModel.refreshDataAsync()
    }

    val filteredItems = remember(items, searchQuery, showHidden) {
        val query = searchQuery.trim().lowercase()
        items.filter { item ->
            val matchesSearch = query.isEmpty() || 
                item.displayStreet.lowercase().contains(query) ||
                item.displaySecondaryInfo.lowercase().contains(query) ||
                item.displayCaseNumber.lowercase().contains(query) ||
                item.id.lowercase().contains(query)
            
            val matchesHidden = if (showHidden) true else item.fakturaStatus != "Fakturer ikke"
            
            matchesSearch && matchesHidden
        }.sortedByDescending { it.displayEndDate }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToTilsyn,
                    icon = { Icon(Icons.Default.Receipt, contentDescription = "Tilsyn") },
                    label = { Text("Tilsyn") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToRegelrytteren,
                    icon = { Icon(Icons.Default.Route, contentDescription = "RegelRytteren") },
                    label = { Text("RegelRytteren") }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Historik") },
                    label = { Text("Historik") }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Søg i historik...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = showHidden,
                    onCheckedChange = { showHidden = it }
                )
                Text("Vis skjulte henstillinger (Fakturer ikke)", style = MaterialTheme.typography.bodyMedium)
            }

            PullToRefreshBox(
                modifier = Modifier.fillMaxSize(),
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                onRefresh = onRefresh
            ) {
                if (loading != null && items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Ingen historik fundet")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredItems) { item ->
                            HistoryCard(item, viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("AssignedValueIsNeverRead")
@Composable
fun HistoryCard(item: TilsynItem, viewModel: TilsynViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var showInspectDialog by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE")
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE")
    val coroutineScope = rememberCoroutineScope()

    if (showInspectDialog) {
        InspectionDialog(
            item = item,
            viewModel = viewModel,
            onDismiss = { showInspectDialog = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.displayStreet, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(item.displaySecondaryInfo, style = MaterialTheme.typography.bodyMedium)
                }
                Surface(
                    color = (if (item.type == "henstilling") Color(0xFFFF9800) else Color(0xFF4CAF50)).copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        item.typeLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.type == "henstilling") Color(0xFFFF9800) else Color(0xFF4CAF50),
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sluttede: ${tilsynFormatDate(item.displayEndDate)}", style = MaterialTheme.typography.bodySmall)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showInspectDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(28.dp))
                    }
                    
                    if (item.type == "henstilling") {
                        val status = item.fakturaStatus
                        if (status == "Til fakturering") {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.updateRow(context, item, "Ny", "Fortrød fakturering")
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Fortryd fakturering", fontSize = 12.sp)
                            }
                        } else if (status == "Fakturer ikke") {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    viewModel.updateRow(context, item, "Ny")
                                }
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo, 
                                    contentDescription = "Gendan (Vis)",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                TilsynExpandedDetails(item)
            }
        }
    }
}
