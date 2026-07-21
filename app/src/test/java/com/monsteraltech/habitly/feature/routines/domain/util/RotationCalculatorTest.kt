package com.monsteraltech.habitly.feature.routines.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RotationCalculatorTest {

    private val members = listOf("ana", "beto", "carla")

    @Test
    fun `advances to the next member`() {
        assertEquals("beto", RotationCalculator.next(members, "ana"))
        assertEquals("carla", RotationCalculator.next(members, "beto"))
    }

    @Test
    fun `wraps around at the end of the list`() {
        assertEquals("ana", RotationCalculator.next(members, "carla"))
    }

    @Test
    fun `without a current holder it starts with the first member`() {
        assertEquals("ana", RotationCalculator.next(members, null))
    }

    @Test
    fun `a member who left the household falls back to the first`() {
        assertEquals("ana", RotationCalculator.next(members, "alguien-que-ya-no-esta"))
    }

    @Test
    fun `with a single member the turn stays with them`() {
        assertEquals("ana", RotationCalculator.next(listOf("ana"), "ana"))
    }

    @Test
    fun `without members there is nobody to assign`() {
        assertNull(RotationCalculator.next(emptyList(), "ana"))
        assertNull(RotationCalculator.next(emptyList(), null))
    }

    @Test
    fun `a full lap returns to the starting point`() {
        var current: String? = "ana"
        repeat(members.size) { current = RotationCalculator.next(members, current) }

        assertEquals("ana", current)
    }
}
