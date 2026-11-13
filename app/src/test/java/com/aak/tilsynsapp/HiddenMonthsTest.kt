package com.aak.tilsynsapp

import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenMonthsTest {

    @Test
    fun parkletWinterIsOneSpanAcrossTheNewYear() {
        assertEquals("oktober til marts", describeMonths(listOf(1, 2, 3, 10, 11, 12)))
    }

    @Test
    fun aSpanInsideTheYear() {
        assertEquals("april til september", describeMonths(listOf(4, 5, 6, 7, 8, 9)))
    }

    @Test
    fun scatteredMonthsAreListed() {
        assertEquals("januar, marts og maj", describeMonths(listOf(5, 1, 3)))
    }

    @Test
    fun twoMonthsInARowAreNamedNotSpanned() {
        assertEquals("december og januar", describeMonths(listOf(12, 1)))
        assertEquals("januar, februar og maj", describeMonths(listOf(1, 2, 5)))
    }

    @Test
    fun spansAndSingleMonthsTogether() {
        assertEquals("januar til marts og juli", describeMonths(listOf(1, 2, 3, 7)))
    }

    @Test
    fun oneMonth() {
        assertEquals("juli", describeMonths(listOf(7)))
    }

    @Test
    fun noneAllAndOddValues() {
        assertEquals("", describeMonths(emptyList()))
        assertEquals("hele året", describeMonths((1..12).toList()))
        assertEquals("juli", describeMonths(listOf(7, 7, 0, 13)))
    }

    @Test
    fun whatTheAppSaysOnceSaved() {
        assertEquals("Tilladelsen er med på tilsynslisten i alle måneder.", hiddenMonthsSavedText(emptyList()))
        assertEquals(
            "Tilladelsen er ikke med på tilsynslisten i disse måneder: oktober til marts.",
            hiddenMonthsSavedText(listOf(10, 11, 12, 1, 2, 3))
        )
    }
}
