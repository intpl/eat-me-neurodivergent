package pl.gladecki.eatmen.core

/** Where the log lives. The Android layer owns the file; the core owns the meaning. */
interface EventStore {
    fun readLines(): List<String>
    fun append(line: String)
    fun clear()
}

/** Replays the log into the one state the app renders. */
object Reducer {

    fun reduce(lines: List<String>): State {
        var state = State()
        for (line in lines) {
            val event = EventCodec.decode(line) ?: continue
            state = apply(state, event)
        }
        return state
    }

    fun apply(state: State, event: Event): State {
        val next = when (event) {
            is Event.MealLogged -> state.copy(
                meals = (state.meals + Meal(event.mealId, event.eatenAt, event.kind))
                    .sortedBy { it.eatenAt },
            )

            is Event.MealEdited -> state.copy(
                meals = state.meals.map { meal ->
                    if (meal.id != event.mealId) meal
                    else meal.copy(
                        eatenAt = event.eatenAt ?: meal.eatenAt,
                        kind = event.kind ?: meal.kind,
                    )
                }.sortedBy { it.eatenAt },
            )

            is Event.MealDeleted -> state.copy(meals = state.meals.filterNot { it.id == event.mealId })

            is Event.ReminderSet -> state.copy(plan = planForSet(event))
            is Event.ReminderCleared -> state.copy(plan = null)
            is Event.ReminderDeferred -> state.copy(
                plan = Plan.Repeating(event.toAt, state.plan?.source ?: Source.SUGGESTED),
            )
            is Event.AlertFired -> state.copy(plan = planAfterAlert(state.plan, event, state.settings))
            is Event.TomorrowSet -> state.copy(
                plan = Plan.TomorrowSilent(event.silenceUntil, event.normalAt),
            )
            is Event.SettingsChanged -> state.copy(settings = state.settings.changed(event.key, event.value))
            is Event.AppOpened -> state
            is Event.ClockSkew -> state
            is Event.ExportCopied -> state
        }
        return next.copy(eventCount = state.eventCount + 1, lastEventAt = event.at)
    }

    private fun planForSet(event: Event.ReminderSet): Plan {
        val due = maxOf(event.dueAt, event.at)
        if (event.source != Source.EXPLICIT) return Plan.Due(due, event.source)
        val warnAt = due - Planner.WARNING_LEAD_MS
        return if (warnAt > event.at) Plan.Warning(warnAt, due) else Plan.Due(due, event.source)
    }

    private fun planAfterAlert(plan: Plan?, event: Event.AlertFired, settings: Settings): Plan? = when (event.kind) {
        AlertKind.WARNING -> if (plan is Plan.Warning) Plan.Due(plan.dueAt, Source.EXPLICIT) else plan
        AlertKind.DUE -> plan?.let { Plan.Repeating(event.at + settings.notNowMs, it.source) }
        AlertKind.REPEAT -> plan?.let { Plan.Repeating(event.at + settings.notNowMs, it.source) }
        AlertKind.SILENT -> if (plan is Plan.TomorrowSilent) Plan.TomorrowDue(plan.normalAt) else plan
    }
}
