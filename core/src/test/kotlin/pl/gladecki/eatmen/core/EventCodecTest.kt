package pl.gladecki.eatmen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventCodecTest {

    @Test
    fun everyEventTypeSurvivesTheRoundTrip() {
        val events = listOf(
            Event.MealLogged("e1", 100, "m1", 90, Kind.SNACK),
            Event.MealEdited("e2", 110, "m1", 95, null),
            Event.MealDeleted("e3", 120, "m1"),
            Event.ReminderSet("e4", 130, 200, Source.EXPLICIT),
            Event.ReminderCleared("e5", 140, "no_meals"),
            Event.ReminderDeferred("e6", 150, 200, 300),
            Event.AlertFired("e7", 160, AlertKind.REPEAT),
            Event.TomorrowSet("e8", 170, 180, 190),
            Event.SettingsChanged("e9", 180, Settings.KEY_MEAL_GAP, "10800000"),
            Event.AppOpened("e10", 190),
            Event.ClockSkew("e11", 200, -120_000),
            Event.ExportCopied("e12", 210, 42),
        )
        for (event in events) {
            val line = EventCodec.encode(event)
            assertEquals(event, EventCodec.decode(line))
        }
    }

    @Test
    fun aLineWithoutEnoughInformationIsSkipped() {
        assertNull(EventCodec.decode("{\"id\":\"e1\",\"at\":1}"))
        assertNull(EventCodec.decode("{\"id\":\"e1\",\"type\":\"meal_logged\",\"at\":1}"))
        assertNull(EventCodec.decode("garbage"))
        assertNull(EventCodec.decode("{\"id\":\"e1\",\"at\":1,\"type\":\"unknown_type\"}"))
    }

    @Test
    fun eventsAreOneLineOfJson() {
        val line = EventCodec.encode(Event.MealLogged("e1", 100, "m1", 90, Kind.MEAL))
        assertEquals(-1, line.indexOf('\n'))
    }
}
