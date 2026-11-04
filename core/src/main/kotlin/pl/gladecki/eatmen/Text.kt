package pl.gladecki.eatmen.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * Every word the app says. Statements of time and fact, nothing to decode and
 * nothing that turns a missed meal into a failure.
 */
object Text {

    fun clock(at: Long, zone: ZoneId): String {
        val local = Instant.ofEpochMilli(at).atZone(zone)
        return twoDigits(local.hour) + ":" + twoDigits(local.minute)
    }

    fun kindWord(kind: Kind): String = if (kind == Kind.MEAL) "meal" else "snack"

    /** "2h 14m", "45m", "0m". Used where space is tight. */
    fun short(ms: Long): String {
        val minutes = (ms / MINUTE_MS.toDouble()).roundToLong()
        if (minutes <= 0) return "0m"
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0L -> "${rest}m"
            rest == 0L -> "${hours}h"
            else -> "${hours}h ${rest}m"
        }
    }

    /** "9 hours", "1 hour", "45 minutes". Used in the one sentence that is read at a glance. */
    fun spelled(ms: Long): String {
        val minutes = (ms / MINUTE_MS.toDouble()).roundToLong().coerceAtLeast(1)
        if (minutes < 60) return if (minutes == 1L) "1 minute" else "$minutes minutes"
        val hours = minutes / 60
        return if (hours == 1L) "1 hour" else "$hours hours"
    }

    fun ago(now: Long, at: Long): String = short(now - at) + " ago"

    fun lastLine(now: Long, meal: Meal?, zone: ZoneId): String =
        if (meal == null) "nothing logged"
        else clock(meal.eatenAt, zone) + " · " + kindWord(meal.kind) + " · " + ago(now, meal.eatenAt)

    fun todayRow(now: Long, meal: Meal, zone: ZoneId): String =
        clock(meal.eatenAt, zone) + " · " + kindWord(meal.kind) + " · " + ago(now, meal.eatenAt)

    /** The front screen's next line. The countdown is to the next moment the app speaks. */
    fun nextLine(now: Long, plan: Plan?, zone: ZoneId): String {
        if (plan == null) return "no reminder"
        if (plan is Plan.TomorrowSilent) return "silent until " + clock(plan.at, zone)
        val at = Planner.alertAt(plan)
        val tail = if (at <= now) "now" else "in " + short(at - now)
        return clock(at, zone) + " · " + tail
    }

    /** The ongoing countdown notification's title. */
    fun countdownTitle(): String = "Next meal in"

    fun alertTitle(now: Long, zone: ZoneId): String = "It's " + clock(now, zone) + "."

    fun alertBody(now: Long, lastMeal: Meal?): String =
        if (lastMeal == null) "No meal logged yet."
        else "It's been " + short(now - lastMeal.eatenAt) + "."

    fun warningText(dueAt: Long, zone: ZoneId): String = clock(dueAt, zone) + " in 5 minutes"

    fun tomorrowSilentTitle(now: Long, zone: ZoneId): String = "It's " + clock(now, zone) + "."

    fun tomorrowSilentBody(lastMeal: Meal?, zone: ZoneId): String =
        if (lastMeal == null) "No meal logged yet."
        else "Last meal " + clock(lastMeal.eatenAt, zone) + "."

    /** What the band says out loud, for a screen reader. */
    fun timelineDescription(timeline: Timeline, zone: ZoneId): String {
        if (timeline.meals.isEmpty()) return "Last 24 hours: no meals."
        val listed = timeline.meals.joinToString(", ") { kindWord(it.kind) + " at " + clock(it.eatenAt, zone) }
        return "Last 24 hours: $listed."
    }

    fun staleLine(now: Long, lastMeal: Meal?): String? {
        if (lastMeal == null) return null
        val age = now - lastMeal.eatenAt
        if (age < STALE_AFTER_MS) return null
        return "😕 " + spelled(age) + " since your last meal"
    }

    fun nightWarning(clock: String): String =
        "$clock is the middle of the night. This will make a sound and may wake you."

    fun deleteMealQuestion(): String = "Delete this meal?"

    fun eraseQuestion(): String = "Delete every meal and setting? This cannot be undone."

    private fun twoDigits(value: Int): String = if (value < 10) "0$value" else value.toString()
}
