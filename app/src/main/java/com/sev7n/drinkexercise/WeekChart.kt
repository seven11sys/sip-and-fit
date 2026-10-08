package com.sev7n.drinkexercise

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class WeekChart(context: Context, private val accent: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var days: List<DailyAmount> = emptyList()
    private var target: BigDecimal? = null
    fun show(days: List<DailyAmount>, unit: String, target: BigDecimal? = null) {
        this.days = days; this.target = target
        contentDescription = days.joinToString("；") { "${it.day.monthValue}月${it.day.dayOfMonth}日 ${WorkoutGoals.display(it.amount)} $unit" } +
            (target?.let { "；当前每日目标 ${WorkoutGoals.display(it)} $unit" } ?: "")
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        if (days.isEmpty()) return
        val density = resources.displayMetrics.density
        val fontScale = resources.displayMetrics.scaledDensity
        val baseline = height - 30 * fontScale
        val graphTop = 30 * fontScale
        val graphHeight = max(1f, baseline - graphTop)
        val maxValue = max(1f, max(days.maxOf { it.amount.toFloat() }, target?.toFloat() ?: 0f))
        val slot = width / days.size.toFloat()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = density; paint.color = AppUi.border
        canvas.drawLine(0f, baseline, width.toFloat(), baseline, paint)
        target?.let {
            val y = baseline - it.toFloat() / maxValue * graphHeight
            paint.color = accent; paint.pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            paint.pathEffect = null
        }
        val labelFormatter = DateTimeFormatter.ofPattern("E", Locale.CHINA)
        days.forEachIndexed { index, day ->
            val center = (index + .5f) * slot
            val top = baseline - day.amount.toFloat() / maxValue * graphHeight
            paint.style = Paint.Style.FILL; paint.color = accent
            if (day.amount > BigDecimal.ZERO) canvas.drawRoundRect(center - slot * .24f, top, center + slot * .24f, baseline, 4 * density, 4 * density, paint)
            paint.textAlign = Paint.Align.CENTER; paint.textSize = 10 * fontScale; paint.color = AppUi.muted
            // Put a value inside the bar when it meets the goal reference line.
            val labelY = if (target != null && day.amount.compareTo(target) == 0) top + 14 * fontScale else top - 7 * fontScale
            if (target != null && day.amount.compareTo(target) == 0) paint.color = android.graphics.Color.WHITE
            canvas.drawText(compact(day.amount), center, labelY, paint)
            paint.color = AppUi.muted; paint.textSize = 11 * fontScale
            canvas.drawText(if (index == days.lastIndex) "今天" else day.day.format(labelFormatter), center, baseline + 21 * fontScale, paint)
        }
    }
    private fun compact(value: BigDecimal): String = if (value >= BigDecimal("10000"))
        WorkoutGoals.display(value.divide(BigDecimal("1000"), 1, java.math.RoundingMode.HALF_UP)) + "k"
        else WorkoutGoals.display(value.setScale(1, java.math.RoundingMode.HALF_UP))
}
