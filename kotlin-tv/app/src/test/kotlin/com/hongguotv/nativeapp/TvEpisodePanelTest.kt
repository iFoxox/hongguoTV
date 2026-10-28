// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.app.Dialog
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23,26],qualifiers="w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class TvEpisodePanelTest {
    private var oldTouch=false
    @Before fun remote() { oldTouch=TvStyle.touch; TvStyle.touch=false }
    @After fun restore() { TvStyle.touch=oldTouch }
    private fun views(v:View):List<View> = listOf(v) +
        if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun root(d:Dialog):View = d.findViewById(android.R.id.content)
    private fun texts(d:Dialog)=views(root(d)).filterIsInstance<TextView>()
    private fun cells(d:Dialog)=texts(d).filter { it.text.matches(Regex("[0-9]+(?:\n.*)?")) }
    private fun layout(d:Dialog) {
        val v=root(d)
        v.measure(View.MeasureSpec.makeMeasureSpec(d.window!!.attributes.width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(d.window!!.attributes.height,View.MeasureSpec.EXACTLY))
        v.layout(0,0,v.measuredWidth,v.measuredHeight)
        ShadowLooper.idleMainLooper()
    }

    @Test fun centeredPanelUsesVerticalGroupTabsAndChangingGroupsDoesNotStartPlayback() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val selected=mutableListOf<Int>()
        val panel=EpisodePanel(c.get(),247,11,{ _,_,_ -> },{ selected+=it },{})
        try {
            panel.show(); val d=ShadowDialog.getLatestDialog(); layout(d)
            assertEquals(Gravity.CENTER,d.window!!.attributes.gravity)
            assertEquals(0,d.window!!.attributes.x)
            assertEquals(120,cells(d).size)
            assertEquals("1",cells(d).first().text)
            assertEquals("120",cells(d).last().text)
            assertEquals(8,cells(d).map { it.parent }.distinct().size)
            assertEquals(2,views(root(d)).filterIsInstance<ScrollView>().size)
            assertFalse(texts(d).any { it.text.contains("上一组") || it.text.contains("下一组") })
            val first=texts(d).single { it.text=="1—120" }
            val second=texts(d).single { it.text=="121—240" }
            assertEquals(LinearLayout.VERTICAL,(first.parent as LinearLayout).orientation)
            assertEquals(second.id,first.nextFocusDownId)
            second.requestFocus(); layout(d)
            assertEquals(120,cells(d).size)
            assertEquals("121",cells(d).first().text)
            assertEquals("240",cells(d).last().text)
            val selectedTab=texts(d).single { it.text=="121—240" }
            assertTrue(selectedTab.hasFocus()); assertTrue(selectedTab.isSelected)
            root(d).findViewById<View>(selectedTab.nextFocusRightId).requestFocus()
            assertTrue(cells(d).first().hasFocus())
            val remembered=cells(d)[30]
            remembered.requestFocus()
            assertEquals(selectedTab.id,remembered.nextFocusLeftId)
            selectedTab.requestFocus()
            assertEquals(remembered.id,selectedTab.nextFocusRightId)
            root(d).findViewById<View>(selectedTab.nextFocusRightId).requestFocus()
            assertTrue(remembered.hasFocus())
            texts(d).single { it.text=="241—247" }.requestFocus(); layout(d)
            assertEquals(7,cells(d).size)
            assertEquals("241",cells(d).first().text)
            assertEquals("247",cells(d).last().text)
            assertTrue(selected.isEmpty())
            cells(d).last().performClick(); assertEquals(listOf(246),selected)
            texts(d).single { it.text=="121—240" }.requestFocus(); layout(d)
            assertEquals("121",cells(d).first().text)
        } finally { panel.dismiss(); c.pause().stop().destroy() }
    }

    @Test fun currentAndResumeMarkersKeepFourDigitNumbersUnclippedAtLargeFonts() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            for(scale in listOf(1f,1.3f,1.6f)) {
                c.get().resources.updateConfiguration(Configuration(c.get().resources.configuration).apply { fontScale=scale },c.get().resources.displayMetrics)
                val panel=EpisodePanel(c.get(),9999,9998,{ _,_,_ -> },{},{},lastWatched=9997)
                try {
                    panel.show(); val d=ShadowDialog.getLatestDialog(); layout(d)
                    assertEquals(39,cells(d).size)
                    val current=cells(d).single { it.text=="9999\n当前" }
                    assertTrue(current.isSelected)
                    assertTrue(current.hasFocus())
                    assertTrue(cells(d).any { it.text=="9998\n上次" })
                    for(cell in cells(d)) {
                        assertTrue("$scale / ${cell.text}",cell.paint.measureText(cell.text.toString().substringBefore('\n'))<=cell.width-cell.paddingLeft-cell.paddingRight)
                        assertTrue(cell.layout.getLineEnd(0)>=4)
                        assertEquals(cell.text.count { it=='\n' }+1,cell.layout.lineCount)
                    }
                    assertTrue(texts(d).single { it.text=="9961—9999" }.isSelected)
                    val tabScroll=views(root(d)).filterIsInstance<ScrollView>().single {
                        views(it).filterIsInstance<TextView>().any { text -> text.text=="1—120" }
                    }
                    assertTrue(tabScroll.scrollY>0)
                } finally { panel.dismiss(); ShadowLooper.idleMainLooper() }
            }
        } finally { c.pause().stop().destroy() }
    }

    @Test fun jumpUsesBrowsedEpisodeAndRemoteFocusCanReachIncompleteLastRow() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val located=mutableListOf<Int>()
        val selected=mutableListOf<Int>()
        val panel=EpisodePanel(c.get(),287,240,{ index,_,done -> located+=index; done(286) },{selected+=it},{})
        try {
            panel.show(); val d=ShadowDialog.getLatestDialog(); layout(d)
            val grid=cells(d)
            assertEquals(47,grid.size)
            val columns=(grid.first().parent as ViewGroup).childCount
            assertEquals(grid[columns].id,grid[0].nextFocusDownId)
            assertEquals(texts(d).single { it.text=="241—287" }.id,grid[0].nextFocusLeftId)
            assertEquals(grid[46].id,grid[columns*3-1].nextFocusDownId)
            assertEquals(grid[46-columns].id,grid[46].nextFocusUpId)
            grid[12].requestFocus()
            texts(d).single { it.text=="跳转集数" }.performClick(); layout(d)
            assertEquals(listOf(252),located)
            assertEquals("287",cells(d).single { it.hasFocus() }.text)
            assertTrue(selected.isEmpty())
            cells(d).single { it.hasFocus() }.performClick(); assertEquals(listOf(286),selected)
        } finally { panel.dismiss(); c.pause().stop().destroy() }
    }
}
