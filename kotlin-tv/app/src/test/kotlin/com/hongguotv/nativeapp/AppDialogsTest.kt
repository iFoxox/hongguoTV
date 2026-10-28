// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23,26],qualifiers="w360dp-h640dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class AppDialogsTest {
    private var originalMode=TvStyle.Mode.LIGHT
    private var originalTouch=false
    @Before fun usePhone() {
        originalMode=TvStyle.mode; originalTouch=TvStyle.touch
        TvStyle.touch=true; TvStyle.apply(TvStyle.Mode.LIGHT)
    }
    @After fun restore() { TvStyle.touch=originalTouch; TvStyle.apply(originalMode) }
    private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun accountMenuUsesThePageThemeAndOnlyOffersWebsiteOrQrLogin() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        c.get().setTheme(R.style.AppTheme)
        val account=AccountController(c.get()) {}
        try {
            val worker=AccountController::class.java.getDeclaredField("worker").apply { isAccessible=true }.get(account) as java.util.concurrent.ExecutorService
            worker.submit {}.get(5,java.util.concurrent.TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            for (touch in listOf(true,false)) {
                TvStyle.touch=touch
                account.show(); ShadowLooper.idleMainLooper()
                val menu=ShadowAlertDialog.getLatestAlertDialog()
                val labels=(0 until menu.listView.adapter.count).map { menu.listView.adapter.getItem(it).toString() }
                assertEquals(if(touch) listOf("官方网页登录","另一台设备扫码登录") else listOf("使用红果 App 扫码登录"),labels)
                val texts=views(menu.window!!.decorView).filterIsInstance<TextView>()
                assertTrue(texts.any { it.text.toString()=="红果账号" })
                assertFalse(texts.any { it.text.toString().contains("测试") || it.text.toString().contains("手机号 +") })
                val item=views(menu.listView.adapter.getView(0,null,menu.listView)).filterIsInstance<TextView>().first()
                assertTrue(android.graphics.Color.red(item.currentTextColor)<100)
                menu.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); ShadowLooper.idleMainLooper()
                assertFalse(menu.isShowing)
            }
        } finally { account.destroy(); c.pause().stop().destroy() }
    }

    @Test fun standardShowKeepsCallbacksAndTouchButtons() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        var confirms=0
        val popup=AppDialogs.builder(c.get()).setTitle("确认操作").setMessage("确认后继续")
            .setPositiveButton("确认") { _,_ -> confirms++ }.setNegativeButton("取消",null).show()
        try {
            ShadowLooper.idleMainLooper()
            val positive=popup.getButton(AlertDialog.BUTTON_POSITIVE)
            assertEquals(SurfaceStyle.accentText,positive.currentTextColor)
            assertTrue(positive.minHeight>=48)
            assertFalse(positive.isFocusableInTouchMode)
            positive.performClick(); ShadowLooper.idleMainLooper()
            assertEquals(1,confirms)
            assertFalse(popup.isShowing)
        } finally { popup.dismiss(); c.pause().stop().destroy() }
    }

    @Test fun optionDialogsFollowBothAppThemesAndKeepCheckedChoice() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            TvStyle.Mode.entries.forEach { mode ->
                TvStyle.apply(mode)
                var chosen=-1
                val popup=AppDialogs.builder(c.get()).setTitle("外观")
                    .setSingleChoiceItems(arrayOf("浅色","深色"),1) { dialog,index -> chosen=index; dialog.dismiss() }.show()
                ShadowLooper.idleMainLooper()
                val item=popup.listView.adapter.getView(0,null,popup.listView) as TextView
                val brightness=android.graphics.Color.red(item.currentTextColor)
                assertTrue(if(mode==TvStyle.Mode.LIGHT) brightness<100 else brightness>180)
                assertEquals(1,popup.listView.checkedItemPosition)
                popup.listView.performItemClick(item,0,0); ShadowLooper.idleMainLooper()
                assertEquals(0,chosen)
                assertFalse(popup.isShowing)
            }
        } finally { c.pause().stop().destroy() }
    }
}
