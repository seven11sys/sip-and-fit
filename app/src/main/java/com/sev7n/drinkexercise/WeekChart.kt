package com.sev7n.drinkexercise

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import android.view.MotionEvent
import android.app.AlertDialog
import java.time.LocalDate
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class WeekChart(context: Context, private val accent: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var days: List<DailyAmount> = emptyList()
    private var target: BigDecimal? = null
    private var period = StatsPeriod.WEEK
    private var unit = ""
    private var selected = 0
    private var today = LocalDate.now()
    init {
        setOnClickListener {
            days.getOrNull(selected)?.let {
                AlertDialog.Builder(context).setTitle(if (period == StatsPeriod.YEAR) "${it.day.year}年${it.day.monthValue}月" else it.day.toString())
                    .setMessage(if (it.day > today) "尚未到此日期" else "记录量：${WorkoutGoals.display(it.amount)} $unit")
                    .setPositiveButton("关闭", null).show()
            }
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && days.isNotEmpty())
            selected = (event.x / width * days.size).toInt().coerceIn(days.indices)
        return super.onTouchEvent(event)
    }
    fun show(days: List<DailyAmount>, unit: String, target: BigDecimal? = null, period: StatsPeriod = StatsPeriod.WEEK, today: LocalDate = LocalDate.now()) {
        this.days = days; this.target = target
        this.period = period; this.unit = unit; this.today = today
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
            val valueLabel = if (period == StatsPeriod.YEAR && day.amount >= BigDecimal("1000"))
                WorkoutGoals.display(day.amount.divide(BigDecimal("1000"), 0, java.math.RoundingMode.HALF_UP)) + "k" else compact(day.amount)
            if (days.size <= 12) canvas.drawText(valueLabel, center, labelY, paint)
            paint.color = AppUi.muted; paint.textSize = 11 * fontScale
            val label = when (period) {
                StatsPeriod.WEEK -> if (day.day == today) "今天" else day.day.format(labelFormatter)
                StatsPeriod.MONTH -> day.day.dayOfMonth.toString()
                StatsPeriod.YEAR -> "${day.day.monthValue}月"
            }
            if (period != StatsPeriod.MONTH || index == 0 || index == days.lastIndex || ((index + 1) % 5 == 0 && days.lastIndex - index >= 3))
                canvas.drawText(label, center, baseline + 21 * fontScale, paint)
        }
    }
    private fun compact(value: BigDecimal): String = if (value >= BigDecimal("10000"))
        WorkoutGoals.display(value.divide(BigDecimal("1000"), 1, java.math.RoundingMode.HALF_UP)) + "k"
        else WorkoutGoals.display(value.setScale(1, java.math.RoundingMode.HALF_UP))
}
