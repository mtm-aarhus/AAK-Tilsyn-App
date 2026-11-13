package com.aak.tilsynsapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiHelperTest {

    @Test
    fun testWktPointParsing() {
        val pointRegex = Regex("""POINT\s*\(\s*([0-9.-]+)\s+([0-9.-]+)\s*\)""", RegexOption.IGNORE_CASE)

        val wkt1 = "POINT (575197.12 6224100.45)"
        val match1 = pointRegex.find(wkt1)
        assertTrue(match1 != null)
        assertEquals(575197.12, match1!!.groupValues[1].toDouble(), 0.01)
        assertEquals(6224100.45, match1.groupValues[2].toDouble(), 0.01)

        val wkt2 = "POINT(500000 6200000)"
        val match2 = pointRegex.find(wkt2)
        assertTrue(match2 != null)
        assertEquals(500000.0, match2!!.groupValues[1].toDouble(), 0.01)
        assertEquals(6200000.0, match2.groupValues[2].toDouble(), 0.01)
    }

    @Test
    fun testHusnummerStatusFilter() {
        fun isDropped(status: Any?): Boolean {
            return when (status) {
                is Number -> status.toInt() in listOf(4, 5)
                is String -> status.trim() in listOf("4", "5")
                else -> false
            }
        }

        assertTrue(isDropped(4))
        assertTrue(isDropped(5))
        assertTrue(isDropped("4"))
        assertTrue(isDropped("5"))
        assertTrue(isDropped(" 4 "))

        assertFalse(isDropped(1))
        assertFalse(isDropped(3))
        assertFalse(isDropped("1"))
        assertFalse(isDropped("ok"))
        assertFalse(isDropped(null))
    }

    @Test
    fun testExtractObjectOrFirst() {
        fun extractObjectOrFirst(map: Map<*, *>?, key: String): Map<*, *>? {
            if (map == null) return null
            val value = map[key]
            if (value is Map<*, *>) return value
            if (value is List<*> && value.isNotEmpty()) {
                val first = value[0]
                if (first is Map<*, *>) return first
            }
            return null
        }

        // Direct map
        val mapWithObject = mapOf("husnummer" to mapOf("vejnavn" to "Rådhuspladsen"))
        val extracted1 = extractObjectOrFirst(mapWithObject, "husnummer")
        assertEquals("Rådhuspladsen", extracted1?.get("vejnavn"))

        // Array wrapper
        val mapWithArray = mapOf("husnummer" to listOf(mapOf("vejnavn" to "Rådhuspladsen")))
        val extracted2 = extractObjectOrFirst(mapWithArray, "husnummer")
        assertEquals("Rådhuspladsen", extracted2?.get("vejnavn"))

        // Missing key
        val extracted3 = extractObjectOrFirst(mapWithObject, "missing")
        assertEquals(null, extracted3)
    }

    @Test
    fun testFindFirstListInJson() {
        fun findFirstListInJson(obj: Any?): List<Map<*, *>> {
            if (obj is List<*>) {
                return obj.filterIsInstance<Map<*, *>>()
            }
            if (obj is Map<*, *>) {
                for ((key, value) in obj) {
                    if (key == "errors") continue
                    if (value is List<*>) {
                        val maps = value.filterIsInstance<Map<*, *>>()
                        if (maps.isNotEmpty()) return maps
                    }
                }
                for ((key, value) in obj) {
                    if (key == "errors") continue
                    if (value is Map<*, *>) {
                        val nested = findFirstListInJson(value)
                        if (nested.isNotEmpty()) return nested
                    }
                }
            }
            return emptyList()
        }

        val directList = listOf(mapOf("tekst" to "Søndergade 1"))
        assertEquals(1, findFirstListInJson(directList).size)

        val mapWithAdresser = mapOf("status" to "ok", "adresser" to listOf(mapOf("tekst" to "Søndergade 1")))
        assertEquals("Søndergade 1", findFirstListInJson(mapWithAdresser)[0]["tekst"])

        val mapWithNestedData = mapOf("status" to "ok", "data" to mapOf("items" to listOf(mapOf("tekst" to "Søndergade 1"))))
        assertEquals("Søndergade 1", findFirstListInJson(mapWithNestedData)[0]["tekst"])
    }
}
