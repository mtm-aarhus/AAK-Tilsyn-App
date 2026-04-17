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

    // Unified Location Fields
    @SerializedName("street_name") val streetName: String? = null,
    @SerializedName("full_address") val fullAddress: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,

    // Unified Date Fields
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,

    // Permission specific (Vejman)
    @SerializedName("case_number") val caseNumber: String? = null,
    @SerializedName("case_id") val caseId: String? = null,
    @SerializedName("vejman_state") val vejmanState: String? = null,
    @SerializedName("vejman_display_state") val vejmanDisplayState: String? = null,
    @SerializedName("applicant") val applicant: String? = null,
    @SerializedName("marker") val marker: String? = null,
    @SerializedName("rovm_equipment_type") val rovmEquipmentType: String? = null,
    @SerializedName("applicant_folder_number") val applicantFolderNumber: String? = null,
    @SerializedName("authority_reference_number") val authorityReferenceNumber: String? = null,
    @SerializedName("street_status") val streetStatus: String? = null,
    @SerializedName("initials") val initials: String? = null,
    @SerializedName("connected_case") val connectedCase: String? = null,
    @SerializedName("sharepoint_link") val sharepointLink: String? = null,

    // Henstilling specific (VejmanKassen)
    @SerializedName("HenstillingId") val henstillingId: String? = null,
    @SerializedName("CVR") val cvr: String? = null, // CVR can be long or string, usually safer with String if it can be empty
    @SerializedName("Tilladelsestype") val tilladelsestype: String? = null,
    @SerializedName("Kvadratmeter") val kvadratmeter: Float? = null,
    @SerializedName("Forseelse") val forseelse: String? = null,
    @SerializedName("FirmaNavn") val firmanavn: String? = null,
    @SerializedName("FakturaStatus") val fakturaStatus: String? = null,

    // Indmeldt specific (ad-hoc tilsyn created manually)
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("created_by") val createdBy: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("created_by_source") val createdBySource: String? = null
) {
    val displayStreet: String
        get() = fullAddress ?: streetName ?: "Ukendt Vej"

    val displayCaseNumber: String
        get() = when (type) {
            "permission" -> caseNumber
            "indmeldt" -> caseNumber
            else -> henstillingId
        } ?: "Ingen Sag"

    val displaySecondaryInfo: String
        get() = when (type) {
            "permission" -> applicant
            "indmeldt" -> createdBy
            else -> firmanavn
        } ?: "-"

    val displayEquipment: String
        get() = when (type) {
            "permission" -> rovmEquipmentType
            "indmeldt" -> title
            else -> forseelse
        } ?: "-"

    val displayEndDate: String?
        get() = endDate

    val typeLabel: String
        get() = when (type) {
            "permission" -> vejmanDisplayState?.uppercase() ?: "TILLADELSE"
            "indmeldt" -> "INDMELDT"
            else -> "HENSTILLING"
        }
}

data class InspectionRecord(
    @SerializedName("inspected_at") val inspectedAt: String,
    @SerializedName("inspector_email") val inspectorEmail: String,
    @SerializedName("comment") val comment: String?,
    @SerializedName("selection") val selection: String? = null,
    @SerializedName("kvadratmeter") val kvadratmeter: Float? = null,
    @SerializedName("faktura_status") val fakturaStatus: String? = null,
    @SerializedName("end_date") val endDate: String? = null
)
