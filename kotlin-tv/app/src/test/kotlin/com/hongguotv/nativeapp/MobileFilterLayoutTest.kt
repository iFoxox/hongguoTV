// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.view.*
import android.widget.*
import com.hongguotv.core.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23,26],qualifiers="w320dp-h640dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class MobileFilterLayoutTest {
    private var previous=false
    @Before fun useTouch() { previous=TvStyle.touch; TvStyle.touch=true }
    @After fun restore() { TvStyle.touch=previous }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun layout(v:View,w:Int=320,h:Int=480) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
        v.layout(0,0,w,h)
    }
    @Test fun allFourRankingBoardsAreVisibleAndKeepCompleteLabels() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val tools=TvTools(c.get())
        val state=RankBrowserState()
        RankBoard.entries.forEach { board -> state.endRefresh(board,RankSnapshot(board,listOf(Series("1","作品")),ComicRanking(emptyMap(),1,""))) }
        val shell=MobileCatalogShell(c.get(),2,{}, {})
        val screen=RankingScreen(c.get(),state,shell.navigation,tools,{ _,_ -> },{}, { _,_ -> },{ _,_ -> fail("fresh cache fetched") })
        try {
            shell.setHeaderContent(screen.mobileHeader!!)
            shell.content.addView(screen,LinearLayout.LayoutParams(-1,-1))
            c.get().setContentView(shell); screen.start(false,false); layout(shell)
            val boards=RankBoard.entries.map { board -> views(shell.headerContent).filterIsInstance<TextView>().single { it.text==board.label } }
            assertEquals(4,boards.size)
            assertSame(boards[0].parent,boards[3].parent)
            assertEquals(boards[0].top,boards[2].top)
            assertFalse(views(screen).filterIsInstance<TextView>().any { it.text.toString().endsWith("热播榜") })
            boards.forEach { chip ->
                assertTrue(chip.left>=0 && chip.right<=(chip.parent as View).width)
                assertEquals(Gravity.CENTER,chip.gravity)
                assertEquals(0,chip.layout.getEllipsisCount(0))
            }
            boards[3].performClick()
            assertEquals(RankBoard.entries[3],state.board)
            assertFalse(views(shell).filterIsInstance<HorizontalScrollView>().any { it.getChildAt(0)===boards[0].parent })
        } finally { tools.destroy(); c.pause().stop().destroy() }
    }
    @Test fun topicDialogShowsLongLabelsAndReturnsTheCorrectChoice() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val tools=TvTools(c.get())
        val labels=listOf("全部题材","较长题材名称与作品数量完整显示 · 100 部","都市 · 12 部")
        var chosen=-1
        try {
            tools.chooseGrid("题材",labels,0,null) { chosen=it }
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val content=dialog.findViewById<View>(android.R.id.content)
            layout(content,320,440)
            val options=views(content).filterIsInstance<TextView>().filter { it.text.toString() in labels }
            assertEquals(3,options.size)
            options.forEach { option ->
                assertEquals(Gravity.CENTER,option.gravity)
                assertEquals(option.text.length,option.layout.getLineEnd(option.layout.lineCount-1))
            }
            assertEquals(options[0].top,options[1].top)
            assertEquals(options[0].height,options[1].height)
            assertTrue(options.all { it.height>=48 })
            options.first { it.text==labels[1] }.performClick()
            assertEquals(1,chosen)
            assertFalse(dialog.isShowing)
        } finally { tools.destroy(); c.pause().stop().destroy() }
    }

    @Test fun longFilterGroupsWrapEveryChoiceAndCenterTextAtLargeFontSize() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val config=android.content.res.Configuration(c.get().resources.configuration).apply { fontScale=1.3f }
        c.get().resources.updateConfiguration(config,c.get().resources.displayMetrics)
        val options=(1..12).map { DiscoveryFilter("cate_$it",if(it==12) "较长类别名称完整展示" else "测试类别$it") }
        val state=DiscoveryBrowserState().apply {
            loaded=true
            groups=listOf(DiscoveryGroup("genre","类型",listOf(DiscoveryFilter("short_play","真人剧"),DiscoveryFilter("comic_series","漫剧"),DiscoveryFilter("ai_series","AI剧"))),DiscoveryGroup("category_dim_theme","题材",options))
            items=(1..12).map { Series("$it","作品$it") }
        }
        val selected=mutableListOf<DiscoverySelection>()
        val screen=DiscoveryScreen(c.get(),state,TextView(c.get()),{ _,_ -> },{}, { _,_ -> },{ selection,_,_ -> selected+=selection })
        try {
            c.get().setContentView(screen); screen.start(false); layout(screen)
            assertFalse(views(screen).any { it is HorizontalScrollView })
            val chips=views(screen).filterIsInstance<TextView>().filter { it.text.toString().startsWith("测试类别") || it.text=="较长类别名称完整展示" }
            assertEquals(12,chips.size)
            assertTrue(chips.last().top>chips.first().top)
            chips.forEach { chip ->
                assertEquals(Gravity.CENTER,chip.gravity)
                assertTrue(chip.left>=0 && chip.right<=(chip.parent as View).width)
                assertEquals(chip.text.length,chip.layout.getLineEnd(chip.layout.lineCount-1))
                assertEquals(0,chip.layout.getEllipsisCount(0))
            }
            chips.last().performClick()
            assertEquals("cate_12",selected.single().values["category_dim_theme"])
        } finally { screen.close(); c.pause().stop().destroy() }
    }
}
