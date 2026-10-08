package com.sev7n.drinkexercise

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StatsPanel(context: Context, private val accent: Int, key: String, private val changed: () -> Unit) : LinearLayout(context) {
    var period = StatsPeriod.WEEK
        private set
    var anchor: LocalDate = LocalDate.now()
        private set
    private val dp get() = resources.displayMetrics.density
    private val tabs = mutableMapOf<StatsPeriod, Button>()
    private val title = text(15f)
    private val summary = text(14f)
    private val note = text(12f)
    private val chart = WeekChart(context, accent).apply { tag = "$key.chart" }
    private val heatmap = YearHeatmap(context, accent).apply { tag = "$key.heatmap" }
    private val heatScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = true }
    private val next = Button(context)
    private val previous = Button(context)
    fun restore(period: StatsPeriod, anchor: LocalDate) {
        this.period = period
        this.anchor = anchor.coerceAtMost(LocalDate.now())
    }
    fun setFilter(view: View) {
        addView(view, 2, LayoutParams(LayoutParams.MATCH_PARENT, (48 * dp).toInt()))
    }
    init {
        orientation = VERTICAL
        val row = LinearLayout(context)
        StatsPeriod.entries.forEach { item ->
            val tab = Button(context).apply {
                text = item.label; contentDescription = "按${item.label}统计"
                tag = "stats.period.${item.name}"
                setOnClickListener { period = item; anchor = LocalDate.now(); changed() }
            }
            tabs[item] = tab
            row.addView(tab, LayoutParams(0, (44 * dp).toInt(), 1f).apply { marginEnd = (4 * dp).toInt() })
        }
        addView(row)
        val dates = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        previous.text = "‹"; previous.contentDescription = "上一统计周期"; AppUi.button(previous, accent)
        next.text = "›"; next.contentDescription = "下一统计周期"; AppUi.button(next, accent)
        previous.setOnClickListener { anchor = period.move(anchor, -1); changed() }
        next.setOnClickListener { anchor = period.move(anchor, 1).coerceAtMost(LocalDate.now()); changed() }
        title.gravity = Gravity.CENTER
        dates.addView(previous, LayoutParams((44 * dp).toInt(), (48 * dp).toInt()))
        dates.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        dates.addView(next, LayoutParams((44 * dp).toInt(), (48 * dp).toInt()))
        dates.addView(Button(context).apply {
            text = "当前"; contentDescription = "返回当前统计周期"; AppUi.button(this, accent); textSize = 12f
            setOnClickListener { anchor = LocalDate.now(); changed() }
        }, LayoutParams((56 * dp).toInt(), (48 * dp).toInt()))
        addView(dates); addView(summary)
        addView(chart, LayoutParams(LayoutParams.MATCH_PARENT, (172 * dp).toInt()))
        heatScroll.addView(heatmap, LayoutParams((704 * dp).toInt(), (146 * dp).toInt()))
        addView(heatScroll, LayoutParams(LayoutParams.MATCH_PARENT, (154 * dp).toInt()))
        addView(note)
    }
    private fun text(size: Float) = TextView(context).apply {
        textSize = size; setTextColor(AppUi.muted); includeFontPadding = false
        setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
    }
    fun show(days: List<DailyAmount>, unit: String, target: BigDecimal? = null, empty: Boolean = false, today: LocalDate = LocalDate.now()) {
        tabs.forEach { (item, tab) -> AppUi.button(tab, accent, filled = item == period) }
        next.isEnabled = period.start(period.move(anchor, 1)) <= today
        title.text = when (period) {
            StatsPeriod.WEEK -> "${period.start(anchor).monthValue}/${period.start(anchor).dayOfMonth} — ${anchor.monthValue}/${anchor.dayOfMonth}"
            StatsPeriod.MONTH -> "${anchor.year}年${anchor.monthValue}月"
            StatsPeriod.YEAR -> "${anchor.year}年"
        }
        val total = days.fold(BigDecimal.ZERO) { sum, day -> sum + day.amount }
        val active = days.count { it.amount > BigDecimal.ZERO }
        summary.text = if (empty) "还没有运动项目，记录后可查看统计。" else "合计 ${WorkoutGoals.display(total)} $unit · 有记录 $active 天"
        chart.visibility = if (empty) GONE else VISIBLE
        heatScroll.visibility = if (!empty && period == StatsPeriod.YEAR) VISIBLE else GONE
        chart.show(if (period == StatsPeriod.YEAR) DailyStats.months(days) else days, unit,
            if (period == StatsPeriod.YEAR) null else target, period, today)
        if (period == StatsPeriod.YEAR) heatmap.show(days, unit, today)
        note.text = when {
            empty -> "支持按周、月、年查看；不同运动和计量方式分别统计。"
            period == StatsPeriod.YEAR -> "上图为每月总量；下图每格一天，可左右滑动并点按查看。颜色由浅到深表示记录量增加，浅灰为无记录，斜线为未来日期。"
            target != null -> "虚线：当前每日目标 ${WorkoutGoals.display(target)} $unit（仅作参考）；点按柱子查看当天数值。"
            else -> "点按柱子查看当天记录量。"
        }
    }
}

class YearHeatmap(context: Context, private val accent: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp get() = resources.displayMetrics.density
    private var days = emptyList<DailyAmount>()
    private var first = LocalDate.now()
    private var today = LocalDate.now()
    private var unit = ""
    private var selected = 0
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((704 * dp).toInt(), widthMeasureSpec), resolveSize((146 * dp).toInt(), heightMeasureSpec))
    }
    init {
        isFocusable = true
        setOnClickListener {
            days.getOrNull(selected)?.let { day ->
                AlertDialog.Builder(context).setTitle(day.day.toString())
                    .setMessage(if (day.day > today) "尚未到此日期" else "记录量：${WorkoutGoals.display(day.amount)} $unit")
                    .setPositiveButton("关闭", null).show()
            }
        }
    }
    fun show(days: List<DailyAmount>, unit: String, today: LocalDate) {
        this.days = days; this.unit = unit; this.today = today
        if (days.isNotEmpty()) first = days.first().day.minusDays((days.first().day.dayOfWeek.value - 1).toLong())
        selected = days.indexOfFirst { it.day == today }.coerceAtLeast(0)
        contentDescription = "${days.firstOrNull()?.day?.year ?: ""}年每日记录热力图。" + days.filter { it.amount > BigDecimal.ZERO }
            .joinToString("；") { "${it.day.monthValue}月${it.day.dayOfMonth}日 ${WorkoutGoals.display(it.amount)} $unit" }
        invalidate()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (days.isEmpty()) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            val col = ((event.x / dp - 26) / 12).toInt()
            val row = ((event.y / dp - 24) / 12).toInt()
            if (event.x < 26 * dp || event.y < 24 * dp || row !in 0..6) return false
            val day = first.plusDays((col * 7 + row).toLong())
            selected = days.indexOfFirst { it.day == day }
            if (selected < 0) return false
        }
        return super.onTouchEvent(event)
    }
    override fun onDraw(canvas: Canvas) {
        if (days.isEmpty()) return
        paint.textSize = 10 * resources.displayMetrics.scaledDensity; paint.color = AppUi.muted
        for ((row, label) in listOf(0 to "一", 2 to "三", 4 to "五", 6 to "日"))
            canvas.drawText(label, 0f, (row * 12 + 33) * dp, paint)
        val max = days.maxOf { it.amount.toFloat() }.coerceAtLeast(1f)
        days.forEach { day ->
            val offset = ChronoUnit.DAYS.between(first, day.day).toInt()
            val x = (26 + offset / 7 * 12) * dp; val y = (24 + offset % 7 * 12) * dp
            if (day.day.dayOfMonth == 1) {
                paint.color = AppUi.muted
                canvas.drawText("${day.day.monthValue}月", x, 15 * dp, paint)
            }
            val ratio = if (day.amount == BigDecimal.ZERO) 0f else when {
                day.amount.toFloat() / max <= .25f -> .28f
                day.amount.toFloat() / max <= .5f -> .48f
                day.amount.toFloat() / max <= .75f -> .72f
                else -> 1f
            }
            paint.color = if (ratio == 0f) AppUi.border else Color.rgb(
                (255 + (Color.red(accent) - 255) * ratio).toInt(),
                (255 + (Color.green(accent) - 255) * ratio).toInt(),
                (255 + (Color.blue(accent) - 255) * ratio).toInt())
            canvas.drawRoundRect(x, y, x + 10 * dp, y + 10 * dp, 2 * dp, 2 * dp, paint)
            if (day.day > today) {
                paint.color = Color.rgb(184, 193, 205); paint.strokeWidth = dp
                canvas.drawLine(x + 2 * dp, y + 8 * dp, x + 8 * dp, y + 2 * dp, paint)
            }
        }
        paint.color = AppUi.muted
        canvas.drawText("少", 26 * dp, 128 * dp, paint)
        for (i in 0..4) {
            val r = i / 4f
            paint.color = if (i == 0) AppUi.border else Color.rgb((255+(Color.red(accent)-255)*r).toInt(),(255+(Color.green(accent)-255)*r).toInt(),(255+(Color.blue(accent)-255)*r).toInt())
            canvas.drawRoundRect((44+i*12)*dp,118*dp,(54+i*12)*dp,128*dp,2*dp,2*dp,paint)
        }
        paint.color = AppUi.muted; canvas.drawText("多",108*dp,128*dp,paint)
    }
}
