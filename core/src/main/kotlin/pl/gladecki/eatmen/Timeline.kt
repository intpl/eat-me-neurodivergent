package pl.gladecki.eatmen.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A window of time in epoch millis, clipped to what the band shows. */
data class Span(val start: Long, val end: Long)

/**
 * The band on the front screen: the last 24 hours, ending now.
 *
 * It is a picture of what happened, so everything here is a fact from the log.
 * Positions are left to the view; this decides what falls inside the window and
 * where the clock's own hours sit.
 */
data class Timeline(
    val start: Long,
    val end: Long,
    val meals: List<Meal>,
    val nights: List<Span>,
    val hourMarks: List<Long>,
) {
    val span: Long get() = end - start
}

/**
 * Builds the band.
 *
 * The window is a rolling 24 hours rather than a calendar day on purpose: the
 * long gaps that matter cross midnight, and a day chopped at 00:00 hides exactly
 * the stretch this app exists for. Midnight itself is not marked; the shaded
 * night hours and the labels carry it.
 */
object TimelineBuilder {

    const val WINDOW_MS: Long = 24 * HOUR_MS
    const val HOUR_MARK_STEP: Int = 3

    fun build(now: Long, meals: List<Meal>, settings: Settings, zone: ZoneId): Timeline {
        val start = now - WINDOW_MS
        return Timeline(
            start = start,
            end = now,
            meals = meals.filter { it.isLive() && it.eatenAt in start..now }.sortedBy { it.eatenAt },
            nights = nights(start, now, settings, zone),
            hourMarks = hourMarks(start, now, zone),
        )
    }

    /** Night hours as the settings define them, clipped to the window. */
    private fun nights(from: Long, to: Long, settings: Settings, zone: ZoneId): List<Span> {
        val startMinute = settings.nightStartMinute
        val endMinute = settings.nightEndMinute
        if (startMinute == endMinute) return emptyList()

        val first = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1)
        val last = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        val out = mutableListOf<Span>()
        var day = first
        while (!day.isAfter(last)) {
            val endDay = if (startMinute > endMinute) day.plusDays(1) else day
            val spanStart = at(day, startMinute, zone)
            val spanEnd = at(endDay, endMinute, zone)
            val clipped = Span(maxOf(spanStart, from), minOf(spanEnd, to))
            if (clipped.end > clipped.start) out += clipped
            day = day.plusDays(1)
        }
        return out
    }

    /**
     * Clock hours on a three-hour grid, taken from the local calendar rather than
     * by stepping durations: on the day the clocks move, a stepped grid drifts off
     * the clock the phone shows, and the labels are the whole point of the axis.
     */
    private fun hourMarks(from: Long, to: Long, zone: ZoneId): List<Long> {
        val first = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        val out = mutableListOf<Long>()
        var day = first
        while (!day.isAfter(last)) {
            var hour = 0
            while (hour < 24) {
                val at = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
                if (at > from && at <= to && out.lastOrNull() != at) out += at
                hour += HOUR_MARK_STEP
            }
            day = day.plusDays(1)
        }
        return out.sorted()
    }

    private fun at(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Long =
        date.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone).toInstant().toEpochMilli()
}
