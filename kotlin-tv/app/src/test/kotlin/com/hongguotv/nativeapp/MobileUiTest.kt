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
@Config(sdk=[23,26],qualifiers="w360dp-h640dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class MobileUiTest {
    private var wasTouch=false
    @Before fun touchDevice() { wasTouch=TvStyle.touch; TvStyle.touch=true }
    @After fun restore() { TvStyle.touch=wasTouch }
    private fun views(v:View):List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun labels(v:View) = views(v).filterIsInstance<TextView>()
    private fun layout(v:View,w:Int,h:Int) {
        repeat(2) {
            v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
            v.layout(0,0,w,h)
        }
    }
    private fun tap(v:View) {
        val now=android.os.SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,10f,10f,0))
        v.dispatchTouchEvent(MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,10f,10f,0))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }
    @Test fun bottomNavigationHasFourVisibleDestinationsAndOneTapSwitches() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val calls=mutableListOf<Int>()
        val shell=MobileCatalogShell(c.get(),3,{ calls+=it },{})
        try {
            c.get().setContentView(shell); layout(shell,360,640)
            val bar=shell.getChildAt(shell.childCount-1) as LinearLayout
            assertEquals(listOf("首页","观看历史","排行榜","我的"),labels(bar).map { it.text.toString() })
            assertEquals(7,shell.navigation.size)
            assertSame(shell.navigation[3],shell.navigation[6])
            assertTrue(shell.navigation[6].isSelected)
            assertFalse(shell.navigation[4].isFocusableInTouchMode)
            tap(shell.navigation[4])
            assertEquals(listOf(4),calls)
        } finally { c.pause().stop().destroy() }
    }
    @Test fun compactChromePreservesContentSpaceAndLargeFontTouchTargets() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            for(scale in listOf(1f,1.3f,1.6f)) {
                val config=android.content.res.Configuration(c.get().resources.configuration).apply { fontScale=scale }
                c.get().resources.updateConfiguration(config,c.get().resources.displayMetrics)
                val shell=MobileCatalogShell(c.get(),0,{}, {})
                c.get().setContentView(shell); layout(shell,320,640)
                assertEquals(52,shell.getChildAt(0).height)
                val bar=shell.getChildAt(shell.childCount-1) as LinearLayout
                // Legacy graphics uses fallback font metrics; reserve room for complete labels.
                assertTrue("navigation height at font $scale: ${bar.height}",bar.height<=96)
                assertTrue("catalog loses its reading area at font $scale: ${shell.content.height}",shell.content.height>=490)
                for(index in 0 until bar.childCount) {
                    val item=bar.getChildAt(index)
                    assertTrue(item.height>=48 && item.width>=48)
                    val label=labels(item).single()
                    assertEquals(label.text.length,label.layout.getLineEnd(label.layout.lineCount-1))
                    assertEquals(0,label.layout.getEllipsisCount(0))
                }
                val button=mobileButton(c.get(),"登录后加入账户收藏") {}
                button.measure(View.MeasureSpec.makeMeasureSpec(140,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                button.layout(0,0,button.measuredWidth,button.measuredHeight)
                assertTrue(button.height>=48)
                assertEquals(button.text.length,button.layout.getLineEnd(button.layout.lineCount-1))
                assertTrue(button.layout.height<=button.height-button.paddingTop-button.paddingBottom)
            }
        } finally { c.pause().stop().destroy() }
    }

    @Test fun historyIsAnIndependentDestinationAndSearchRemainsInHeader() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        var searches=0
        val shell=MobileCatalogShell(c.get(),4,{}, { searches++ })
        try {
            c.get().setContentView(shell); layout(shell,320,640)
            assertTrue(shell.navigation[4].isSelected)
            assertFalse(shell.navigation[6].isSelected)
            val search=views(shell).first { it.contentDescription=="找剧：关键词与分类筛选" }
            assertTrue(search is ImageButton)
            val density=search.resources.displayMetrics.density
            assertEquals((48*density).toInt(),search.width)
            assertEquals((48*density).toInt(),search.height)
            assertFalse(search.isFocusableInTouchMode)
            tap(search)
            assertEquals(1,searches)
        } finally { c.pause().stop().destroy() }
    }

    @Test fun softControlsDrawAndRespondToPressInBothThemes() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val original=TvStyle.mode
        try {
            val bitmap=android.graphics.Bitmap.createBitmap(160,80,android.graphics.Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(bitmap)
            TvStyle.Mode.entries.forEach { mode ->
                TvStyle.apply(mode)
                val button=mobileButton(c.get(),"测试") {}
                layout(button,160,64)
                assertTrue(button.background.isStateful)
                button.draw(canvas)
                button.isPressed=true
                button.draw(canvas)
                button.isEnabled=false
                button.draw(canvas)
            }
            bitmap.recycle()
        } finally { TvStyle.apply(original); c.pause().stop().destroy() }
    }

    @Test fun playbackButtonsKeepIconAndLabelTogetherAndCentered() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val config=android.content.res.Configuration(c.get().resources.configuration).apply { fontScale=1.3f }
            c.get().resources.updateConfiguration(config,c.get().resources.displayMetrics)
            val calls=mutableListOf<MobilePlayerView.Action>()
            val page=MobilePlayerView(c.get(),{ a,_ -> calls+=a },{})
            page.update(Series("a","测试作品"),11,100,true,"加入账户收藏")
            c.get().setContentView(page); layout(page,320,640)
            val fullscreenChoices=listOf("竖屏全屏","横屏全屏").map { name -> labels(page).first { it.text==name } }
            assertEquals(fullscreenChoices[0].height,fullscreenChoices[1].height)
            fullscreenChoices.forEach { button ->
                assertTrue(button.width>=48 && button.height>=48)
                assertEquals(button.text.length,button.layout.getLineEnd(button.layout.lineCount-1))
            }
            for(name in listOf("上一集","选集","下一集")) {
                val button=labels(page).first { it.text==name }
                val icon=button.compoundDrawables[0]!!
                val (offset,_)=mobileIconOffset(button,icon)
                val contentWidth=icon.bounds.width()+button.compoundDrawablePadding+button.layout.getLineWidth(0)
                val left=button.paddingLeft+offset
                assertEquals(button.width/2f,left+contentWidth/2f,1f)
                assertTrue(button.width>=48 && button.height>=48)
            }
            val settings=views(page).first { it.contentDescription=="播放设置" } as TextView
            val settingsIcon=settings.compoundDrawables[0]!!
            val (offset,_)=mobileIconOffset(settings,settingsIcon)
            assertEquals(settings.width/2f,settings.paddingLeft+offset+settingsIcon.bounds.width()/2f,1f)
            tap(settings)
            assertEquals(listOf(MobilePlayerView.Action.SETTINGS),calls)
            val optionButtons=listOf("加入账户收藏","评论","弹幕","设置").map { name -> labels(page).first { it.text==name } }
            assertTrue(optionButtons.map { it.height }.distinct().size==1)
            optionButtons.forEach { button ->
                val icon=button.compoundDrawables[1]!!
                val (_,offsetY)=mobileIconOffset(button,icon)
                val groupHeight=icon.bounds.height()+button.compoundDrawablePadding+button.layout.height
                assertEquals(button.height/2f,button.paddingTop+offsetY+groupHeight/2f,1f)
            }
        } finally { c.pause().stop().destroy() }
    }

    @Test fun portraitPlaybackKeepsVideoAndCommonActionsAccessibleWithLargeFonts() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val config=android.content.res.Configuration(c.get().resources.configuration).apply { fontScale=1.3f }
        c.get().resources.updateConfiguration(config,c.get().resources.displayMetrics)
        val calls=mutableListOf<MobilePlayerView.Action>()
        val selected=mutableListOf<Int>()
        val page=MobilePlayerView(c.get(),{ a,_ -> calls+=a },{ selected+=it })
        val video=View(c.get())
        try {
            page.attachVideo(video); page.update(Series("a","测试作品"),11,100,true,"收藏")
            c.get().setContentView(page); layout(page,320,640)
            assertFalse(page.fullscreen)
            assertTrue(page.video.height in 150..240)
            assertTrue(labels(page).any { it.text=="下一集" && it.visibility==View.VISIBLE })
            val next=labels(page).first { it.text=="下一集" }
            assertFalse(next.isFocusableInTouchMode)
            tap(next)
            assertEquals(listOf(MobilePlayerView.Action.NEXT),calls)
            tap(labels(page).first { it.text=="12" })
            assertEquals(listOf(11),selected)
            assertSame(page.video,video.parent)
        } finally { c.pause().stop().destroy() }
    }
    @Test fun relatedWorksShowsAllSeasonsAndAllowsRetryAndUpcomingSelection() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val actions=mutableListOf<MobilePlayerView.Action>()
        val selected=mutableListOf<RelatedWork>()
        val page=MobilePlayerView(c.get(),{ a,_ -> actions+=a },{})
        val current=Series("100","当前作品")
        val rows=(1..35).map { index -> RelatedWork(Series("${100+index}","作品$index"),if(index==35) "同IP" else "第${index}季",index==34) }
        try {
            page.attachVideo(View(c.get())); c.get().setContentView(page); layout(page,320,640)
            assertTrue(labels(page).any { it.text=="正在加载相关作品…" })
            page.showRelatedFailed(); layout(page,320,640)
            tap(labels(page).first { it.text=="重试" })
            assertEquals(listOf(MobilePlayerView.Action.RETRY_RELATED),actions)
            page.showRelatedWorks(current,rows,{ _,_ -> },{ work,_ -> selected+=work }); layout(page,320,640)
            assertTrue(labels(page).any { it.text=="相关作品" })
            assertTrue(labels(page).any { it.text=="当前播放" })
            assertTrue(labels(page).any { it.text=="第34季 · 待上线 · 预约" })
            assertTrue(labels(page).any { it.text=="同IP · 已上线" })
            assertEquals(36,views(page).count { it.tag is String && (it.tag as String).matches(Regex("1[0-3][0-9]")) })
            views(page).first { it.tag=="134" }.performClick()
            assertEquals(rows[33],selected.single())
            assertTrue(selected.single().upcoming)
            views(page).first { it.tag=="100" }.performClick()
            assertEquals(1,selected.size)
            page.showRelatedWorks(current,emptyList(),{ _,_ -> },{ _,_ -> }); layout(page,320,640)
            assertTrue(labels(page).any { it.text=="暂无相关作品" })
        } finally { c.pause().stop().destroy() }
    }
    @Test fun relatedSeasonsInitiallyScrollToThePreviousSeasonInSourceOrder() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val page=MobilePlayerView(c.get(),{ _,_ -> },{})
        val rows=(1..12).map { n -> RelatedWork(Series("$n","作品第${n}季"),"第${n}季",false) }
        try {
            c.get().setContentView(page)
            // 官网实际响应可能没有当前作品；用当前标题与官方相邻季标记定位。
            val currentSeries=Series("9","作品第九季")
            val withoutCurrent=rows.filterNot { it.series.id==currentSeries.id }
            page.showRelatedWorks(currentSeries,withoutCurrent,{ _,_ -> },{ _,_ -> })
            layout(page,320,640)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            val current=views(page).single { it.tag=="9" }
            val previous=views(page).single { it.tag=="8" }
            val rail=previous.parent.parent as HorizontalScrollView
            assertEquals(previous.left,rail.scrollX)
            assertTrue(current.left>previous.left)
            assertTrue(labels(current).any { it.text=="第9季 · 当前播放" })
            assertFalse(current.isClickable)
            assertEquals(0,relatedStartIndex(rows,"1"))
            assertEquals(0,relatedStartIndex(rows,"missing"))
            val reordered=rows.take(8)+RelatedWork(Series("200","同 IP"),"同IP",false)+rows.drop(8)
            assertEquals(7,relatedStartIndex(reordered,"9"))
            assertEquals(12,titleSeasonNumber("作品第十二季"))
            assertEquals(10,titleSeasonNumber("作品第十季"))
            assertNull(titleSeasonNumber("作品前篇"))
            assertNull(titleSeasonNumber("作品第一季和第二季"))
            assertEquals("9",relatedWithCurrent(currentSeries,withoutCurrent)[8].series.id)
            assertEquals("9",relatedWithCurrent(Series("9","作品前篇"),withoutCurrent).first().series.id)
            assertEquals(rows,relatedWithCurrent(rows[8].series,rows))
        } finally { c.pause().stop().destroy() }
    }
    @Test fun episodeSheetSwitchesGroupsAndOneTapSelectsAndCloses() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val selected=mutableListOf<Int>()
        var closed=0
        lateinit var panel:EpisodePanel
        panel=EpisodePanel(c.get(),100,11,{ _,_,_ -> },{ selected+=it; panel.dismiss() },{ closed++ })
        try {
            panel.show()
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,dialog.window!!.attributes.gravity)
            val content=dialog.findViewById<View>(android.R.id.content)
            layout(content,360,470)
            tap(labels(content).first { it.text=="21—40" })
            val updated=dialog.findViewById<View>(android.R.id.content)
            layout(updated,360,470)
            tap(labels(updated).first { it.text=="23" })
            assertEquals(listOf(22),selected)
            assertEquals(1,closed)
            assertFalse(dialog.isShowing)
        } finally { panel.dismiss(); c.pause().stop().destroy() }
    }

    @Test fun fullscreenAndRotationReuseVideoAndExitCanRestorePortraitContent() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val page=MobilePlayerView(c.get(),{ _,_ -> },{})
        val video=View(c.get())
        try {
            page.attachVideo(video); c.get().setContentView(page); layout(page,360,640)
            page.toggleFullscreen(); layout(page,360,640)
            assertTrue(page.fullscreen); assertEquals(640,page.video.height)
            page.toggleFullscreen(); layout(page,360,640)
            assertFalse(page.fullscreen)
            layout(page,640,360)
            assertTrue(page.fullscreen); assertEquals(360,page.video.height)
            page.toggleFullscreen(); layout(page,640,336)
            assertFalse(page.fullscreen)
            assertSame(page.video,video.parent)
            assertEquals(2,page.video.childCount)
        } finally { c.pause().stop().destroy() }
    }
    @Test fun landscapeFullscreenButtonsRestorePreviousOrientationAndKeepTheVideo() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val previous=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        var requested=previous
        val orientations=mutableListOf<Int>()
        val orientation=MobileFullscreenOrientation({ requested },{ requested=it; orientations+=it })
        lateinit var page:MobilePlayerView
        page=MobilePlayerView(c.get(),{ action,_ ->
            if(action==MobilePlayerView.Action.FULLSCREEN || action==MobilePlayerView.Action.LANDSCAPE_FULLSCREEN) {
                if(page.fullscreen) { page.toggleFullscreen(); orientation.restore() }
                else { page.toggleFullscreen(); orientation.enter(if(action==MobilePlayerView.Action.FULLSCREEN)
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) }
            }
        },{})
        val video=View(c.get())
        try {
            page.attachVideo(video); c.get().setContentView(page); layout(page,320,640)
            tap(labels(page).first { it.text=="竖屏全屏" })
            assertTrue(page.fullscreen)
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,requested)
            tap(views(page).first { it.contentDescription=="退出全屏" })
            assertFalse(page.fullscreen)
            assertEquals(previous,requested)
            tap(labels(page).first { it.text=="横屏全屏" })
            assertTrue(page.fullscreen)
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,requested)
            layout(page,640,360)
            val exit=views(page).first { it.contentDescription=="退出全屏" }
            tap(exit)
            assertFalse(page.fullscreen)
            assertEquals(previous,requested)
            layout(page,320,640)
            assertSame(page.video,video.parent)
            assertEquals(listOf(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,previous,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,previous),orientations)
            orientation.restore()
            assertEquals(4,orientations.size)
        } finally { c.pause().stop().destroy() }
    }
    @Test fun mobileFeedActionsAreVisibleAndBackDoesNotTrapTheUser() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val state=DramaFeedState()
        state.append(CatalogPage(listOf(Series("a","测试作品")),false),1)
        val config=android.content.res.Configuration(c.get().resources.configuration).apply { fontScale=1.3f }
        c.get().resources.updateConfiguration(config,c.get().resources.displayMetrics)
        val backend=object:FeedBackend {
            override fun catalog(category:DramaCategory,page:Int)=CatalogPage(emptyList(),false)
            override fun detail(series:Series)=Detail(series,emptyList())
            override fun prepare(detail:Detail):TrialClip=error("No network in layout test")
            override fun close() {}
        }
        val playback=object:TrialPlayback {
            override val view=View(c.get())
            override var status:((TrialStatus)->Unit)?=null
            override fun load(clip:TrialClip,position:Long,autoplay:Boolean) {}
            override fun pause() {}; override fun resume() {}; override fun restart() {}
            override fun position()=0L; override fun clear() {}; override fun release() {}
        }
        val shell=MobileCatalogShell(c.get(),0,{}, {})
        val feed=HomeFeedScreen(c.get(),state,shell.navigation,{}, { _,_ -> },{"登录后加入账户收藏"},backend,playback,mobileHeader=shell::setHeaderContent)
        try {
            shell.content.addView(feed,LinearLayout.LayoutParams(-1,-1))
            c.get().setContentView(shell); layout(shell,360,640)
            assertEquals(listOf("推荐","漫剧","真人剧","换一批"),labels(shell.headerContent).map { it.text.toString() })
            assertFalse(labels(feed).any { it.text=="推荐" || it.text=="换一批" })
            val scrolling=views(feed).filterIsInstance<ScrollView>().single()
            scrolling.scrollTo(0,100)
            assertEquals(0,shell.headerContent.scrollY)
            assertTrue(labels(shell.headerContent).first { it.text=="推荐" }.isSelected)
            val watch=labels(feed).first { it.text=="观看全集" }
            val favorite=labels(feed).first { it.text=="登录后加入账户收藏" }
            assertEquals(watch.height,favorite.height)
            assertTrue(favorite.height>=48)
            assertEquals(favorite.text.length,favorite.layout.getLineEnd(favorite.layout.lineCount-1))
            assertEquals(View.VISIBLE,(watch.parent as View).visibility)
            assertFalse(watch.isFocusableInTouchMode)
            assertFalse(feed.back())
            assertTrue(views(feed).any { it is ScrollView })
            assertFalse(labels(feed).any { it.text.toString().contains("点按画面选择操作") })
            labels(shell.headerContent).first { it.text=="漫剧" }.performClick()
            assertEquals(DramaCategory.COMIC,state.category)
            assertFalse(state.recommendation)
            assertTrue(labels(shell.headerContent).first { it.text=="漫剧" }.isSelected)
        } finally { feed.close(); c.pause().stop().destroy() }
    }
}
