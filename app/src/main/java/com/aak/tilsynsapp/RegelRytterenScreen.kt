package com.aak.tilsynsapp


import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RegelRytterenScreen(
    modifier: Modifier = Modifier,
    viewModel: RegelRytterenViewModel = viewModel(),
    onBack: () -> Unit

) {
    val bikes by viewModel.bikes.collectAsState()
    val cars by viewModel.cars.collectAsState()
    val vejman by viewModel.vejman.collectAsState()
    val henstillinger by viewModel.henstillinger.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSubmitting by remember { mutableStateOf(false) }
    var successLockout by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(300) }

    // Timer countdown effect
    LaunchedEffect(successLockout) {
        if (successLockout) {
            countdown = 300
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            successLockout = false
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
                    onClick = onBack,
                    icon = { Icon(Icons.Default.Receipt, contentDescription = "Fakturering") },
                    label = { Text("Fakturering") }
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
                value = bikes.toFloat(),
                onValueChange = { viewModel.setBikes(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9
            )

            Text("Biler: $cars")
            Slider(
                value = cars.toFloat(),
                onValueChange = { viewModel.setCars(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9
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
                    onClick = {
                        coroutineScope.launch {
                            if ((bikes + cars) == 0) {
                                snackbarHostState.showSnackbar("Du skal vælge mindst én cykel eller bil", duration = SnackbarDuration.Short)
                                return@launch
                            }
                            if (!vejman && !henstillinger) {
                                snackbarHostState.showSnackbar("Vælg mindst én type: Tilladelser eller Henstillinger", duration = SnackbarDuration.Short)
                                return@launch
                            }
                            isSubmitting = true
                            val success = ApiHelper.sendRegelrytterenPayload(
                                bikes = bikes,
                                cars = cars,
                                vejman = vejman,
                                henstillinger = henstillinger
                            )
                            viewModel.updateStatusMessage(success ?: "Uventet fejl ved netværkskaldet")
                            isSubmitting = false
                            if (success != null) {
                                successLockout = true
                                snackbarHostState.showSnackbar(success, duration = SnackbarDuration.Long)
                            }
                        }
                    },
                    enabled = !isSubmitting && !successLockout
                ) {
                    Text("Generer nye ruter")
                }
            }

            if (successLockout) {
                Text("Du kan sende igen om $countdown sek.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
