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

fun cleanStreetForGrouping(street: String): String {
    // Address cleaning is now handled by the API
    return street
}

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
    viewModel: VejmanViewModel,
    onNavigateToRegelrytteren: () -> Unit,
    onNavigateToHistory: () -> Unit
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

    val groupedItems = filteredItems.groupBy { cleanStreetForGrouping(it.displayStreet) }

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
                    onClick = onNavigateToRegelrytteren,
                    icon = { Icon(Icons.Default.Route, contentDescription = "RegelRytteren") },
                    label = { Text("RegelRytteren") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToHistory,
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
                                TilsynCard(item, viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TilsynCard(item: TilsynItem, viewModel: VejmanViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var showInspectDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showInspectDialog) {
        InspectionDialog(
            item = item,
            viewModel = viewModel,
            onDismiss = { showInspectDialog = false }
        )
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
                    color = (if (item.type == "henstilling") Color(0xFFFF9800) else Color(0xFF4CAF50)).copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        item.typeLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = if (item.type == "henstilling") Color(0xFFFF9800) else Color(0xFF4CAF50),
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val endLabel = if (item.type == "henstilling") "Sidst registreret opstillet" else "Slutter"
                Text("$endLabel: ${tilsynFormatDateShort(item.displayEndDate)}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        val address = "${item.displayStreet}, Aarhus"
                        val gmmIntentUri = "geo:0,0?q=${Uri.encode(address)}".toUri()
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
                        context.startActivity(mapIntent)
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { showInspectDialog = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(thickness = 0.5.dp)
                    if (item.type == "permission") {
                        TilsynDetailRow("Ansøger", item.applicant)
                        TilsynDetailRow("Marker", item.marker)
                        TilsynDetailRow("Sag ID", item.caseId)
                        TilsynDetailRow("Sagsnummer", item.caseNumber)
                        TilsynDetailRow("Udstyr", item.rovmEquipmentType)
                        TilsynDetailRow("Sagsmappenr", item.applicantFolderNumber)
                        TilsynDetailRow("Ref", item.authorityReferenceNumber)
                        TilsynDetailRow("Vejstatus", item.streetStatus)
                        TilsynDetailRow("Initialer", item.initials)
                        TilsynDetailRow("Relateret", item.connectedCase)
                        TilsynDetailRow("Start", tilsynFormatDate(item.startDate))
                        TilsynDetailRow("Slut", tilsynFormatDate(item.endDate))
                    } else {
                        TilsynDetailRow("Firma", item.firmanavn)
                        TilsynDetailRow("Sag ID", item.id)
                        TilsynDetailRow("Henstilling ID", item.henstillingId)
                        TilsynDetailRow("CVR", item.cvr?.toString())
                        TilsynDetailRow("Forseelse", item.forseelse)
                        TilsynDetailRow("Tilladelsestype", item.tilladelsestype)
                        TilsynDetailRow("Type", prettyType(item.tilladelsestype))
                        // NORMAL in card expanded
                        TilsynDetailRow("Areal", if (item.kvadratmeter != null) "${item.kvadratmeter} m²" else null)
                        TilsynDetailRow("Start", item.startdatoHenstilling)
                        TilsynDetailRow("Status", item.fakturaStatus)
                    }

                    // --- Unified History Section ---
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
        val output = SimpleDateFormat("dd-MM HH:mm", Locale.getDefault())
        output.format(parser.parse(raw)!!)
    } catch (_: Exception) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
            val output = SimpleDateFormat("dd-MM HH:mm", Locale.getDefault())
            output.format(parser.parse(raw)!!)
        } catch (_: Exception) {
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                val output = SimpleDateFormat("dd-MM", Locale.getDefault())
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
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            val output = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            output.format(parser.parse(raw)!!)
        } catch (_: Exception) {
            raw
        }
    }
}
