// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23,26], qualifiers = "w360dp-h640dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class InteractionModeTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var originalTouch = false

    @Before fun phoneWithoutTouchscreenFeature() {
        originalTouch = TvStyle.touch
        InteractionMode.save(context, InteractionMode.Mode.AUTO)
        val pm = Shadows.shadowOf(context.packageManager)
        pm.setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN,false)
        pm.setSystemFeature(PackageManager.FEATURE_FAKETOUCH,false)
        pm.setSystemFeature(PackageManager.FEATURE_TELEVISION,false)
        val config = Configuration(context.resources.configuration).apply {
            touchscreen = Configuration.TOUCHSCREEN_FINGER
            uiMode = Configuration.UI_MODE_TYPE_NORMAL
        }
        context.resources.updateConfiguration(config,context.resources.displayMetrics)
    }

    @After fun restore() { TvStyle.touch = originalTouch }

    @Test fun missingHardwareFeatureStillSelectsPhoneFromConfiguration() {
        assertFalse(context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))
        assertTrue(InteractionMode.useMobile(context))
    }

    @Test fun televisionDoesNotBecomePhoneJustBecauseItHasTouchCapability() {
        val pm = Shadows.shadowOf(context.packageManager)
        pm.setSystemFeature(PackageManager.FEATURE_TELEVISION,true)
        pm.setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN,true)
        assertFalse(InteractionMode.useMobile(context))
    }

    @Test fun manualSelectionOverridesDetectionAndPersistsAcrossContexts() {
        InteractionMode.save(context,InteractionMode.Mode.TV)
        val c = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            assertEquals(InteractionMode.Mode.TV,InteractionMode.selected(c.get()))
            assertFalse(InteractionMode.useMobile(c.get()))
            Shadows.shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEVISION,true)
            InteractionMode.save(c.get(),InteractionMode.Mode.MOBILE)
            assertTrue(InteractionMode.useMobile(context))
        } finally { c.pause().stop().destroy() }
    }

    @Test fun automaticPhoneStartupActuallyMountsFourBottomDestinations() {
        val c = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = c.get()
            assertTrue(TvStyle.touch)
            MainActivity::class.java.getDeclaredMethod("catalogPage").apply { isAccessible = true }.invoke(activity)
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
            assertTrue(root.getChildAt(0) is MobileCatalogShell)
            val shell = root.getChildAt(0) as MobileCatalogShell
            val bar = shell.getChildAt(shell.childCount-1) as ViewGroup
            val labels = (0 until bar.childCount).map { ((bar.getChildAt(it) as ViewGroup).getChildAt(1) as TextView).text.toString() }
            assertEquals(listOf("首页","观看历史","排行榜","我的"),labels)
        } finally { c.destroy() }
    }

    @Test fun phoneAndTvSettingsBothExposeTheInterfaceModeControl() {
        for (mode in listOf(InteractionMode.Mode.MOBILE, InteractionMode.Mode.TV)) {
            InteractionMode.save(context, mode)
            val controller = Robolectric.buildActivity(MainActivity::class.java).create()
            try {
                val activity = controller.get()
                val library = MainActivity::class.java.getDeclaredField("library")
                    .apply { isAccessible = true }.get(activity) as Library
                val loader = Library::class.java.getDeclaredField("loader")
                    .apply { isAccessible = true }.get(library) as java.util.concurrent.ExecutorService
                loader.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                val parent = MainActivity::class.java.getDeclaredMethod("catalogPage")
                    .apply { isAccessible = true }.invoke(activity) as android.widget.LinearLayout
                MainActivity::class.java.getDeclaredMethod("settings", android.widget.LinearLayout::class.java)
                    .apply { isAccessible = true }.invoke(activity, parent)
                fun findMode(view: View): TextView? {
                    if (view is TextView && view.text.startsWith("界面模式")) return view
                    if (view is ViewGroup) for (index in 0 until view.childCount) {
                        findMode(view.getChildAt(index))?.let { return it }
                    }
                    return null
                }
                val control = findMode(parent)
                assertNotNull("Missing interface mode setting for $mode", control)
                assertTrue(control!!.hasOnClickListeners())
            } finally { controller.destroy() }
        }
    }
}
