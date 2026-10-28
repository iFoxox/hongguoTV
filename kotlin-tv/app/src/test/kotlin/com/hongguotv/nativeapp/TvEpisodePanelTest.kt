// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.app.Dialog
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
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

    @Test fun fiftyEpisodesUseFiveRowsAndPagingDoesNotStartPlayback() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val selected=mutableListOf<Int>()
        val panel=EpisodePanel(c.get(),107,11,{ _,_,_ -> },{ selected+=it },{})
        try {
            panel.show(); val d=ShadowDialog.getLatestDialog(); layout(d)
            assertEquals(50,cells(d).size)
            assertEquals("1",cells(d).first().text)
            assertEquals("50",cells(d).last().text)
            assertEquals(5,cells(d).map { it.parent }.distinct().size)
            assertEquals(1,views(root(d)).filterIsInstance<ScrollView>().size)
            assertFalse(texts(d).single { it.text=="‹ 上一组" }.isEnabled)
            texts(d).single { it.text=="下一组 ›" }.performClick(); layout(d)
            assertEquals(50,cells(d).size)
            assertEquals("51",cells(d).first().text)
            assertEquals("100",cells(d).last().text)
            assertTrue(cells(d).first().hasFocus())
            texts(d).single { it.text=="下一组 ›" }.performClick(); layout(d)
            assertEquals(7,cells(d).size)
            assertEquals("101",cells(d).first().text)
            assertEquals("107",cells(d).last().text)
            assertFalse(texts(d).single { it.text=="下一组 ›" }.isEnabled)
            assertTrue(selected.isEmpty())
            cells(d).last().performClick(); assertEquals(listOf(106),selected)
            texts(d).single { it.text=="‹ 上一组" }.performClick(); layout(d)
            assertEquals("51",cells(d).first().text)
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
                    assertEquals(49,cells(d).size)
                    val current=cells(d).single { it.text=="9999\n当前" }
                    assertTrue(current.isSelected)
                    assertTrue(current.hasFocus())
                    assertTrue(cells(d).any { it.text=="9998\n上次" })
                    for(cell in cells(d)) {
                        assertTrue("$scale / ${cell.text}",cell.paint.measureText(cell.text.toString().substringBefore('\n'))<=cell.width-cell.paddingLeft-cell.paddingRight)
                        assertTrue(cell.layout.getLineEnd(0)>=4)
                        assertEquals(cell.text.count { it=='\n' }+1,cell.layout.lineCount)
                    }
                    assertFalse(texts(d).single { it.text=="下一组 ›" }.isEnabled)
                } finally { panel.dismiss(); ShadowLooper.idleMainLooper() }
            }
        } finally { c.pause().stop().destroy() }
    }

    @Test fun jumpUsesBrowsedEpisodeAndRemoteFocusCanReachIncompleteLastRow() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val located=mutableListOf<Int>()
        val selected=mutableListOf<Int>()
        val panel=EpisodePanel(c.get(),147,100,{ index,_,done -> located+=index; done(146) },{selected+=it},{})
        try {
            panel.show(); val d=ShadowDialog.getLatestDialog(); layout(d)
            val grid=cells(d)
            assertEquals(47,grid.size)
            assertEquals(grid[10].id,grid[0].nextFocusDownId)
            assertEquals(grid[0].id,grid[0].nextFocusLeftId)
            assertEquals(grid[46].id,grid[39].nextFocusDownId)
            assertEquals(grid[36].id,grid[46].nextFocusUpId)
            grid[12].requestFocus()
            texts(d).single { it.text=="跳转集数" }.performClick(); layout(d)
            assertEquals(listOf(112),located)
            assertEquals("147",cells(d).single { it.hasFocus() }.text)
            assertTrue(selected.isEmpty())
            cells(d).single { it.hasFocus() }.performClick(); assertEquals(listOf(146),selected)
        } finally { panel.dismiss(); c.pause().stop().destroy() }
    }
}
