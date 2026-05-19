@file:Suppress("CascadeIf", "CascadeIf", "RedundantSuppression")

package com.aak.tilsynsapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val MAX_DIMENSION = 1920

/**
 * Decode a bitmap from a content URI at a reduced resolution.
 * Uses inSampleSize (API <28) or ImageDecoder size constraint (API 28+)
 * to avoid allocating a full-resolution bitmap in memory.
 */
fun decodeSampledBitmap(context: android.content.Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                val ratio = width.toFloat() / height.toFloat()
                if (ratio > 1) {
                    decoder.setTargetSize(MAX_DIMENSION, (MAX_DIMENSION / ratio).toInt())
                } else {
                    decoder.setTargetSize((MAX_DIMENSION * ratio).toInt(), MAX_DIMENSION)
                }
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    // API < 28: two-pass BitmapFactory decode
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

    val rawWidth = options.outWidth
    val rawHeight = options.outHeight
    var inSampleSize = 1
    if (rawWidth > MAX_DIMENSION || rawHeight > MAX_DIMENSION) {
        val halfWidth = rawWidth / 2
        val halfHeight = rawHeight / 2
        while (halfWidth / inSampleSize >= MAX_DIMENSION && halfHeight / inSampleSize >= MAX_DIMENSION) {
            inSampleSize *= 2
        }
    }

    val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    val sampled = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOptions)
    } ?: throw IllegalStateException("Could not open image URI")

    return sampled
}

fun processImageForUpload(bitmap: Bitmap): ByteArray {
    var width = bitmap.width
    var height = bitmap.height

    if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
        val ratio = width.toFloat() / height.toFloat()
        if (ratio > 1) {
            width = MAX_DIMENSION
            height = (MAX_DIMENSION / ratio).toInt()
        } else {
            height = MAX_DIMENSION
            width = (MAX_DIMENSION * ratio).toInt()
        }
    }

    val resized = bitmap.scale(width, height, true)
    val stream = ByteArrayOutputStream()
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

    // Igangværende submit-job. Bruges til at vise spinner, blokere dobbeltklik, og
    // give brugeren et "Annullér"-knap der reelt afbryder netværkskaldet via OkHttp.
    var submitJob by remember { mutableStateOf<Job?>(null) }
    val submitting = submitJob != null
    // Tekst under spinneren — fx "Uploader 2 af 5 billeder…" mens vi behandler queue'en.
    var submitProgress by remember { mutableStateOf<String?>(null) }
    // Annullér-knappen vises kun mens vi er i en fase hvor cancel er meningsfuldt.
    // Når permission-flow'et har gemt serveren og er ved at sætte uploads i kø,
    // sætter vi denne til false så brugeren ikke ender med et delvist annulleret resultat.
    var submitCancellable by remember { mutableStateOf(true) }

    fun blockedByOffline(): Boolean {
        if (!ApiHelper.hasInternet(context)) {
            Toast.makeText(context, "Ingen internetforbindelse — prøv igen", Toast.LENGTH_LONG).show()
            return true
        }
        return false
    }

    fun showSubmitFailed() {
        Toast.makeText(context, "Kunne ikke gemme tilsyn — prøv igen", Toast.LENGTH_LONG).show()
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { uri ->
                try {
                    capturedBitmaps.add(decodeSampledBitmap(context, uri))
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
                capturedBitmaps.add(decodeSampledBitmap(context, uri))
            } catch (_: Exception) {
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    /**
     * Wraps a submit action: blocks double-fire, runs the suspend `work` on the dialog's
     * scope, tracks the Job so a Cancel button can abort it, and cleans up state in
     * a `finally` so spinners always come down. The lambda's Boolean return decides
     * whether to dismiss (true) or surface the generic failure toast (false). The lambda
     * can also call `onDismiss()` / show its own toast and return null to mean
     * "I handled the outcome".
     */
    fun runSubmission(work: suspend () -> Boolean?) {
        if (submitting) return
        if (blockedByOffline()) return
        submitCancellable = true
        submitJob = coroutineScope.launch {
            try {
                when (work()) {
                    true -> onDismiss()
                    false -> showSubmitFailed()
                    null -> { /* lambda handled the outcome itself */ }
                }
            } catch (_: CancellationException) {
                // User pressed "Annullér" — leave the dialog open with the user's data.
                Toast.makeText(context, "Annulleret", Toast.LENGTH_SHORT).show()
            } finally {
                submitJob = null
                submitProgress = null
                submitCancellable = true
            }
        }
    }

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
        // Mens vi indsender, undertryk dismiss via tilbage-knap/tap-udenfor — så vi ikke
        // mister tilsynet i vinduet mellem "inspect succeeded" og "uploads enqueued".
        // Brugeren kan stadig annullere eksplicit via "Annullér"-knappen.
        onDismissRequest = { if (!submitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
                .padding(vertical = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = onDismiss,
                    enabled = !submitting,
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
                                    TilsynDialogDetailRow("Adresse", item.fullAddress)
                                    TilsynDialogDetailRow("Sag ID", item.id)
                                    TilsynDialogDetailRow("Firma", item.firmanavn)
                                    TilsynDialogDetailRow("CVR", item.cvr)
                                    TilsynDialogDetailRow("Forseelse", item.forseelse)
                                    TilsynDialogDetailRow("Type", prettyType(item.tilladelsestype))
                                    TilsynDialogDetailRow("Areal", if (item.kvadratmeter != null) "${item.kvadratmeter} m²" else null)
                                    TilsynDialogDetailRow("Start", tilsynFormatDate(item.startDate))
                                    val slutLabel = if (item.fakturaStatus == "Ny") "Sidst set" else "Slut"
                                    TilsynDialogDetailRow(slutLabel, tilsynFormatDate(item.endDate))
                                } else if (item.type == "indmeldt") {
                                    TilsynDialogDetailRow("Sagsnummer", item.caseNumber)
                                    TilsynDialogDetailRow("Titel", item.title)
                                    TilsynDialogDetailRow("Beskrivelse", item.description)
                                    TilsynDialogDetailRow("Adresse", item.fullAddress)
                                    TilsynDialogDetailRow("Oprettet af", item.createdBy)
                                    TilsynDialogDetailRow("Oprettet", tilsynFormatDate(item.createdAt))
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

                        if (item.type == "indmeldt") {
                            OutlinedTextField(
                                value = comment,
                                onValueChange = { comment = it },
                                label = { Text("Kommentar", fontSize = 18.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Skriv observationer...") },
                                minLines = 3,
                                maxLines = 6
                            )
                        } else if (item.type == "henstilling") {
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
                                Text("Sidst registreret slutdato: ${tilsynFormatDate(item.endDate)}", style = MaterialTheme.typography.bodyMedium)
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
                        if (item.type == "indmeldt") {
                            Button(
                                onClick = {
                                    runSubmission {
                                        viewModel.inspectIndmeldtAwait(item.id, comment)
                                    }
                                },
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (submitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Gem tilsyn", fontSize = 18.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    runSubmission {
                                        viewModel.hidePermissionAwait(item.id)
                                    }
                                },
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error))
                            ) {
                                Text("Skjul / Fjern fra tilsyn", fontSize = 16.sp)
                            }
                        } else if (item.type == "henstilling") {
                            if (step == 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            runSubmission {
                                                val updated = item.copy(
                                                    kvadratmeter = m2Value.replace(",", ".").toFloatOrNull(),
                                                    endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                                )
                                                viewModel.updateRow(context, updated, "Ny", comment)
                                            }
                                        },
                                        enabled = !submitting,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        if (submitting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Text(if (item.fakturaStatus == "Til fakturering" || item.fakturaStatus == "Faktureret") "Genåbn" else "Stadig opstillet", fontSize = 15.sp, maxLines = 1)
                                        }
                                    }

                                    Button(
                                        onClick = { step = 2 },
                                        enabled = !submitting && m2Value.isNotBlank() && isKvadratmeterValid,
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
                                        runSubmission {
                                            viewModel.updateRow(context, item, "Fakturer ikke", comment)
                                        }
                                    },
                                    enabled = !submitting,
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
                                        runSubmission {
                                            val updated = item.copy(
                                                kvadratmeter = m2Value.replace(",", ".").toFloatOrNull(),
                                                endDate = manualSlutDate
                                            )
                                            viewModel.updateRow(context, updated, "Til fakturering", comment)
                                        }
                                    },
                                    enabled = !submitting && m2Value.isNotBlank() && isKvadratmeterValid && manualSlutDate.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (submitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text("Send til fakturering", fontSize = 18.sp)
                                    }
                                }
                                TextButton(onClick = { step = 1 }, enabled = !submitting, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                    Text("Tilbage", fontSize = 18.sp)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    runSubmission {
                                        submitProgress = "Gemmer tilsyn…"
                                        val inspectOk = viewModel.inspectPermissionAwait(item.id, comment, selection)
                                        if (!inspectOk) return@runSubmission false

                                        if (capturedBitmaps.isEmpty()) {
                                            return@runSubmission true
                                        }

                                        // Tilsynet er nu gemt server-side. Fra dette punkt giver
                                        // det ikke mening at annullere — vi ville bare efterlade
                                        // billeder som forældreløse filer i cache. Gem-til-disk +
                                        // service-enqueue i en NonCancellable blok.
                                        submitCancellable = false
                                        submitProgress = "Forbereder ${capturedBitmaps.size} billede(r)…"
                                        val email = SecurePrefs.getEmail(context) ?: "Unknown"
                                        val initials = email.substringBefore("@").uppercase()
                                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

                                        withContext(NonCancellable) {
                                            val savedPaths = withContext(Dispatchers.IO) {
                                                val cacheRoot = File(context.cacheDir, "uploads").apply { mkdirs() }
                                                capturedBitmaps.mapIndexed { index, bitmap ->
                                                    val bytes = processImageForUpload(bitmap)
                                                    val outFile = File(cacheRoot, "Tilsyn_${initials}_${timeStamp}_${index + 1}.jpg")
                                                    outFile.outputStream().use { it.write(bytes) }
                                                    outFile.absolutePath
                                                }
                                            }

                                            UploadService.enqueueUploads(
                                                context = context.applicationContext,
                                                itemId = item.id,
                                                label = item.displayStreet,
                                                filePaths = savedPaths,
                                            )
                                        }

                                        Toast.makeText(
                                            context,
                                            "Tilsyn gemt — billeder uploades i baggrunden",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onDismiss()
                                        null // Vi har selv håndteret afslutningen.
                                    }
                                },
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (submitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Gem tilsyn", fontSize = 18.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    runSubmission {
                                        viewModel.hidePermissionAwait(item.id)
                                    }
                                },
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error))
                            ) {
                                Text("Skjul / Fjern fra tilsyn", fontSize = 16.sp)
                            }
                        }

                        // Progress-tekst + Annullér: ét sted, ikke per knap, så vi ikke har
                        // 5 forskellige kopier af samme UI for alle submit-flows.
                        if (submitting) {
                            submitProgress?.let { msg ->
                                Text(
                                    msg,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                            if (submitCancellable) {
                                TextButton(
                                    onClick = { submitJob?.cancel() },
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                ) {
                                    Text("Annullér", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
