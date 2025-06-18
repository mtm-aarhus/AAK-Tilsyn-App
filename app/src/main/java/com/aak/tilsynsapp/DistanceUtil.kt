package com.aak.tilsynsapp

import android.location.Location

object DistanceUtil {
    /**
     * Calculates distance (in meters) between two coordinates.
     */
    fun distanceInMeters(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Float {
        val result = FloatArray(1)
        Location.distanceBetween(fromLat, fromLon, toLat, toLon, result)
        return result[0]
    }

    /**
     * Annotates rows with distance from current location.
     */
    fun annotateDistances(
        rows: List<VejmanKassenRow>,
        currentLat: Double,
        currentLon: Double
    ): List<VejmanKassenRow> {
        return rows.map { row ->
            if (row.latitude != null && row.longitude != null) {
                row.distanceFromCurrent = distanceInMeters(
                    currentLat,
                    currentLon,
                    row.latitude,
                    row.longitude
                )
            } else {
                row.distanceFromCurrent = Float.MAX_VALUE // Treat nulls as "very far"
            }
            row
        }.sortedBy { it.distanceFromCurrent }
    }
}