package com.sev7n.drinkexercise

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.Duration
import java.time.ZonedDateTime

object ReminderScheduler {
    const val WATER = "water"
    const val WORKOUT = "workout"
    private const val CHANNEL = "daily_reminders"
    private fun id(kind: String) = if (kind == WATER) 10 else 20

    fun notificationBlockReason(context: Context): String? {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return "未允许通知权限"
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return "系统已关闭本应用通知"
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return "“喝水和健身提醒”通知类别已关闭"
        return null
    }

    fun notificationsAllowed(context: Context) = notificationBlockReason(context) == null
    fun channelId() = CHANNEL

    fun notificationStyle(context: Context): String = when (context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance) {
        NotificationManager.IMPORTANCE_HIGH, NotificationManager.IMPORTANCE_MAX -> "弹出提醒（仍取决于系统设置）"
        NotificationManager.IMPORTANCE_DEFAULT -> "普通通知；可在通知类别设置中开启弹出提醒"
        NotificationManager.IMPORTANCE_LOW, NotificationManager.IMPORTANCE_MIN -> "静默通知；请检查通知类别设置"
        else -> "请检查通知类别设置"
    }

    fun preciseAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun nextWater(store: ReminderStore, now: ZonedDateTime): ZonedDateTime? = ReminderRules.nextWater(
        now, store.water(), store.timestamp("water.anchor", now.zone), store.totalWater(now),
        store.timestamp("water.snooze", now.zone),
    )

    fun nextWorkout(store: ReminderStore, now: ZonedDateTime): ZonedDateTime? {
        val snooze = store.timestamp("workout.snooze", now.zone)
        val notified = store.timestamp("workout.notified", now.zone)?.toLocalDate()
        val blocked = store.workoutBlocked() + if (snooze != null && snooze > now) emptySet() else setOfNotNull(notified)
        return ReminderRules.nextWorkout(now, store.workout(), blocked, snooze)
    }

    fun refresh(context: Context, recalculate: Set<String> = emptySet(), restoreAlarms: Boolean = false,
        now: ZonedDateTime = ZonedDateTime.now()) {
        val store = ReminderStore(context)
        ensureChannel(context)
        for (kind in listOf(WATER, WORKOUT)) {
            val existing = store.timestamp("$kind.scheduled", now.zone)
            val sameDayOrFuture = existing != null && (existing > now ||
                (existing.toLocalDate() == now.toLocalDate() && Duration.between(existing, now).toMinutes() <= 60))
            val eligible = if (existing == null) false else if (kind == WATER) {
                ReminderRules.waterAllowed(existing, store.water(), if (existing.toLocalDate() == now.toLocalDate()) store.totalWater(now) else 0)
            } else {
                store.workout().enabled && existing.dayOfWeek in store.workout().days &&
                    existing.toLocalDate() !in store.workoutBlocked(now.zone)
            }
            val keep = kind !in recalculate && sameDayOrFuture && eligible
            val next = if (!notificationsAllowed(context)) null else if (keep) existing
                else if (kind == WATER) nextWater(store, now) else nextWorkout(store, now)
            schedule(context, kind, next, restoreAlarms)
        }
        if (!ReminderRules.waterAllowed(now, store.water(), store.totalWater(now))) dismiss(context, WATER)
        if (!store.workout().enabled || now.toLocalDate() in store.workoutBlocked()) dismiss(context, WORKOUT)
    }

    private fun alarmIntent(context: Context, kind: String, at: Long) = PendingIntent.getBroadcast(
        context, id(kind), Intent(context, ReminderReceiver::class.java).setAction("alarm.$kind")
            .putExtra("kind", kind).putExtra("at", at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(context: Context, kind: String, time: ZonedDateTime?, force: Boolean = false) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val store = ReminderStore(context)
        val mode = if (preciseAllowed(context)) "exact" else "inexact"
        if (!force && time != null && time == store.timestamp("$kind.scheduled", time.zone) && store.alarmMode(kind) == mode) return
        val pending = alarmIntent(context, kind, time?.toInstant()?.toEpochMilli() ?: 0)
        alarm.cancel(pending)
        store.clearTimestamp("$kind.scheduled")
        if (time == null) {
            return
        }
        store.setTimestamp("$kind.scheduled", time)
        store.setAlarmMode(kind, mode)
        val millis = time.toInstant().toEpochMilli()
        setAlarm(context, millis, pending)
    }

    private fun setAlarm(context: Context, millis: Long, pending: PendingIntent) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        if (preciseAllowed(context)) {
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
                return
            } catch (_: SecurityException) { /* permission may have changed between check and call */ }
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
    }

    fun deliver(context: Context, kind: String, scheduledMillis: Long, now: ZonedDateTime = ZonedDateTime.now()) {
        if (kind != WATER && kind != WORKOUT) return
        val store = ReminderStore(context)
        store.setTimestamp("$kind.received", now)
        val scheduled = store.timestamp("$kind.scheduled", now.zone) ?: return
        if (scheduled.toInstant().toEpochMilli() != scheduledMillis || now < scheduled) return
        store.clearTimestamp("$kind.scheduled")
        store.clearTimestamp("$kind.snooze")
        val onTime = scheduled.toLocalDate() == now.toLocalDate() && Duration.between(scheduled, now).toMinutes() <= 60
        val eligible = if (kind == WATER) {
            store.setTimestamp("water.anchor", now)
            store.setTimestamp("water.lastReminder", now)
            ReminderRules.waterAllowed(now, store.water(), store.totalWater(now))
        } else {
            val settings = store.workout()
            val available = settings.enabled && now.dayOfWeek in settings.days && now.toLocalDate() !in store.workoutBlocked()
            available
        }
        if (onTime && eligible && notificationsAllowed(context)) {
            if (show(context, kind, now) && kind == WORKOUT) store.setTimestamp("workout.notified", scheduled)
        } else store.setDiagnostic(kind, notificationBlockReason(context) ?: if (!onTime) "系统触发过晚，已跳过过期提醒" else "当前时段或目标状态不需要提醒")
        refresh(context, now = now)
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "喝水和健身提醒", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun show(context: Context, kind: String, now: ZonedDateTime, test: Boolean = false): Boolean {
        ensureChannel(context)
        val store = ReminderStore(context)
        val blocked = notificationBlockReason(context)
        if (blocked != null) { store.setDiagnostic(kind, blocked); return false }
        val token = now.toInstant().toEpochMilli()
        if (!test) store.setTimestamp("$kind.notification", now)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(label: String, action: String, request: Int) = android.app.Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_info_details), label,
            PendingIntent.getBroadcast(context, request, Intent(context, ReminderReceiver::class.java)
                .setAction(action).putExtra("kind", kind).putExtra("token", token),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        ).build()
        val builder = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle((if (test) "测试 · " else "") + if (kind == WATER) "休息一下，喝点水" else "到健身时间了")
            .setContentText(if (test) "这是测试通知，不会更改记录或正式提醒计划。" else if (kind == WATER) "按需补水，也可以稍后提醒。" else "准备开始今天的运动吧。")
            .setContentIntent(open).setAutoCancel(true)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
        if (!test) builder
            .addAction(if (kind == WATER) action("喝了 250 毫升", "record", id(kind) + 1)
                else android.app.Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_edit),
                    "记录运动", open).build())
            .addAction(action("稍后 15 分钟", "snooze", id(kind) + 2))
            .addAction(action(if (kind == WATER) "跳过本次" else "今天跳过", "skip", id(kind) + 3))
        return try {
            context.getSystemService(NotificationManager::class.java).notify(id(kind) + if (test) 1000 else 0, builder.build())
            store.setTimestamp("$kind.sent", now)
            store.setDiagnostic(kind, if (test) "测试通知已提交，请检查通知栏" else "通知已提交，请检查通知栏")
            true
        } catch (_: SecurityException) {
            store.setDiagnostic(kind, "系统拒绝发送通知，请重新检查通知权限")
            false
        }
    }

    fun testNotification(context: Context, kind: String): Boolean = show(context, kind, ZonedDateTime.now(), test = true)

    fun scheduleTest(context: Context, kind: String): ZonedDateTime? {
        ensureChannel(context)
        if (!notificationsAllowed(context)) return null
        val at = ZonedDateTime.now().plusMinutes(1)
        val pending = PendingIntent.getBroadcast(context, id(kind) + 1000,
            Intent(context, ReminderReceiver::class.java).setAction("test.$kind").putExtra("kind", kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarm(context, at.toInstant().toEpochMilli(), pending)
        ReminderStore(context).setDiagnostic(kind, "测试安排在 ${at.toLocalTime().withNano(0)}，请退到桌面等待${if (!preciseAllowed(context)) "；未允许准时提醒，可能延迟" else ""}")
        return at
    }

    fun dismiss(context: Context, kind: String) {
        context.getSystemService(NotificationManager::class.java).cancel(id(kind))
        ReminderStore(context).clearTimestamp("$kind.notification")
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: return
        if (kind != ReminderScheduler.WATER && kind != ReminderScheduler.WORKOUT) return
        if (intent.action == "test.$kind") {
            ReminderScheduler.testNotification(context, kind)
            return
        }
        if (intent.action == "alarm.$kind") {
            ReminderScheduler.deliver(context, kind, intent.getLongExtra("at", 0))
            return
        }
        val store = ReminderStore(context)
        val now = ZonedDateTime.now()
        val issued = store.timestamp("$kind.notification", now.zone) ?: return
        // Old or double-tapped notification actions cannot create duplicate records.
        if (issued.toInstant().toEpochMilli() != intent.getLongExtra("token", -1)) return
        ReminderScheduler.dismiss(context, kind)
        if (issued.toLocalDate() != now.toLocalDate()) {
            ReminderScheduler.refresh(context)
            return
        }
        when (intent.action) {
            "record" -> if (kind == ReminderScheduler.WATER) store.recordWater(250, now)
            "snooze" -> {
                val until = now.plusMinutes(15)
                if (until.toLocalDate() == now.toLocalDate()) store.setTimestamp("$kind.snooze", until)
            }
            "skip" -> if (kind == ReminderScheduler.WORKOUT) store.skipWorkout(now)
        }
        ReminderScheduler.refresh(context, recalculate = setOf(kind))
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recalculate = if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED)
            setOf(ReminderScheduler.WATER, ReminderScheduler.WORKOUT) else emptySet()
        ReminderScheduler.refresh(context, recalculate, restoreAlarms = true)
    }
}
