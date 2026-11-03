package pl.gladecki.eatmen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The alarm went off, or the alert's "Not now" was tapped. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val controller = EatMeApp.controller(context)
        when (intent.action) {
            ACTION_DEFER -> controller.notNow()
            else -> controller.onAlarm()
        }
    }

    companion object {
        const val ACTION_DEFER = "pl.gladecki.eatmen.DEFER"
    }
}
