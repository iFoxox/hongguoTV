package com.hongguotv.nativeapp

import android.app.Activity
import android.view.View
import android.widget.*
import com.hongguotv.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 26], qualifiers = "w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class RelatedWorksViewTest {
    private var priorTouch = false
    @Before fun remoteMode() { priorTouch = TvStyle.touch; TvStyle.touch = false }
    @After fun restoreMode() { TvStyle.touch = priorTouch }

    private fun descendants(v: View): List<View> =
        listOf(v) +
            if (v is android.view.ViewGroup)
                (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) }
            else emptyList()

    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 960, 540)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    @Test fun ninthSeasonStartsAtEighthWithoutStealingFocusAndKeepsEveryOfficialWork() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val episode = TextView(activity).apply { id = View.generateViewId(); isFocusable = true; isFocusableInTouchMode = true }
            val selected = mutableListOf<String>()
            val strip = RelatedWorksView(activity, listOf(episode), { _, _ -> }, { "登录后预约" },
                { work, _ -> selected += work.series.id }, {})
            val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; addView(episode); addView(strip) }
            activity.setContentView(root)
            episode.requestFocus()
            val rows = (1..35).map { n -> RelatedWork(Series("$n", "作品第${n}季"), "第${n}季", n == 34) }
                .filterNot { it.series.id == "9" } + RelatedWork(Series("ip", "同 IP 作品"), "同IP", false)
            strip.show(rows, Series("9", "作品第九季"))
            layout(root)
            assertSame(episode, root.findFocus())
            val views = descendants(strip)
            val previous = views.single { it.tag == "8" }
            val current = views.single { it.tag == "9" }
            val rail = previous.parent.parent as HorizontalScrollView
            assertEquals(36, views.count { it.tag is String })
            assertEquals(previous.left, rail.scrollX)
            assertEquals(previous.id, episode.nextFocusDownId)
            assertTrue(current.isSelected)
            assertFalse(current.isClickable)
            assertTrue(descendants(current).filterIsInstance<TextView>().any { it.text == "当前作品" })
            assertTrue(views.filterIsInstance<TextView>().any { it.text == "同IP" })
            assertTrue(views.filterIsInstance<TextView>().any { it.text == "待上线 · 登录后预约" })
            previous.requestFocus()
            assertEquals(current.id, previous.nextFocusRightId)
            current.requestFocus()
            current.performClick()
            assertTrue(selected.isEmpty())
            views.single { it.tag == "34" }.performClick()
            assertEquals(listOf("34"), selected)
            views.single { it.tag == "ip" }.performClick()
            assertEquals(listOf("34", "ip"), selected)
            val first = views.single { it.tag == "1" }
            val last = views.single { it.tag == "ip" }
            assertEquals(first.id, first.nextFocusLeftId)
            assertEquals(last.id, last.nextFocusRightId)
            // 刷新期间用户返回选集，完成回调不能把焦点抢回相关作品。
            strip.loading()
            episode.requestFocus()
            strip.show(rows, Series("9", "作品第九季")); layout(root)
            assertSame(episode, root.findFocus())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun refreshAndFailureKeepAReachableFocusAndReturnToBrowsedCard() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val episode = TextView(activity).apply { id = View.generateViewId(); isFocusable = true }
            val strip = RelatedWorksView(activity, listOf(episode), { _, _ -> }, { "预约" }, { _, _ -> }, {})
            activity.setContentView(strip)
            val rows = (1..12).map { n -> RelatedWork(Series("$n", "作品第${n}季"), "第${n}季", false) }
            strip.show(rows, rows[8].series); layout(strip)
            val browsed = descendants(strip).single { it.tag == "11" }
            browsed.requestFocus()
            val refresh = descendants(strip).filterIsInstance<TextView>().single { it.text == "刷新" }
            assertEquals(refresh.id, browsed.nextFocusUpId)
            refresh.requestFocus()
            assertEquals(browsed.id, refresh.nextFocusDownId)
            strip.loading(); layout(strip)
            assertEquals("刷新", (strip.findFocus() as TextView).text)
            strip.failed(); layout(strip)
            assertEquals("刷新", (strip.findFocus() as TextView).text)
            assertEquals(strip.findFocus()!!.id, episode.nextFocusDownId)
            strip.loading()
            strip.show(rows, rows[8].series); layout(strip)
            assertEquals("11", strip.findFocus()!!.tag)
            strip.show(emptyList(), rows[8].series); layout(strip)
            assertEquals("刷新", (strip.findFocus() as TextView).text)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun uncertainCurrentSeasonFallsBackToStartAndDoesNotInterpretSameIpAsSeason() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val rows = listOf(RelatedWork(Series("ip", "同 IP"), "同IP", false),
                RelatedWork(Series("other", "其他作品"), "", false))
            val current = Series("9", "作品前篇")
            val strip = RelatedWorksView(controller.get(), emptyList(), { _, _ -> }, { "预约" }, { _, _ -> }, {})
            controller.get().setContentView(strip)
            strip.show(rows, current); layout(strip)
            val views = descendants(strip)
            val card = views.single { it.tag == "9" }
            assertEquals(0, (card.parent.parent as HorizontalScrollView).scrollX)
            assertEquals(0, relatedStartIndex(rows + RelatedWork(current, "同IP", false), current.id))
            assertEquals(0, relatedStartIndex(rows + RelatedWork(current, "第9季", false), current.id))
            assertFalse(descendants(card).filterIsInstance<TextView>().any { it.text.startsWith("第") })
        } finally { controller.pause().stop().destroy() }
    }

    @Test
    fun upcomingCardsUseReservationActionAndRemoteCanReachStripFromEpisodes() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val episode = TextView(activity.get()).apply { id = View.generateViewId() }
            val current = RelatedWork(Series("123", "第二季"), "第2季", false)
            val upcoming = RelatedWork(Series("456", "第三季"), "第3季", true)
            var selected: RelatedWork? = null
            var label = "登录后预约"
            var reloads = 0
            val strip =
                RelatedWorksView(
                    activity.get(),
                    listOf(episode),
                    { _, _ -> },
                    { label },
                    { work, _ -> selected = work },
                    { reloads++ },
                )
            activity.get().setContentView(strip)
            strip.show(listOf(current, upcoming))
            val views = descendants(strip)
            val released = views.single { it.tag == "123" }
            val future = views.single { it.tag == "456" }
            assertEquals(released.id, episode.nextFocusDownId)
            assertEquals(future.id, released.nextFocusRightId)
            val refresh = views.filterIsInstance<TextView>().single { it.text.toString() == "刷新" }
            assertEquals(refresh.id, future.nextFocusUpId)
            assertEquals(episode.id, refresh.nextFocusUpId)
            future.performClick()
            assertEquals(upcoming, selected)
            assertTrue(selected!!.upcoming)
            label = "已预约"
            strip.refreshLabels()
            assertTrue(
                descendants(strip).filterIsInstance<TextView>().any { it.text.toString() == "待上线 · 已预约" }
            )
            strip.failed()
            descendants(strip)
                .filterIsInstance<TextView>()
                .single { it.text.toString() == "刷新" }
                .performClick()
            assertEquals(1, reloads)
        } finally {
            activity.pause().stop().destroy()
        }
    }
}
