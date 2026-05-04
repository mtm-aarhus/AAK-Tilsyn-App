@file:Suppress("ReplaceIsEmptyWithIfEmpty", "ReplaceIsEmptyWithIfEmpty",
    "ReplaceIsEmptyWithIfEmpty", "RedundantSuppression"
)

package com.aak.tilsynsapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlinx.coroutines.launch

private val MapColorNy = Color(0xFF4CAF50)
private val MapColorFaerdig = Color(0xFF2196F3)
private val MapColorHenstilling = Color(0xFFFF9800)
private val MapColorIndmeldt = Color(0xFF00BCD4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: TilsynViewModel,
    onNavigateToTilsyn: () -> Unit,
    onNavigateToRegelrytteren: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val items by viewModel.tilsynItems.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    LaunchedEffect(Unit) {
        viewModel.refreshDataAsync()
    }
    
    val sharedPrefs = remember { context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE) }
    var isDarkMode by remember { mutableStateOf(sharedPrefs.getBoolean("map_dark_mode", false)) }
    var zoomLevel by remember { mutableDoubleStateOf(15.0) }

    // Cache til markers for at undgå jank og OOM ved zoom
    val markerCache = remember { mutableMapOf<Pair<Int, Int>, Drawable>() }
    fun getCachedMarker(color: Int, size: Int): Drawable {
        return markerCache.getOrPut(color to size) {
            createDotMarker(context, color, size)
        }
    }

    // Initialize Osmdroid
    remember {
        Configuration.getInstance().load(context, sharedPrefs)
        true
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Power saving: lifecycle management
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
        }
    }

    val myLocationOverlay = remember(hasLocationPermission) {
        if (hasLocationPermission) {
            MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
        } else null
    }

    // Power saving: Only enable location when this screen is active
    DisposableEffect(myLocationOverlay) {
        myLocationOverlay?.enableMyLocation()
        onDispose {
            myLocationOverlay?.disableMyLocation()
            myLocationOverlay?.disableFollowLocation()
        }
    }

    LaunchedEffect(myLocationOverlay) {
        myLocationOverlay?.let {
            if (!mapView.overlays.contains(it)) {
                mapView.overlays.add(it)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = onNavigateToMap,
                    icon = { Icon(Icons.Default.Map, contentDescription = "Kort") },
                    label = { Text("Kort") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToTilsyn,
                    icon = { Icon(Icons.Default.Receipt, contentDescription = "Tilsyn") },
                    label = { Text("Tilsyn") }
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                myLocationOverlay?.let {
                    it.enableFollowLocation()
                    if (it.myLocation != null) {
                        mapView.controller.animateTo(it.myLocation)
                    }
                }
            }) {
                Icon(Icons.Default.MyLocation, "Min position")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val selectedMapItem by viewModel.selectedMapItem.collectAsState()
            var showInspectDialog by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            // Indmeldt-creation state (triggered by tapping an empty point on the map)
            var tappedLat by remember { mutableStateOf<Double?>(null) }
            var tappedLon by remember { mutableStateOf<Double?>(null) }
            var tappedAddress by remember { mutableStateOf<ApiHelper.DawaSuggestion?>(null) }
            var isLoadingAddress by remember { mutableStateOf(false) }
            var showIndmeldtFormDialog by remember { mutableStateOf(false) }

            fun clearTapState() {
                tappedLat = null
                tappedLon = null
                tappedAddress = null
                showIndmeldtFormDialog = false
            }

            // Visual tap-preview marker (ID'd so the `update` marker-cleanup skips it)
            val tapMarkerRef = remember { mutableStateOf<Marker?>(null) }
            LaunchedEffect(tappedLat, tappedLon) {
                tapMarkerRef.value?.let { mapView.overlays.remove(it) }
                tapMarkerRef.value = null
                val lat = tappedLat
                val lon = tappedLon
                if (lat != null && lon != null) {
                    val m = Marker(mapView).apply {
                        id = "tap_preview"
                        position = GeoPoint(lat, lon)
                        icon = getCachedMarker(MapColorIndmeldt.toArgb(), 80)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        // No click listener - taps on this marker fall through to map tap.
                    }
                    mapView.overlays.add(m)
                    tapMarkerRef.value = m
                }
                mapView.invalidate()
            }

            // Handle externally selected item (from Tilsyn or History)
            LaunchedEffect(selectedMapItem) {
                selectedMapItem?.let { item ->
                    // Clear any pending tap-to-create state when an existing item is selected.
                    clearTapState()
                    if (item.latitude != null && item.longitude != null) {
                        val point = GeoPoint(item.latitude, item.longitude)
                        // Brug animateTo med zoom for en blødere overgang
                        mapView.controller.animateTo(point, 18.0, 800L)
                    }
                }
            }

            if (showInspectDialog && selectedMapItem != null) {
                InspectionDialog(
                    item = selectedMapItem!!,
                    viewModel = viewModel,
                    onDismiss = { showInspectDialog = false }
                )
            }

            // Tap-to-create-indmeldt: title/description dialog (shown after "Opret tilsyn" is clicked on the bottom card)
            if (showIndmeldtFormDialog && tappedLat != null && tappedLon != null) {
                CreateIndmeldtTapDialog(
                    latitude = tappedLat!!,
                    longitude = tappedLon!!,
                    address = tappedAddress,
                    isLoadingAddress = isLoadingAddress,
                    onDismiss = {
                        // Return to bottom card; user can re-open the form if they want.
                        showIndmeldtFormDialog = false
                    },
                    onConfirm = { title, description ->
                        val lat = tappedLat ?: return@CreateIndmeldtTapDialog
                        val lon = tappedLon ?: return@CreateIndmeldtTapDialog
                        // Use the ORIGINAL click coords, not the DAWA-snapped ones
                        val fullAddress = tappedAddress?.fullAddress ?: "Ukendt adresse"
                        val streetName = tappedAddress?.streetName
                        viewModel.createIndmeldt(
                            fullAddress = fullAddress,
                            streetName = streetName,
                            latitude = lat,
                            longitude = lon,
                            title = title,
                            description = description,
                        ) { success, _ ->
                            if (success) clearTapState()
                        }
                    }
                )
            }

            AndroidView(
                factory = {
                    mapView.apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(zoomLevel)
                        // Default to Aarhus C
                        controller.setCenter(GeoPoint(56.1567, 10.2108))

                        addMapListener(object : org.osmdroid.events.MapListener {
                            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean = false
                            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                // Hent præcis zoom med decimaler (f.eks. 15.5) i stedet for bare et heltal
                                zoomLevel = mapView.zoomLevelDouble
                                return false
                            }
                        })

                        // Tap-on-empty-point → open indmeldt dialog with reverse-geocoded address
                        // Placed at index 0 (bottom of z-order) so markers get first chance to claim taps.
                        val tapReceiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                viewModel.selectMapItem(null)
                                tappedLat = p.latitude
                                tappedLon = p.longitude
                                tappedAddress = null
                                isLoadingAddress = true
                                coroutineScope.launch {
                                    tappedAddress = ApiHelper.dawaReverseGeocode(p.latitude, p.longitude)
                                    isLoadingAddress = false
                                }
                                return true
                            }
                            override fun longPressHelper(p: GeoPoint): Boolean = false
                        }
                        overlays.add(0, MapEventsOverlay(tapReceiver))
                    }
                },
                update = { view ->
                    val currentZoom = zoomLevel
                    // Nedskaleret: Start ved 40px, læg 12px til per zoom-level over 14. Max 110px.
                    val markerSize = (40 + (currentZoom - 14).coerceAtLeast(0.0) * 12).toInt().coerceIn(40, 110)

                    if (isDarkMode) {
                        // Invert lysstyrke, men roter hue 180° så grønne områder forbliver grønne
                        // og blåt vand forbliver blåt — i stedet for negativ-effektens magenta/orange.
                        val invert = ColorMatrix(floatArrayOf(
                            -1f, 0f, 0f, 0f, 255f,
                            0f, -1f, 0f, 0f, 255f,
                            0f, 0f, -1f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        val hueRotate180 = ColorMatrix(floatArrayOf(
                            -0.574f,  1.430f,  0.144f, 0f, 0f,
                             0.426f,  0.430f,  0.144f, 0f, 0f,
                             0.426f,  1.430f, -0.856f, 0f, 0f,
                             0f,      0f,      0f,     1f, 0f
                        ))
                        invert.postConcat(hueRotate180)
                        // Lidt afdæmpet kontrast så ren hvid bliver mørk grå i stedet for sort.
                        val softenContrast = ColorMatrix(floatArrayOf(
                            0.85f, 0f, 0f, 0f, 15f,
                            0f, 0.85f, 0f, 0f, 15f,
                            0f, 0f, 0.85f, 0f, 15f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        invert.postConcat(softenContrast)
                        view.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(invert))
                    } else {
                        view.overlayManager.tilesOverlay.setColorFilter(null)
                    }

                    // Fjern gamle markers (men ikke tap-preview-markøren)
                    view.overlays.removeAll { it is Marker && it.id != "tap_preview" }

                    // Filtrer og grupper items der har koordinater
                    val markerGroups = items.filter { it.latitude != null && it.longitude != null }
                        .groupBy { GeoPoint(it.latitude!!, it.longitude!!) }

                    markerGroups.forEach { (basePoint, itemsAtPoint) ->
                        itemsAtPoint.forEachIndexed { index, item ->
                            val marker = Marker(view)
                            if (itemsAtPoint.size > 1) {
                                // Hvis der er flere på samme spot, spred dem lidt
                                val offset = 0.00005 * index 
                                marker.position = GeoPoint(basePoint.latitude + offset, basePoint.longitude + offset)
                            } else {
                                marker.position = basePoint
                            }
                            
                            marker.title = item.displayStreet
                            marker.snippet = item.displaySecondaryInfo
                            
                            val color = when {
                                item.type == "henstilling" -> MapColorHenstilling
                                item.type == "indmeldt" -> MapColorIndmeldt
                                item.vejmanDisplayState == "Færdig tilladelse" -> MapColorFaerdig
                                else -> MapColorNy
                            }
                            
                            // Nu bruger vi cachet marker i stedet for at oprette en ny hver gang
                            marker.icon = getCachedMarker(color.toArgb(), markerSize)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            
                            marker.setOnMarkerClickListener { m, _ ->
                                viewModel.selectMapItem(item)
                                m.showInfoWindow()
                                true
                            }
                            view.overlays.add(marker)
                        }
                    }
                    view.invalidate() // Tving kortet til at gentegne
                },
                modifier = Modifier.fillMaxSize()
            )

            // Legend & Dark Mode Toggle
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LegendItem(MapColorNy, "Ny")
                        LegendItem(MapColorFaerdig, "Færdig")
                        LegendItem(MapColorHenstilling, "Henstilling")
                        LegendItem(MapColorIndmeldt, "Indmeldt")
                    }
                }

                FloatingActionButton(
                    onClick = {
                        isDarkMode = !isDarkMode
                        sharedPrefs.edit { putBoolean("map_dark_mode", isDarkMode) }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Skift tema",
                        modifier = Modifier.size(24.dp)
                    )
                }

                FloatingActionButton(
                    onClick = {
                        if (!isRefreshing) viewModel.refreshDataAsync()
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Opdater tilsyn",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Info overlay
            selectedMapItem?.let { item ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.displayStreet, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.selectMapItem(null) }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                        
                        Text(item.displaySecondaryInfo, style = MaterialTheme.typography.bodyMedium)
                        if (item.type != "henstilling" && !item.streetStatus.isNullOrBlank()) {
                            Text("Vejstatus: ${item.streetStatus}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }

                        if (item.type == "indmeldt") {
                            Text(
                                "Oprettet: ${tilsynFormatDateShort(item.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val start = tilsynFormatDateShort(item.startDate)
                            val end = tilsynFormatDateShort(item.displayEndDate)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(start, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp).padding(horizontal = 4.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(end, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        if (item.type == "henstilling") {
                            val line = listOfNotNull(
                                if (item.displayCaseNumber.isNotBlank()) item.displayCaseNumber else null,
                                if (!item.forseelse.isNullOrBlank()) item.forseelse else null
                            ).joinToString(" | ")
                            if (line.isNotBlank()) {
                                Text(line, style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            val line = listOfNotNull(
                                if (item.displayCaseNumber.isNotBlank()) item.displayCaseNumber else null,
                                if (item.displayEquipment.isNotBlank()) item.displayEquipment else null
                            ).joinToString(" | ")
                            if (line.isNotBlank()) {
                                Text(line, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showInspectDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Inspicer")
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            if (!item.sharepointLink.isNullOrBlank()) {
                                FilledIconButton(
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, item.sharepointLink.toUri()))
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, "Filer", modifier = Modifier.size(24.dp))
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            FilledIconButton(
                                onClick = {
                                    val uri = if (item.latitude != null && item.longitude != null) {
                                        "https://www.google.com/maps/dir/?api=1&destination=${item.latitude},${item.longitude}&travelmode=bicycling"
                                    } else {
                                        "https://www.google.com/maps/dir/?api=1&destination=${java.net.URLEncoder.encode(item.displayStreet + ", Aarhus", "UTF-8")}&travelmode=bicycling"
                                    }
                                    val mapIntent = Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    context.startActivity(mapIntent)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.NearMe, "Naviger", modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            // Tap-to-create-indmeldt bottom card: shown after user taps an empty map point.
            // Matches the selected-item card above, but with no navigation - just "Opret tilsyn".
            if (tappedLat != null && tappedLon != null && selectedMapItem == null) {
                val lat = tappedLat!!
                val lon = tappedLon!!
                // After loading completes, a null address means either "no match" or
                // "outside Aarhus Kommune" (DAWA filter rejects non-0751).
                val addressResolved = !isLoadingAddress && tappedAddress != null
                val outsideAarhus = !isLoadingAddress && tappedAddress == null
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val headline = when {
                                isLoadingAddress -> "Henter adresse…"
                                addressResolved -> tappedAddress!!.fullAddress
                                else -> "Uden for Aarhus Kommune"
                            }
                            Text(
                                headline,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (outsideAarhus) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { clearTapState() }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }

                        Text(
                            "Koordinater: %.5f, %.5f".format(lat, lon),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showIndmeldtFormDialog = true },
                            enabled = addressResolved,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MapColorIndmeldt,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Opret tilsyn")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color.White, CircleShape)
                .padding(2.dp)
                .background(color, CircleShape)
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateIndmeldtTapDialog(
    latitude: Double,
    longitude: Double,
    address: ApiHelper.DawaSuggestion?,
    isLoadingAddress: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Opret indmeldt tilsyn") },
        text = {
            Column {
                // Address row (read-only; filled by DAWA reverse-geocode)
                Surface(
                    color = MapColorIndmeldt.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = MapColorIndmeldt)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (isLoadingAddress && address == null) {
                                Text("Henter adresse…", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(
                                    address?.fullAddress ?: "Ukendt adresse",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                "Koordinater: %.5f, %.5f".format(latitude, longitude),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isLoadingAddress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= TITLE_MAX_LENGTH) title = it },
                    label = { Text("Titel") },
                    supportingText = { Text("${title.length} / $TITLE_MAX_LENGTH") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Beskrivelse (valgfri)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    isSubmitting = true
                    onConfirm(title.trim(), description.trim().ifBlank { null })
                },
                enabled = !isSubmitting && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MapColorIndmeldt, contentColor = Color.White)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Opret")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Annullér")
            }
        }
    )
}

fun createDotMarker(context: Context, color: Int, size: Int = 48): Drawable {
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = color
    canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, paint)
    return bitmap.toDrawable(context.resources)
}
