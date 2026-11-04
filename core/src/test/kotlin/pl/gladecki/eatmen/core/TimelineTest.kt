package pl.gladecki.eatmen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimelineTest {

    private val athens: ZoneId = ZoneId.of("Europe/Athens")
    private val settings = Settings()

    private fun local(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(athens).toInstant().toEpochMilli()

    private fun clock(at: Long): String {
        val time = java.time.Instant.ofEpochMilli(at).atZone(athens)
        return "%02d:%02d".format(time.hour, time.minute)
    }

    private fun everyHour(from: Long, to: Long, zone: ZoneId = athens): List<Long> {
        val out = mutableListOf<Long>()
        var t = from
        while (t <= to) {
            out += t
            t += HOUR_MS
        }
        return out
    }

    @Test
    fun theWindowIsTheLastTwentyFourHoursEndingNow() {
        val now = local(2026, 11, 4, 15, 20)
        val band = TimelineBuilder.build(now, emptyList(), settings, athens)
        assertEquals(now, band.end)
        assertEquals(now - 24 * HOUR_MS, band.start)
    }

    @Test
    fun onlyMealsInsideTheWindowAreDrawn() {
        val now = local(2026, 11, 4, 15, 20)
        val meals = listOf(
            Meal("old", now - 25 * HOUR_MS, Kind.MEAL),
            Meal("yesterday", now - 19 * HOUR_MS, Kind.SNACK),
            Meal("today", now - 2 * HOUR_MS, Kind.MEAL),
        )
        val band = TimelineBuilder.build(now, meals, settings, athens)
        assertEquals(listOf("yesterday", "today"), band.meals.map { it.id })
    }

    @Test
    fun aMealExactlyTwentyFourHoursOldSitsOnTheLeftEdge() {
        val now = local(2026, 11, 4, 15, 20)
        val edge = Meal("edge", now - 24 * HOUR_MS, Kind.MEAL)
        val band = TimelineBuilder.build(now, listOf(edge), settings, athens)
        assertEquals(listOf("edge"), band.meals.map { it.id })
    }

    @Test
    fun mealsComeOutOldestFirst() {
        val now = local(2026, 11, 4, 15, 20)
        val meals = listOf(
            Meal("c", now - HOUR_MS, Kind.MEAL),
            Meal("a", now - 9 * HOUR_MS, Kind.SNACK),
            Meal("b", now - 4 * HOUR_MS, Kind.MEAL),
        )
        val band = TimelineBuilder.build(now, meals, settings, athens)
        assertEquals(listOf("a", "b", "c"), band.meals.map { it.id })
    }

    @Test
    fun aDeletedMealIsNotOnTheBand() {
        val now = local(2026, 11, 4, 15, 20)
        val band = TimelineBuilder.build(now, listOf(Meal("", now - HOUR_MS, Kind.MEAL)), settings, athens)
        assertTrue(band.meals.isEmpty())
    }

    @Test
    fun midnightIsMarkedInsideTheWindowNotAtItsEdge() {
        val now = local(2026, 11, 4, 15, 20)
        val band = TimelineBuilder.build(now, emptyList(), settings, athens)
        assertEquals(listOf(local(2026, 11, 4, 0, 0)), band.midnights)
        assertTrue(band.midnights.single() > band.start)
        assertTrue(band.midnights.single() < band.end)
    }

    @Test
    fun aWindowThatEndsJustAfterMidnightStillHasItsMidnight() {
        val now = local(2026, 11, 4, 0, 10)
        val band = TimelineBuilder.build(now, emptyList(), settings, athens)
        assertEquals(listOf(local(2026, 11, 4, 0, 0)), band.midnights)
    }

    @Test
    fun theNightHoursAreClippedToTheWindow() {
        val now = local(2026, 11, 4, 15, 20)
        val band = TimelineBuilder.build(now, emptyList(), settings, athens)
        assertEquals(1, band.nights.size)
        val night = band.nights.single()
        assertEquals(local(2026, 11, 3, 23, 0), night.start)
        assertEquals(local(2026, 11, 4, 7, 0), night.end)
        assertEquals(8 * HOUR_MS, night.end - night.start)
    }

    @Test
    fun theNightHoursFollowTheSetting() {
        val now = local(2026, 11, 4, 15, 20)
        val night = settings.copy(nightStartMinute = 60, nightEndMinute = 5 * 60)
        val band = TimelineBuilder.build(now, emptyList(), night, athens)
        assertEquals(1, band.nights.size)
        assertEquals("01:00", clock(band.nights.single().start))
        assertEquals("05:00", clock(band.nights.single().end))
    }

    @Test
    fun aNightWindowThatDoesNotCrossMidnightStaysInsideItsDay() {
        val now = local(2026, 11, 4, 15, 20)
        val night = settings.copy(nightStartMinute = 22 * 60, nightEndMinute = 23 * 60)
        val band = TimelineBuilder.build(now, emptyList(), night, athens)
        assertEquals(1, band.nights.size)
        val span = band.nights.single()
        assertEquals("22:00", clock(span.start))
        assertEquals("23:00", clock(span.end))
    }

    @Test
    fun theHourMarksSitOnTheClockEveryThreeHours() {
        val now = local(2026, 11, 4, 15, 20)
        val band = TimelineBuilder.build(now, emptyList(), settings, athens)
        assertEquals(
            listOf("18:00", "21:00", "00:00", "03:00", "06:00", "09:00", "12:00", "15:00"),
            band.hourMarks.map { clock(it) },
        )
    }

    @Test
    fun springForwardKeepsTheLabelsOnTheClock() {
        // Athens jumps 03:00 to 04:00 on 2026-03-29.
        val now = local(2026, 3, 29, 12, 0)
        val band = TimelineBuilder.build(now, emptyList(), settings, athens)
        // A rolling 24 hours across the jump starts at 11:00 wall time, not 12:00,
        // so the first label on the grid is 12:00 and 03:00 is missing entirely.
        assertEquals(
            listOf("12:00", "15:00", "18:00", "21:00", "00:00", "04:00", "06:00", "09:00", "12:00"),
            band.hourMarks.map { clock(it) },
        )
        assertEquals(listOf(local(2026, 3, 29, 0, 0)), band.midnights)
        assertTrue("the band is still 24 real hours", band.span == 24 * HOUR_MS)
    }

    @Test
    fun fallBackKeepsTheLabelsOnTheClock() {
        // Athens falls back 04:00 to 03:00 on 2026-10-25.
        val now = local(2026, 10, 25, 12, 0)
        val band = TimelineBuilder.build(now, emptyList(), settings, athens)
        assertEquals(
            listOf("15:00", "18:00", "21:00", "00:00", "03:00", "06:00", "09:00", "12:00"),
            band.hourMarks.map { clock(it) },
        )
    }

    @Test
    fun anEmptyLogStillGivesAFrame() {
        val now = local(2026, 11, 4, 15, 20)
        val band = TimelineBuilder.build(now, emptyList(), settings, athens)
        assertTrue(band.meals.isEmpty())
        assertEquals(8, band.hourMarks.size)
        assertEquals(24 * HOUR_MS, band.span)
    }

    @Test
    fun mealsAreDrawnInThePhonesZoneNotUtc() {
        // 22:30 UTC is 00:30 in Athens, so a meal at that instant belongs to the
        // next local day: it must sit to the right of the midnight mark.
        val now = local(2026, 11, 4, 15, 20)
        val late = local(2026, 11, 4, 0, 30)
        val band = TimelineBuilder.build(now, listOf(Meal("late", late, Kind.SNACK)), settings, athens)
        assertTrue(band.meals.single().eatenAt > band.midnights.single())
    }

    @Test
    fun everyMealOnTheBandIsInsideTheWindow() {
        val now = local(2026, 11, 4, 15, 20)
        val meals = everyHour(now - 30 * HOUR_MS, now, athens).mapIndexed { i, t -> Meal("m$i", t, Kind.MEAL) }
        val band = TimelineBuilder.build(now, meals, settings, athens)
        assertTrue(band.meals.isNotEmpty())
        assertTrue(band.meals.all { it.eatenAt in band.start..band.end })
    }
}
