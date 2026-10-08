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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import java.math.BigDecimal
import java.util.UUID
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var store: ReminderStore
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var permissionStatus: TextView
    private lateinit var waterEnabled: CheckBox
    private lateinit var start: Button
    private lateinit var end: Button
    private lateinit var interval: EditText
    private lateinit var pauseEnabled: CheckBox
    private lateinit var pauseStart: Button
    private lateinit var pauseEnd: Button
    private lateinit var goal: EditText
    private lateinit var stopAtGoal: CheckBox
    private lateinit var workoutEnabled: CheckBox
    private lateinit var workoutTime: Button
    private lateinit var waterSaveStatus: TextView
    private lateinit var workoutSaveStatus: TextView
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
        val water = store.water()
        val workout = store.workout()
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
            setBackgroundColor(Color.rgb(246, 249, 247))
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(content)
        })
        heading("喝水与健身", 28f)
        label("把日常的小习惯，慢慢坚持下来。")
        status = label("")
        heading("记录喝水")
        (100..500 step 50).toList().chunked(3).forEach { amounts ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            amounts.forEach { ml ->
                row.addView(Button(this).apply {
                    text = "$ml 毫升"
                    setOnClickListener { recordWater(ml) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            add(row)
        }
        button("记录其他水量") {
            val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; hint = "毫升" }
            val dialog = AlertDialog.Builder(this).setTitle("记录喝水").setView(input)
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
        heading("运动项目与每日目标")
        label("选择目标方式和目标值；各项目分别累计，所有已设目标的项目达标后自动完成。未设目标的项目也能记录。")
        projects = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; add(this) }
        button("添加自定义运动") { editGoalDialog(null) }
        button("今天跳过健身") {
            store.skipWorkout(ZonedDateTime.now())
            ReminderScheduler.dismiss(this, ReminderScheduler.WORKOUT)
            refresh(ReminderScheduler.WORKOUT)
        }
        button("撤回今日跳过") {
            val now = ZonedDateTime.now()
            store.resetWorkout(now.toLocalDate(), now.zone)
            refresh(ReminderScheduler.WORKOUT)
        }
        heading("最近记录")
        label("点记录旁的“撤回”可纠正误点，包括从通知栏添加的记录。")
        history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; add(this) }

        heading("喝水提醒")
        waterEnabled = check("开启喝水提醒", water.enabled)
        start = timeButton("开始", water.start)
        end = timeButton("结束（此时间后不再提醒）", water.end)
        interval = numberField("提醒间隔（分钟，15—720）", water.intervalMinutes.toInt())
        pauseEnabled = check("午休暂停", water.pauseEnabled)
        pauseStart = timeButton("午休开始", water.pauseStart)
        pauseEnd = timeButton("午休结束", water.pauseEnd)
        goal = numberField("每日目标（毫升，按需设置）", water.goalMl)
        stopAtGoal = check("达到目标后，当天停止提醒", water.stopAtGoal)
        label("记录喝水后重新计时；午休和夜间不提醒。未处理的提醒不会连续催促。")
        waterSaveStatus = label("设置会自动保存")

        heading("健身提醒")
        workoutEnabled = check("开启健身提醒", workout.enabled)
        workoutTime = timeButton("固定时间", workout.time)
        val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        DayOfWeek.entries.forEachIndexed { index, day -> dayChecks[day] = check(names[index], day in workout.days) }
        label("每天最多一次正常提醒。全部目标达成或跳过后，当天不再提醒；可从通知延后 15 分钟。")
        workoutSaveStatus = label("设置会自动保存")

        heading("提醒权限")
        permissionStatus = label("")
        button("开启通知") {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            } else {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        button("设置准时提醒权限") {
            if (Build.VERSION.SDK_INT >= 31 && !ReminderScheduler.preciseAllowed(this)) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:$packageName")))
            } else Toast.makeText(this, "已允许准时提醒", Toast.LENGTH_SHORT).show()
        }
        button("通知类别设置（声音／悬浮通知）") {
            startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderScheduler.channelId()))
        }
        button("应用系统设置（后台／电量管理）") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$packageName")))
        }
        button("立即测试喝水通知") { testNotification(ReminderScheduler.WATER, false) }
        button("立即测试健身通知") { testNotification(ReminderScheduler.WORKOUT, false) }
        button("1 分钟后测试喝水通知") { testNotification(ReminderScheduler.WATER, true) }
        button("1 分钟后测试健身通知") { testNotification(ReminderScheduler.WORKOUT, true) }
        label("未开启准时提醒权限时，提醒可能延迟。手机省电设置也会影响通知。首次使用请开启需要的提醒和通知。")
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
        refresh()
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
        status.text = "今日喝水 ${store.totalWater(now)} / ${store.water().goalMl} 毫升\n${store.workoutStatus(now.toLocalDate(), now.zone)}\n\n下次喝水提醒：${next("water")}\n下次健身提醒：${next("workout")}"
        fun last(kind: String, key: String) = store.timestamp("$kind.$key", now.zone)?.format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")) ?: "无"
        permissionStatus.text = "通知：${ReminderScheduler.notificationBlockReason(this) ?: "已开启"}\n${ReminderScheduler.notificationStyle(this)}\n准时提醒：${if (ReminderScheduler.preciseAllowed(this)) "已允许" else "未允许，使用可能延迟的提醒"}\n\n喝水：${store.diagnostic("water")}\n上次触发：${last("water", "received")}；上次提交通知：${last("water", "sent")}\n健身：${store.diagnostic("workout")}\n上次触发：${last("workout", "received")}；上次提交通知：${last("workout", "sent")}"
        renderHistory(now)
        renderProjects(now)
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
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun editGoalDialog(existing: WorkoutGoal?) {
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), 0) }
        val name = EditText(this).apply { hint = "运动名称"; setSingleLine(true); setText(existing?.name ?: "") }
        val unit = unitSelector(existing?.unit ?: WorkoutUnit.REPETITIONS)
        val hasTarget = CheckBox(this).apply { text = "设为每日目标"; isChecked = existing?.target != null }
        val target = amountInput("每日目标值，按你自己的计划填写").apply {
            existing?.target?.let { setText(WorkoutGoals.display(it)) }
            isEnabled = hasTarget.isChecked
        }
        hasTarget.setOnCheckedChangeListener { _, checked -> target.isEnabled = checked }
        fields.addView(name); fields.addView(unit); fields.addView(hasTarget); fields.addView(target)
        fields.addView(TextView(this).apply { text = "次数须为整数；时长、距离最多保留 3 位小数。更换目标方式后，只累计同类单位的记录。" })
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "添加运动项目" else "编辑项目和目标")
            .setView(fields).setNegativeButton("取消", null).setPositiveButton("确定", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = name.text.toString().trim()
                val selected = WorkoutUnit.entries[unit.selectedItemPosition]
                val value = target.text.toString().toBigDecimalOrNull()
                when {
                    title.isEmpty() || title.length > 40 -> name.error = "请输入 1—40 字的项目名称"
                    hasTarget.isChecked && (value == null || !WorkoutGoals.validAmount(value, selected)) -> target.error = "请输入有效正数，次数须为整数"
                    else -> {
                        store.saveGoal(WorkoutGoal(existing?.id ?: UUID.randomUUID().toString(), title, selected,
                            if (hasTarget.isChecked) value else null))
                        ReminderScheduler.dismiss(this, ReminderScheduler.WORKOUT)
                        refresh(ReminderScheduler.WORKOUT); dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun recordWorkoutDialog(goal: WorkoutGoal) {
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), 0) }
        val unit = unitSelector(goal.unit)
        val amount = amountInput("本次实际完成量")
        fields.addView(unit); fields.addView(amount)
        fields.addView(TextView(this).apply { text = "每次记录会累计。单位与目标同类时计入进度，否则只保存记录。" })
        val dialog = AlertDialog.Builder(this).setTitle("记录：${goal.name}").setView(fields)
            .setNegativeButton("取消", null).setPositiveButton("记录", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = WorkoutUnit.entries[unit.selectedItemPosition]
                val value = amount.text.toString().toBigDecimalOrNull()
                when {
                    value == null || !WorkoutGoals.validAmount(value, selected) -> amount.error = "请输入有效正数，次数须为整数"
                    else -> {
                        store.recordWorkout(goal, selected, value, ZonedDateTime.now())
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
        store.workoutGoals().forEach { goal ->
            val progress = WorkoutGoals.progress(goal, now.toLocalDate(), records)
            val target = goal.target
            projects.addView(TextView(this).apply {
                text = "${goal.name}\n${if (target == null) "未设每日目标，已记录 ${WorkoutGoals.display(progress)} ${goal.unit.label}"
                else "${WorkoutGoals.display(progress)} / ${WorkoutGoals.display(target)} ${goal.unit.label}${if (progress >= target) " · 已达标" else ""}"}"
                textSize = 16f; setPadding(0, dp(12), 0, dp(4))
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("记录" to { recordWorkoutDialog(goal) }, "目标" to { editGoalDialog(goal) }, "移除" to {
                AlertDialog.Builder(this).setTitle("移除 ${goal.name}？")
                    .setMessage("项目和目标将移除，已有运动记录会保留。")
                    .setNegativeButton("取消", null).setPositiveButton("移除") { _, _ ->
                        store.deleteGoal(goal.id); refresh(ReminderScheduler.WORKOUT)
                    }.show()
                Unit
            }).forEach { (title, action) ->
                actions.addView(Button(this).apply { text = title; setOnClickListener { action() } },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            projects.addView(actions)
        }
    }

    private fun renderHistory(now: ZonedDateTime) {
        if (!::history.isInitialized) return
        history.removeAllViews()
        val rows = mutableListOf<Pair<ZonedDateTime, Pair<String, () -> Unit>>>()
        store.waterRecords(now.zone).forEach { record ->
            rows.add(record.at to ("喝水 ${record.ml} 毫升" to {
                store.deleteWater(record.id)
                ReminderScheduler.dismiss(this, ReminderScheduler.WATER)
                refresh(ReminderScheduler.WATER)
            }))
        }
        store.workoutRecords(now.zone).forEach { record ->
            rows.add(record.at to ("${record.type} ${WorkoutGoals.display(record.amount)} ${record.unit.label}" to {
                store.deleteWorkout(record.id, now.zone)
                ReminderScheduler.dismiss(this, ReminderScheduler.WORKOUT)
                refresh(ReminderScheduler.WORKOUT)
            }))
        }
        if (rows.isEmpty()) history.addView(TextView(this).apply { text = "还没有记录" })
        rows.sortedByDescending { it.first.toInstant() }.take(20).forEach { (at, detail) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply { text = "${at.format(formatter)}\n${detail.first}" },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply { text = "撤回"; setOnClickListener { detail.second() } })
            history.addView(row)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun add(view: View) {
        content.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }
    private fun label(text: String) = TextView(this).apply {
        this.text = text; textSize = 15f; setTextColor(Color.rgb(64, 78, 70)); add(this)
    }
    private fun heading(text: String, size: Float = 21f) {
        label(text).apply { textSize = size; setPadding(0, dp(20), 0, dp(8)); setTypeface(null, android.graphics.Typeface.BOLD) }
    }
    private fun button(text: String, clicked: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; setOnClickListener { clicked() }; add(this)
    }
    private fun check(text: String, checked: Boolean) = CheckBox(this).apply {
        this.text = text; isChecked = checked; add(this)
    }
    private fun numberField(text: String, value: Int): EditText {
        label(text)
        return EditText(this).apply {
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
