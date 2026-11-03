package pl.gladecki.eatmen.core

/** All the arithmetic of when the app will speak. No android.* here, so it is tested on the JVM. */
object Planner {

    /** Explicit reminders get a silent heads-up this long before. */
    const val WARNING_LEAD_MS: Long = 5 * MINUTE_MS

    /**
     * A reminder computed from a meal that is already past starts a minute out
     * instead of firing under the finger that just logged the meal.
     */
    const val OVERDUE_GRACE_MS: Long = MINUTE_MS

    fun nextAfterMeal(meal: Meal, settings: Settings, now: Long): Long =
        maxOf(meal.eatenAt + settings.gapFor(meal.kind), now + OVERDUE_GRACE_MS)

    fun tomorrowSilence(now: Long, settings: Settings): Pair<Long, Long> {
        val silenceUntil = now + settings.tomorrowSilenceMs
        return silenceUntil to (silenceUntil + settings.mealGapMs)
    }

    /** The instant the app actually alerts, as opposed to the silent warning before it. */
    fun alertAt(plan: Plan): Long = when (plan) {
        is Plan.Warning -> plan.dueAt
        is Plan.Due -> plan.at
        is Plan.Repeating -> plan.at
        is Plan.TomorrowSilent -> plan.normalAt
        is Plan.TomorrowDue -> plan.at
    }

    /** The step that is already due at [now], if any. Used when the app opens or boots late. */
    fun overdueStep(plan: Plan, now: Long): Step? = when (plan) {
        is Plan.Warning -> when {
            now >= plan.dueAt -> Step.DUE
            now >= plan.warnAt -> Step.WARNING
            else -> null
        }
        is Plan.Due -> if (now >= plan.at) Step.DUE else null
        is Plan.Repeating -> if (now >= plan.at) Step.REPEAT else null
        is Plan.TomorrowSilent ->
            if (now >= plan.normalAt) Step.TOMORROW_DUE
            else if (now >= plan.at) Step.TOMORROW_SILENT
            else null
        is Plan.TomorrowDue -> if (now >= plan.at) Step.TOMORROW_DUE else null
    }

    fun alertKindOf(step: Step): AlertKind? = when (step) {
        Step.WARNING -> AlertKind.WARNING
        Step.DUE -> AlertKind.DUE
        Step.REPEAT -> AlertKind.REPEAT
        Step.TOMORROW_SILENT -> AlertKind.SILENT
        Step.TOMORROW_DUE -> AlertKind.DUE
    }

    fun isAudible(step: Step): Boolean = step == Step.DUE || step == Step.TOMORROW_DUE
}
