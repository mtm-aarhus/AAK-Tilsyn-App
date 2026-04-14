package com.aak.tilsynsapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.preference.PreferenceManager
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.NearMe
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private val MapColorNy = Color(0xFF4CAF50)
private val MapColorFaerdig = Color(0xFF2196F3)
private val MapColorHenstilling = Color(0xFFFF9800)

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
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    
    val sharedPrefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var isDarkMode by remember { mutableStateOf(sharedPrefs.getBoolean("map_dark_mode", false)) }
    var zoomLevel by remember { mutableDoubleStateOf(15.0) }

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
                    selected = false,
                    onClick = onNavigateToTilsyn,
                    icon = { Icon(Icons.Default.Receipt, contentDescription = "Tilsyn") },
                    label = { Text("Tilsyn") }
                )
                NavigationBarItem(
                    selected = true,
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
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val selectedMapItem by viewModel.selectedMapItem.collectAsState()
            var showInspectDialog by remember { mutableStateOf(false) }

            // Handle externally selected item (from Tilsyn or History)
            LaunchedEffect(selectedMapItem) {
                selectedMapItem?.let { item ->
                    if (item.latitude != null && item.longitude != null) {
                        val point = GeoPoint(item.latitude!!, item.longitude!!)
                        mapView.controller.animateTo(point)
                        mapView.controller.setZoom(18.5)
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
                                zoomLevel = event?.zoomLevel ?: 15.0
                                return false
                            }
                        })
                    }
                },
                update = { view ->
                    val currentZoom = view.zoomLevelDouble
                    val markerSize = (48 + (currentZoom - 14).coerceAtLeast(0.0) * 18).toInt().coerceIn(48, 150)

                    if (isDarkMode) {
                        val matrix = ColorMatrix()
                        matrix.set(floatArrayOf(
                            -0.85f, 0f, 0f, 0f, 255f,
                            0f, -0.85f, 0f, 0f, 255f,
                            0f, 0f, -0.85f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        view.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
                    } else {
                        view.overlayManager.tilesOverlay.setColorFilter(null)
                    }

                    view.overlays.removeAll { it is Marker }

                    val markerGroups = items.filter { it.latitude != null && it.longitude != null }
                        .groupBy { GeoPoint(it.latitude!!, it.longitude!!) }

                    markerGroups.forEach { (basePoint, itemsAtPoint) ->
                        itemsAtPoint.forEachIndexed { index, item ->
                            val marker = Marker(view)
                            if (itemsAtPoint.size > 1) {
                                val offset = 0.00005 * index 
                                marker.position = GeoPoint(basePoint.latitude + offset, basePoint.longitude + offset)
                            } else {
                                marker.position = basePoint
                            }
                            
                            marker.title = item.displayStreet
                            marker.snippet = item.displaySecondaryInfo
                            
                            val color = when {
                                item.type == "henstilling" -> MapColorHenstilling
                                item.vejmanDisplayState == "Færdig tilladelse" -> MapColorFaerdig
                                else -> MapColorNy
                            }
                            
                            marker.icon = createDotMarker(context, color.toArgb(), markerSize)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            
                            marker.setOnMarkerClickListener { m, _ ->
                                viewModel.selectMapItem(item)
                                m.showInfoWindow()
                                true
                            }
                            view.overlays.add(marker)
                        }
                    }
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
                    }
                }

                FloatingActionButton(
                    onClick = { 
                        isDarkMode = !isDarkMode 
                        sharedPrefs.edit().putBoolean("map_dark_mode", isDarkMode).apply()
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

                            FilledIconButton(
                                onClick = {
                                    val uri = if (item.latitude != null && item.longitude != null) {
                                        "https://www.google.com/maps/dir/?api=1&destination=${item.latitude},${item.longitude}&travelmode=bicycling"
                                    } else {
                                        "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(item.displayStreet + ", Aarhus")}&travelmode=bicycling"
                                    }
                                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
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

fun createDotMarker(context: Context, color: Int, size: Int = 48): Drawable {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = color
    canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, paint)
    return BitmapDrawable(context.resources, bitmap)
}
