// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.hongguotv.core.DiscoveryBrowserState
import com.hongguotv.core.DiscoveryFilter
import com.hongguotv.core.DiscoveryGroup
import com.hongguotv.core.Series
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

/** 手机 / 平板的自适应规则：列数、搜索页单列布局与触屏文案。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 26], qualifiers = "w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class MobileAdaptationTest {
    private var previousTouch = false

    @Before fun useTouch() {
        previousTouch = TvStyle.touch
        TvStyle.touch = true
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication().packageManager)
            .setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN, true)
    }

    @After fun restoreTouch() { TvStyle.touch = previousTouch }

    @Test
    @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    fun smallPhoneSearchKeepsTouchTargetsAndResultSpaceWithLargeText() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val configuration = android.content.res.Configuration(activity.resources.configuration)
        configuration.fontScale = 1.3f
        activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
        val upper = TextView(activity).apply { id = View.generateViewId() }
        val panel = TvSearchPanel(activity, "", emptyList(), upper, { emptyList() }, {}, {}, {})
        try {
            activity.setContentView(panel)
            panel.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            panel.layout(0, 0, 320, 400)
            val actions = texts(panel).filter { it.isClickable && it !is EditText }
            assertTrue(actions.isNotEmpty())
            actions.forEach {
                assertTrue("${it.text}: height ${it.height}", it.height >= 48)
                assertTrue("${it.text}: width ${it.width}", it.width >= 48)
            }
            assertTrue(panel.results.height >= 120)
        } finally {
            panel.suggestions.close()
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun episodeWindowResizesWithoutLosingSelectedGroup() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val panel = EpisodePanel(activity, 100, 45, { _, _, _ -> }, {}, {})
        try {
            panel.show()
            val dialog = ShadowDialog.getLatestDialog()
            val content = dialog.findViewById<View>(android.R.id.content)
            val portraitWidth = dialog.window!!.attributes.width
            texts(content).filter { it.isClickable }.forEach { assertTrue(it.minHeight >= 48) }
            RuntimeEnvironment.setQualifiers("w640dp-h360dp-land-mdpi")
            panel.onWindowChanged()
            assertSame(dialog, ShadowDialog.getLatestDialog())
            assertSame(content, dialog.findViewById<View>(android.R.id.content))
            assertTrue(dialog.window!!.attributes.width > portraitWidth)
            assertTrue(dialog.window!!.attributes.height <= 360)
            assertTrue(texts(content).any { it.text.toString().contains("41—60") })
        } finally {
            panel.dismiss()
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun commentWindowRotatesInPlaceWithoutReloading() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val completed = java.util.concurrent.CountDownLatch(1)
        val social = TvSocial(activity, { null }, { "1" to "2" }, { _, _, _ ->
            reads.incrementAndGet()
            completed.countDown()
            org.json.JSONObject().put("items", org.json.JSONArray())
                .put("total", 0).put("hasMore", false).put("nextCursor", "")
        })
        try {
            social.showComments(TextView(activity))
            assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val dialog = ShadowDialog.getLatestDialog()
            val content = dialog.findViewById<View>(android.R.id.content)
            assertEquals(android.view.Gravity.BOTTOM, dialog.window!!.attributes.gravity)
            RuntimeEnvironment.setQualifiers("w640dp-h360dp-land-mdpi")
            social.onWindowChanged()
            assertSame(content, dialog.findViewById<View>(android.R.id.content))
            assertEquals(android.view.Gravity.END, dialog.window!!.attributes.gravity)
            assertEquals(416, dialog.window!!.attributes.width)
            assertEquals(1, reads.get())
            dialog.dismiss()
        } finally {
            social.destroy()
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    fun playbackActionsRemainScrollableAndTouchSized() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        // 仅搭建控制层，不请求视频或启动播放器。
        val controls = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        MainActivity::class.java.getDeclaredField("controls").apply { isAccessible = true }
            .set(activity, controls)
        try {
            TvStyle.touch = true
            MainActivity::class.java.getDeclaredMethod("showPanel").apply { isAccessible = true }
                .invoke(activity)
            assertEquals(2, views(controls).filterIsInstance<HorizontalScrollView>().size)
            val actions = texts(controls).filter { it.isClickable }
            assertEquals(8, actions.size)
            actions.forEach { assertTrue(it.minHeight >= 48) }
            assertTrue(actions.any { it.text.toString() == "退出" })
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    fun activityFullscreenActionAndBackRestoreOrientationWithoutReplacingPlayerView() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity=controller.get()
        try {
            TvStyle.touch=true
            val library=MainActivity::class.java.getDeclaredField("library").apply { isAccessible=true }.get(activity) as Library
            val loader=Library::class.java.getDeclaredField("loader").apply { isAccessible=true }.get(library) as java.util.concurrent.ExecutorService
            loader.submit {}.get(5,java.util.concurrent.TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            val screen=MainActivity::class.java.getDeclaredField("screen").apply { isAccessible=true }
            screen.set(activity,"player")
            activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            MainActivity::class.java.getDeclaredMethod("setupMobilePlayback",Boolean::class.javaPrimitiveType)
                .apply { isAccessible=true }.invoke(activity,false)
            val playerField=MainActivity::class.java.getDeclaredField("mobilePlayer").apply { isAccessible=true }
            val player=playerField.get(activity) as MobilePlayerView
            val video=player.video.getChildAt(0)
            texts(player).single { it.text=="竖屏全屏" }.performClick()
            assertTrue(player.fullscreen)
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,activity.requestedOrientation)
            MainActivity::class.java.getDeclaredMethod("goBack").apply { isAccessible=true }.invoke(activity)
            assertFalse(player.fullscreen)
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,activity.requestedOrientation)
            texts(player).single { it.text=="横屏全屏" }.performClick()
            assertTrue(player.fullscreen)
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,activity.requestedOrientation)
            MainActivity::class.java.getDeclaredMethod("goBack").apply { isAccessible=true }.invoke(activity)
            assertFalse(player.fullscreen)
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,activity.requestedOrientation)
            assertSame(player,playerField.get(activity))
            assertSame(video,player.video.getChildAt(0))
            texts(player).single { it.text=="横屏全屏" }.performClick()
            val release=MainActivity::class.java.getDeclaredMethod("releasePlayer",Boolean::class.javaPrimitiveType).apply { isAccessible=true }
            release.invoke(activity,true) // 重建播放页时保留横屏与原始方向。
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,activity.requestedOrientation)
            release.invoke(activity,false) // 离开播放页必须恢复原始方向。
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,activity.requestedOrientation)
        } finally { controller.pause().stop().destroy() }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun keyboardAndSafeAreaInsetsUpdatePaddingWithoutReplacingInput() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val root = MainActivity::class.java.getDeclaredField("root").apply { isAccessible = true }
            .get(activity) as FrameLayout
        val input = EditText(activity)
        root.addView(input)
        input.requestFocus()
        try {
            MainActivity::class.java.getDeclaredMethod("trackPageInset", View::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(activity, input, 10, 12, 10, 12)
            // API 23/26 的 Robolectric 不模拟真实刘海/IME；验证收到边距后的原地更新。
            val bars = androidx.core.graphics.Insets.of(18, 24, 0, 24)
            val applyInsets = MainActivity::class.java.getDeclaredMethod("updateWindowInsets",
                androidx.core.graphics.Insets::class.java, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
            applyInsets.invoke(activity, bars, 240)
            assertEquals(28, input.paddingLeft)
            assertEquals(36, input.paddingTop)
            assertEquals(252, input.paddingBottom)
            assertTrue(input.hasFocus())
            assertSame(root, input.parent)
            applyInsets.invoke(activity, bars, 0)
            assertEquals(36, input.paddingBottom)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun danmakuSettingsStayScrollableInLandscape() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val social = TvSocial(activity, { null }, { null })
        try {
            social.settings(TextView(activity))
            val dialog = ShadowDialog.getLatestDialog()
            val content = dialog.findViewById<View>(android.R.id.content)
            assertEquals(1, views(content).filterIsInstance<ScrollView>().size)
            RuntimeEnvironment.setQualifiers("w640dp-h360dp-land-mdpi")
            social.onWindowChanged()
            assertTrue(dialog.window!!.attributes.height <= 360)
            assertSame(content, dialog.findViewById<View>(android.R.id.content))
            assertTrue(texts(content).any { it.text.toString() == "关闭" })
        } finally {
            social.destroy()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun posterColumnsFollowAvailableWidth() {
        assertEquals(3, TvStyle.posterColumns(360))
        assertEquals(3, TvStyle.posterColumns(519))
        assertEquals(4, TvStyle.posterColumns(520))
        assertEquals(4, TvStyle.posterColumns(899))
        assertEquals(6, TvStyle.posterColumns(900))
        assertEquals(6, TvStyle.posterColumns(1280))
    }

    @Test
    fun touchDevicesReadTouchHintsAndNarrowWidthsUseSmallerLayouts() {
        val previous = TvStyle.touch
        try {
            TvStyle.touch = true
            assertEquals("点按继续", TvStyle.hint("按确认继续", "点按继续"))
            TvStyle.touch = false
            assertEquals("按确认继续", TvStyle.hint("按确认继续", "点按继续"))
            assertTrue(TvStyle.isNarrow(360))
            assertFalse(TvStyle.isNarrow(700))
        } finally {
            TvStyle.touch = previous
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun phoneDiscoveryKeepsThreePosterColumns() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val state =
            DiscoveryBrowserState().apply {
                loaded = true
                groups =
                    listOf(
                        DiscoveryGroup(
                            "genre",
                            "类型",
                            listOf(DiscoveryFilter("short_play", "真人剧")),
                        )
                    )
                items = (1..12).map { Series("$it", "作品$it") }
            }
        val upper =
            TextView(activity).apply {
                id = View.generateViewId()
                isFocusable = true
            }
        val screen =
            DiscoveryScreen(
                activity,
                state,
                upper,
                { _, _ -> },
                {},
                { _, _ -> },
                { _, _, _ -> },
            )
        try {
            activity.setContentView(screen)
            screen.start(false)
            screen.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            )
            screen.layout(0, 0, 360, 600)
            ShadowLooper.idleMainLooper()
            val browser = views(screen).filterIsInstance<ContinuousCatalogView>().single()
            assertEquals(3, browser.grid.numColumns)
            assertTrue(browser.grid.width <= 360)
        } finally {
            screen.close()
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun phoneSearchPanelStacksInputAboveResults() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val upper =
            TextView(activity).apply {
                id = View.generateViewId()
                isFocusable = true
            }
        var searched = ""
        val panel =
            TvSearchPanel(
                activity,
                "测试",
                listOf("最近剧名"),
                upper,
                { emptyList() },
                { searched = it },
                {},
                {},
            )
        try {
            activity.setContentView(panel)
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            )
            panel.layout(0, 0, 360, 600)
            ShadowLooper.idleMainLooper()
            assertEquals(LinearLayout.VERTICAL, panel.orientation)
            assertTrue(panel.results.width >= 320)
            assertEquals(panel.input.id, panel.entry().id)
            // 触屏使用系统输入法，遥控器专用的两个入口不再出现在手机布局里。
            val labels = texts(panel).map { it.text.toString() }
            assertTrue(labels.contains("清空"))
            assertTrue(labels.contains("返回分类"))
            assertFalse(labels.contains("手机输入"))
            assertFalse(labels.contains("屏幕键盘"))
            panel.input.performClick()
            texts(panel).first { it.text.toString() == "搜索" }.performClick()
            assertEquals("测试", searched)
        } finally {
            panel.suggestions.close()
            controller.pause().stop().destroy()
        }
    }

    private fun texts(view: View): List<TextView> =
        (if (view is TextView) listOf(view) else emptyList()) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            else emptyList()

    private fun views(view: View): List<View> =
        listOf(view) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
            else emptyList())
}
