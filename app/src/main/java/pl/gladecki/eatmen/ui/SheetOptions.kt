package pl.gladecki.eatmen.ui

import android.content.Context
import pl.gladecki.eatmen.R

/**
 * The two lists of times the app offers. Writing them once keeps the logging
 * sheet and the edit sheet from drifting apart.
 */
object SheetOptions {

    /** Minutes before now, in the order they appear. 0 means now. */
    val pastMinutes = listOf(0, 30, 60, 120, 180, 240, 300)

    /** Minutes from now, in the order they appear. */
    val aheadMinutes = listOf(30, 60, 120, 180, 240)

    fun pastLabels(context: Context): List<String> = pastMinutes.map { minutes ->
        when (minutes) {
            0 -> context.getString(R.string.opt_now)
            in 1 until 60 -> context.resources.getQuantityString(
                R.plurals.opt_minutes_ago, minutes, minutes,
            )
            else -> {
                val hours = minutes / 60
                context.resources.getQuantityString(R.plurals.opt_hours_ago, hours, hours)
            }
        }
    }

    fun aheadLabels(context: Context): List<String> = aheadMinutes.map { minutes ->
        if (minutes < 60) {
            context.resources.getQuantityString(R.plurals.opt_in_minutes, minutes, minutes)
        } else {
            val hours = minutes / 60
            context.resources.getQuantityString(R.plurals.opt_in_hours, hours, hours)
        }
    }
}
