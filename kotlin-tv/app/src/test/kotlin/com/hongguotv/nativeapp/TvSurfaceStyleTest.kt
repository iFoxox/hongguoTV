// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 26], qualifiers = "w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class TvSurfaceStyleTest {
    private var previousTouch = false
    private var previousMode = TvStyle.Mode.LIGHT

    @Before
    fun tvMode() {
        previousTouch = TvStyle.touch
        previousMode = TvStyle.mode
        TvStyle.touch = false
        InteractionMode.save(RuntimeEnvironment.getApplication(), InteractionMode.Mode.TV)
    }

    @After
    fun restore() {
        TvStyle.touch = previousTouch
        TvStyle.apply(previousMode)
    }

    private fun lastStroke(drawable: Drawable): android.graphics.Paint {
        val bitmap = Bitmap.createBitmap(180, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, 180, 80)
        drawable.draw(canvas)
        val paint = android.graphics.Paint(Shadows.shadowOf(canvas).lastDrawnRoundRect.paint)
        bitmap.recycle()
        return paint
    }

    @Test
    fun bothThemesShareSurfacesAndTvFocusIsVisibleForButtonsAndGridCards() {
        val context = RuntimeEnvironment.getApplication()
        TvStyle.Mode.entries.forEach { mode ->
            TvStyle.apply(mode)
            assertEquals(SurfaceStyle.background, TvStyle.background)
            assertEquals(SurfaceStyle.surface, TvStyle.surface)
            val button =
                TextView(context).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
            SurfaceStyle.button(button)
            val normal = lastStroke(button.background)
            button.background.state =
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused)
            val focused = lastStroke(button.background)
            assertNotEquals(normal.color, focused.color)
            assertEquals(TvStyle.accent, focused.color)
            assertTrue(focused.strokeWidth >= 3f * context.resources.displayMetrics.density)
            assertTrue(button.isFocusableInTouchMode)
            val gridCard = TvStyle.card(context, true)
            assertEquals(TvStyle.accent, lastStroke(gridCard).color)
            TvStyle.touch = true
            val phoneCard = TvStyle.card(context, true)
            assertNotEquals(TvStyle.accent, lastStroke(phoneCard).color)
            TvStyle.touch = false
        }
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-mdpi")
    fun narrowTvPutsHistoryNextToHomeAndOpensHistoryDirectly() {
        val c = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = c.get()
            val library =
                MainActivity::class
                    .java
                    .getDeclaredField("library")
                    .apply { isAccessible = true }
                    .get(activity) as Library
            val loader =
                Library::class
                    .java
                    .getDeclaredField("loader")
                    .apply { isAccessible = true }
                    .get(library) as java.util.concurrent.ExecutorService
            loader.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            MainActivity::class
                .java
                .getDeclaredMethod("catalogPage")
                .apply { isAccessible = true }
                .invoke(activity)
            val nav =
                MainActivity::class
                    .java
                    .getDeclaredField("nav")
                    .apply { isAccessible = true }
                    .get(activity) as List<*>
            val decor = activity.window.decorView
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, 640, 360)
            val home = nav[0] as View
            val history = nav[4] as TextView
            val find = nav[1] as View
            assertFalse(TvStyle.sideNav)
            val page = home.parent.parent.parent.parent as View
            assertEquals(8, page.paddingTop)
            assertEquals(8, page.paddingBottom)
            assertEquals("观看历史", history.text.toString())
            assertTrue(history.left > home.left && history.left < find.left)
            assertSame(home, history.focusSearch(View.FOCUS_LEFT))
            history.performClick()
            assertEquals(
                4,
                MainActivity::class
                    .java
                    .getDeclaredField("tab")
                    .apply { isAccessible = true }
                    .getInt(activity),
            )
            val root = activity.findViewById<View>(android.R.id.content)
            fun labels(v: View): List<String> =
                if (v is ViewGroup) (0 until v.childCount).flatMap { labels(v.getChildAt(it)) }
                else if (v is TextView) listOf(v.text.toString()) else emptyList()
            assertTrue(labels(root).contains("本机历史"))
            assertTrue(labels(root).contains("账户历史"))
        } finally {
            c.destroy()
        }
    }

    @Test
    fun televisionKeepsSevenTopDestinationsAndDpadFocusTraversal() {
        val c = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = c.get()
            MainActivity::class
                .java
                .getDeclaredMethod("catalogPage")
                .apply { isAccessible = true }
                .invoke(activity)
            val nav =
                MainActivity::class
                    .java
                    .getDeclaredField("nav")
                    .apply { isAccessible = true }
                    .get(activity) as List<*>
            assertEquals(7, nav.size)
            assertFalse(TvStyle.touch)
            assertFalse(TvStyle.sideNav)
            val decor = activity.window.decorView
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, 960, 540)
            val home = nav[0] as View
            val history = nav[4] as View
            val find = nav[1] as View
            val ranking = nav[2] as View
            assertTrue(home.requestFocus())
            assertSame(history, home.focusSearch(View.FOCUS_RIGHT))
            assertTrue(history.requestFocus())
            assertSame(find, history.focusSearch(View.FOCUS_RIGHT))
            assertTrue(find.requestFocus())
            assertSame(ranking, find.focusSearch(View.FOCUS_RIGHT))
            assertTrue(ranking.requestFocus())
            assertEquals(TvStyle.accent, lastStroke(ranking.background).color)
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            assertFalse(root.getChildAt(0) is MobileCatalogShell)
        } finally {
            c.destroy()
        }
    }
}
