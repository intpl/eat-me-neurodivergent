package pl.gladecki.eatmen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import pl.gladecki.eatmen.core.Kind
import java.io.File
import java.io.FileOutputStream

/**
 * There is no emulator in this devbox, so the screens are drawn here and written
 * out as PNGs. It is the only way to look at what the phone will show.
 */
abstract class ScreenshotBase {

    val outDir = File("build/shots").apply { mkdirs() }

    fun seed() {
        val controller = ApplicationProvider.getApplicationContext<EatMeApp>().controller
        val now = System.currentTimeMillis()
        controller.logMeal(now - 7 * 3_600_000L, Kind.SNACK)
        controller.logMeal(now - 2 * 3_600_000L, Kind.MEAL)
    }

    fun draw(view: View, name: String, width: Int = 1080, height: Int = 2340) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun shot(activity: android.app.Activity, name: String) {
        draw(activity.window.decorView, name)
    }

    /** The launcher icon is a drawable, so it can be drawn even without a launcher. */
    fun icon(name: String) {
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            ApplicationProvider.getApplicationContext(),
            R.mipmap.ic_launcher,
        )!!
        val size = 432
        drawable.setBounds(0, 0, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(bitmap))
        FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest : ScreenshotBase() {

    @Test
    fun theFrontWithMeals() {
        seed()
        shot(Robolectric.buildActivity(MainActivity::class.java).setup().get(), "front")
    }

    @Test
    fun theFrontOnAFreshInstall() {
        shot(Robolectric.buildActivity(MainActivity::class.java).setup().get(), "front-empty")
    }

    @Test
    fun theFrontWhenTheLastMealIsOld() {
        val controller = ApplicationProvider.getApplicationContext<EatMeApp>().controller
        controller.logMeal(System.currentTimeMillis() - 9 * 3_600_000L, Kind.MEAL)
        shot(Robolectric.buildActivity(MainActivity::class.java).setup().get(), "front-stale")
    }

    @Test
    fun theWhenSheet() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<View>(R.id.buttonAte).performClick()
        val dialog = ShadowDialog.getLatestDialog()
        draw(dialog.window!!.decorView, "sheet-when", 1080, 1200)
    }

    @Test
    fun theHistoryScreen() {
        seed()
        shot(Robolectric.buildActivity(EditHistoryActivity::class.java).setup().get(), "history")
    }

    @Test
    fun theSettingsScreen() {
        seed()
        shot(Robolectric.buildActivity(SettingsActivity::class.java).setup().get(), "settings")
    }

    @Test
    fun theLauncherIcon() {
        icon("icon")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-night-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotNightTest : ScreenshotBase() {

    @Test
    fun theFrontAtNight() {
        seed()
        shot(Robolectric.buildActivity(MainActivity::class.java).setup().get(), "front-night")
    }

    @Test
    fun theSettingsAtNight() {
        seed()
        shot(Robolectric.buildActivity(SettingsActivity::class.java).setup().get(), "settings-night")
    }
}
