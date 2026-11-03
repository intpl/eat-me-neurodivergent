package pl.gladecki.eatmen.core

import java.time.Instant
import java.time.ZoneId

/** Night hours are a warning, never a silence. */
object Night {

    fun minuteOfDay(at: Long, zone: ZoneId): Int {
        val local = Instant.ofEpochMilli(at).atZone(zone)
        return local.hour * 60 + local.minute
    }

    fun isNight(at: Long, settings: Settings, zone: ZoneId): Boolean {
        val start = settings.nightStartMinute
        val end = settings.nightEndMinute
        if (start == end) return false
        val minute = minuteOfDay(at, zone)
        return if (start < end) minute in start until end else minute >= start || minute < end
    }
}
