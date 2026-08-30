package com.moneydance.modules.features.starling.api

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DateChunksTest {
    @Test
    fun splitsLongRangeInto180DayWindows() {
        val chunks = DateChunks.windows(LocalDate.of(2019, 3, 11), LocalDate.of(2026, 8, 30))
        assertTrue(chunks.size > 10)
        chunks.forEach { (a, b) ->
            assertTrue(!b.isBefore(a))
            assertTrue(java.time.temporal.ChronoUnit.DAYS.between(a, b) < DateChunks.MAX_DAYS)
        }
        assertEquals(LocalDate.of(2019, 3, 11), chunks.first().first)
        assertEquals(LocalDate.of(2026, 8, 30), chunks.last().second)
    }

    @Test
    fun clampsBeforeAccountOpened() {
        val opened = "2019-03-11T16:50:01.000Z"
        assertEquals(
            LocalDate.of(2019, 3, 11),
            DateChunks.notBeforeOpened(LocalDate.of(2000, 1, 1), opened)
        )
    }

    @Test
    fun singleDay() {
        val d = LocalDate.of(2026, 1, 1)
        assertEquals(listOf(d to d), DateChunks.windows(d, d))
    }
}
