package com.aak.tilsynsapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val IndmeldtAccent = Color(0xFF00BCD4)

// Keep titles short so they fit on a single row in tilsyn/historik lists.
internal const val TITLE_MAX_LENGTH = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIndmeldtScreen(
    viewModel: TilsynViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Intercept system back (button + gesture) so we return to the Tilsyn screen
    // instead of letting the Activity handle it (which would close the app since
    // we drive navigation via a single `currentScreen` state rather than a back stack).
    BackHandler(onBack = onBack)

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<ApiHelper.DawaSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var selected by remember { mutableStateOf<ApiHelper.DawaSuggestion?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Debounced live autocomplete
    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 3 || searchQuery == selected?.label) {
            suggestions = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(250)
        suggestions = ApiHelper.dawaAutocomplete(searchQuery)
        isSearching = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Opret indmeldt tilsyn") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tilbage")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (selected != null && it != selected?.label) {
                        selected = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Søg adresse…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = MaterialTheme.shapes.medium
            )

            // Live suggestions list - always visible while typing with results
            if (suggestions.isNotEmpty() && selected == null) {
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(suggestions) { s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = s
                                        searchQuery = s.label
                                        suggestions = emptyList()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = s.label,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Selected address confirmation
            selected?.let { s ->
                Surface(
                    color = IndmeldtAccent.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = IndmeldtAccent)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                s.fullAddress,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.sp
                            )
                            Text(
                                "Koordinater: %.5f, %.5f".format(s.latitude, s.longitude),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= TITLE_MAX_LENGTH) title = it },
                label = { Text("Titel", fontSize = 16.sp) },
                supportingText = { Text("${title.length} / $TITLE_MAX_LENGTH") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Beskrivelse (valgfri)", fontSize = 16.sp) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.weight(1f))

            val canSubmit = !isSubmitting && title.isNotBlank() && selected != null

            Button(
                onClick = {
                    val s = selected ?: return@Button
                    isSubmitting = true
                    viewModel.createIndmeldt(
                        fullAddress = s.fullAddress,
                        streetName = s.streetName,
                        latitude = s.latitude,
                        longitude = s.longitude,
                        title = title.trim(),
                        description = description.trim().ifBlank { null },
                    ) { success, _ ->
                        isSubmitting = false
                        if (success) {
                            onCreated()
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Kunne ikke oprette tilsyn")
                            }
                        }
                    }
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(52.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Opret tilsyn", fontSize = 18.sp)
                }
            }
        }
    }
}
