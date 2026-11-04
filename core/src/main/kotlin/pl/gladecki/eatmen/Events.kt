package pl.gladecki.eatmen.core

/**
 * Every action is one line of JSON, appended and never rewritten. Edits and
 * deletions are new events that point at an earlier one, so the record of a bad
 * week cannot be quietly rewritten later.
 */
sealed class Event {
    abstract val id: String

    /** When the event happened, in epoch milliseconds. */
    abstract val at: Long

    abstract val type: String

    abstract fun payload(): Map<String, Json>

    data class MealLogged(
        override val id: String,
        override val at: Long,
        val mealId: String,
        val eatenAt: Long,
        val kind: Kind,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = linkedMapOf(
            "meal_id" to Json.of(mealId),
            "eaten_at" to Json.of(eatenAt),
            "kind" to Json.of(kind.name.lowercase()),
        )

        companion object { const val TYPE = "meal_logged" }
    }

    data class MealEdited(
        override val id: String,
        override val at: Long,
        val mealId: String,
        val eatenAt: Long?,
        val kind: Kind?,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = linkedMapOf<String, Json>(
            "meal_id" to Json.of(mealId),
        ).apply {
            eatenAt?.let { put("eaten_at", Json.of(it)) }
            kind?.let { put("kind", Json.of(it.name.lowercase())) }
        }

        companion object { const val TYPE = "meal_edited" }
    }

    data class MealDeleted(
        override val id: String,
        override val at: Long,
        val mealId: String,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = linkedMapOf("meal_id" to Json.of(mealId))

        companion object { const val TYPE = "meal_deleted" }
    }

    data class ReminderSet(
        override val id: String,
        override val at: Long,
        val dueAt: Long,
        val source: Source,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = linkedMapOf(
            "due_at" to Json.of(dueAt),
            "source" to Json.of(source.name.lowercase()),
        )

        companion object { const val TYPE = "reminder_set" }
    }

    data class ReminderCleared(
        override val id: String,
        override val at: Long,
        val reason: String,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = linkedMapOf("reason" to Json.of(reason))

        companion object { const val TYPE = "reminder_cleared" }
    }

    data class ReminderDeferred(
        override val id: String,
        override val at: Long,
        val fromAt: Long,
        val toAt: Long,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = linkedMapOf(
            "from_at" to Json.of(fromAt),
            "to_at" to Json.of(toAt),
        )

        companion object { const val TYPE = "reminder_deferred" }
    }

    data class AlertFired(
        override val id: String,
        override val at: Long,
        val kind: AlertKind,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = linkedMapOf("kind" to Json.of(kind.name.lowercase()))

        companion object { const val TYPE = "alert_fired" }
    }

    data class TomorrowSet(
        override val id: String,
        override val at: Long,
        val silenceUntil: Long,
        val normalAt: Long,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = linkedMapOf(
            "silence_until" to Json.of(silenceUntil),
            "normal_at" to Json.of(normalAt),
        )

        companion object { const val TYPE = "tomorrow_set" }
    }

    data class SettingsChanged(
        override val id: String,
        override val at: Long,
        val key: String,
        val value: String,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = linkedMapOf(
            "key" to Json.of(key),
            "value" to Json.of(value),
        )

        companion object { const val TYPE = "settings_changed" }
    }

    data class AppOpened(
        override val id: String,
        override val at: Long,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = emptyMap()

        companion object { const val TYPE = "app_opened" }
    }

    data class ClockSkew(
        override val id: String,
        override val at: Long,
        val deltaMs: Long,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = linkedMapOf("delta_ms" to Json.of(deltaMs))

        companion object { const val TYPE = "clock_skew" }
    }

    data class ExportCopied(
        override val id: String,
        override val at: Long,
        val eventCount: Int,
    ) : Event() {
        override val type: String get() = TYPE
        override fun payload(): Map<String, Json> = linkedMapOf("event_count" to Json.of(eventCount))

        companion object { const val TYPE = "export_copied" }
    }
}

/**
 * [WARNING] is only ever read: 2.x and 3.0 logs contain it, and a log has to
 * replay exactly as it was written. Nothing emits it since 3.1.0.
 */
enum class AlertKind { WARNING, DUE, REPEAT, SILENT }

object EventCodec {

    fun encode(event: Event): String {
        val fields = linkedMapOf<String, Json>(
            "id" to Json.of(event.id),
            "at" to Json.of(event.at),
            "type" to Json.of(event.type),
        )
        fields.putAll(event.payload())
        return JsonCodec.write(Json.Obj(fields))
    }

    /** Returns null for a line that is not an event this version understands. */
    fun decode(line: String): Event? {
        val obj = JsonCodec.parseObjectOrNull(line) ?: return null
        val id = obj.string("id") ?: return null
        val at = obj.long("at") ?: return null
        val type = obj.string("type") ?: return null
        return when (type) {
            Event.MealLogged.TYPE -> {
                val mealId = obj.string("meal_id") ?: return null
                val eatenAt = obj.long("eaten_at") ?: return null
                val kind = kindOf(obj.string("kind")) ?: return null
                Event.MealLogged(id, at, mealId, eatenAt, kind)
            }
            Event.MealEdited.TYPE -> {
                val mealId = obj.string("meal_id") ?: return null
                val eatenAt = obj.long("eaten_at")
                val kind = obj.string("kind")?.let { kindOf(it) ?: return null }
                if (eatenAt == null && kind == null) return null
                Event.MealEdited(id, at, mealId, eatenAt, kind)
            }
            Event.MealDeleted.TYPE -> {
                val mealId = obj.string("meal_id") ?: return null
                Event.MealDeleted(id, at, mealId)
            }
            Event.ReminderSet.TYPE -> {
                val dueAt = obj.long("due_at") ?: return null
                val source = sourceOf(obj.string("source")) ?: return null
                Event.ReminderSet(id, at, dueAt, source)
            }
            Event.ReminderCleared.TYPE ->
                Event.ReminderCleared(id, at, obj.string("reason") ?: "unknown")
            Event.ReminderDeferred.TYPE -> {
                val fromAt = obj.long("from_at") ?: return null
                val toAt = obj.long("to_at") ?: return null
                Event.ReminderDeferred(id, at, fromAt, toAt)
            }
            Event.AlertFired.TYPE -> {
                val kind = obj.string("kind")?.uppercase()?.let { raw ->
                    AlertKind.entries.firstOrNull { it.name == raw }
                } ?: return null
                Event.AlertFired(id, at, kind)
            }
            Event.TomorrowSet.TYPE -> {
                val silenceUntil = obj.long("silence_until") ?: return null
                val normalAt = obj.long("normal_at") ?: return null
                Event.TomorrowSet(id, at, silenceUntil, normalAt)
            }
            Event.SettingsChanged.TYPE -> {
                val key = obj.string("key") ?: return null
                val value = obj.string("value") ?: return null
                Event.SettingsChanged(id, at, key, value)
            }
            Event.AppOpened.TYPE -> Event.AppOpened(id, at)
            Event.ClockSkew.TYPE -> Event.ClockSkew(id, at, obj.long("delta_ms") ?: 0L)
            Event.ExportCopied.TYPE -> Event.ExportCopied(id, at, obj.long("event_count")?.toInt() ?: 0)
            else -> null
        }
    }

    private fun kindOf(raw: String?): Kind? = when (raw?.lowercase()) {
        "meal" -> Kind.MEAL
        "snack" -> Kind.SNACK
        else -> null
    }

    private fun sourceOf(raw: String?): Source? = when (raw?.lowercase()) {
        "explicit" -> Source.EXPLICIT
        "suggested" -> Source.SUGGESTED
        "tomorrow" -> Source.TOMORROW
        else -> null
    }
}

internal fun Json.Obj.string(key: String): String? = (fields[key] as? Json.Str)?.value

internal fun Json.Obj.long(key: String): Long? = (fields[key] as? Json.Num)?.asLong()
