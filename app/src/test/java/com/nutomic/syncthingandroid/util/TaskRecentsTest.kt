package com.nutomic.syncthingandroid.util

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import java.io.File
import java.io.ObjectOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppTask
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 35])
class TaskRecentsTest {
    private lateinit var app: Application
    private lateinit var preferences: SharedPreferences
    private lateinit var manager: ActivityManager

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        preferences = PreferenceManager.getDefaultSharedPreferences(app)
        manager = app.getSystemService(ActivityManager::class.java)
    }

    @Test
    fun defaultAndSavedPreferenceApplyToEveryNewTask() {
        TaskRecents.watch(app)
        val first = ShadowAppTask.newInstance()
        first.setExcludeFromRecents(true)
        shadowOf(manager).setAppTasks(listOf(first))
        Robolectric.buildActivity(Activity::class.java).setup().use {
            assertFalse(shadowOf(first).isExcludedFromRecents)
            preferences.edit().putBoolean(Constants.PREF_HIDE_FROM_RECENTS, true).commit()
            ShadowLooper.idleMainLooper()

            val next = ShadowAppTask.newInstance()
            shadowOf(manager).setAppTasks(listOf(first, next))
            Robolectric.buildActivity(Activity::class.java).setup().use {
                assertTrue(shadowOf(first).isExcludedFromRecents)
                assertTrue(shadowOf(next).isExcludedFromRecents)
            }
        }
    }

    @Test
    fun togglingUpdatesAllTasksWithoutFinishingActivities() {
        TaskRecents.watch(app)
        val tasks = listOf(ShadowAppTask.newInstance(), ShadowAppTask.newInstance())
        shadowOf(manager).setAppTasks(tasks)
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            preferences.edit().putBoolean(Constants.PREF_HIDE_FROM_RECENTS, true).commit()
            ShadowLooper.idleMainLooper()
            tasks.forEach { assertTrue(shadowOf(it).isExcludedFromRecents) }
            assertFalse(controller.get().isFinishing)

            preferences.edit().putBoolean(Constants.PREF_HIDE_FROM_RECENTS, false).commit()
            ShadowLooper.idleMainLooper()
            tasks.forEach {
                assertFalse(shadowOf(it).isExcludedFromRecents)
                assertFalse(shadowOf(it).isFinishedAndRemoved)
            }
            assertFalse(controller.get().isFinishing)
        }
    }

    @Test
    fun coldLaunchAppliesSavedPreference() {
        preferences.edit().putBoolean(Constants.PREF_HIDE_FROM_RECENTS, true).commit()
        TaskRecents.watch(app)
        val task = ShadowAppTask.newInstance()
        shadowOf(manager).setAppTasks(listOf(task))
        Robolectric.buildActivity(Activity::class.java).setup().use {
            assertTrue(shadowOf(task).isExcludedFromRecents)
        }
    }

    @Test
    fun importingPreferencesAppliesTrueFalseAndMissingKeyFromOldBackup() {
        TaskRecents.watch(app)
        val task = ShadowAppTask.newInstance()
        shadowOf(manager).setAppTasks(listOf(task))
        val service = Robolectric.buildService(SyncthingService::class.java).get()
        ReflectionHelpers.setField(service, "mPreferences", preferences)
        val backup = File(app.cacheDir, "recents-preferences.bin")
        try {
            for (hidden in listOf(true, false, true, null)) {
                val values = HashMap<String, Any>()
                if (hidden != null) values[Constants.PREF_HIDE_FROM_RECENTS] = hidden
                ObjectOutputStream(backup.outputStream()).use { it.writeObject(values) }
                assertTrue(ReflectionHelpers.callInstanceMethod(
                    service,
                    "importConfigSharedPrefs",
                    ClassParameter.from(File::class.java, backup),
                ))
                ShadowLooper.idleMainLooper()
                assertTrue(shadowOf(task).isExcludedFromRecents == (hidden == true))
            }
        } finally {
            backup.delete()
        }
    }
}
