package com.aak.tilsynsapp

import com.google.gson.annotations.SerializedName

data class TilsynItem(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String, // "permission" or "henstilling"
    
    // Common inspection fields
    @SerializedName("last_inspected_at") val lastInspectedAt: String? = null,
    @SerializedName("last_inspector_email") val lastInspectorEmail: String? = null,
    @SerializedName("inspection_comment") val inspectionComment: String? = null,
    @SerializedName("hidden") val hidden: Boolean? = false,
    @SerializedName("inspections") val inspections: List<InspectionRecord>? = emptyList(),

    // Permission specific (Vejman)
    @SerializedName("case_number") val caseNumber: String? = null,
    @SerializedName("case_id") val caseId: String? = null,
    @SerializedName("vejman_state") val vejmanState: String? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("applicant") val applicant: String? = null,
    @SerializedName("marker") val marker: String? = null,
    @SerializedName("rovm_equipment_type") val rovmEquipmentType: String? = null,
    @SerializedName("applicant_folder_number") val applicantFolderNumber: String? = null,
    @SerializedName("authority_reference_number") val authorityReferenceNumber: String? = null,
    @SerializedName("street_status") val streetStatus: String? = null,
    @SerializedName("street_name") val streetName: String? = null,
    @SerializedName("street_number_text") val streetNumberText: String? = null,
    @SerializedName("initials") val initials: String? = null,
    @SerializedName("connected_case") val connectedCase: String? = null,

    // Henstilling specific (VejmanKassen)
    @SerializedName("HenstillingId") val henstillingId: String? = null,
    @SerializedName("CVR") val cvr: Int? = null,
    @SerializedName("Tilladelsestype") val tilladelsestype: String? = null,
    @SerializedName("Kvadratmeter") val kvadratmeter: Float? = null,
    @SerializedName("Startdato") val startdatoHenstilling: String? = null,
    @SerializedName("Slutdato") val slutdatoHenstilling: String? = null,
    @SerializedName("Adresse") val adresse: String? = null,
    @SerializedName("Forseelse") val forseelse: String? = null,
    @SerializedName("FirmaNavn") val firmanavn: String? = null,
    @SerializedName("FakturaStatus") val fakturaStatus: String? = null
) {
    val displayStreet: String
        get() = if (type == "permission") {
            "${streetName ?: ""} ${streetNumberText ?: ""}".trim()
        } else {
            adresse ?: "Ukendt Vej"
        }

    val displayCaseNumber: String
        get() = (if (type == "permission") caseNumber else henstillingId) ?: "Ingen Sag"

    val displaySecondaryInfo: String
        get() = (if (type == "permission") applicant else firmanavn) ?: "-"

    val displayEquipment: String
        get() = (if (type == "permission") rovmEquipmentType else forseelse) ?: "-"

    val displayEndDate: String?
        get() = if (type == "permission") endDate else slutdatoHenstilling

    val typeLabel: String
        get() = if (type == "permission") {
            val state = vejmanState ?: "Aktiv"
            when {
                state.contains("Ny", ignoreCase = true) -> "NY TILLADELSE"
                state.contains("Færdig", ignoreCase = true) -> "FÆRDIG TILLADELSE"
                state == "Godkendt" -> "NY TILLADELSE"
                state == "Endelig færdigmeldt" -> "FÆRDIG TILLADELSE"
                state == "Udløbet" -> "UDLØBET TILLADELSE"
                else -> "${state.uppercase()} TILLADELSE"
            }
        } else {
            "HENSTILLING"
        }
}

data class InspectionRecord(
    @SerializedName("inspected_at") val inspectedAt: String,
    @SerializedName("inspector_email") val inspectorEmail: String,
    @SerializedName("comment") val comment: String?,
    @SerializedName("selection") val selection: String? = null,
    @SerializedName("kvadratmeter") val kvadratmeter: Float? = null,
    @SerializedName("faktura_status") val fakturaStatus: String? = null,
    @SerializedName("slutdato") val slutdato: String? = null
)
