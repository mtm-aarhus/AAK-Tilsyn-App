package com.aak.tilsynsapp

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Map
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
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.*

fun prettyType(type: String?): String {
    if (type == null) return "-"
    return when (type) {
        "751_Stillads pr. kvadratmeter"   -> "Stillads (m²)"
        "751_Afmærkning pr.kvadratmeter" -> "Afmærkning (m²)"
        "751_Byggeplads pr.kvadratmeter" -> "Byggeplads (m²)"
        "751_Materiel pr. kvadratmeter"  -> "Materiel (m²)"
        "751_Bygninger pr. kvadratmeter" -> "Bygninger (m²)"
        "751_Lift pr. kvadratmeter"      -> "Lift (m²)"
        "751_Kran pr. kvadratmeter"      -> "Kran (m²)"
        "751_Skurvogn pr. kvadratmeter"  -> "Skurvogn (m²)"
        "751_Container pr. kvadratmeter" -> "Container (m²)"
        else -> type
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TilsynScreen(
    viewModel: TilsynViewModel,
    onNavigateToRegelrytteren: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val items by viewModel.tilsynItems.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val loading by viewModel.loadingStatus.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshDataAsync()
    }

    val pullRefreshState = rememberPullToRefreshState()
    val onRefresh = {
        viewModel.refreshDataAsync()
    }

    val filteredItems = remember(items, searchQuery) {
        val query = searchQuery.trim().lowercase()
        val visibleItems = items.filter { it.hidden != true }
        if (query.isEmpty()) visibleItems
        else visibleItems.filter { 
            it.displayStreet.lowercase().contains(query) ||
            it.displaySecondaryInfo.lowercase().contains(query) ||
            it.displayCaseNumber.lowercase().contains(query) ||
            it.id.lowercase().contains(query)
        }
    }

    val groupedItems = filteredItems.groupBy { it.streetName ?: "Ukendt Vej" }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
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
                    selected = false,
                    onClick = onNavigateToHistory,
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
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Søg på vej, sag, ansøger...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

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
                } else if (groupedItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (searchQuery.isEmpty()) "Ingen udestående tilsyn" else "Ingen match på søgning")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        groupedItems.forEach { (streetGroup, itemsOnStreet) ->
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = streetGroup,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            items(itemsOnStreet) { item ->
                                TilsynCard(item, viewModel, onNavigateToMap)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("AssignedValueIsNeverRead")
@Composable
fun TilsynCard(item: TilsynItem, viewModel: TilsynViewModel, onNavigateToMap: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showInspectDialog by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE")
    val context = LocalContext.current

    if (showInspectDialog) {
        InspectionDialog(
            item = item,
            viewModel = viewModel,
            onDismiss = { showInspectDialog = false }
        )
    }

    val typeColor = when {
        item.type == "henstilling" -> Color(0xFFFF9800) // Orange
        item.vejmanDisplayState == "Færdig tilladelse" -> Color(0xFF2196F3) // Blue
        else -> Color(0xFF4CAF50) // Green
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.displayEquipment, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(item.displaySecondaryInfo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(item.displayStreet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = typeColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        item.typeLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = typeColor,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val endLabel = if (item.type == "henstilling") "Sidst registreret opstillet" else "Slutter"
                Text("$endLabel: ${tilsynFormatDateShort(item.displayEndDate)}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        viewModel.selectMapItem(item)
                        onNavigateToMap()
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { showInspectDialog = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                TilsynExpandedDetails(item)
            }
        }
    }
}

@Composable
fun TilsynDetailRow(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(120.dp), // Unified width
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TilsynDialogDetailRow(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(120.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

fun tilsynFormatDateShort(raw: String?): String {
    if (raw.isNullOrBlank()) return "-"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH)
        val date = parser.parse(raw)
        val formatter = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        formatter.format(date ?: Date())
    } catch (_: Exception) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
            val output = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
            output.format(parser.parse(raw)!!)
        } catch (_: Exception) {
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                val output = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                output.format(parser.parse(raw)!!)
            } catch (_: Exception) {
                raw
            }
        }
    }
}

fun tilsynFormatDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "-"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH)
        val output = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        output.format(parser.parse(raw)!!)
    } catch (_: Exception) {
        try {
            // Fixed: Added support for naive ISO format to keep the clock
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
            val output = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
            output.format(parser.parse(raw)!!)
        } catch (_: Exception) {
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                val output = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                output.format(parser.parse(raw)!!)
            } catch (_: Exception) {
                raw
            }
        }
    }
}
