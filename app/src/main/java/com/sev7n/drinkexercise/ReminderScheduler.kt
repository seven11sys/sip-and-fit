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

    fun notificationsAllowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

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

    fun refresh(context: Context) {
        val now = ZonedDateTime.now()
        val store = ReminderStore(context)
        ensureChannel(context)
        schedule(context, WATER, if (notificationsAllowed(context)) nextWater(store, now) else null)
        schedule(context, WORKOUT, if (notificationsAllowed(context)) nextWorkout(store, now) else null)
        if (!ReminderRules.waterAllowed(now, store.water(), store.totalWater(now))) dismiss(context, WATER)
        if (!store.workout().enabled || now.toLocalDate() in store.workoutBlocked()) dismiss(context, WORKOUT)
    }

    private fun alarmIntent(context: Context, kind: String, at: Long) = PendingIntent.getBroadcast(
        context, id(kind), Intent(context, ReminderReceiver::class.java).setAction("alarm.$kind")
            .putExtra("kind", kind).putExtra("at", at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(context: Context, kind: String, time: ZonedDateTime?) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val store = ReminderStore(context)
        val pending = alarmIntent(context, kind, time?.toInstant()?.toEpochMilli() ?: 0)
        alarm.cancel(pending)
        store.clearTimestamp("$kind.scheduled")
        if (time == null) return
        store.setTimestamp("$kind.scheduled", time)
        val millis = time.toInstant().toEpochMilli()
        if (preciseAllowed(context)) {
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
                return
            } catch (_: SecurityException) { /* permission may have changed between check and call */ }
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
    }

    fun deliver(context: Context, kind: String, scheduledMillis: Long) {
        if (kind != WATER && kind != WORKOUT) return
        val store = ReminderStore(context)
        val now = ZonedDateTime.now()
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
            store.setTimestamp("workout.notified", scheduled)
            available
        }
        if (onTime && eligible && notificationsAllowed(context)) show(context, kind, now)
        refresh(context)
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "喝水和健身提醒", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun show(context: Context, kind: String, now: ZonedDateTime) {
        ensureChannel(context)
        val token = now.toInstant().toEpochMilli()
        ReminderStore(context).setTimestamp("$kind.notification", now)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(label: String, action: String, request: Int) = android.app.Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_info_details), label,
            PendingIntent.getBroadcast(context, request, Intent(context, ReminderReceiver::class.java)
                .setAction(action).putExtra("kind", kind).putExtra("token", token),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        ).build()
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (kind == WATER) "休息一下，喝点水" else "到健身时间了")
            .setContentText(if (kind == WATER) "按需补水，也可以稍后提醒。" else "准备开始今天的运动吧。")
            .setContentIntent(open).setAutoCancel(true)
            .addAction(if (kind == WATER) action("喝了 250 毫升", "record", id(kind) + 1)
                else android.app.Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_edit),
                    "记录运动", open).build())
            .addAction(action("稍后 15 分钟", "snooze", id(kind) + 2))
            .addAction(action(if (kind == WATER) "跳过本次" else "今天跳过", "skip", id(kind) + 3))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id(kind), notification)
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
        ReminderScheduler.refresh(context)
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { ReminderScheduler.refresh(context) }
}
