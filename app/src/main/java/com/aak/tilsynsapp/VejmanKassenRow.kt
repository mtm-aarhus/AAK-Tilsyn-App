package com.aak.tilsynsapp

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "VejmanKassen")
data class VejmanKassenRow(
    @PrimaryKey
    @SerializedName("id") val id: String,
    @SerializedName("HenstillingId") val henstillingId: String?,
    @SerializedName("CVR") val cvr: Int?,
    @SerializedName("Tilladelsestype") val tilladelsestype: String?,
    @SerializedName("Kvadratmeter") val kvadratmeter: Float?,
    @SerializedName("Startdato") val startdato: String?,
    @SerializedName("Slutdato") val slutdato: String?,
    @SerializedName("Adresse") val adresse: String?,
    @SerializedName("Forseelse") val forseelse: String?,
    @SerializedName("FirmaNavn") val firmanavn: String?,
    @SerializedName("Longitude") val longitude: Double?,
    @SerializedName("Latitude") val latitude: Double?,
    @SerializedName("FakturaStatus") val fakturaStatus: String?
)