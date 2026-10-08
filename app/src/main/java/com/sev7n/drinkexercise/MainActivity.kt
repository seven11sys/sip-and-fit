package com.sev7n.drinkexercise

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.ImageView
import android.widget.AdapterView
import android.widget.PopupMenu
import android.view.Gravity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var store: ReminderStore
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var workoutSummary: TextView
    private lateinit var waterCaption: TextView
    private lateinit var waterNext: TextView
    private lateinit var waterRing: ProgressRing
    private lateinit var waterChart: WeekChart
    private lateinit var waterStats: TextView
    private lateinit var waterLegend: TextView
    private lateinit var workoutChart: WeekChart
    private lateinit var workoutStats: TextView
    private lateinit var statsSelector: Spinner
    private var selectedSeries: String? = null
    private lateinit var waterDetails: LinearLayout
    private lateinit var workoutDetails: LinearLayout
    private lateinit var testDetails: LinearLayout
    private lateinit var waterReminderSummary: TextView
    private lateinit var workoutReminderSummary: TextView
    private lateinit var notificationBadge: TextView
    private lateinit var preciseBadge: TextView
    private lateinit var librarySummary: TextView
    private lateinit var workoutHistory: LinearLayout
    private val pages = linkedMapOf<String, ScrollView>()
    private val navigation = mutableMapOf<String, Button>()
    private var currentPage = "water"
    private lateinit var permissionStatus: TextView
    private lateinit var waterEnabled: CompoundButton
    private lateinit var start: Button
    private lateinit var end: Button
    private lateinit var interval: EditText
    private lateinit var pauseEnabled: CheckBox
    private lateinit var pauseStart: Button
    private lateinit var pauseEnd: Button
    private lateinit var goal: EditText
    private lateinit var stopAtGoal: CheckBox
    private lateinit var workoutEnabled: CompoundButton
    private lateinit var workoutTime: Button
    private lateinit var waterSaveStatus: TextView
    private lateinit var workoutSaveStatus: TextView
    private var waterHistoryExpanded = false
    private var workoutHistoryExpanded = false
    private lateinit var history: LinearLayout
    private lateinit var projects: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private val saveWaterTask = Runnable { saveWater() }
    private val dayChecks = mutableMapOf<DayOfWeek, CheckBox>()
    private val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ReminderStore(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(AppUi.background)
        }
        val host = FrameLayout(this)
        root.addView(host, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val pageContents = linkedMapOf<String, LinearLayout>()
        for (key in listOf("water", "workout", "settings")) {
            val page = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), dp(24))
            }
            pageContents[key] = page
            val scroll = ScrollView(this).apply { tag = "page.$key"; addView(page) }
            pages[key] = scroll
            host.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE); setPadding(dp(12), dp(4), dp(12), dp(4)) }
        listOf("water" to "喝水", "workout" to "健身", "settings" to "设置").forEach { (key, title) ->
            val tab = Button(this).apply {
                text = title; isAllCaps = false
                AppUi.button(this)
                textSize = 12f
                setPadding(0, dp(6), 0, dp(6))
                compoundDrawablePadding = dp(4)
                contentDescription = "${title}页面"
                setOnClickListener { selectPage(key) }
            }
            navigation[key] = tab
            tabs.addView(tab, LinearLayout.LayoutParams(0, dp(64), 1f))
        }
        root.addView(View(this).apply { setBackgroundColor(AppUi.border) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
        root.addView(tabs)
        setContentView(root)
        content = pageContents.getValue("water")
        buildWaterPage()
        content = pageContents.getValue("workout")
        buildWorkoutPage()
        content = pageContents.getValue("settings")
        buildSettingsPage()
        ready = true
        listOf(waterEnabled, pauseEnabled, stopAtGoal).forEach { it.setOnCheckedChangeListener { _, _ -> saveWater() } }
        (listOf(workoutEnabled) + dayChecks.values).forEach { it.setOnCheckedChangeListener { _, _ -> saveWorkout() } }
        listOf(interval, goal).forEach { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    handler.removeCallbacks(saveWaterTask)
                    waterSaveStatus.text = "正在编辑…有效设置将自动保存"
                    handler.postDelayed(saveWaterTask, 500)
                }
            })
            input.setOnFocusChangeListener { _, focused -> if (!focused) saveWater() }
        }
        interval.tag = "water.interval"
        goal.tag = "water.goal"
        workoutEnabled.tag = "workout.enabled"
        selectPage(savedInstanceState?.getString("page") ?: intent.getStringExtra("page") ?: "water")
        refresh()
    }

    private fun makeText(text: String, size: Float = 15f, color: Int = AppUi.text, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); includeFontPadding = false
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun card(padding: Int = 16) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        background = AppUi.rounded(this@MainActivity)
    }

    private fun buildWaterPage() {
        heading("喝水", 28f)
        label(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)))
        val overview = card(18)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val numbers = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        numbers.addView(makeText("今日饮水", 16f, bold = true))
        status = makeText("0", 36f, AppUi.blue, true).apply { tag = "water.total" }
        numbers.addView(status)
        waterCaption = makeText("", 15f, AppUi.muted)
        numbers.addView(waterCaption)
        row.addView(numbers, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        waterRing = ProgressRing(this).apply { tag = "water.progress" }
        row.addView(waterRing, LinearLayout.LayoutParams(dp(104), dp(104)))
        overview.addView(row)
        waterNext = makeText("", 13f, AppUi.muted).apply { setPadding(0, dp(12), 0, 0) }
        overview.addView(waterNext)
        add(overview)
        heading("记录喝水")
        (100..500 step 50).toList().chunked(3).forEach { amounts ->
            val buttons = LinearLayout(this)
            amounts.forEachIndexed { index, ml ->
                buttons.addView(Button(this).apply {
                    text = "$ml ml"; contentDescription = "记录喝水 $ml 毫升"
                    AppUi.button(this, tinted = true)
                    setOnClickListener { recordWater(ml) }
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (index < 2) marginEnd = dp(8) })
            }
            add(buttons)
        }
        button("其他水量", outline = true) {
            val fields = dialogFields()
            val input = amountInput("本次水量（毫升）").apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
            fields.addView(input)
            val dialog = AlertDialog.Builder(this).setTitle("记录喝水").setView(fields)
                .setNegativeButton("取消", null).setPositiveButton("记录", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val ml = input.text.toString().toIntOrNull()
                    if (ml == null || ml !in 1..5000) input.error = "请输入 1—5000 毫升"
                    else { recordWater(ml); dialog.dismiss() }
                }
            }
            dialog.show()
        }
        heading("最近记录")
        history = card(12).apply { tag = "water.history"; add(this) }
        heading("近 7 天")
        val stats = card()
        waterStats = makeText("", 14f, AppUi.muted)
        stats.addView(waterStats)
        waterChart = WeekChart(this, AppUi.blue).apply { tag = "water.chart" }
        stats.addView(waterChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(172)))
        waterLegend = makeText("", 12f, AppUi.muted)
        stats.addView(waterLegend)
        add(stats)
    }

    private fun buildWorkoutPage() {
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(8)) }
        header.addView(makeText("健身", 28f, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(this).apply {
            AppUi.button(this, AppUi.muted)
            setCompoundDrawablesWithIntrinsicBounds(UiIcon("more", AppUi.muted, dp(24)), null, null, null)
            contentDescription = "今日健身安排"; tag = "workout.dayMenu"
            setOnClickListener {
                val popup = PopupMenu(this@MainActivity, this)
                popup.menu.add("今天跳过健身").setOnMenuItemClickListener {
                    store.skipWorkout(ZonedDateTime.now()); ReminderScheduler.dismiss(this@MainActivity, ReminderScheduler.WORKOUT)
                    refresh(ReminderScheduler.WORKOUT); true
                }
                popup.menu.add("撤回今日跳过").setOnMenuItemClickListener {
                    val now = ZonedDateTime.now(); store.resetWorkout(now.toLocalDate(), now.zone)
                    refresh(ReminderScheduler.WORKOUT); true
                }
                popup.show()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        add(header)
        workoutSummary = label("")
        heading("今日目标")
        projects = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "workout.targets"; add(this) }
        button("设置运动目标", AppUi.green) { editGoalDialog(null) }
        button("记录其他运动", AppUi.green, outline = true) { recordWorkoutDialog(null) }
        label("无需设置目标，也可以直接记录").apply { textSize = 13f; gravity = Gravity.CENTER }
        heading("最近记录")
        workoutHistory = card(12).apply { tag = "workout.history"; add(this) }
        heading("近 7 天")
        val stats = card()
        statsSelector = Spinner(this).apply { tag = "workout.stats.selector" }
        stats.addView(statsSelector, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        workoutStats = makeText("", 14f, AppUi.muted)
        stats.addView(workoutStats)
        workoutChart = WeekChart(this, AppUi.green).apply { tag = "workout.chart" }
        stats.addView(workoutChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(172)))
        add(stats)
    }

    private fun settingRow(container: LinearLayout, title: String, icon: String, accent: Int = AppUi.text,
        toggle: CompoundButton? = null, key: String? = null, action: () -> Unit): TextView {
        if (container.childCount > 0) container.addView(View(this).apply { setBackgroundColor(AppUi.border) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(60)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            background = AppUi.ripple(this@MainActivity, Color.TRANSPARENT, accent)
            tag = key; setOnClickListener { action() }
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(UiIcon(icon, accent, dp(24))); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) })
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(makeText(title, 15f, bold = true))
        val subtitle = makeText("", 12f, AppUi.muted)
        text.addView(subtitle)
        row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toggle?.let { row.addView(it, LinearLayout.LayoutParams(dp(50), dp(48))) }
        row.addView(ImageView(this).apply {
            setImageDrawable(UiIcon("chevron", AppUi.muted, dp(18))); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginStart = dp(6) })
        container.addView(row)
        return subtitle
    }

    private fun reminderSwitch(enabled: Boolean, title: String, accent: Int, key: String) = Switch(this).apply {
        isChecked = enabled; contentDescription = title; tag = key
        thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, AppUi.muted))
        trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, AppUi.border))
    }

    private fun buildSettingsPage() {
        val settingsPage = content
        heading("设置", 28f)
        heading("提醒设置")
        val reminders = card(8)
        waterEnabled = reminderSwitch(store.water().enabled, "开启喝水提醒", AppUi.blue, "water.enabled")
        workoutEnabled = reminderSwitch(store.workout().enabled, "开启健身提醒", AppUi.green, "workout.enabled")
        waterReminderSummary = settingRow(reminders, "喝水提醒", "water", AppUi.blue, waterEnabled, "setting.water") { showDetails("喝水提醒", waterDetails) { saveWater() } }
        workoutReminderSummary = settingRow(reminders, "健身提醒", "workout", AppUi.green, workoutEnabled, "setting.workout") { showDetails("健身提醒", workoutDetails) { saveWorkout() } }
        add(reminders)
        heading("运动项目库")
        val library = card(8)
        librarySummary = settingRow(library, "预设与自定义运动", "list", key = "setting.library") {
            val available = store.workoutProjects()
            AlertDialog.Builder(this).setTitle("运动项目库（${available.size} 项）")
                .setItems(available.map { it.name }.toTypedArray(), null).setPositiveButton("关闭", null).show()
        }
        settingRow(library, "添加自定义运动", "plus", key = "setting.addProject") { addProjectDialog() }.apply { visibility = View.GONE }
        add(library)
        heading("通知与后台")
        val permissions = card(8)
        notificationBadge = settingRow(permissions, "通知权限", "bell", key = "setting.notifications") {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        preciseBadge = settingRow(permissions, "准时提醒", "clock", key = "setting.precise") {
            if (Build.VERSION.SDK_INT >= 31 && !ReminderScheduler.preciseAllowed(this)) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                android.net.Uri.parse("package:$packageName")))
            else Toast.makeText(this, "已允许准时提醒", Toast.LENGTH_SHORT).show()
        }
        settingRow(permissions, "应用省电设置", "battery", key = "setting.battery") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
        }.apply { text = "锁屏收不到时，检查是否设为无限制" }
        add(permissions)
        heading("提醒测试")
        val tests = card(8)
        settingRow(tests, "测试通知", "send", key = "setting.tests") { refresh(); showDetails("提醒检查与测试", testDetails) {} }
            .apply { text = "立即测试 · 锁屏测试 · 状态检查" }
        add(tests)

        // Keep detail controls alive between openings so unsaved edits and validation messages remain visible.
        waterDetails = dialogFields(); content = waterDetails
        val water = store.water()
        start = timeButton("开始", water.start)
        end = timeButton("结束", water.end)
        interval = numberField("提醒间隔（分钟，15—720）", water.intervalMinutes.toInt())
        pauseEnabled = check("午休暂停", water.pauseEnabled)
        pauseStart = timeButton("午休开始", water.pauseStart)
        pauseEnd = timeButton("午休结束", water.pauseEnd)
        goal = numberField("每日目标（毫升，按需设置）", water.goalMl)
        stopAtGoal = check("达到目标后，当天停止提醒", water.stopAtGoal)
        label("记录喝水后重新计时，午休和夜间不提醒。")
        waterSaveStatus = label("设置会自动保存")
        workoutDetails = dialogFields(); content = workoutDetails
        val workout = store.workout()
        workoutTime = timeButton("固定时间", workout.time)
        val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        DayOfWeek.entries.forEachIndexed { index, day -> dayChecks[day] = check(names[index], day in workout.days) }
        label("每天最多一次正常提醒。全部目标达成或跳过后，当天不再提醒。")
        workoutSaveStatus = label("设置会自动保存")
        testDetails = dialogFields(); content = testDetails
        permissionStatus = label("")
        button("通知类别设置（声音／悬浮通知）") {
            startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderScheduler.channelId()))
        }
        button("立即测试喝水通知") { testNotification(ReminderScheduler.WATER, false) }
        button("立即测试健身通知") { testNotification(ReminderScheduler.WORKOUT, false) }
        button("1 分钟后测试喝水通知") { testNotification(ReminderScheduler.WATER, true) }
        button("1 分钟后测试健身通知") { testNotification(ReminderScheduler.WORKOUT, true) }
        label("安排锁屏测试后，退到桌面并锁屏等待。小米／红米收不到时，将应用省电设为无限制；仍收不到再检查后台自启动。")
        content = settingsPage
    }

    private fun showDetails(title: String, fields: LinearLayout, onClose: () -> Unit) {
        (fields.parent as? android.view.ViewGroup)?.removeView(fields)
        val scroll = ScrollView(this).apply { addView(fields) }
        AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("关闭", null).create().apply {
            setOnDismissListener { onClose() }
            show()
            window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun selectPage(key: String) {
        if (ready && currentPage == "settings" && key != "settings") {
            handler.removeCallbacks(saveWaterTask)
            saveWater()
            saveWorkout()
        }
        currentPage = if (key in pages) key else "water"
        pages.forEach { (name, page) -> page.visibility = if (name == currentPage) View.VISIBLE else View.GONE }
        navigation.forEach { (name, tab) ->
            val accent = if (name == "workout") AppUi.green else AppUi.blue
            val tint = if (name == currentPage) accent else AppUi.muted
            tab.isSelected = name == currentPage
            tab.setTextColor(tint)
            tab.setCompoundDrawablesWithIntrinsicBounds(null, UiIcon(name, tint, dp(24)), null, null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", currentPage)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refresh()
    }

    override fun onPause() {
        if (ready) {
            handler.removeCallbacks(saveWaterTask)
            saveWater()
            saveWorkout()
        }
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun recordWater(ml: Int) {
        store.recordWater(ml, ZonedDateTime.now())
        ReminderScheduler.dismiss(this, ReminderScheduler.WATER)
        refresh(ReminderScheduler.WATER)
    }

    private fun testNotification(kind: String, delayed: Boolean) {
        val sent = if (delayed) ReminderScheduler.scheduleTest(this, kind) != null else ReminderScheduler.testNotification(this, kind)
        refresh()
        Toast.makeText(this, if (!sent) ReminderScheduler.notificationBlockReason(this) ?: "通知发送失败"
            else if (delayed) "已安排测试，请退到桌面等待" else "测试已发送，请查看通知栏", Toast.LENGTH_LONG).show()
    }

    private fun refresh(vararg changed: String) {
        ReminderScheduler.refresh(this, recalculate = changed.toSet())
        val now = ZonedDateTime.now()
        fun next(kind: String) = store.timestamp("$kind.scheduled", now.zone)?.format(formatter) ?: "未安排"
        val water = store.water()
        val workout = store.workout()
        val total = store.totalWater(now)
        val numberFormat = NumberFormat.getIntegerInstance(Locale.CHINA)
        status.text = numberFormat.format(total)
        waterCaption.text = "/ ${numberFormat.format(water.goalMl)} 毫升"
        waterNext.text = "下次提醒 ${next("water")}"
        waterRing.fraction = total.toFloat() / water.goalMl
        waterReminderSummary.text = "${water.start}–${water.end} · 每${water.intervalMinutes}分钟"
        val repeat = if (workout.days.size == 7) "每天" else workout.days.sortedBy { it.value }.joinToString("、") { listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[it.value - 1] }.ifEmpty { "未选择日期" }
        workoutReminderSummary.text = "${workout.time} · $repeat"
        notificationBadge.text = ReminderScheduler.notificationBlockReason(this) ?: "已开启"
        preciseBadge.text = if (ReminderScheduler.preciseAllowed(this)) "已允许" else "未允许，提醒可能延迟"
        librarySummary.text = "${store.workoutProjects().size} 个备选项目"
        workoutSummary.text = "${store.workoutStatus(now.toLocalDate(), now.zone)}\n下次健身提醒：${next("workout")}"
        fun last(kind: String, key: String) = store.timestamp("$kind.$key", now.zone)?.format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")) ?: "无"
        permissionStatus.text = "通知：${ReminderScheduler.notificationBlockReason(this) ?: "已开启"}\n${ReminderScheduler.notificationStyle(this)}\n准时提醒：${if (ReminderScheduler.preciseAllowed(this)) "已允许" else "未允许，使用可能延迟的提醒"}\n\n喝水：${store.diagnostic("water")}\n上次触发：${last("water", "received")}；上次提交通知：${last("water", "sent")}\n健身：${store.diagnostic("workout")}\n上次触发：${last("workout", "received")}；上次提交通知：${last("workout", "sent")}"
        renderHistory(now)
        renderProjects(now)
        renderStats(now)
    }

    private fun saveWater() {
        if (!ready) return
        handler.removeCallbacks(saveWaterTask)
        val minutes = interval.text.toString().toLongOrNull()
        val ml = goal.text.toString().toIntOrNull()
        fun invalid(message: String) {
            waterSaveStatus.text = "$message；仍使用上次有效设置"
            // Turning reminders off always takes effect, even during an unfinished edit.
            if (!waterEnabled.isChecked && store.water().enabled) {
                store.saveWater(store.water().copy(enabled = false)); refresh(ReminderScheduler.WATER)
            }
        }
        if (minutes == null || minutes !in 15..720) { interval.error = "请输入 15—720 分钟"; invalid("间隔无效"); return }
        if (ml == null || ml !in 1..20000) { goal.error = "请输入 1—20000 毫升"; invalid("目标无效"); return }
        interval.error = null
        goal.error = null
        val from = start.tag as LocalTime
        val until = end.tag as LocalTime
        val pauseFrom = pauseStart.tag as LocalTime
        val pauseUntil = pauseEnd.tag as LocalTime
        if (from >= until) { invalid("开始时间必须早于结束时间，暂不支持跨夜时段"); return }
        if (pauseEnabled.isChecked && (pauseFrom >= pauseUntil || pauseFrom < from || pauseUntil > until || (pauseFrom == from && pauseUntil == until))) {
            invalid("午休需在提醒时段内，且不能占满整个时段"); return
        }
        val settings = WaterSettings(waterEnabled.isChecked, from, until, minutes, pauseEnabled.isChecked,
            pauseFrom, pauseUntil, ml, stopAtGoal.isChecked)
        if (settings != store.water()) {
            store.saveWater(settings)
            ReminderScheduler.dismiss(this, ReminderScheduler.WATER)
            refresh(ReminderScheduler.WATER)
        }
        waterSaveStatus.text = "喝水设置已自动保存"
    }

    private fun saveWorkout() {
        if (!ready) return
        val days = dayChecks.filterValues { it.isChecked }.keys.toSet()
        val settings = WorkoutSettings(workoutEnabled.isChecked, workoutTime.tag as LocalTime, days)
        if (settings.enabled && days.isEmpty()) {
            // An empty week pauses reminders instead of silently retaining old days.
            store.saveWorkout(settings.copy(enabled = false))
            ready = false
            workoutEnabled.isChecked = false
            ready = true
            workoutSaveStatus.text = "请选择至少一天；健身提醒已暂停"
            refresh(ReminderScheduler.WORKOUT)
            return
        }
        if (settings != store.workout()) {
            store.saveWorkout(settings)
            ReminderScheduler.dismiss(this, ReminderScheduler.WORKOUT)
            refresh(ReminderScheduler.WORKOUT)
        }
        workoutSaveStatus.text = "健身设置已自动保存"
    }

    private fun unitSelector(initial: WorkoutUnit) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
            WorkoutUnit.entries.map { it.choice })
        setSelection(initial.ordinal)
    }

    private fun amountInput(hint: String) = EditText(this).apply {
        this.hint = hint
        textSize = 16f; setTextColor(AppUi.text); setHintTextColor(AppUi.muted)
        background = AppUi.rounded(this@MainActivity, radius = 10)
        setPadding(dp(12), dp(12), dp(12), dp(12)); minimumHeight = dp(52)
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun dialogFields() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), 0, dp(24), 0)
    }

    private fun addProjectDialog() {
        val fields = dialogFields()
        val name = EditText(this).apply { hint = "自定义运动名称"; setSingleLine(true); tag = "project.name" }
        fields.addView(name)
        fields.addView(TextView(this).apply { text = "加入项目库后，设置目标和记录运动时都可以选择；不会自动创建目标或记录。" })
        val dialog = AlertDialog.Builder(this).setTitle("添加自定义运动")
            .setView(fields).setNegativeButton("取消", null).setPositiveButton("添加", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = name.text.toString().trim()
                when {
                    title.isEmpty() || title.length > 40 -> name.error = "请输入 1—40 字的项目名称"
                    store.workoutProjects().any { it.name.equals(title, ignoreCase = true) } -> name.error = "项目库中已有这个运动"
                    else -> { store.addWorkoutProject(title); refresh(); dialog.dismiss() }
                }
            }
        }
        dialog.show()
    }

    private fun editGoalDialog(existing: WorkoutGoal?) {
        val available = store.workoutProjects()
        if (available.isEmpty()) {
            Toast.makeText(this, "项目库为空，请先添加自定义运动", Toast.LENGTH_LONG).show()
            return
        }
        val fields = dialogFields()
        var project = available.firstOrNull { it.id == existing?.id } ?: available.first()
        val choose = Button(this).apply { text = "项目：${project.name}"; isEnabled = existing == null }
        val unit = unitSelector(existing?.unit ?: project.defaultUnit).apply { tag = "goal.unit" }
        val target = amountInput("每日目标值，按你自己的计划填写").apply {
            tag = "goal.amount"
            existing?.target?.let { setText(WorkoutGoals.display(it)) }
        }
        choose.setOnClickListener {
            AlertDialog.Builder(this).setTitle("选择运动项目")
                .setItems(available.map { it.name }.toTypedArray()) { _, index ->
                    project = available[index]
                    choose.text = "项目：${project.name}"
                    val saved = store.workoutGoals().firstOrNull { it.id == project.id }
                    unit.setSelection((saved?.unit ?: project.defaultUnit).ordinal)
                    target.setText(saved?.target?.let { WorkoutGoals.display(it) } ?: "")
                }.show()
        }
        fields.addView(choose); fields.addView(unit); fields.addView(target)
        fields.addView(TextView(this).apply { text = "次数须为整数；时长、距离最多保留 3 位小数。同一项目保留一个每日目标，重新设置会更新原目标。" })
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "设置运动目标" else "修改运动目标")
            .setView(fields).setNegativeButton("取消", null).setPositiveButton("确定", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = WorkoutUnit.entries[unit.selectedItemPosition]
                val value = target.text.toString().toBigDecimalOrNull()
                if (value == null || !WorkoutGoals.validAmount(value, selected)) target.error = "请输入有效正数，次数须为整数"
                else {
                    store.saveGoal(WorkoutGoal(project.id, project.name, selected, value))
                    ReminderScheduler.dismiss(this, ReminderScheduler.WORKOUT)
                    refresh(ReminderScheduler.WORKOUT); dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun recordWorkoutDialog(goal: WorkoutGoal?) {
        val fields = dialogFields()
        val name = if (goal == null) EditText(this).apply {
            hint = "运动项目名称，也可以从项目库选择"; setSingleLine(true); tag = "record.name"
        } else null
        val unit = if (goal == null) unitSelector(WorkoutUnit.REPETITIONS).apply { tag = "record.unit" } else null
        if (name != null && unit != null) {
            fields.addView(name)
            fields.addView(Button(this).apply {
                text = "从项目库选择（预设／自定义）"
                setOnClickListener {
                    val available = store.workoutProjects()
                    if (available.isEmpty()) Toast.makeText(this@MainActivity, "项目库为空，可以直接输入运动名称", Toast.LENGTH_LONG).show()
                    else AlertDialog.Builder(this@MainActivity).setTitle("选择运动项目")
                        .setItems(available.map { it.name }.toTypedArray()) { _, index ->
                            name.setText(available[index].name)
                            unit.setSelection(available[index].defaultUnit.ordinal)
                        }.show()
                }
            })
            fields.addView(unit)
        }
        val selectedUnit = goal?.unit ?: WorkoutUnit.REPETITIONS
        val amount = amountInput(if (goal == null) "本次实际完成量" else "本次完成量（${selectedUnit.label}）").apply { tag = "record.amount" }
        if (goal != null) fields.addView(TextView(this).apply { text = "按已设目标记录：${goal.unit.choice}。只填写本次实际完成量，多次记录会累计。" })
        else fields.addView(TextView(this).apply { text = "选择时长、距离或次数，填写本次实际完成量。直接记录不会创建每日目标或添加项目；如有同项目同类目标，会计入进度。" })
        fields.addView(amount)
        val dialog = AlertDialog.Builder(this).setTitle(if (goal == null) "记录其他运动" else "记录：${goal.name}").setView(fields)
            .setNegativeButton("取消", null).setPositiveButton("记录", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = goal?.name ?: name!!.text.toString().trim()
                val selected = goal?.unit ?: WorkoutUnit.entries[unit!!.selectedItemPosition]
                val value = amount.text.toString().toBigDecimalOrNull()
                when {
                    title.isEmpty() || title.length > 40 -> name?.error = "请输入 1—40 字的项目名称"
                    value == null || !WorkoutGoals.validAmount(value, selected) -> amount.error = "请输入有效正数，次数须为整数"
                    else -> {
                        if (goal != null) store.recordWorkout(goal, selected, value, ZonedDateTime.now())
                        else {
                            val project = store.workoutProjects().firstOrNull { it.name.equals(title, ignoreCase = true) }
                            store.recordWorkout(project?.name ?: title, selected, value, ZonedDateTime.now(), project?.id)
                        }
                        ReminderScheduler.dismiss(this, ReminderScheduler.WORKOUT)
                        refresh(ReminderScheduler.WORKOUT)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun renderProjects(now: ZonedDateTime) {
        if (!::projects.isInitialized) return
        projects.removeAllViews()
        val records = store.workoutRecords(now.zone)
        val goals = store.workoutGoals()
        if (goals.isEmpty()) projects.addView(card(24).apply {
            addView(makeText("还没有运动目标", 17f, bold = true))
            addView(makeText("设置一个目标，或直接记录今天的运动。", 14f, AppUi.muted).apply { setPadding(0, dp(8), 0, 0) })
        })
        goals.forEach { goal ->
            val progress = WorkoutGoals.progress(goal, now.toLocalDate(), records)
            val target = goal.target ?: return@forEach
            val item = card(14)
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(makeText(goal.name, 17f, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(Button(this).apply {
                AppUi.button(this, AppUi.muted)
                setCompoundDrawablesWithIntrinsicBounds(UiIcon("more", AppUi.muted, dp(20)), null, null, null)
                contentDescription = "${goal.name}的目标选项"; tag = "goal.menu.${goal.id}"
                setOnClickListener {
                    val menu = PopupMenu(this@MainActivity, this)
                    menu.menu.add("修改目标").setOnMenuItemClickListener { editGoalDialog(goal); true }
                    menu.menu.add("移除目标").setOnMenuItemClickListener {
                        AlertDialog.Builder(this@MainActivity).setTitle("移除 ${goal.name} 的目标？")
                            .setMessage("运动项目和已有记录会保留。")
                            .setNegativeButton("取消", null).setPositiveButton("移除") { _, _ ->
                                store.deleteGoal(goal.id); refresh(ReminderScheduler.WORKOUT)
                            }.show()
                        true
                    }
                    menu.show()
                }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            item.addView(header)
            val values = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val amount = android.text.SpannableString("${WorkoutGoals.display(progress)} / ${WorkoutGoals.display(target)} ${goal.unit.label}")
            val offset = WorkoutGoals.display(progress).length
            amount.setSpan(android.text.style.ForegroundColorSpan(AppUi.green), 0, offset, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            amount.setSpan(android.text.style.AbsoluteSizeSpan(28, true), 0, offset, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            values.addView(makeText(amount.toString(), 18f).apply { text = amount }, LinearLayout.LayoutParams(0, dp(40), 1f))
            val fraction = progress.divide(target, 6, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
            values.addView(makeText(if (progress >= target) "已达标" else "${(fraction * 100).roundToInt()}%", 13f, AppUi.green))
            item.addView(values)
            item.addView(GoalProgress(this, fraction).apply { contentDescription = "${goal.name}进度 ${(fraction * 100).toInt()}%" },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply { bottomMargin = dp(14) })
            item.addView(Button(this).apply {
                text = "记录"; AppUi.button(this, AppUi.green, filled = true)
                setOnClickListener { recordWorkoutDialog(goal) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
            projects.addView(item, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
        }
    }

    private fun renderStats(now: ZonedDateTime) {
        val waterDays = DailyStats.water(now.toLocalDate(), now.zone, store.waterRecords(now.zone))
        val waterTotal = waterDays.fold(BigDecimal.ZERO) { sum, day -> sum + day.amount }
        waterStats.text = "近 7 天共 ${WorkoutGoals.display(waterTotal)} 毫升"
        waterLegend.text = "虚线：当前每日目标 ${store.water().goalMl} 毫升"
        waterChart.show(waterDays, "毫升", BigDecimal(store.water().goalMl))
        val records = store.workoutRecords(now.zone)
        val choices = DailyStats.series(store.workoutGoals(), records)
        statsSelector.onItemSelectedListener = null
        if (choices.isEmpty()) {
            statsSelector.visibility = View.GONE; workoutChart.visibility = View.GONE
            workoutStats.text = "记录运动后，这里会显示近 7 天趋势。"
            return
        }
        statsSelector.visibility = View.VISIBLE; workoutChart.visibility = View.VISIBLE
        statsSelector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices.map { it.label })
        val selected = choices.indexOfFirst { it.key == selectedSeries }.coerceAtLeast(0)
        fun display(index: Int) {
            val choice = choices[index]
            selectedSeries = choice.key
            val days = DailyStats.workout(now.toLocalDate(), now.zone, choice, records)
            val total = days.fold(BigDecimal.ZERO) { sum, day -> sum + day.amount }
            workoutStats.text = "近 7 天共 ${WorkoutGoals.display(total)} ${choice.unit.label}"
            workoutChart.show(days, choice.unit.label)
        }
        statsSelector.setSelection(selected)
        display(selected)
        statsSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { if (position in choices.indices) display(position) }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun renderHistory(now: ZonedDateTime) {
        if (!::history.isInitialized || !::workoutHistory.isInitialized) return
        fun render(container: LinearLayout, rows: List<Pair<ZonedDateTime, Pair<String, () -> Unit>>>) {
            container.removeAllViews()
            if (rows.isEmpty()) container.addView(makeText("还没有记录", 14f, AppUi.muted).apply { setPadding(dp(4), dp(12), dp(4), dp(12)) })
            val expanded = if (container == history) waterHistoryExpanded else workoutHistoryExpanded
            rows.sortedByDescending { it.first.toInstant() }.take(if (expanded) 20 else 3).forEach { (at, detail) ->
                if (container.childCount > 0) container.addView(View(this).apply { setBackgroundColor(AppUi.border) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56) }
                row.addView(makeText("${at.format(if (at.toLocalDate() == now.toLocalDate()) DateTimeFormatter.ofPattern("HH:mm") else formatter)}  ${detail.first}", 14f).apply { setPadding(dp(4), dp(8), dp(4), dp(8)) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Button(this).apply { text = "撤回"; AppUi.button(this, if (container == history) AppUi.blue else AppUi.green); textSize = 13f; setOnClickListener { detail.second() } })
                container.addView(row)
            }
            if (rows.size > 3) container.addView(Button(this).apply {
                text = if (expanded) "收起记录" else "展开最近 20 条记录"
                AppUi.button(this, AppUi.muted)
                setOnClickListener {
                    if (container == history) waterHistoryExpanded = !expanded else workoutHistoryExpanded = !expanded
                    renderHistory(ZonedDateTime.now())
                }
            })
        }
        val waterRows = store.waterRecords(now.zone).map { record ->
            val undo: () -> Unit = {
                store.deleteWater(record.id)
                ReminderScheduler.dismiss(this, ReminderScheduler.WATER)
                refresh(ReminderScheduler.WATER)
            }
            record.at to ("喝水 ${record.ml} 毫升" to undo)
        }
        val workoutRows = store.workoutRecords(now.zone).map { record ->
            val undo: () -> Unit = {
                store.deleteWorkout(record.id, now.zone)
                ReminderScheduler.dismiss(this, ReminderScheduler.WORKOUT)
                refresh(ReminderScheduler.WORKOUT)
            }
            record.at to ("${record.type} ${WorkoutGoals.display(record.amount)} ${record.unit.label}" to undo)
        }
        render(history, waterRows)
        render(workoutHistory, workoutRows)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun add(view: View) {
        content.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }
    private fun label(text: String) = makeText(text, 14f, AppUi.muted).apply { add(this) }
    private fun heading(text: String, size: Float = 18f) {
        makeText(text, size, bold = true).apply { setPadding(0, dp(if (size >= 28f) 8 else 12), 0, dp(4)); add(this) }
    }
    private fun button(text: String, accent: Int = AppUi.blue, outline: Boolean = false, clicked: () -> Unit) = Button(this).apply {
        this.text = text; AppUi.button(this, accent, outline = outline); setOnClickListener { clicked() }; add(this)
    }
    private fun check(text: String, checked: Boolean) = CheckBox(this).apply {
        this.text = text; isChecked = checked; textSize = 15f; setTextColor(AppUi.text); minimumHeight = dp(48); add(this)
    }
    private fun numberField(text: String, value: Int): EditText {
        label(text)
        return amountInput(text).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(value.toString()); add(this)
        }
    }
    private fun timeButton(title: String, initial: LocalTime): Button {
        lateinit var view: Button
        view = button("$title：$initial") {
            val current = view.tag as LocalTime
            TimePickerDialog(this, { _, hour, minute ->
                val picked = LocalTime.of(hour, minute)
                view.tag = picked; view.text = "$title：$picked"
                if (ready) {
                    if (view === workoutTime) saveWorkout() else saveWater()
                }
            }, current.hour, current.minute, true).show()
        }
        view.tag = initial
        return view
    }
}
