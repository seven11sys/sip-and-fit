package com.sev7n.drinkexercise

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalTime
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var store: ReminderStore
    private lateinit var manager: NotificationManager
    private val now = ZonedDateTime.parse("2026-10-08T10:00:00+08:00[Asia/Shanghai]")

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
        store = ReminderStore(context)
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        manager.deleteNotificationChannel(ReminderScheduler.channelId())
        store.saveWater(WaterSettings(enabled = true, intervalMinutes = 60, pauseEnabled = false))
        store.saveWorkout(WorkoutSettings(enabled = true, time = LocalTime.of(11, 0)))
    }

    @Test fun openingScreenDoesNotRestartWaterCountdown() {
        ReminderScheduler.refresh(context, now = now)
        val first = store.timestamp("water.scheduled", now.zone)
        assertEquals(now.plusHours(1), first)
        ReminderScheduler.refresh(context, now = now.plusMinutes(30))
        assertEquals(first, store.timestamp("water.scheduled", now.zone))
    }

    @Test fun openingAtDueTimeKeepsBothPendingAlarms() {
        ReminderScheduler.refresh(context, now = now)
        ReminderScheduler.refresh(context, now = now.plusHours(1).plusSeconds(2))
        assertEquals(now.plusHours(1), store.timestamp("water.scheduled", now.zone))
        assertEquals(now.plusHours(1), store.timestamp("workout.scheduled", now.zone))
    }

    @Test fun reschedulingWorkoutDoesNotMoveWater() {
        ReminderScheduler.refresh(context, now = now)
        store.saveWorkout(WorkoutSettings(enabled = true, time = LocalTime.of(12, 0)))
        ReminderScheduler.refresh(context, recalculate = setOf("workout"), now = now.plusMinutes(30))
        assertEquals(now.plusHours(1), store.timestamp("water.scheduled", now.zone))
        assertEquals(now.plusHours(2), store.timestamp("workout.scheduled", now.zone))
    }

    @Test fun drinkingReallyResetsCountdown() {
        ReminderScheduler.refresh(context, now = now)
        store.recordWater(250, now.plusMinutes(30))
        ReminderScheduler.refresh(context, recalculate = setOf("water"), now = now.plusMinutes(30))
        assertEquals(now.plusMinutes(90), store.timestamp("water.scheduled", now.zone))
    }

    @Test fun bothDueAlarmsActuallyPostNotificationsWithoutAnActivity() {
        ReminderScheduler.refresh(context, now = now)
        val due = now.plusHours(1)
        ReminderScheduler.deliver(context, "water", due.toInstant().toEpochMilli(), now = due)
        ReminderScheduler.deliver(context, "workout", due.toInstant().toEpochMilli(), now = due)
        assertEquals(setOf(10, 20), manager.activeNotifications.map { it.id }.toSet())
        assertEquals(due, store.timestamp("workout.notified", now.zone))
        assertEquals(due, store.timestamp("water.sent", now.zone))
    }

    @Test fun channelDisabledIsDetectedAndDoesNotConsumeWorkoutReminder() {
        ReminderScheduler.refresh(context, now = now)
        manager.deleteNotificationChannel(ReminderScheduler.channelId())
        manager.createNotificationChannel(NotificationChannel(ReminderScheduler.channelId(), "提醒", NotificationManager.IMPORTANCE_NONE))
        val due = now.plusHours(1)
        assertFalse(ReminderScheduler.notificationsAllowed(context))
        ReminderScheduler.deliver(context, "workout", due.toInstant().toEpochMilli(), now = due)
        assertNull(store.timestamp("workout.notified", now.zone))
        assertTrue(manager.activeNotifications.isEmpty())
        assertTrue(store.diagnostic("workout").contains("通知类别已关闭"))
    }

    @Test fun immediateTestsDoNotChangeGoalsRecordsOrNormalSchedule() {
        ReminderScheduler.refresh(context, now = now)
        val waterDue = store.timestamp("water.scheduled", now.zone)
        val workoutDue = store.timestamp("workout.scheduled", now.zone)
        assertTrue(ReminderScheduler.testNotification(context, "water"))
        assertTrue(ReminderScheduler.testNotification(context, "workout"))
        assertEquals(setOf(1010, 1020), manager.activeNotifications.map { it.id }.toSet())
        assertEquals(waterDue, store.timestamp("water.scheduled", now.zone))
        assertEquals(workoutDue, store.timestamp("workout.scheduled", now.zone))
        assertTrue(store.waterRecords(now.zone).isEmpty())
        assertTrue(store.workoutRecords(now.zone).isEmpty())
        assertNull(store.timestamp("workout.notified", now.zone))
    }

    @Test fun delayedTestReceiverPostsNotificationWithoutActivity() {
        assertNotNull(ReminderScheduler.scheduleTest(context, "water"))
        ReminderReceiver().onReceive(context, Intent(context, ReminderReceiver::class.java)
            .setAction("test.water").putExtra("kind", "water"))
        assertEquals(1010, manager.activeNotifications.single().id)
        assertTrue(store.diagnostic("water").contains("测试通知已提交"))
        assertNull(store.timestamp("water.anchor", now.zone))
    }

    @Test fun rebootRestorePreservesFutureDeadline() {
        ReminderScheduler.refresh(context, now = now)
        ReminderScheduler.refresh(context, restoreAlarms = true, now = now.plusMinutes(30))
        assertEquals(now.plusHours(1), store.timestamp("water.scheduled", now.zone))
    }
}
