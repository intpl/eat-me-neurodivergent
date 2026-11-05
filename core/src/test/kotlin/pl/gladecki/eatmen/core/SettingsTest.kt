package pl.gladecki.eatmen.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    @Test
    fun theTomorrowSilenceIsTwelveHoursByDefault() {
        assertEquals(12 * HOUR_MS, Settings().tomorrowSilenceMs)
    }

    @Test
    fun theNightHoursTheSettingsScreenWritesAreAccepted() {
        // The picker writes clock times; 21:00-09:00 is a window that crosses midnight.
        val changed = Settings().changed(Settings.KEY_NIGHT, "21:00-09:00")
        assertEquals(21 * 60, changed.nightStartMinute)
        assertEquals(9 * 60, changed.nightEndMinute)
    }

    @Test
    fun nightHoursWrittenAsBareMinutesStillReplay() {
        // 3.0.0 and 3.1.0 wrote the picker's minutes and could never read them back.
        val changed = Settings().changed(Settings.KEY_NIGHT, "1260-540")
        assertEquals(21 * 60, changed.nightStartMinute)
        assertEquals(9 * 60, changed.nightEndMinute)
    }

    @Test
    fun theNightHoursRoundTripThroughTheirStoredValue() {
        val settings = Settings().changed(Settings.KEY_NIGHT, "21:00-09:00")
        assertEquals("21:00-09:00", settings.value(Settings.KEY_NIGHT))
        assertEquals(settings, Settings().changed(Settings.KEY_NIGHT, settings.value(Settings.KEY_NIGHT)))
    }

    @Test
    fun aNightValueInNeitherShapeChangesNothing() {
        assertEquals(Settings(), Settings().changed(Settings.KEY_NIGHT, "banana"))
        assertEquals(Settings(), Settings().changed(Settings.KEY_NIGHT, "25:00-09:00"))
    }
}
