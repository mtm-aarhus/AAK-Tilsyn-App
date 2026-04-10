package com.aak.tilsynsapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
    viewModel: VejmanViewModel,
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

@Composable
fun HistoryCard(item: TilsynItem, viewModel: VejmanViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var showInspectDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
                        if (status == "Fakturer ikke" || status == "Til fakturering") {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    viewModel.updateRow(context, item, "Ny")
                                }
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo, 
                                    contentDescription = if (status == "Fakturer ikke") "Gendan (Vis)" else "Fortryd fakturering", 
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(thickness = 0.5.dp)
                    if (item.type == "permission") {
                        TilsynDetailRow("Vejman Status", item.vejmanState)
                        TilsynDetailRow("Sag ID", item.caseId)
                        TilsynDetailRow("Sagsnummer", item.caseNumber)
                        TilsynDetailRow("Ansøger", item.applicant)
                        TilsynDetailRow("Marker", item.marker)
                        TilsynDetailRow("Udstyr", item.rovmEquipmentType)
                        TilsynDetailRow("Sagsmappenr", item.applicantFolderNumber)
                        TilsynDetailRow("Ref", item.authorityReferenceNumber)
                        TilsynDetailRow("Vejstatus", item.streetStatus)
                        TilsynDetailRow("Relateret", item.connectedCase)
                        TilsynDetailRow("Initialer", item.initials)
                        TilsynDetailRow("Start", tilsynFormatDate(item.startDate))
                        TilsynDetailRow("Slut", tilsynFormatDate(item.endDate))
                    } else {
                        TilsynDetailRow("Backend Status", item.fakturaStatus)
                        TilsynDetailRow("Sag ID", item.id)
                        TilsynDetailRow("Henstilling ID", item.henstillingId)
                        TilsynDetailRow("Firma", item.firmanavn)
                        TilsynDetailRow("CVR", item.cvr?.toString())
                        TilsynDetailRow("Forseelse", item.forseelse)
                        TilsynDetailRow("Tilladelsestype", item.tilladelsestype)
                        TilsynDetailRow("M2", item.kvadratmeter?.toString())
                        TilsynDetailRow("Start", item.startdatoHenstilling)
                        TilsynDetailRow("Slut", item.slutdatoHenstilling)
                    }

                    // Unified History Section
                    if (!item.inspections.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Tilsynshistorik:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        item.inspections.reversed().forEach { record ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = record.inspectorEmail.substringBefore("@").uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = tilsynFormatDateShort(record.inspectedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!record.selection.isNullOrBlank()) {
                                    Text(
                                        text = "Status: ${record.selection}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (!record.comment.isNullOrBlank()) {
                                    Text(
                                        text = record.comment,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp
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
