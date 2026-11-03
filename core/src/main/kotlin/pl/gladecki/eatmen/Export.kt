package pl.gladecki.eatmen.core

import java.time.Instant
import java.time.ZoneId

/** The whole log as one JSON document, for the clipboard. Nothing leaves the phone on its own. */
object Export {

    fun document(
        events: List<Event>,
        zone: ZoneId,
        now: Long,
        appVersion: String,
        schema: Int = 1,
    ): Json.Obj = Json.Obj(
        linkedMapOf(
            "schema" to Json.of(schema),
            "exported_at" to Json.of(Instant.ofEpochMilli(now).toString()),
            "app_version" to Json.of(appVersion),
            "timezone" to Json.of(zone.id),
            "event_count" to Json.of(events.size),
            "events" to Json.Arr(events.map { eventToJson(it) }),
        ),
    )

    fun text(
        events: List<Event>,
        zone: ZoneId,
        now: Long,
        appVersion: String,
    ): String = JsonCodec.write(document(events, zone, now, appVersion), pretty = true)

    private fun eventToJson(event: Event): Json {
        val fields = linkedMapOf<String, Json>(
            "id" to Json.of(event.id),
            "at" to Json.of(event.at),
            "type" to Json.of(event.type),
        )
        fields.putAll(event.payload())
        return Json.Obj(fields)
    }
}
