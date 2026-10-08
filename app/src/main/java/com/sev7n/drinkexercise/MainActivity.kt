package com.sev7n.drinkexercise

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
        button("喝了 250 毫升") { recordWater(250) }
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
        button("完成今日健身") {
            store.markWorkout(ZonedDateTime.now(), true)
            ReminderScheduler.dismiss(this, ReminderScheduler.WORKOUT)
            refresh()
        }
        button("今天跳过健身") {
            store.markWorkout(ZonedDateTime.now(), false)
            ReminderScheduler.dismiss(this, ReminderScheduler.WORKOUT)
            refresh()
        }

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

        heading("健身提醒")
        workoutEnabled = check("开启健身提醒", workout.enabled)
        workoutTime = timeButton("固定时间", workout.time)
        val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        DayOfWeek.entries.forEachIndexed { index, day -> dayChecks[day] = check(names[index], day in workout.days) }
        label("每天最多一次正常提醒。完成或跳过后，当天不再提醒；可从通知延后 15 分钟。")
        button("保存提醒设置") { save() }

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
        label("未开启准时提醒权限时，提醒可能延迟。手机省电设置也会影响通知。首次使用请保存设置并开启通知。")
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun recordWater(ml: Int) {
        store.recordWater(ml, ZonedDateTime.now())
        ReminderScheduler.dismiss(this, ReminderScheduler.WATER)
        refresh()
    }

    private fun refresh() {
        ReminderScheduler.refresh(this)
        val now = ZonedDateTime.now()
        fun next(kind: String) = store.timestamp("$kind.scheduled", now.zone)?.format(formatter) ?: "未安排"
        status.text = "今日喝水 ${store.totalWater(now)} / ${store.water().goalMl} 毫升\n${store.workoutStatus(now.toLocalDate())}\n\n下次喝水提醒：${next("water")}\n下次健身提醒：${next("workout")}"
        permissionStatus.text = "通知：${if (ReminderScheduler.notificationsAllowed(this)) "已开启" else "未开启"}\n准时提醒：${if (ReminderScheduler.preciseAllowed(this)) "已允许" else "未允许，使用可能延迟的提醒"}"
    }

    private fun save() {
        val minutes = interval.text.toString().toLongOrNull()
        val ml = goal.text.toString().toIntOrNull()
        if (minutes == null || minutes !in 15..720) { interval.error = "请输入 15—720 分钟"; return }
        if (ml == null || ml !in 1..20000) { goal.error = "请输入 1—20000 毫升"; return }
        val from = start.tag as LocalTime
        val until = end.tag as LocalTime
        val pauseFrom = pauseStart.tag as LocalTime
        val pauseUntil = pauseEnd.tag as LocalTime
        fun invalid(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        if (from >= until) { invalid("开始时间必须早于结束时间，暂不支持跨夜时段"); return }
        if (pauseEnabled.isChecked && (pauseFrom >= pauseUntil || pauseFrom < from || pauseUntil > until || (pauseFrom == from && pauseUntil == until))) {
            invalid("午休需在提醒时段内，且不能占满整个时段"); return
        }
        val days = dayChecks.filterValues { it.isChecked }.keys.toSet()
        if (workoutEnabled.isChecked && days.isEmpty()) { invalid("请至少选择一个健身重复日期"); return }
        store.save(
            WaterSettings(waterEnabled.isChecked, from, until, minutes, pauseEnabled.isChecked,
                pauseFrom, pauseUntil, ml, stopAtGoal.isChecked),
            WorkoutSettings(workoutEnabled.isChecked, workoutTime.tag as LocalTime, days),
        )
        ReminderScheduler.dismiss(this, ReminderScheduler.WATER)
        ReminderScheduler.dismiss(this, ReminderScheduler.WORKOUT)
        refresh()
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
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
            }, current.hour, current.minute, true).show()
        }
        view.tag = initial
        return view
    }
}
