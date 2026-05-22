package com.example.swiftaid

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SOSOverlayActivityLaunchTest {
    @Test
    fun sosOverlayLaunchesWithoutCrashing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, SOSOverlayActivity::class.java)
            .setAction(SOSOverlayActivity.ACTION_CRASH_CONFIRMED)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        activity.finish()
    }
}
