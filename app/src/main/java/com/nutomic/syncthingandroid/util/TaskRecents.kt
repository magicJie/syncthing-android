package com.nutomic.syncthingandroid.util

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.nutomic.syncthingandroid.service.Constants

/** Changes task visibility while keeping activities and background synchronization alive. */
internal object TaskRecents {
    @JvmStatic
    fun watch(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            // Application retains these callbacks, which retain the preference listener.
            private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key == Constants.PREF_HIDE_FROM_RECENTS) {
                    apply(app)
                }
            }

            init {
                PreferenceManager.getDefaultSharedPreferences(app)
                    .registerOnSharedPreferenceChangeListener(listener)
            }

            override fun onActivityStarted(activity: Activity) = apply(activity)
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    @JvmStatic
    fun apply(context: Context) {
        val hidden = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(Constants.PREF_HIDE_FROM_RECENTS, false)
        for (task in context.getSystemService(ActivityManager::class.java).appTasks) {
            task.setExcludeFromRecents(hidden)
        }
    }
}
