package com.sev7n.drinkexercise

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import android.widget.CheckBox
import android.widget.EditText
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ReminderStoreTest {
    private lateinit var store: ReminderStore
    private val now = ZonedDateTime.of(2026, 10, 8, 10, 0, 0, 0, ZoneId.of("Asia/Shanghai"))

    @Before fun setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
        store = ReminderStore(RuntimeEnvironment.getApplication())
    }

    @Test fun undoDrinkSurvivesReopeningAndRestoresInterval() {
        store.recordWater(100, now)
        store.recordWater(500, now.plusMinutes(10))
        val latest = store.waterRecords(now.zone).first()
        assertTrue(store.deleteWater(latest.id))
        val reopened = ReminderStore(RuntimeEnvironment.getApplication())
        assertEquals(100, reopened.totalWater(now))
        assertEquals(now, reopened.timestamp("water.anchor", now.zone))
        assertFalse(reopened.deleteWater(latest.id))
    }

    @Test fun undoDrinkPreservesNewerReminderAnchor() {
        store.recordWater(250, now)
        store.setTimestamp("water.lastReminder", now.plusHours(2))
        assertTrue(store.deleteWater(store.waterRecords(now.zone).first().id))
        assertEquals(now.plusHours(2), store.timestamp("water.anchor", now.zone))
    }

    @Test fun oldDrinkRecordsGainStableIdsWithoutLosingData() {
        RuntimeEnvironment.getApplication().getSharedPreferences("reminders", Context.MODE_PRIVATE).edit()
            .putString("water.records", "[{\"at\":${now.toInstant().toEpochMilli()},\"ml\":250}]").commit()
        val record = store.waterRecords(now.zone).single()
        assertEquals(record.id, store.waterRecords(now.zone).single().id)
        assertTrue(store.deleteWater(record.id))
        assertEquals(0, store.totalWater(now))
    }

    @Test fun workoutCompletionAndSkipCanBeReversed() {
        store.markWorkout(now, true)
        store.markWorkout(now, false)
        assertEquals("今日健身已跳过", store.workoutStatus(now.toLocalDate()))
        store.setTimestamp("workout.notified", now)
        store.resetWorkout(now.toLocalDate(), now.zone)
        assertFalse(now.toLocalDate() in store.workoutBlocked())
        assertNull(store.timestamp("workout.notified", now.zone))
    }

    @Test fun removingLastWorkoutReopensTodayWithoutDeletingOtherDays() {
        store.recordWorkout("跑步", 30, now.minusDays(1))
        store.recordWorkout("力量训练", 45, now)
        store.recordWorkout("拉伸", 10, now.plusMinutes(1))
        store.deleteWorkout(store.workoutRecords(now.zone).first().id, now.zone)
        assertTrue(now.toLocalDate() in store.workoutBlocked())
        store.deleteWorkout(store.workoutRecords(now.zone).first().id, now.zone)
        assertFalse(now.toLocalDate() in store.workoutBlocked())
        assertEquals("跑步", store.workoutRecords(now.zone).single().type)
        assertEquals(30, store.workoutRecords(now.zone).single().minutes)
    }

    @Test fun savingOneReminderDoesNotOverwriteOtherSettingsOrSnooze() {
        store.saveWorkout(WorkoutSettings(enabled = true))
        store.setTimestamp("workout.snooze", now.plusMinutes(15))
        store.saveWater(WaterSettings(enabled = true, intervalMinutes = 120))
        assertTrue(store.workout().enabled)
        assertEquals(now.plusMinutes(15), store.timestamp("workout.snooze", now.zone))
    }

    @Test fun invalidWaterInputDoesNotPreventWorkoutAutosave() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val interval = activity.findViewById<android.view.View>(android.R.id.content).findViewWithTag<EditText>("water.interval")
        interval.setText("")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val workout = activity.findViewById<android.view.View>(android.R.id.content).findViewWithTag<CheckBox>("workout.enabled")
        workout.isChecked = true
        assertTrue(store.workout().enabled)
        assertEquals(90, store.water().intervalMinutes)
        interval.setText("120")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(120, store.water().intervalMinutes)
    }
}
