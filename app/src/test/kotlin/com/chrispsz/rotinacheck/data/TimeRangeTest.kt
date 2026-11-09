package com.chrispsz.rotinacheck.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hand-verified parsing for every supported `horario` shape. */
class TimeRangeTest {

    @Test
    fun `closed range parses both ends`() {
        val range = TimeRange.parse("05:00 - 12:30")
        assertEquals(TimeRange(300, 750), range)
    }

    @Test
    fun `open block parses start only`() {
        assertEquals(TimeRange(720, null), TimeRange.parse("12:00 em diante"))
    }

    @Test
    fun `single time parses as open block`() {
        assertEquals(TimeRange(1290, null), TimeRange.parse("21:30"))
    }

    @Test
    fun `garbage empty and impossible times yield null`() {
        assertNull(TimeRange.parse(""))
        assertNull(TimeRange.parse(null))
        assertNull(TimeRange.parse("sem horário"))
        assertNull(TimeRange.parse("25:00"))
        assertNull(TimeRange.parse("10:70"))
    }

    @Test
    fun `end not after start degrades to open block instead of dropping`() {
        assertEquals(TimeRange(600, null), TimeRange.parse("10:00 - 09:00"))
        assertEquals(TimeRange(600, null), TimeRange.parse("10:00 - 10:00"))
    }

    @Test
    fun `format round-trips parse`() {
        assertEquals("05:00 - 12:30", TimeRange.parse("05:00 - 12:30")?.format())
        assertEquals("12:00 em diante", TimeRange.parse("12:00 em diante")?.format())
    }

    @Test
    fun `label is zero-padded and locale-free`() {
        assertEquals("05:00", TimeRange.label(300))
        assertEquals("21:30", TimeRange.label(1290))
        assertEquals("00:00", TimeRange.label(0))
        assertEquals("23:59", TimeRange.label(23 * 60 + 59))
    }

    @Test
    fun `contains is start-inclusive end-exclusive`() {
        val range = TimeRange(840, 855)
        assertTrue(range.contains(840))
        assertTrue(range.contains(854))
        assertFalse(range.contains(855))
        assertFalse(range.contains(839))
    }
}
