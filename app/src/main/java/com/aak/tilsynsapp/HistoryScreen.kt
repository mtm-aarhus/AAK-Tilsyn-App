@file:Suppress("RedundantSuppression", "RedundantSuppression")

package com.aak.tilsynsapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import android.content.Intent
import androidx.compose.material3.*
import androidx.core.net.toUri
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
    onNavigateToRegelrytteren: () -> Unit,
    @Suppress("UNUSED_PARAMETER", "unused") onNavigateToHistory: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val items by viewModel.historyItems.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val loading by viewModel.loadingStatus.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    
    val filterOptions = listOf("Henstilling", "Ny till.", "Færdig till.", "Indmeldt")
    var selectedFilters by remember { mutableStateOf(filterOptions.toSet()) }

    LaunchedEffect(Unit) {
        viewModel.refreshDataAsync()
    }

    val pullRefreshState = rememberPullToRefreshState()
    val onRefresh = {
        viewModel.refreshDataAsync()
    }

    val filteredItems = remember(items, searchQuery, showHidden, selectedFilters) {
        val query = searchQuery.trim().lowercase()
        items.filter { item ->
            val matchesSearch = query.isEmpty() || 
                item.displayStreet.lowercase().contains(query) ||
                item.displaySecondaryInfo.lowercase().contains(query) ||
                item.displayCaseNumber.lowercase().contains(query) ||
                item.id.lowercase().contains(query)
            
            val isHidden = item.hidden == true || item.fakturaStatus == "Fakturer ikke"
            val matchesHidden = if (showHidden) true else !isHidden

            val itemType = when {
                item.type == "henstilling" -> "Henstilling"
                item.type == "indmeldt" -> "Indmeldt"
                item.vejmanDisplayState == "Færdig tilladelse" -> "Færdig till."
                else -> "Ny till."
            }
            val matchesType = itemType in selectedFilters
            
            matchesSearch && matchesHidden && matchesType
        }.sortedByDescending { it.endDate }
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
                    onClick = onNavigateToMap,
                    icon = { Icon(Icons.Default.Map, contentDescription = "Kort") },
                    label = { Text("Kort") }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Historik") },
                    label = { Text("Historik") }
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
        Column(modifier = Modifier.padding(innerPadding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 4.dp),
                placeholder = { Text("Søg i historik...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { option ->
                    FilterChip(
                        selected = option in selectedFilters,
                        onClick = {
                            selectedFilters = if (option in selectedFilters) {
                                if (selectedFilters.size > 1) selectedFilters - option else selectedFilters
                            } else {
                                selectedFilters + option
                            }
                        },
                        label = { Text(option, fontSize = 12.sp) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = showHidden,
                    onCheckedChange = { showHidden = it }
                )
                Text("Vis skjulte tilsyn", style = MaterialTheme.typography.bodyMedium)
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
                        HistoryCard(item, viewModel, onNavigateToMap)
                    }
                    }
                }
            }
        }
    }
}

@Suppress("AssignedValueIsNeverRead")
@Composable
fun HistoryCard(item: TilsynItem, viewModel: TilsynViewModel, onNavigateToMap: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showInspectDialog by remember { mutableStateOf(false) }
    var showConfirmFortryd by remember { mutableStateOf(false) }
    var showConfirmUnhide by remember { mutableStateOf(false) }
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

    // Confirmation: Fortryd fakturering
    if (showConfirmFortryd) {
        AlertDialog(
            onDismissRequest = { showConfirmFortryd = false },
            title = { Text("Fortryd fakturering") },
            text = { Text("Er du sikker på at du vil fortryde faktureringen for denne henstilling?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmFortryd = false
                    coroutineScope.launch {
                        viewModel.updateRow(context, item, "Ny", "Fortrød fakturering")
                    }
                }) { Text("Ja, fortryd") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmFortryd = false }) { Text("Annullér") }
            }
        )
    }

    // Confirmation: Unhide
    if (showConfirmUnhide) {
        AlertDialog(
            onDismissRequest = { showConfirmUnhide = false },
            title = { Text("Gendan tilsyn") },
            text = { Text("Er du sikker på at du vil gendanne dette tilsyn, så det vises igen?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmUnhide = false
                    if (item.type == "henstilling") {
                        coroutineScope.launch { viewModel.updateRow(context, item, "Ny") }
                    } else {
                        viewModel.toggleHidePermission(item.id, false) { }
                    }
                }) { Text("Ja, gendan") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmUnhide = false }) { Text("Annullér") }
            }
        )
    }

    val typeColor = when {
        item.type == "henstilling" -> Color(0xFFFF9800) // Orange
        item.type == "indmeldt" -> Color(0xFF00BCD4) // Cyan
        item.vejmanDisplayState == "Færdig tilladelse" -> Color(0xFF2196F3) // Blue
        else -> Color(0xFF4CAF50) // Green
    }

    val isFaktureret = item.type == "henstilling" &&
        (item.fakturaStatus == "Faktureret" || item.fakturaStatus == "Til fakturering")

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.displayStreet,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    val secondary = if (item.type == "indmeldt") item.title.orEmpty() else item.displaySecondaryInfo
                    if (secondary.isNotBlank()) {
                        Text(
                            secondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    color = typeColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        item.typeLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val endLabel = when (item.type) {
                    "indmeldt" -> "Oprettet"
                    "henstilling" -> "Sidst set"
                    else -> "Sluttede"
                }
                val endValue = if (item.type == "indmeldt") item.createdAt else item.displayEndDate
                Text("$endLabel: ${tilsynFormatDate(endValue)}", style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!item.sharepointLink.isNullOrBlank()) {
                        val spContext = LocalContext.current
                        IconButton(onClick = {
                            spContext.startActivity(Intent(Intent.ACTION_VIEW, item.sharepointLink.toUri()))
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.FolderOpen, "Filer", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }

                    IconButton(onClick = {
                        viewModel.selectMapItem(item)
                        onNavigateToMap()
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }

                    if (isFaktureret) {
                        // Faktureret: show a "sent" icon instead of inspect checkmark
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Sendt til Vejmankassen",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        IconButton(onClick = { showInspectDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(28.dp))
                        }
                    }

                    if (item.type == "henstilling") {
                        val status = item.fakturaStatus
                        if (status == "Til fakturering") {
                            TextButton(
                                onClick = { showConfirmFortryd = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Fortryd fakturering", fontSize = 12.sp)
                            }
                        } else if (item.hidden == true) {
                            IconButton(onClick = { showConfirmUnhide = true }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Gendan (Vis)",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else if (item.hidden == true) {
                        IconButton(onClick = { showConfirmUnhide = true }, modifier = Modifier.size(32.dp)) {
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

            AnimatedVisibility(visible = expanded) {
                TilsynExpandedDetails(item)
            }
        }
    }
}
