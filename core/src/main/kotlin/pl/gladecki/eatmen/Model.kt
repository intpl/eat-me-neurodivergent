package pl.gladecki.eatmen.core

const val MINUTE_MS: Long = 60_000L
const val HOUR_MS: Long = 60L * MINUTE_MS

/** What was eaten is never asked; only this much is kept. */
enum class Kind { MEAL, SNACK }

data class Meal(val id: String, val eatenAt: Long, val kind: Kind) {
    fun isLive(): Boolean = !id.isBlank()
}

/** Where the next reminder came from. */
enum class Source { EXPLICIT, SUGGESTED, TOMORROW }

/** One step of the notification ladder. There is no step before a reminder: it speaks when it is due. */
enum class Step { DUE, REPEAT, TOMORROW_SILENT, TOMORROW_DUE }

/**
 * The one pending thing the app will do, as a value.
 *
 * [at] is always the instant the next notification step fires. Whatever draws
 * the front screen or arms the alarm reads this, so the screen and the alarm
 * cannot disagree.
 */
sealed interface Plan {
    val at: Long
    val source: Source

    /** The first alert of a run. The only step that makes a sound. */
    data class Due(override val at: Long, override val source: Source) : Plan

    /** Silent ten-minute repeats until a meal is logged or tomorrow is tapped. */
    data class Repeating(override val at: Long, override val source: Source) : Plan

    /** Tomorrow: one silent notification at [at], the first normal step at [normalAt]. */
    data class TomorrowSilent(override val at: Long, val normalAt: Long) : Plan {
        override val source: Source get() = Source.TOMORROW
    }

    /** Tomorrow: the first audible step after the silence. */
    data class TomorrowDue(override val at: Long) : Plan {
        override val source: Source get() = Source.TOMORROW
    }
}

/** Nothing here is a judgement: gaps are how long the app waits, not how long you may wait. */
data class Settings(
    val mealGapMs: Long = 4 * HOUR_MS,
    val snackGapMs: Long = 2 * HOUR_MS,
    val tomorrowSilenceMs: Long = 12 * HOUR_MS,
    val notNowMs: Long = 10 * MINUTE_MS,
    val nightStartMinute: Int = 23 * 60,
    val nightEndMinute: Int = 7 * 60,
    val soundId: String = "eat_something",
    val vibration: Boolean = true,
) {
    fun gapFor(kind: Kind): Long = if (kind == Kind.MEAL) mealGapMs else snackGapMs

    fun changed(key: String, value: String): Settings = when (key) {
        KEY_MEAL_GAP -> parseMinutes(value)?.let { copy(mealGapMs = it) } ?: this
        KEY_SNACK_GAP -> parseMinutes(value)?.let { copy(snackGapMs = it) } ?: this
        KEY_TOMORROW_SILENCE -> parseMinutes(value)?.let { copy(tomorrowSilenceMs = it) } ?: this
        KEY_NOT_NOW -> parseMinutes(value)?.let { copy(notNowMs = it) } ?: this
        KEY_NIGHT -> {
            val parts = value.split("-")
            if (parts.size == 2) {
                val start = parseMinuteOfDay(parts[0])
                val end = parseMinuteOfDay(parts[1])
                if (start != null && end != null) copy(nightStartMinute = start, nightEndMinute = end) else this
            } else this
        }
        KEY_SOUND -> if (value.isBlank()) this else copy(soundId = value)
        KEY_VIBRATION -> copy(vibration = value == "true")
        else -> this
    }

    fun value(key: String): String = when (key) {
        KEY_MEAL_GAP -> mealGapMs.toString()
        KEY_SNACK_GAP -> snackGapMs.toString()
        KEY_TOMORROW_SILENCE -> tomorrowSilenceMs.toString()
        KEY_NOT_NOW -> notNowMs.toString()
        KEY_NIGHT -> clockOf(nightStartMinute) + "-" + clockOf(nightEndMinute)
        KEY_SOUND -> soundId
        KEY_VIBRATION -> vibration.toString()
        else -> ""
    }

    companion object {
        const val KEY_MEAL_GAP = "meal_gap_ms"
        const val KEY_SNACK_GAP = "snack_gap_ms"
        const val KEY_TOMORROW_SILENCE = "tomorrow_silence_ms"
        const val KEY_NOT_NOW = "not_now_ms"
        const val KEY_NIGHT = "night_hours"
        const val KEY_SOUND = "sound"
        const val KEY_VIBRATION = "vibration"

        val KEYS = listOf(
            KEY_MEAL_GAP, KEY_SNACK_GAP, KEY_TOMORROW_SILENCE,
            KEY_NOT_NOW, KEY_NIGHT, KEY_SOUND, KEY_VIBRATION,
        )

        private fun parseMinutes(raw: String): Long? =
            raw.trim().toLongOrNull()?.takeIf { it > 0 }

        /**
         * Night hours reach the log in two shapes: "21:00-09:00" from the
         * settings screen, and "1260-540" from the 3.0.0/3.1.0 picker. Both
         * parse. A value in neither shape changes nothing.
         */
        private fun parseMinuteOfDay(raw: String): Int? {
            val text = raw.trim()
            val colon = text.indexOf(':')
            if (colon >= 0) {
                val h = text.substring(0, colon).toIntOrNull() ?: return null
                val m = text.substring(colon + 1).toIntOrNull() ?: return null
                if (h !in 0..23 || m !in 0..59) return null
                return h * 60 + m
            }
            return text.toIntOrNull()?.takeIf { it in 0 until 24 * 60 }
        }

        private fun clockOf(minuteOfDay: Int): String =
            "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
    }
}

/** The whole app state: a replay of the event log, nothing else. */
data class State(
    val meals: List<Meal> = emptyList(),
    val plan: Plan? = null,
    val settings: Settings = Settings(),
    val eventCount: Int = 0,
    val lastEventAt: Long? = null,
) {
    fun newest(): Meal? = meals.maxByOrNull { it.eatenAt }

    fun lastMeal(): Meal? = newest()

    fun liveMeals(): List<Meal> = meals.filter { it.isLive() }

    fun mealById(id: String): Meal? = meals.firstOrNull { it.id == id }

    fun mealBeforeMeal(meal: Meal): Meal? =
        meals.filter { it.eatenAt < meal.eatenAt }.maxByOrNull { it.eatenAt }

    fun isFresh(): Boolean = eventCount == 0
}

/** How long without a meal before the front screen says so. Deliberately not a setting. */
const val STALE_AFTER_MS: Long = 8 * HOUR_MS
