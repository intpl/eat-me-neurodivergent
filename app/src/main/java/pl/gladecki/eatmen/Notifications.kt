package pl.gladecki.eatmen

import pl.gladecki.eatmen.core.State

/** The one surface the app talks to the outside world through. */
interface Notifications {
    /** Channels are created once and re-created when the sound or vibration changes. */
    fun ensureChannels(state: State) {}

    fun showCountdown(at: Long, now: Long)
    fun showAlert(state: State, now: Long, audible: Boolean)
    fun showSilentStep(state: State, now: Long)
    fun cancel()

    /** Whether this app is allowed to notify at all. A screen that cannot speak has to say so. */
    fun canNotify(): Boolean = true

    /** Posts a reminder-shaped notification now, on the channel a real one would use. */
    fun test(state: State, now: Long) {}
}

interface Alarms {
    fun arm(at: Long)
    fun cancel()
}
