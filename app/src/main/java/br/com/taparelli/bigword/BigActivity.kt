package br.com.taparelli.bigword

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.text.LineBreaker
import android.os.Bundle
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt

class BigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val display = BigTextView(this, settings).apply {
            setOnClickListener { finish() }
        }
        configureWindow(display, immersive = true)
    }
}

private class BigTextView(context: Context, settings: Settings) : View(context) {
    private val message = settings.message.trim()
    private val mode = settings.mode
    private val fadeMs = fadeDurationMillis(settings.speed, mode)
    private val carouselPixelsPerMs =
        carouselDpPerSecond(settings.speed) * resources.displayMetrics.density / 1000.0
    private val blink = settings.blink
    private val palette = palettes[settings.paletteIndex]
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = palette.foreground
        typeface = fonts[settings.fontIndex].load(context)
        density = resources.displayMetrics.density
    }
    private val words = message.split(Regex("\\s+")).filter(String::isNotEmpty)
    private val carouselText = message.replace(Regex("[\\r\\n\\u2028\\u2029]+"), " ")
    private val margin = dp(10)
    private val animated = message.isNotEmpty() && (mode != Mode.FIT || blink)

    private var layouts = emptyList<StaticLayout>()
    private var layoutWidth = -1
    private var layoutHeight = -1
    private var resumedAt = 0L
    private var activeTime = 0L

    init {
        setBackgroundColor(palette.background)
        contentDescription = message
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        val now = SystemClock.uptimeMillis()
        if (visibility == VISIBLE) {
            if (resumedAt == 0L) resumedAt = now
            invalidate()
        } else if (resumedAt != 0L) {
            activeTime += now - resumedAt
            resumedAt = 0
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft + margin
        val top = paddingTop + margin
        val availableWidth = width - left - paddingRight - margin
        val availableHeight = height - top - paddingBottom - margin
        if (availableWidth <= 0 || availableHeight <= 0 || words.isEmpty()) return

        if (availableWidth != layoutWidth || availableHeight != layoutHeight) {
            layouts = buildLayouts(availableWidth, availableHeight)
            layoutWidth = availableWidth
            layoutHeight = availableHeight
        }

        val elapsed = activeTime + if (resumedAt == 0L) 0L else SystemClock.uptimeMillis() - resumedAt
        val fadeCycle = 2 * fadeMs
        val phase = elapsed % fadeCycle
        val index = if (mode == Mode.WORDS) {
            ((elapsed / fadeCycle + if (phase >= fadeMs) 1 else 0) % layouts.size).toInt()
        } else {
            0
        }
        // A word changes when the old word has fully faded out.
        paint.alpha = if (blink || mode == Mode.WORDS) {
            (255 * (1 + cos(2 * Math.PI * phase / fadeCycle)) / 2).roundToInt()
        } else {
            255
        }
        val layout = layouts[index]
        val x = if (mode == Mode.CAROUSEL) {
            val passDistance = availableWidth + layout.width
            val traveled = (elapsed * carouselPixelsPerMs) % passDistance
            left + availableWidth - traveled.toFloat()
        } else {
            left.toFloat()
        }
        canvas.save()
        canvas.clipRect(left, top, left + availableWidth, top + availableHeight)
        canvas.translate(x, top + (availableHeight - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()

        if (animated && windowVisibility == VISIBLE) postInvalidateOnAnimation()
    }

    private fun buildLayouts(width: Int, height: Int): List<StaticLayout> {
        paint.alpha = 255
        if (mode == Mode.CAROUSEL) {
            paint.textSize = dp(100).toFloat()
            var line = layout(carouselText, lineWidth(carouselText))
            if (line.height > height) {
                paint.textSize *= height.toFloat() / line.height
                line = layout(carouselText, lineWidth(carouselText))
            }
            return listOf(line)
        }

        var low = 1
        var high = maxOf(width, height)
        while (low < high) {
            val candidate = (low + high + 1) / 2
            paint.textSize = candidate.toFloat()
            val fits = words.all { lineWidth(it) <= width } &&
                if (mode == Mode.WORDS) {
                    words.all { layout(it, width).height <= height }
                } else {
                    layout(message, width).height <= height
                }
            if (fits) low = candidate else high = candidate - 1
        }
        paint.textSize = low.toFloat()
        val pages = if (mode == Mode.WORDS) words else listOf(message)
        return pages.map { layout(it, width) }
    }

    private fun lineWidth(text: String): Int =
        ceil(Layout.getDesiredWidth(text, paint)).toInt().coerceAtLeast(1)

    // The constant is inlined and StaticLayout.Builder has accepted the same value since API 23.
    @SuppressLint("InlinedApi")
    private fun layout(text: String, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
}
