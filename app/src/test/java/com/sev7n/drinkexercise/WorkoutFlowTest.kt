package com.sev7n.drinkexercise

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WorkoutFlowTest {
    private lateinit var activity: MainActivity
    private lateinit var store: ReminderStore
    private val root get() = activity.window.decorView

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
        store = ReminderStore(context)
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun click(root: View, label: String) {
        views(root).filterIsInstance<Button>().single { it.text.toString() == label }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun dialog() = ShadowAlertDialog.getLatestAlertDialog()
    private fun input(dialog: AlertDialog, tag: String) = dialog.window!!.decorView.findViewWithTag<EditText>(tag)
    private fun unit(dialog: AlertDialog, tag: String) = dialog.window!!.decorView.findViewWithTag<Spinner>(tag)
    private fun confirm(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun firstScreenHasEmptyGoalAreaAndPresetsOnlyInChoices() {
        val area = root.findViewWithTag<LinearLayout>("workout.targets")
        assertEquals(1, area.childCount)
        assertTrue(views(area).filterIsInstance<Button>().isEmpty())
        assertTrue(store.workoutGoals().isEmpty())
        assertEquals(7, store.workoutProjects().size)
    }

    @Test fun navigationKeepsRecordsAndMovesLibraryAndReminderControlsToSettings() {
        assertEquals(View.VISIBLE, root.findViewWithTag<View>("page.water").visibility)
        assertEquals(View.GONE, root.findViewWithTag<View>("page.workout").visibility)
        click(root, "100 毫升")
        click(root, "健身")
        assertEquals(View.VISIBLE, root.findViewWithTag<View>("page.workout").visibility)
        assertEquals(View.GONE, root.findViewWithTag<View>("page.water").visibility)
        assertTrue(views(root.findViewWithTag("page.workout")).filterIsInstance<Button>().none { it.text.toString().contains("自定义运动") })
        click(root, "设置")
        assertEquals(View.VISIBLE, root.findViewWithTag<View>("page.settings").visibility)
        assertNotNull(root.findViewWithTag<View>("page.settings").findViewWithTag<View>("water.interval"))
        click(root, "喝水")
        assertEquals(100, store.totalWater(ZonedDateTime.now()))
    }

    @Test fun customChoiceThenGoalRecordsOnlyConfiguredAmountAndAccumulates() {
        click(root, "设置")
        click(root, "添加自定义运动到项目库")
        val add = dialog()
        input(add, "project.name").setText("我的训练")
        assertNull(add.window!!.decorView.findViewWithTag<View>("goal.amount"))
        confirm(add)
        assertTrue(store.workoutGoals().isEmpty())
        assertTrue(store.workoutRecords(ZonedDateTime.now().zone).isEmpty())

        click(root, "健身")
        click(root, "设置运动目标")
        val goal = dialog()
        click(goal.window!!.decorView, "项目：速臂器锻炼")
        val choices = dialog()
        val index = store.workoutProjects().indexOfFirst { it.name == "我的训练" }
        choices.listView.performItemClick(null, index, index.toLong())
        unit(goal, "goal.unit").setSelection(WorkoutUnit.MINUTES.ordinal)
        input(goal, "goal.amount").setText("10")
        confirm(goal)
        assertEquals("我的训练", store.workoutGoals().single().name)

        repeat(2) {
            click(root.findViewWithTag("workout.targets"), "记录")
            val record = dialog()
            assertNull(record.window!!.decorView.findViewWithTag<View>("record.unit"))
            assertNull(record.window!!.decorView.findViewWithTag<View>("record.name"))
            input(record, "record.amount").setText("5")
            confirm(record)
        }
        val now = ZonedDateTime.now()
        assertEquals(listOf(WorkoutUnit.MINUTES, WorkoutUnit.MINUTES), store.workoutRecords(now.zone).map { it.unit })
        assertTrue(now.toLocalDate() in store.workoutBlocked(now.zone))
    }

    @Test fun freeRecordSelectsPresetWithOwnMeasurementWithoutCreatingGoal() {
        click(root, "健身")
        click(root, "记录其他运动（不设目标）")
        val record = dialog()
        click(record.window!!.decorView, "从项目库选择（预设／自定义）")
        val choices = dialog()
        val index = store.workoutProjects().indexOfFirst { it.name == "变式平板支撑" }
        choices.listView.performItemClick(null, index, index.toLong())
        assertEquals(WorkoutUnit.SECONDS.ordinal, unit(record, "record.unit").selectedItemPosition)
        input(record, "record.amount").setText("45")
        confirm(record)
        val saved = store.workoutRecords(ZonedDateTime.now().zone).single()
        assertEquals("变式平板支撑", saved.type)
        assertEquals(WorkoutUnit.SECONDS, saved.unit)
        assertNotNull(saved.projectId)
        assertTrue(store.workoutGoals().isEmpty())
    }

    @Test fun freeTypedRecordDoesNotCreateCustomChoice() {
        click(root, "健身")
        click(root, "记录其他运动（不设目标）")
        val record = dialog()
        input(record, "record.name").setText("爬楼梯")
        unit(record, "record.unit").setSelection(WorkoutUnit.MINUTES.ordinal)
        input(record, "record.amount").setText("8")
        confirm(record)
        assertEquals("爬楼梯", store.workoutRecords(ZonedDateTime.now().zone).single().type)
        assertTrue(store.workoutProjects().none { it.name == "爬楼梯" })
        assertTrue(store.workoutGoals().isEmpty())
    }
}
