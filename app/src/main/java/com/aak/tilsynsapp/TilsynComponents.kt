package com.aak.tilsynsapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TilsynExpandedDetails(item: TilsynItem) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        HorizontalDivider(thickness = 0.5.dp)
        if (item.type == "permission") {
            TilsynDetailRow("Status", item.vejmanState)
            TilsynDetailRow("Sag ID", item.caseId)
            TilsynDetailRow("Sagsnummer", item.caseNumber)
            TilsynDetailRow("Ansøger", item.applicant)
            TilsynDetailRow("Marker", item.marker)
            TilsynDetailRow("Udstyr", item.rovmEquipmentType)
            TilsynDetailRow("Sagsmappenr", item.applicantFolderNumber)
            TilsynDetailRow("Ref", item.authorityReferenceNumber)
            TilsynDetailRow("Vejstatus", item.streetStatus)
            TilsynDetailRow("Relateret", item.connectedCase)
            TilsynDetailRow("Initialer", item.initials)
            TilsynDetailRow("Start", tilsynFormatDate(item.startDate))
            TilsynDetailRow("Slut", tilsynFormatDate(item.endDate))
        } else if (item.type == "indmeldt") {
            TilsynDetailRow("Sagsnummer", item.caseNumber)
            TilsynDetailRow("Titel", item.title)
            TilsynDetailRow("Beskrivelse", item.description)
            TilsynDetailRow("Adresse", item.fullAddress)
            TilsynDetailRow("Oprettet af", item.createdBy)
            TilsynDetailRow("Oprettet", tilsynFormatDate(item.createdAt))
        } else {
            TilsynDetailRow("Faktura Status", item.fakturaStatus)
            TilsynDetailRow("Sag ID", item.id)
            TilsynDetailRow("Firma", item.firmanavn)
            TilsynDetailRow("CVR", item.cvr)
            TilsynDetailRow("Forseelse", item.forseelse)
            TilsynDetailRow("Tilladelsestype", prettyType(item.tilladelsestype))
            TilsynDetailRow("Areal", if (item.kvadratmeter != null) "${item.kvadratmeter} m²" else null)
            TilsynDetailRow("Start", tilsynFormatDate(item.startDate))
            val slutLabel = if (item.fakturaStatus == "Ny") "Sidst set" else "Slut"
            TilsynDetailRow(slutLabel, tilsynFormatDate(item.endDate))
        }

        // Unified History Section
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val initials = record.inspectorEmail.substringBefore("@").uppercase()
                        val statusText = when {
                            !record.selection.isNullOrBlank() -> " - ${record.selection}"
                            record.fakturaStatus == "Ny" -> " - Stadig opstillet"
                            record.fakturaStatus == "Til fakturering" -> " - Sendt til fakturering"
                            record.fakturaStatus == "Fakturer ikke" -> " - Skjult/Fjern"
                            !record.fakturaStatus.isNullOrBlank() -> " - ${record.fakturaStatus}"
                            else -> ""
                        }
                        
                        Text(
                            text = "$initials$statusText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (statusText.contains("fakt", true)) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = tilsynFormatDateShort(record.inspectedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (record.kvadratmeter != null || !record.endDate.isNullOrBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (record.kvadratmeter != null) {
                                Text(
                                    text = "Areal: ${record.kvadratmeter} m²",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (!record.endDate.isNullOrBlank()) {
                                Text(
                                    text = "Slutdato: ${record.endDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
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
