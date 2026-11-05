package pl.gladecki.eatmen

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

/**
 * Every screen sits behind the same gate. Without the three accesses the app
 * has nothing to show that would be true, so it shows the gate instead.
 */
abstract class GatedActivity : AppCompatActivity() {

    private var redirected = false

    /**
     * Opens the gate and finishes this screen when the phone withholds anything.
     * Returns true when the caller should stop, so onCreate and onResume can
     * both call it without opening the gate twice.
     */
    protected fun gated(): Boolean {
        if (redirected) return true
        if (Accesses.missing(this).isEmpty()) return false
        redirected = true
        startActivity(Intent(this, AccessActivity::class.java))
        finish()
        return true
    }
}
