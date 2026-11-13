package com.aak.tilsynsapp

import kotlin.math.*

/**
 * Converter between WGS84 (latitude, longitude in degrees)
 * and UTM Zone 32N / EPSG:25832 (easting, northing in meters) on the GRS80 ellipsoid.
 */
object Utm32Converter {
    private const val A = 6378137.0
    private const val E_SQ = 0.0066943799901413165
    private const val K0 = 0.9996
    private const val LON0_RAD = 9.0 * Math.PI / 180.0
    private const val FALSE_EASTING = 500000.0
    private const val FALSE_NORTHING = 0.0

    data class UtmPoint(val easting: Double, val northing: Double)
    data class LatLonPoint(val latitude: Double, val longitude: Double)

    /**
     * Converts WGS84 latitude and longitude (in degrees) to UTM Zone 32N / EPSG:25832 (easting, northing in meters).
     */
    fun latLonToUtm32(lat: Double, lon: Double): UtmPoint {
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val dLon = lonRad - LON0_RAD

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val tanLat = tan(latRad)

        val ePrimeSq = E_SQ / (1.0 - E_SQ)
        val N = A / sqrt(1.0 - E_SQ * sinLat * sinLat)
        val T = tanLat * tanLat
        val C = ePrimeSq * cosLat * cosLat
        val A_term = dLon * cosLat

        val M = A * (
            (1.0 - E_SQ / 4.0 - 3.0 * E_SQ * E_SQ / 64.0 - 5.0 * E_SQ * E_SQ * E_SQ / 256.0) * latRad
            - (3.0 * E_SQ / 8.0 + 3.0 * E_SQ * E_SQ / 32.0 + 45.0 * E_SQ * E_SQ * E_SQ / 1024.0) * sin(2.0 * latRad)
            + (15.0 * E_SQ * E_SQ / 256.0 + 45.0 * E_SQ * E_SQ * E_SQ / 1024.0) * sin(4.0 * latRad)
            - (35.0 * E_SQ * E_SQ * E_SQ / 3072.0) * sin(6.0 * latRad)
        )

        val easting = FALSE_EASTING + K0 * N * (
            A_term
            + (1.0 - T + C) * A_term * A_term * A_term / 6.0
            + (5.0 - 18.0 * T + T * T + 72.0 * C - 58.0 * ePrimeSq) * A_term * A_term * A_term * A_term * A_term / 120.0
        )

        val northing = FALSE_NORTHING + K0 * (
            M + N * tanLat * (
                A_term * A_term / 2.0
                + (5.0 - T + 9.0 * C + 4.0 * C * C) * A_term * A_term * A_term * A_term / 24.0
                + (61.0 - 58.0 * T + T * T + 600.0 * C - 330.0 * ePrimeSq) * A_term * A_term * A_term * A_term * A_term * A_term / 720.0
            )
        )

        return UtmPoint(easting, northing)
    }

    /**
     * Converts UTM Zone 32N / EPSG:25832 (easting, northing in meters) to WGS84 latitude and longitude (in degrees).
     */
    fun utm32ToLatLon(easting: Double, northing: Double): LatLonPoint {
        val x = easting - FALSE_EASTING
        val y = northing - FALSE_NORTHING

        val M = y / K0
        val e1 = (1.0 - sqrt(1.0 - E_SQ)) / (1.0 + sqrt(1.0 - E_SQ))

        val mu = M / (A * (1.0 - E_SQ / 4.0 - 3.0 * E_SQ * E_SQ / 64.0 - 5.0 * E_SQ * E_SQ * E_SQ / 256.0))

        val phi1Rad = mu +
            (3.0 * e1 / 2.0 - 27.0 * e1 * e1 * e1 / 32.0) * sin(2.0 * mu) +
            (21.0 * e1 * e1 / 16.0 - 55.0 * e1 * e1 * e1 * e1 / 32.0) * sin(4.0 * mu) +
            (151.0 * e1 * e1 * e1 / 96.0) * sin(6.0 * mu) +
            (1097.0 * e1 * e1 * e1 * e1 / 512.0) * sin(8.0 * mu)

        val sinPhi1 = sin(phi1Rad)
        val cosPhi1 = cos(phi1Rad)
        val tanPhi1 = tan(phi1Rad)

        val ePrimeSq = E_SQ / (1.0 - E_SQ)
        val N1 = A / sqrt(1.0 - E_SQ * sinPhi1 * sinPhi1)
        val R1 = A * (1.0 - E_SQ) / (sqrt(1.0 - E_SQ * sinPhi1 * sinPhi1) * (1.0 - E_SQ * sinPhi1 * sinPhi1))
        val T1 = tanPhi1 * tanPhi1
        val C1 = ePrimeSq * cosPhi1 * cosPhi1
        val D = x / (N1 * K0)

        val latRad = phi1Rad - (N1 * tanPhi1 / R1) * (
            D * D / 2.0 -
            (5.0 + 3.0 * T1 + 10.0 * C1 - 4.0 * C1 * C1 - 9.0 * ePrimeSq) * D * D * D * D / 24.0 +
            (61.0 + 90.0 * T1 + 298.0 * C1 + 45.0 * T1 * T1 - 252.0 * ePrimeSq - 3.0 * C1 * C1) * D * D * D * D * D * D / 720.0
        )

        val lonRad = LON0_RAD + (1.0 / cosPhi1) * (
            D -
            (1.0 + 2.0 * T1 + C1) * D * D * D / 6.0 +
            (5.0 - 2.0 * C1 + 28.0 * T1 - 3.0 * C1 * C1 + 8.0 * ePrimeSq + 24.0 * T1 * T1) * D * D * D * D * D / 120.0
        )

        return LatLonPoint(Math.toDegrees(latRad), Math.toDegrees(lonRad))
    }
}
