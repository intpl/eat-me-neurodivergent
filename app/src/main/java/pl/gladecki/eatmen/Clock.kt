package pl.gladecki.eatmen

import android.os.SystemClock

/** Wall clock and monotonic clock, behind an interface so a test can move time. */
interface Clock {
    fun now(): Long
    fun elapsed(): Long
}

object RealClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
    override fun elapsed(): Long = SystemClock.elapsedRealtime()
}
