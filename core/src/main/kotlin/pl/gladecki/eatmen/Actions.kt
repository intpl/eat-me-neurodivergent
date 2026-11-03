package pl.gladecki.eatmen.core

/**
 * The only place that decides which events an action writes. Everything here is
 * a pure function of the current state and the clock, so a flow can be replayed
 * in a test without an Activity.
 */
object Actions {

    fun logMeal(
        ids: IdSource,
        now: Long,
        state: State,
        eatenAt: Long,
        kind: Kind,
    ): List<Event> {
        val mealId = ids.next()
        val meal = Meal(mealId, eatenAt, kind)
        // The rhythm runs from the last time you ate, whichever meal is logged
        // last. Logging an older meal does not move a newer reminder backwards.
        val newest = (state.meals + meal).maxByOrNull { it.eatenAt } ?: meal
        val nextAt = Planner.nextAfterMeal(newest, state.settings, now)
        return listOf(
            Event.MealLogged(ids.next(), now, mealId, eatenAt, kind),
            Event.ReminderSet(ids.next(), now, nextAt, Source.SUGGESTED),
        )
    }

    fun planAhead(ids: IdSource, now: Long, at: Long): List<Event> =
        listOf(Event.ReminderSet(ids.next(), now, maxOf(at, now + Planner.OVERDUE_GRACE_MS), Source.EXPLICIT))

    fun tomorrow(ids: IdSource, now: Long, settings: Settings): List<Event> {
        val (silenceUntil, normalAt) = Planner.tomorrowSilence(now, settings)
        return listOf(Event.TomorrowSet(ids.next(), now, silenceUntil, normalAt))
    }

    fun defer(ids: IdSource, now: Long, state: State): List<Event> {
        val plan = state.plan ?: return emptyList()
        return listOf(
            Event.ReminderDeferred(ids.next(), now, plan.at, now + state.settings.notNowMs),
        )
    }

    fun fire(ids: IdSource, now: Long, kind: AlertKind): List<Event> =
        listOf(Event.AlertFired(ids.next(), now, kind))

    fun editMeal(
        ids: IdSource,
        now: Long,
        state: State,
        mealId: String,
        eatenAt: Long?,
        kind: Kind?,
    ): List<Event> {
        val meal = state.mealById(mealId) ?: return emptyList()
        val newAt = eatenAt?.takeIf { it != meal.eatenAt }
        val newKind = kind?.takeIf { it != meal.kind }
        if (newAt == null && newKind == null) return emptyList()
        val change = Event.MealEdited(ids.next(), now, mealId, newAt, newKind)
        val mealsAfter = state.meals.map {
            if (it.id == mealId) it.copy(eatenAt = newAt ?: it.eatenAt, kind = newKind ?: it.kind) else it
        }
        return listOf(change) + replan(ids, now, state, mealsAfter)
    }

    fun deleteMeal(ids: IdSource, now: Long, state: State, mealId: String): List<Event> {
        if (state.mealById(mealId) == null) return emptyList()
        val mealsAfter = state.meals.filterNot { it.id == mealId }
        return listOf(Event.MealDeleted(ids.next(), now, mealId)) + replan(ids, now, state, mealsAfter)
    }

    fun changeSetting(ids: IdSource, now: Long, key: String, value: String): List<Event> =
        if (key in Settings.KEYS) listOf(Event.SettingsChanged(ids.next(), now, key, value)) else emptyList()

    fun appOpened(ids: IdSource, now: Long): List<Event> = listOf(Event.AppOpened(ids.next(), now))

    fun clockSkew(ids: IdSource, now: Long, deltaMs: Long): List<Event> =
        listOf(Event.ClockSkew(ids.next(), now, deltaMs))

    fun exportCopied(ids: IdSource, now: Long, eventCount: Int): List<Event> =
        listOf(Event.ExportCopied(ids.next(), now, eventCount))

    /**
     * A suggested reminder belongs to the newest meal. When that meal is edited
     * or deleted the reminder follows the meal that is newest afterwards; an
     * explicit reminder is the user's word and is left alone.
     */
    private fun replan(ids: IdSource, now: Long, state: State, mealsAfter: List<Meal>): List<Event> {
        val plan = state.plan ?: return emptyList()
        if (plan.source != Source.SUGGESTED) return emptyList()
        val newest = mealsAfter.maxByOrNull { it.eatenAt }
            ?: return listOf(Event.ReminderCleared(ids.next(), now, "no_meals"))
        return listOf(
            Event.ReminderSet(ids.next(), now, Planner.nextAfterMeal(newest, state.settings, now), Source.SUGGESTED),
        )
    }
}
