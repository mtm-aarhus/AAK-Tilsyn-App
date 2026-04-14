package com.aak.tilsynsapp

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "TilsynRows")
data class TilsynRow(
    @PrimaryKey
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String?,
    
    // Unified Location Fields
    @SerializedName("street_name") val streetName: String?,
    @SerializedName("full_address") val fullAddress: String?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,

    // Unified Date Fields
    @SerializedName("start_date") val startDate: String?,
    @SerializedName("end_date") val endDate: String?,

    // Permission specific (Vejman)
    @SerializedName("case_number") val caseNumber: String?,
    @SerializedName("case_id") val caseId: String?,
    @SerializedName("vejman_state") val vejmanState: String?,
    @SerializedName("vejman_display_state") val vejmanDisplayState: String?,
    @SerializedName("applicant") val applicant: String?,
    
    // Henstilling specific (VejmanKassen)
    @SerializedName("HenstillingId") val henstillingId: String?,
    @SerializedName("CVR") val cvr: String?,
    @SerializedName("Tilladelsestype") val tilladelsestype: String?,
    @SerializedName("Kvadratmeter") val kvadratmeter: Float?,
    @SerializedName("Forseelse") val forseelse: String?,
    @SerializedName("FirmaNavn") val firmanavn: String?,
    @SerializedName("FakturaStatus") val fakturaStatus: String?,

    // Status fields for UI/Offline
    val lastInspectedAt: String? = null
)
