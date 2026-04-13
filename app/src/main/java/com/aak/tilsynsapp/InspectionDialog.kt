package com.aak.tilsynsapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.scale
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun processImageForUpload(bitmap: Bitmap): ByteArray {
    val maxDimension = 1920
    
    var width = bitmap.width
    var height = bitmap.height
    
    if (width > maxDimension || height > maxDimension) {
        val ratio = width.toFloat() / height.toFloat()
        if (ratio > 1) {
            width = maxDimension
            height = (maxDimension / ratio).toInt()
        } else {
            height = maxDimension
            width = (maxDimension * ratio).toInt()
        }
    }
    
    val resized = bitmap.scale(width, height, true)
    val stream = ByteArrayOutputStream()
    // 85% quality is high detail but still compressed
    resized.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    return stream.toByteArray()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionDialog(
    item: TilsynItem,
    viewModel: TilsynViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var comment by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf("Alt okay") }
    var showSelectionDropdown by remember { mutableStateOf(false) }
    val standardSelections = listOf("Alt okay", "Kvadratmeter er angivet forkert", "Ikke fjernet til tiden", "Skade på belægning", "Andet")

    val capturedBitmaps = remember { mutableStateListOf<Bitmap>() }
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { uri ->
                try {
                    val bitmap = if (Build.VERSION.SDK_INT < 28) {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    } else {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source)
                    }
                    capturedBitmaps.add(bitmap)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun createPhotoUri(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        return FileProvider.getUriForFile(
            context,
            "com.aak.tilsynsapp.fileprovider",
            file
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                }
                capturedBitmaps.add(bitmap)
                } catch (_: Exception) {
                }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val uri = createPhotoUri()
            photoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    var m2Value by remember { mutableStateOf(item.kvadratmeter?.toString()?.replace(".", ",") ?: "") }
    var isKvadratmeterValid by remember { mutableStateOf(m2Value.isEmpty() || m2Value.replace(",", ".").toFloatOrNull() != null) }
    var manualSlutDate by remember { 
        mutableStateOf(item.displayEndDate.takeIf { !it.isNullOrBlank() } ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) 
    }
    val showDatePicker = remember { mutableStateOf(false) }

    if (showDatePicker.value) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(manualSlutDate)?.time
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker.value = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        manualSlutDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                    }
                    showDatePicker.value = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
                .padding(vertical = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Luk", modifier = Modifier.size(32.dp))
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    val infoScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 56.dp)
                            .padding(horizontal = 20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                                .verticalScroll(infoScrollState)
                                .padding(12.dp)
                        ) {
                            Column {
                                if (item.type == "henstilling") {
                                    TilsynDialogDetailRow("Adresse", item.adresse)
                                    TilsynDialogDetailRow("Sag ID", item.id)
                                    TilsynDialogDetailRow("Firma", item.firmanavn)
                                    TilsynDialogDetailRow("CVR", item.cvr?.toString())
                                    TilsynDialogDetailRow("Forseelse", item.forseelse)
                                    TilsynDialogDetailRow("Type", prettyType(item.tilladelsestype))
                                    TilsynDialogDetailRow("Areal", if (item.kvadratmeter != null) "${item.kvadratmeter} m²" else null)
                                    TilsynDialogDetailRow("Start", tilsynFormatDate(item.startdatoHenstilling))
                                    val slutLabel = if (item.fakturaStatus == "Ny") "Sidst set" else "Slut"
                                    TilsynDialogDetailRow(slutLabel, tilsynFormatDate(item.slutdatoHenstilling))
                                } else {
                                    TilsynDialogDetailRow("Vejnavn", item.displayStreet)
                                    TilsynDialogDetailRow("Sag ID", item.caseId)
                                    TilsynDialogDetailRow("Sagsnummer", item.caseNumber)
                                    TilsynDialogDetailRow("Ansøger", item.applicant)
                                    TilsynDialogDetailRow("Udstyr", item.rovmEquipmentType)
                                    TilsynDialogDetailRow("Start", tilsynFormatDate(item.startDate))
                                    TilsynDialogDetailRow("Slut", tilsynFormatDate(item.endDate))
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (item.type == "henstilling") {
                            if (step == 1) {
                                OutlinedTextField(
                                    value = m2Value,
                                    onValueChange = {
                                        val filtered = it.replace(".", ",").filter { ch -> ch.isDigit() || ch == ',' }
                                        m2Value = filtered
                                        isKvadratmeterValid = filtered.isEmpty() || filtered.replace(",", ".").toFloatOrNull() != null
                                    },
                                    isError = !isKvadratmeterValid,
                                    label = { Text("Kvadratmeter", fontSize = 18.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    supportingText = {
                                        if (!isKvadratmeterValid)
                                            Text("Ugyldigt tal", color = MaterialTheme.colorScheme.error)
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = comment,
                                    onValueChange = { comment = it },
                                    label = { Text("Kommentar (valgfri)", fontSize = 18.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text("Vælg dato for hvornår henstillingen sidst blev set:", style = MaterialTheme.typography.titleMedium)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = tilsynFormatDate(manualSlutDate),
                                        onValueChange = { /* manualSlutDate updated via picker */ },
                                        label = { Text("Slutdato", fontSize = 18.sp) },
                                        modifier = Modifier.weight(1f),
                                        readOnly = true
                                    )
                                    IconButton(onClick = { showDatePicker.value = true }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.CalendarToday, "Vælg dato", modifier = Modifier.size(32.dp))
                                    }
                                }
                                Text("Sidst registreret slutdato: ${item.displayEndDate ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            // Vejman Permission Inspection
                            Text("Vælg status:", style = MaterialTheme.typography.titleSmall)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedCard(
                                    onClick = { showSelectionDropdown = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(selection, style = MaterialTheme.typography.bodyLarge)
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }
                                }
                                DropdownMenu(
                                    expanded = showSelectionDropdown,
                                    onDismissRequest = { showSelectionDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    standardSelections.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt) },
                                            onClick = {
                                                selection = opt
                                                showSelectionDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = comment,
                                onValueChange = { comment = it },
                                label = { Text("Supplerende kommentar", fontSize = 18.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Skriv observationer...") }
                            )

                            Spacer(Modifier.height(12.dp))

                            // Multi-Image Handling
                            if (capturedBitmaps.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(capturedBitmaps) { bitmap ->
                                        Box(modifier = Modifier.size(120.dp)) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = { capturedBitmaps.remove(bitmap) },
                                                modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f)).size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                    item {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedCard(
                                                onClick = {
                                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                        val uri = createPhotoUri()
                                                        photoUri = uri
                                                        cameraLauncher.launch(uri)
                                                    } else {
                                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                                    }
                                                },
                                                modifier = Modifier.size(120.dp, 56.dp)
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(20.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Kamera", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                            OutlinedCard(
                                                onClick = { galleryLauncher.launch("image/*") },
                                                modifier = Modifier.size(120.dp, 56.dp)
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Collections, null, modifier = Modifier.size(20.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Galleri", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                val uri = createPhotoUri()
                                                photoUri = uri
                                                cameraLauncher.launch(uri)
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.AddAPhoto, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Kamera")
                                    }
                                    OutlinedButton(
                                        onClick = { galleryLauncher.launch("image/*") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Collections, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Galleri")
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.type == "henstilling") {
                            if (step == 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val updated = item.copy(
                                                    kvadratmeter = m2Value.replace(",", ".").toFloatOrNull(),
                                                    slutdatoHenstilling = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                                )
                                                viewModel.updateRow(context, updated, "Ny", comment)
                                                onDismiss()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text(if (item.fakturaStatus == "Til fakturering" || item.fakturaStatus == "Faktureret") "Genåbn" else "Stadig opstillet", fontSize = 15.sp, maxLines = 1)
                                    }
                                    
                                    Button(
                                        onClick = { step = 2 },
                                        enabled = m2Value.isNotBlank() && isKvadratmeterValid,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Fjernet ", fontSize = 15.sp, maxLines = 1)
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward, 
                                                null, 
                                                modifier = Modifier.size(18.dp).align(Alignment.CenterVertically)
                                            )
                                            Text(" Fakturer", fontSize = 15.sp, maxLines = 1)
                                        }
                                    }
                                }
                                
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.updateRow(context, item, "Fakturer ikke", comment)
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Fakturer ikke ", fontSize = 16.sp)
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward, 
                                            null, 
                                            modifier = Modifier.size(20.dp).align(Alignment.CenterVertically)
                                        )
                                        Text(" Fjern fra tilsyn", fontSize = 16.sp)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val updated = item.copy(
                                                kvadratmeter = m2Value.replace(",", ".").toFloatOrNull(),
                                                slutdatoHenstilling = manualSlutDate
                                            )
                                            viewModel.updateRow(context, updated, "Til fakturering", comment)
                                            onDismiss()
                                        }
                                    },
                                    enabled = m2Value.isNotBlank() && isKvadratmeterValid && manualSlutDate.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Send til fakturering", fontSize = 18.sp)
                                }
                                TextButton(onClick = { step = 1 }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                    Text("Tilbage", fontSize = 18.sp)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.inspectPermission(item.id, comment, selection) { success ->
                                        if (success && capturedBitmaps.isNotEmpty()) {
                                            val email = SecurePrefs.getEmail(context) ?: "Unknown"
                                            val initials = email.substringBefore("@").uppercase()
                                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                            var uploadsRemaining = capturedBitmaps.size

                                            capturedBitmaps.forEachIndexed { index, bitmap ->
                                                val processedBytes = processImageForUpload(bitmap)
                                                val fileName = "Tilsyn_${initials}_${timeStamp}_${index + 1}.jpg"
                                                viewModel.uploadImage(item.id, processedBytes, fileName) {
                                                    uploadsRemaining--
                                                    if (uploadsRemaining == 0) onDismiss()
                                                }
                                            }
                                        } else {
                                            if (success) onDismiss()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Gem tilsyn", fontSize = 18.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.hidePermission(item.id) { success ->
                                        if (success) onDismiss()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error))
                            ) {
                                Text("Skjul / Fjern fra tilsyn", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
