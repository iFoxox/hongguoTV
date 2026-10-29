// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23,26],qualifiers="w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class TvDialogOptionsTest {
    private val oldTouch=TvStyle.touch
    @Before fun remote() { TvStyle.touch=false }
    @After fun restore() { TvStyle.touch=oldTouch }
    private fun layout(popup:AlertDialog) {
        val decor=popup.window!!.decorView
        repeat(3) {
            decor.viewTreeObserver.dispatchOnGlobalLayout()
            decor.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(507,View.MeasureSpec.AT_MOST))
            decor.layout(0,0,decor.measuredWidth,decor.measuredHeight)
        }
    }
    @Test fun finiteMenusShowEveryOptionAndKeepChoiceCallbacks() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            for(count in listOf(2,3,6)) {
                var chosen=-1
                val popup=AppDialogs.builder(c.get()).setTitle("选项")
                    .setSingleChoiceItems(Array(count) { "第 ${it+1} 项" },0) { _,index -> chosen=index }
                    .setNegativeButton("关闭",null).show()
                try {
                    ShadowLooper.idleMainLooper()
                    // 回归固件／系统默认将 ListView 及其内容容器限制为一行的情况。
                    val list=popup.listView
                    list.layoutParams=list.layoutParams.apply { height=48 }
                    val panel=list.parent as ViewGroup
                    panel.layoutParams=panel.layoutParams.apply { height=48 }
                    layout(popup)
                    assertEquals("$count 项应全部可见",count,list.childCount)
                    assertEquals(count-1,list.lastVisiblePosition)
                    assertTrue(list.getChildAt(count-1).bottom<=list.height-list.paddingBottom)
                    assertTrue(popup.getButton(AlertDialog.BUTTON_NEGATIVE).bottom<=popup.window!!.decorView.height)
                    list.performItemClick(list.getChildAt(count-1),count-1,list.adapter.getItemId(count-1))
                    assertEquals(count-1,chosen)
                    assertEquals(count-1,list.checkedItemPosition)
                } finally { popup.dismiss() }
            }
        } finally { c.pause().stop().destroy() }
    }

    @Test fun longHistoryStaysBoundedAndCanReachLastOption() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val popup=AppDialogs.builder(c.get()).setTitle("云端继续观看")
            .setItems(Array(50) { "作品 ${it+1}" },null).setNegativeButton("关闭",null).show()
        try {
            ShadowLooper.idleMainLooper(); layout(popup)
            val list=popup.listView
            assertTrue(list.height in 100..400)
            assertTrue(list.childCount in 2..49)
            assertTrue(popup.window!!.decorView.height<=507)
            list.setSelection(49); layout(popup)
            assertEquals(49,list.lastVisiblePosition)
        } finally { popup.dismiss(); c.pause().stop().destroy() }
    }

    @Test fun signedAccountMenuShowsAllThreeActions() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val account=AccountController(c.get()) {}
        try {
            val worker=AccountController::class.java.getDeclaredField("worker").apply { isAccessible=true }
                .get(account) as java.util.concurrent.ExecutorService
            worker.submit {}.get(5,java.util.concurrent.TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            AccountController::class.java.getDeclaredField("credentials").apply { isAccessible=true }
                .set(account,com.hongguotv.core.AccountCredentials("fixture-only"))
            account.show(); ShadowLooper.idleMainLooper()
            val popup=ShadowAlertDialog.getLatestAlertDialog()
            layout(popup)
            assertEquals(3,popup.listView.childCount)
            assertEquals(listOf("云端继续观看（最近 50 部）","刷新账号和云端记录","退出红果账号"),
                (0 until popup.listView.adapter.count).map { popup.listView.adapter.getItem(it).toString() })
        } finally { account.destroy(); c.pause().stop().destroy() }
    }

    @Test fun eightOptionGridFitsAndHasRemotePathToReturnButton() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val tools=TvTools(c.get())
        try {
            var chosen=-1
            val labels=listOf("关闭定时停止","15 分钟后","30 分钟后","60 分钟后","90 分钟后","本集播完","再播完 3 集","再播完 5 集")
            tools.chooseGrid("定时停止",labels,-1,null,{chosen=it})
            ShadowLooper.idleMainLooper()
            val popup=ShadowAlertDialog.getLatestAlertDialog()
            layout(popup)
            fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)
                (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
            val choices=views(popup.window!!.decorView).filterIsInstance<android.widget.TextView>().filter { it.text.toString() in labels }
            assertEquals(8,choices.size)
            val back=popup.getButton(AlertDialog.BUTTON_NEGATIVE)
            choices.takeLast(4).forEach { assertEquals(back.id,it.nextFocusDownId) }
            assertEquals(choices.last().id,back.nextFocusUpId)
            choices.last().performClick(); assertEquals(7,chosen)
        } finally { tools.destroy(); c.pause().stop().destroy() }
    }
}
