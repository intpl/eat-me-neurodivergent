package pl.gladecki.eatmen.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import pl.gladecki.eatmen.core.Meal
import pl.gladecki.eatmen.core.Kind
import pl.gladecki.eatmen.core.Text
import pl.gladecki.eatmen.core.Timeline
import java.time.Instant
import java.time.ZoneId

/**
 * The band on the front screen: the last 24 hours as one strip.
 *
 * A meal is a filled dot, a snack a smaller hollow one, the night hours are
 * shaded, midnight is a line wherever it falls, and the right edge is now. It is
 * a picture: nothing here is tappable and nothing is counted.
 */
class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var band: Timeline? = null
    private var zone: ZoneId = ZoneId.systemDefault()

    private val density = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val midnightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mealPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val snackFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val snackStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val timeText = textPaint(dp(12f), Typeface.create("sans-serif-medium", Typeface.NORMAL))
    private val tickText = textPaint(dp(10f), Typeface.create("sans-serif", Typeface.NORMAL))
    private val midnightText = textPaint(dp(10f), Typeface.create("sans-serif-medium", Typeface.NORMAL))

    private val trackPath = Path()

    private var accent = Color.GRAY
    private var accentSoft = Color.LTGRAY
    private var ink = Color.BLACK
    private var inkSoft = Color.DKGRAY
    private var outline = Color.GRAY
    private var track = Color.LTGRAY

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Hand it a band and a zone; whatever was drawn before is replaced. */
    fun show(timeline: Timeline, zone: ZoneId) {
        this.band = timeline
        this.zone = zone
        readThemeColors()
        contentDescription = Text.timelineDescription(timeline, zone)
        invalidate()
    }

    private fun readThemeColors() {
        accent = color(MaterialR.attr.colorPrimary)
        accentSoft = color(MaterialR.attr.colorPrimaryContainer)
        ink = color(MaterialR.attr.colorOnSurface)
        inkSoft = color(MaterialR.attr.colorOnSurfaceVariant)
        outline = color(MaterialR.attr.colorOutline)
        track = color(MaterialR.attr.colorSurfaceVariant)
    }

    private fun color(attr: Int): Int =
        MaterialColors.getColor(this, attr, Color.GRAY)

    override fun onDraw(canvas: Canvas) {
        val timeline = band ?: return
        val w = width.toFloat()
        val span = timeline.span.toFloat()
        if (w <= 0f || span <= 0f) return

        fun x(at: Long): Float = ((at - timeline.start) / span * w).coerceIn(0f, w)

        val trackTop = dp(38f)
        val trackBottom = dp(68f)
        val radius = (trackBottom - trackTop) / 2f

        // The strip itself: round where the 24 hours begin, cut square at now.
        trackPaint.color = track
        trackPath.reset()
        trackPath.addRoundRect(
            0f, trackTop, w, trackBottom,
            floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius),
            Path.Direction.CW,
        )
        canvas.drawPath(trackPath, trackPaint)

        // Night hours, cut to the strip.
        nightPaint.color = ColorUtils.setAlphaComponent(ink, 26)
        canvas.save()
        canvas.clipPath(trackPath)
        for (night in timeline.nights) {
            canvas.drawRect(x(night.start), trackTop, x(night.end), trackBottom, nightPaint)
        }
        canvas.restore()

        val centreY = (trackTop + trackBottom) / 2f

        // Meals: a filled dot for a meal, a small hollow one for a snack.
        for (meal in timeline.meals) {
            val cx = x(meal.eatenAt)
            if (meal.kind == Kind.MEAL) {
                mealPaint.color = accent
                canvas.drawCircle(cx, centreY, dp(6f), mealPaint)
            } else {
                snackFill.color = accentSoft
                canvas.drawCircle(cx, centreY, dp(4f), snackFill)
                snackStroke.color = accent
                canvas.drawCircle(cx, centreY, dp(4f), snackStroke)
            }
        }

        // Clock ticks, then the labels under them.
        tickPaint.color = outline
        tickText.color = inkSoft
        val midnightX = timeline.midnights.map { x(it) }
        for (mark in timeline.hourMarks) {
            val mx = x(mark)
            canvas.drawLine(mx, trackBottom, mx, trackBottom + dp(4f), tickPaint)
            if (midnightX.any { kotlin.math.abs(it - mx) < dp(18f) }) continue
            drawCentred(canvas, clockLabel(mark), mx, dp(82f), tickText, w)
        }

        // Midnight: taller than a tick, and named.
        midnightPaint.color = ColorUtils.setAlphaComponent(inkSoft, 220)
        midnightText.color = ink
        for (midnight in timeline.midnights) {
            val mx = x(midnight)
            canvas.drawLine(mx, trackTop - dp(8f), mx, trackBottom + dp(4f), midnightPaint)
            drawCentred(canvas, clockLabel(midnight), mx, dp(82f), midnightText, w)
        }

        // The cut edge is now: the strip stops here, and it is the one moment
        // the band is entitled to name.
        nowPaint.color = accent
        canvas.drawRect(w - dp(3f), trackTop, w, trackBottom, nowPaint)

        // Meal times above the strip, two rows so neighbours do not have to be dropped.
        drawTimes(canvas, timeline.meals, timeline.start, span, w)
    }

    private fun drawTimes(canvas: Canvas, meals: List<Meal>, start: Long, span: Float, w: Float) {
        val baselines = floatArrayOf(dp(14f), dp(30f))
        val rightEdge = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE)
        timeText.color = ink

        val placed = meals.map { it to ((it.eatenAt - start) / span * w) }.sortedBy { it.second }
        for ((meal, cx) in placed) {
            val label = clockLabel(meal.eatenAt)
            val width = timeText.measureText(label)
            val left = (cx - width / 2f).coerceIn(0f, (w - width).coerceAtLeast(0f))
            var row = -1
            for (candidate in 0..1) {
                if (left > rightEdge[candidate] + dp(6f)) {
                    row = candidate
                    break
                }
            }
            if (row < 0) continue
            rightEdge[row] = left + width
            canvas.drawText(label, left, baselines[row], timeText)
        }
    }

    private fun clockLabel(at: Long): String {
        val local = Instant.ofEpochMilli(at).atZone(zone)
        return "%02d:%02d".format(local.hour, local.minute)
    }

    private fun drawCentred(canvas: Canvas, text: String, cx: Float, baseline: Float, paint: Paint, w: Float) {
        val width = paint.measureText(text)
        canvas.drawText(text, (cx - width / 2f).coerceIn(0f, (w - width).coerceAtLeast(0f)), baseline, paint)
    }

    private fun textPaint(sizePx: Float, typeface: Typeface): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            this.typeface = typeface
        }

    private fun dp(value: Float): Float = value * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val h = resolveSize(dp(92f).toInt(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }
}
