package pl.gladecki.eatmen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ExportTest {

    private val now = Instant.parse("2026-11-03T12:40:00Z").toEpochMilli()
    private val athens: ZoneId = ZoneId.of("Europe/Athens")

    @Test
    fun theDocumentCarriesEveryEventAndTheShapeTheSpecPromises() {
        val events = listOf(
            Event.MealLogged("e1", now, "m1", now - HOUR_MS, Kind.MEAL),
            Event.ReminderSet("e2", now, now + 3 * HOUR_MS, Source.SUGGESTED),
        )
        val text = Export.text(events, athens, now, "2.0.0")
        val parsed = JsonCodec.parse(text) as Json.Obj
        assertEquals(1L, (parsed.fields["schema"] as Json.Num).asLong())
        assertEquals("2026-11-03T12:40:00Z", (parsed.fields["exported_at"] as Json.Str).value)
        assertEquals("2.0.0", (parsed.fields["app_version"] as Json.Str).value)
        assertEquals("Europe/Athens", (parsed.fields["timezone"] as Json.Str).value)
        assertEquals(2L, (parsed.fields["event_count"] as Json.Num).asLong())
        val exported = (parsed.fields["events"] as Json.Arr).items
        assertEquals(2, exported.size)
        assertEquals("meal_logged", ((exported[0] as Json.Obj).fields["type"] as Json.Str).value)
    }

    @Test
    fun everyEventInTheExportParsesBackToItself() {
        val events = listOf(
            Event.MealLogged("e1", now, "m1", now - HOUR_MS, Kind.SNACK),
            Event.MealEdited("e2", now + 1, "m1", now - 2 * HOUR_MS, null),
            Event.MealDeleted("e3", now + 2, "m1"),
            Event.ReminderCleared("e4", now + 3, "no_meals"),
            Event.ReminderDeferred("e5", now + 4, now, now + 10 * MINUTE_MS),
            Event.AlertFired("e6", now + 5, AlertKind.DUE),
            Event.TomorrowSet("e7", now + 6, now + 8 * HOUR_MS, now + 12 * HOUR_MS),
            Event.SettingsChanged("e8", now + 7, Settings.KEY_SOUND, "eat_something"),
            Event.AppOpened("e9", now + 8),
            Event.ClockSkew("e10", now + 9, 120_000),
            Event.ExportCopied("e11", now + 10, 10),
        )
        val text = Export.text(events, athens, now, "2.0.0")
        val parsed = JsonCodec.parse(text) as Json.Obj
        val encoded = (parsed.fields["events"] as Json.Arr).items.map { JsonCodec.write(it) }
        assertEquals(events, encoded.map { EventCodec.decode(it) })
    }

    @Test
    fun anEmptyLogExportsAsAnEmptyDocument() {
        val parsed = JsonCodec.parse(Export.text(emptyList(), athens, now, "2.0.0")) as Json.Obj
        assertEquals(0L, (parsed.fields["event_count"] as Json.Num).asLong())
        assertEquals(0, (parsed.fields["events"] as Json.Arr).items.size)
    }

    @Test
    fun aLogOfAThousandEventsStillExports() {
        val events = (1..1000).map {
            Event.AppOpened("e$it", now + it, )
        }
        val parsed = JsonCodec.parse(Export.text(events, athens, now, "2.0.0")) as Json.Obj
        assertNotNull(parsed.fields["events"])
        assertEquals(1000L, (parsed.fields["event_count"] as Json.Num).asLong())
    }
}
