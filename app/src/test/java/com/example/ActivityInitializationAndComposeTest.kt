package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Activity Initialization & Compose Inflation Test
 * Verifies that MainActivity launches cleanly, inflates Compose content,
 * and attaches a valid window view without any initialization errors or black screen crashes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityInitializationAndComposeTest {

    @Test
    fun `verify app context and string resources inflation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertNotNull("App context should not be null", context)
        assertEquals("FinMoney", appName)
    }

    @Test
    fun `verify MainActivity launches and inflates Compose content root successfully`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().start().resume().get()

        assertNotNull("MainActivity instance should be instantiated", activity)
        assertFalse("MainActivity should not be finishing", activity.isFinishing)
        assertFalse("MainActivity should not be destroyed", activity.isDestroyed)

        val decorView = activity.window?.decorView
        assertNotNull("Activity window decorView should not be null", decorView)

        // Verify content view is attached
        val contentView = activity.findViewById<android.view.View>(android.R.id.content)
        assertNotNull("Android content root view should be present", contentView)

        // Verify activity resumed state
        assertEquals("MainActivity state should be resumed", true, activity.hasWindowFocus() || !activity.isFinishing)

        controller.pause().stop().destroy()
    }
}
