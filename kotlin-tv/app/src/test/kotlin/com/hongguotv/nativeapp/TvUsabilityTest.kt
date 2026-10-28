package com.hongguotv.nativeapp

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.hongguotv.core.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 26], qualifiers = "w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class TvUsabilityTest {
    private fun get(target: Any, name: String) =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    private fun set(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    private fun texts(view: View): List<TextView> =
        if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else if (view is TextView) listOf(view) else emptyList()

    @Test fun playbackRelatedEntryOpensDetailAtPreviousSeasonEvenFromDirectPlayback() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val priorTouch = TvStyle.touch
        try {
            TvStyle.touch = false
            val library = get(activity, "library") as Library
            (get(library, "loader") as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
            (get(get(activity, "cloudAccount")!!, "worker") as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            val rows = (1..12).joinToString(",") { n ->
                """{"series_id":"$n","title":"作品第${n}季","content_type":1004,"episode_cnt":40,"season_index":$n}"""
            }
            set(activity, "repository", ContentRepository(OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body("""{"code":0,"data":[{"show_type":547,"video_data":[$rows]}]}""".toResponseBody()).build()
            }.build()))
            val current = Series("9", "作品第九季")
            val data = Detail(current, (1..40).map { "e$it" })
            library.save(WatchProgress(current, "e25", 24, 12000, 90000, false, 1000))
            set(activity, "detail", data); set(activity, "episodeIndex", 24); set(activity, "group", 1)
            set(activity, "directPlayback", true)
            val controls = android.widget.LinearLayout(activity)
            set(activity, "controls", controls)
            MainActivity::class.java.getDeclaredMethod("showPanel").apply { isAccessible = true }.invoke(activity)
            val actions = texts(controls)
            val entry = actions.single { it.text == "相关作品" }
            val episodes = actions.single { it.text == "选集" }
            assertEquals(entry.id, episodes.nextFocusRightId)
            assertEquals(episodes.id, entry.nextFocusLeftId)
            set(activity, "foreground", true)
            entry.performClick()
            assertEquals("detail", get(activity, "screen"))
            assertEquals(false, get(activity, "directPlayback"))
            assertEquals(24, get(activity, "episodeIndex"))
            assertEquals(12000L, library.progress("9")!!.position)
            val section = get(activity, "relatedSection") as RelatedWorksView
            assertNotNull(section.findFocus())
            (get(activity, "workJob") as java.util.concurrent.Future<*>).get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            val root = activity.findViewById<View>(android.R.id.content)
            root.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY))
            root.layout(0,0,960,540); ShadowLooper.idleMainLooper()
            assertEquals(texts(section).joinToString { it.text }, "8", section.findFocus()!!.tag)
            assertTrue(texts(root).any { it.text == "当前作品" })
            assertTrue(texts(root).any { it.text == "继续第 25 集" })
        } finally { TvStyle.touch = priorTouch; controller.destroy() }
    }

    @Test
    fun detailKeepsPlaybackCommentsAndSeparateSecondaryActions() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        try {
            val library = get(activity, "library") as Library
            (get(library, "loader") as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
            val account = get(activity, "cloudAccount") as AccountController
            (get(account, "worker") as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            set(
                activity,
                "repository",
                ContentRepository(
                    OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(404)
                                .message("fixture")
                                .body("".toResponseBody())
                                .build()
                        }
                        .build()
                ),
            )
            val series = Series("1", "详情测试剧", description = "很长的简介。".repeat(50))
            val data = Detail(series, (1..40).map { "$it" })
            library.save(WatchProgress(series, "25", 24, 12000, 90000, false, 1000))
            set(activity, "detail", data)
            set(activity, "episodeIndex", 24)
            set(activity, "group", 1)
            MainActivity::class
                .java
                .getDeclaredMethod("showDetail", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(activity, false)
            val root = activity.findViewById<View>(android.R.id.content)
            val rows = texts(root)
            val play = rows.first { it.text == "继续第 25 集" }
            val comments = rows.first { it.text == "评论 · 第 25 集" }
            assertFalse(rows.any { it.text.toString().startsWith("选集 · 共") })
            assertSame(play.parent, comments.parent)
            assertEquals(comments.id, play.nextFocusRightId)
            assertEquals(play.id, comments.nextFocusLeftId)
            assertNotSame(play.parent, rows.first { it.text == "加入本机收藏" }.parent)
            assertEquals(2, rows.first { it.text.toString().startsWith("很长的简介") }.maxLines)
            assertEquals(1, get(activity, "group"))
            val selected = texts(root).first { it.text == "25\n当前" }
            assertTrue(selected.isSelected)
            assertTrue(rows.any { it.text == "跳转集数" })
            assertEquals("detail", get(activity, "screen"))
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun settingsSeparatesAccountDataAndUpdatesWithValidFocusLinks() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        try {
            val library = get(activity, "library") as Library
            (get(library, "loader") as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
            (get(get(activity, "cloudAccount")!!, "worker") as ExecutorService)
                .submit {}
                .get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            MainActivity::class
                .java
                .getDeclaredMethod("switchTab", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(activity, 6)
            val root = activity.findViewById<View>(android.R.id.content)
            val rows = texts(root)
            listOf("播放偏好", "红果账户", "应用与更新").forEach { title ->
                assertTrue(rows.any { it.text == title })
            }
            assertFalse(
                rows.any {
                    it.text.toString().contains("手机备份") ||
                        it.text.toString().contains("恢复隐藏推荐") ||
                        it.text == "数据管理"
                }
            )
            val account = rows.first { it.text == "登录与账户管理" }
            assertNotNull(root.findViewById<View>(account.nextFocusRightId))
        } finally {
            controller.destroy()
        }
    }
}
