package com.aak.tilsynsapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Utm32ConverterTest {

    @Test
    fun testAarhusConversionToUtmAndBack() {
        // Aarhus Center approx coordinates
        val lat = 56.1567
        val lon = 10.2108

        val utm = Utm32Converter.latLonToUtm32(lat, lon)

        // Aarhus is in UTM Zone 32N around Easting ~575197 and Northing ~6224100
        assertTrue("Easting should be ~575197m but was ${utm.easting}", utm.easting in 570000.0..580000.0)
        assertTrue("Northing should be ~6224100m but was ${utm.northing}", utm.northing in 6200000.0..6250000.0)

        val back = Utm32Converter.utm32ToLatLon(utm.easting, utm.northing)

        assertEquals("Latitude round-trip exactness", lat, back.latitude, 1e-6)
        assertEquals("Longitude round-trip exactness", lon, back.longitude, 1e-6)
    }

    @Test
    fun testMultipleDenmarkLocationsRoundTrip() {
        val testLocations = listOf(
            Pair(55.6761, 12.5683), // Copenhagen
            Pair(57.0488, 9.9217),  // Aalborg
            Pair(55.4765, 8.4594),  // Esbjerg
            Pair(56.1530, 10.2030)  // Aarhus Town Hall
        )

        for ((lat, lon) in testLocations) {
            val utm = Utm32Converter.latLonToUtm32(lat, lon)
            val back = Utm32Converter.utm32ToLatLon(utm.easting, utm.northing)

            assertEquals("Lat round-trip failed for ($lat, $lon)", lat, back.latitude, 1e-6)
            assertEquals("Lon round-trip failed for ($lat, $lon)", lon, back.longitude, 1e-6)
        }
    }
}
