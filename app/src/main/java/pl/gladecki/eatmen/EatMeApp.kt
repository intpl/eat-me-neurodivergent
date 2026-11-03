package pl.gladecki.eatmen

import android.app.Application
import android.content.Context

class EatMeApp : Application() {

    val controller: AppController by lazy { AppController(this) }

    companion object {
        fun controller(context: Context): AppController =
            (context.applicationContext as EatMeApp).controller
    }
}
