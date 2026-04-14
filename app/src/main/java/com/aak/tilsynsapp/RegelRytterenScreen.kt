package com.aak.tilsynsapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

@Composable
fun RegelRytterenScreen(
    modifier: Modifier = Modifier,
    viewModel: RegelRytterenViewModel = viewModel(),
    onNavigateToTilsyn: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val context = LocalContext.current
    val inspectors by viewModel.inspectors.collectAsState()
    val vejman by viewModel.vejman.collectAsState()
    val henstillinger by viewModel.henstillinger.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val successLockout by viewModel.successLockout.collectAsState()
    val countdown by viewModel.countdown.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    selected = false,
                    onClick = onNavigateToHistory,
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Historik") },
                    label = { Text("Historik") }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Route, contentDescription = "RegelRytteren") },
                    label = { Text("RegelRytteren") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RegelRytteren",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text("Vælg tilsynsførende og transportmiddel", style = MaterialTheme.typography.bodyMedium)

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(inspectors) { inspector ->
                        InspectorItem(
                            inspector = inspector,
                            onToggle = { viewModel.toggleInspector(inspector.initial) },
                            onVehicleChange = { viewModel.setVehicle(inspector.initial, it) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tilladelser", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = vejman, onCheckedChange = { viewModel.setVejman(it) })
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Henstillinger", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = henstillinger, onCheckedChange = { viewModel.setHenstillinger(it) })
                }
            }

            if (isSubmitting) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.submit(context) },
                    enabled = !successLockout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generer nye ruter")
                }
            }

            if (successLockout) {
                val minutes = countdown / 60
                val seconds = countdown % 60
                val timeStr = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
                Text("Du kan sende igen om $timeStr", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun InspectorItem(
    inspector: InspectorConfig,
    onToggle: () -> Unit,
    onVehicleChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = inspector.isIncluded,
            onCheckedChange = { onToggle() }
        )
        
        Text(
            text = inspector.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).clickable { onToggle() }
        )

        Row(
            modifier = Modifier.padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VehicleToggle(
                selected = inspector.vehicle == "Cykel",
                icon = Icons.Default.DirectionsBike,
                onClick = { onVehicleChange("Cykel") },
                enabled = inspector.isIncluded
            )
            Spacer(modifier = Modifier.width(8.dp))
            VehicleToggle(
                selected = inspector.vehicle == "Bil",
                icon = Icons.Default.DirectionsCar,
                onClick = { onVehicleChange("Bil") },
                enabled = inspector.isIncluded
            )
        }
    }
}

@Composable
fun VehicleToggle(
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean
) {
    FilledIconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        enabled = enabled,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier.size(40.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}
