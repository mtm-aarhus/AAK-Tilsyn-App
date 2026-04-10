package com.aak.tilsynsapp


import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RegelRytterenScreen(
    modifier: Modifier = Modifier,
    viewModel: RegelRytterenViewModel = viewModel(),
    onNavigateToTilsyn: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val bikes by viewModel.bikes.collectAsState()
    val cars by viewModel.cars.collectAsState()
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
                    selected = true,
                    onClick = {},
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RegelRytteren",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text("Cykler: $bikes")
            Slider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                value = bikes.toFloat(),
                onValueChange = { raw ->
                    val snapped = raw.roundToInt().coerceIn(0, 10)
                    if (snapped != bikes) viewModel.setBikes(snapped)
                },
                valueRange = 0f..10f
            )


            Text("Biler: $cars")
            Slider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                value = cars.toFloat(),
                onValueChange = { raw ->
                    val snapped = raw.roundToInt().coerceIn(0, 10)
                    if (snapped != cars) viewModel.setCars(snapped)
                },
                valueRange = 0f..10f
            )

            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tilladelser")
                    Switch(checked = vejman, onCheckedChange = { viewModel.setVejman(it) })
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Henstillinger")
                    Switch(checked = henstillinger, onCheckedChange = { viewModel.setHenstillinger(it) })
                }
            }

            if (isSubmitting) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.submit(context) },
                    enabled = !successLockout
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
