package com.sev7n.drinkexercise

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.Button
import kotlin.math.min

object AppUi {
    val background = Color.rgb(247, 248, 250)
    val text = Color.rgb(15, 23, 42)
    val muted = Color.rgb(100, 116, 139)
    val border = Color.rgb(229, 235, 242)
    val blue = Color.rgb(59, 130, 246)
    val green = Color.rgb(22, 138, 112)
    val blueLight = Color.rgb(224, 237, 255)
    fun rounded(context: Context, color: Int = Color.WHITE, radius: Int = 18, stroke: Int? = border): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * context.resources.displayMetrics.density
            stroke?.let { setStroke((context.resources.displayMetrics.density + .5f).toInt(), it) }
        }
    fun ripple(context: Context, fill: Int, accent: Int, stroke: Int? = null, radius: Int = 12) =
        RippleDrawable(ColorStateList.valueOf(Color.argb(32, Color.red(accent), Color.green(accent), Color.blue(accent))),
            rounded(context, fill, radius, stroke), rounded(context, Color.WHITE, radius, null))
    fun button(view: Button, accent: Int = blue, filled: Boolean = false, outline: Boolean = false, tinted: Boolean = false) {
        val density = view.resources.displayMetrics.density
        view.isAllCaps = false
        view.includeFontPadding = false
        view.textSize = 15f
        view.setTextColor(if (filled) Color.WHITE else accent)
        view.setTypeface(null, android.graphics.Typeface.BOLD)
        view.minWidth = 0
        view.minimumWidth = 0
        view.minHeight = (48 * density).toInt()
        view.minimumHeight = (48 * density).toInt()
        view.setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
        view.stateListAnimator = null
        view.background = ripple(view.context, if (filled) accent else if (tinted) blueLight else Color.TRANSPARENT,
            accent, if (outline) accent else null)
    }
}

class UiIcon(private val kind: String, private val tint: Int, private val size: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        when (kind) {
            "water" -> {
                val path = Path().apply { moveTo(12f, 2f); cubicTo(9f, 7f, 5f, 11f, 5f, 15f); cubicTo(5f, 24f, 19f, 24f, 19f, 15f); cubicTo(19f, 11f, 15f, 7f, 12f, 2f); close() }
                canvas.drawPath(path, paint)
            }
            "workout" -> {
                canvas.drawCircle(15f, 4f, 2f, paint)
                val path = Path().apply { moveTo(4f, 9f); lineTo(8f, 8f); lineTo(12f, 11f); lineTo(17f, 9f); lineTo(20f, 10f); moveTo(12f, 11f); lineTo(10f, 15f); lineTo(6f, 16f); lineTo(3f, 21f); moveTo(10f, 15f); lineTo(15f, 17f); lineTo(14f, 22f) }
                canvas.drawPath(path, paint)
            }
            "settings" -> {
                canvas.drawCircle(12f, 12f, 7f, paint); canvas.drawCircle(12f, 12f, 2.5f, paint)
                for (index in 0..7) { canvas.save(); canvas.rotate(index * 45f, 12f, 12f); canvas.drawLine(12f, 2f, 12f, 5f, paint); canvas.restore() }
            }
            "clock" -> { canvas.drawCircle(12f, 12f, 9f, paint); canvas.drawLine(12f, 6f, 12f, 12f, paint); canvas.drawLine(12f, 12f, 16f, 14f, paint) }
            "plus" -> { canvas.drawLine(12f, 4f, 12f, 20f, paint); canvas.drawLine(4f, 12f, 20f, 12f, paint) }
            "list" -> for (y in listOf(6f, 12f, 18f)) { canvas.drawCircle(4f, y, .7f, paint); canvas.drawLine(9f, y, 21f, y, paint) }
            "battery" -> { canvas.drawRoundRect(6f, 4f, 18f, 22f, 2f, 2f, paint); canvas.drawLine(10f, 2f, 14f, 2f, paint); canvas.drawLine(9f, 17f, 15f, 17f, paint) }
            "bell" -> { val path = Path().apply { moveTo(4f, 17f); lineTo(6f, 14f); lineTo(6f, 9f); cubicTo(6f, 1f, 18f, 1f, 18f, 9f); lineTo(18f, 14f); lineTo(20f, 17f); close() }; canvas.drawPath(path, paint); canvas.drawArc(9f, 17f, 15f, 23f, 0f, 180f, false, paint) }
            "more" -> for (y in listOf(5f, 12f, 19f)) canvas.drawCircle(12f, y, 1f, paint)
            "chevron" -> { val path = Path().apply { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }; canvas.drawPath(path, paint) }
            else -> { val path = Path().apply { moveTo(3f, 10f); lineTo(21f, 3f); lineTo(14f, 21f); lineTo(10f, 14f); close() }; canvas.drawPath(path, paint) }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
    @Suppress("DEPRECATION") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

class ProgressRing(context: Context) : View(context) {
    private var percentage = "0%"
    var fraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            percentage = String.format(java.util.Locale.CHINA, "%.1f", field * 100).removeSuffix(".0") + "%"
            contentDescription = "饮水进度 $percentage"
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        val side = min(width, height).toFloat()
        val inset = 6 * resources.displayMetrics.density
        val bounds = RectF((width - side) / 2 + inset, (height - side) / 2 + inset, (width + side) / 2 - inset, (height + side) / 2 - inset)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 9 * resources.displayMetrics.density; paint.strokeCap = Paint.Cap.ROUND
        paint.color = AppUi.blueLight; canvas.drawArc(bounds, 0f, 360f, false, paint)
        paint.color = AppUi.blue; canvas.drawArc(bounds, -90f, fraction * 360, false, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 18f, resources.displayMetrics)
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val available = side - 28 * resources.displayMetrics.density
        if (paint.measureText(percentage) > available) paint.textSize *= available / paint.measureText(percentage)
        canvas.drawText(percentage, width / 2f, height / 2f - (paint.ascent() + paint.descent()) / 2, paint)
    }
}

class GoalProgress(context: Context, private val fraction: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        paint.color = AppUi.border
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, paint)
        paint.color = AppUi.green
        val fill = width * fraction.coerceIn(0f, 1f)
        if (fill > 0) canvas.drawRoundRect(0f, 0f, fill, height.toFloat(), height / 2f, height / 2f, paint)
    }
}
