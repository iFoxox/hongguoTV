// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23,26],qualifiers="w360dp-h800dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class MobileHistoryTest {
    private var oldTouch=false
    @Before fun mobile() {
        oldTouch=TvStyle.touch; TvStyle.touch=true
        InteractionMode.save(RuntimeEnvironment.getApplication(),InteractionMode.Mode.MOBILE)
    }
    @After fun restore() { TvStyle.touch=oldTouch }
    private fun all(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList())
    private fun layout(v:View) {
        v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));v.layout(0,0,360,800)
    }
    @Test fun historyTabsSwitchEmptyStatesWithoutDuplicatingHeadingOrMixingSources() {
        val c=Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val a=c.get()
            val library=MainActivity::class.java.getDeclaredField("library").apply { isAccessible=true }.get(a) as Library
            val loader=Library::class.java.getDeclaredField("loader").apply { isAccessible=true }.get(library) as java.util.concurrent.ExecutorService
            loader.submit {}.get(5,java.util.concurrent.TimeUnit.SECONDS);ShadowLooper.idleMainLooper()
            MainActivity::class.java.getDeclaredMethod("switchTab",Int::class.javaPrimitiveType).apply { isAccessible=true }.invoke(a,4)
            val root=a.findViewById<View>(android.R.id.content);layout(root)
            var nodes=all(root)
            var tabs=nodes.filterIsInstance<MobileHistoryTabs>().single().tabs
            assertEquals(tabs[0].top,tabs[1].top);assertTrue(tabs[1].left>tabs[0].left)
            assertEquals(tabs[0].width,tabs[1].width)
            assertTrue(tabs[0].isSelected);assertFalse(tabs[1].isSelected)
            val labels=nodes.filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(labels.contains("还没有观看记录"));assertTrue(labels.contains("去找剧"))
            assertEquals(2,labels.count { it=="观看历史" }) // 页头和底部导航各一处。
            assertEquals(View.GONE,nodes.filterIsInstance<SeriesPreview>().single().visibility)
            tabs[1].performClick();ShadowLooper.idleMainLooper();layout(root)
            nodes=all(root);tabs=nodes.filterIsInstance<MobileHistoryTabs>().single().tabs
            assertTrue(tabs[1].isSelected)
            assertTrue(nodes.filterIsInstance<TextView>().any { it.text.toString()=="登录后查看账户历史" })
            nodes.filterIsInstance<TextView>().single { it.text.toString()=="登录红果" }.performClick();ShadowLooper.idleMainLooper()
            val menu=ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(listOf("官方网页登录","另一台设备扫码登录"),(0 until menu.listView.adapter.count).map { menu.listView.adapter.getItem(it).toString() })
            assertTrue(menu.listView.adapter.getView(0,null,menu.listView) is FrameLayout)
            menu.dismiss()
            tabs[0].performClick();ShadowLooper.idleMainLooper()
            assertTrue(all(root).filterIsInstance<TextView>().any { it.text.toString()=="还没有观看记录" })
        } finally { c.destroy() }
    }
    @Test fun equalTabsKeepSingleTapTargetsWithLargeText() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val config=android.content.res.Configuration(c.get().resources.configuration).apply { fontScale=1.3f }
            c.get().resources.updateConfiguration(config,c.get().resources.displayMetrics)
            val selections=mutableListOf<Boolean>()
            val tabs=MobileHistoryTabs(c.get(),false) { selections+=it }
            layout(tabs)
            assertEquals(tabs.tabs[0].width,tabs.tabs[1].width)
            tabs.tabs.forEach { assertTrue(it.height>=48);assertFalse(it.isFocusableInTouchMode) }
            tabs.tabs[0].performClick();tabs.tabs[1].performClick()
            assertEquals(listOf(true),selections)
        } finally { c.pause().stop().destroy() }
    }
}
