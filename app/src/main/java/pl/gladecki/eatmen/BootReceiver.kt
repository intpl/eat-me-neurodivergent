package pl.gladecki.eatmen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot the alarms are gone and are re-armed from the log. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                EatMeApp.controller(context).refresh()
            }
        }
    }
}
