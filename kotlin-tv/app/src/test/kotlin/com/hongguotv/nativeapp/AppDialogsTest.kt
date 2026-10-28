// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
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

    @Test fun windowStaysOpaqueWithoutEnabledStateAndAllTextContrastsInBothModes() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            for(touch in listOf(false,true)) for(mode in TvStyle.Mode.entries) {
                TvStyle.touch=touch; TvStyle.apply(mode)
                val content=LinearLayout(c.get())
                val instructions=TextView(c.get()).apply { text="手机扫码后，用浏览器打开并发送剧名。" }
                content.addView(instructions)
                val popup=AppDialogs.builder(c.get()).setTitle("手机输入剧名").setView(content)
                    .setNegativeButton("关闭",null).setPositiveButton("连接帮助",null).show()
                try {
                    ShadowLooper.idleMainLooper()
                    val background=popup.window!!.decorView.background as GradientDrawable
                    // 窗口背景不应按控件的禁用态变透明；回归覆盖无 state_enabled 的固件状态。
                    for(state in listOf(intArrayOf(),intArrayOf(android.R.attr.state_window_focused),intArrayOf(android.R.attr.state_enabled))) {
                        background.state=state
                        val fill=Shadows.shadowOf(background).lastSetColor
                        assertFalse(background.isStateful)
                        assertEquals("$mode / $touch",255,background.alpha)
                        assertEquals(255,Color.alpha(fill))
                        assertEquals(SurfaceStyle.surface,fill)
                    }
                    assertEquals(TvStyle.text,instructions.currentTextColor)
                    for(text in views(popup.window!!.decorView).filterIsInstance<TextView>().filter { it.text.isNotBlank() }) {
                        assertTrue("$mode / ${text.text}",ColorUtils.calculateContrast(text.currentTextColor,SurfaceStyle.surface)>=4.5)
                    }
                    // 异步填入的说明不依赖仅在 onShow 执行一次的递归着色。
                    content.addView(TextView(c.get()).apply {
                        text="等待手机连接…"; setTextColor(TvStyle.text)
                        assertTrue(ColorUtils.calculateContrast(currentTextColor,SurfaceStyle.surface)>=4.5)
                    })
                } finally { popup.dismiss() }
            }
        } finally { c.pause().stop().destroy() }
    }

    @Test fun phoneInputInstructionsRemainReadableAfterQrLoadsAsynchronously() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val tools=TvTools(c.get())
        try {
            TvStyle.touch=false
            for(mode in TvStyle.Mode.entries) {
                TvStyle.apply(mode)
                TvTools::class.java.declaredMethods.single { it.name=="showPhoneInput" }.apply { isAccessible=true }
                    .invoke(tools,null,{ _:String -> },listOf(LanInputAddress("127.0.0.1","测试局域网")),0)
                val worker=TvTools::class.java.getDeclaredField("worker").apply { isAccessible=true }
                    .get(tools) as java.util.concurrent.ExecutorService
                worker.submit {}.get(5,java.util.concurrent.TimeUnit.SECONDS)
                ShadowLooper.idleMainLooper()
                val popup=ShadowAlertDialog.getLatestAlertDialog()
                val labels=views(popup.window!!.decorView).filterIsInstance<TextView>()
                assertTrue(labels.any { it.text=="等待手机连接…" })
                val instructions=labels.single { it.text.contains("二维码有效 5 分钟") }
                assertTrue(ColorUtils.calculateContrast(instructions.currentTextColor,SurfaceStyle.surface)>=4.5)
                assertEquals(SurfaceStyle.surface,Shadows.shadowOf(popup.window!!.decorView.background as GradientDrawable).lastSetColor)
                tools.close()
            }
        } finally { tools.destroy(); c.pause().stop().destroy() }
    }

    @Test fun remoteGridSelectionAndFocusedEpisodeDigitsKeepReadableText() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        val tools=TvTools(c.get())
        try {
            TvStyle.touch=false
            for(mode in TvStyle.Mode.entries) {
                TvStyle.apply(mode)
                tools.chooseGrid("题材",listOf("全部","家庭"),1,null,{})
                ShadowLooper.idleMainLooper()
                val popup=ShadowAlertDialog.getLatestAlertDialog()
                val selected=views(popup.window!!.decorView).filterIsInstance<TextView>().single { it.text=="家庭" }
                selected.onFocusChangeListener.onFocusChange(selected,true)
                assertTrue(ColorUtils.calculateContrast(selected.currentTextColor,SurfaceStyle.surface)>=4.5)
                tools.close()
                tools.episodePicker(100,0,null,{})
                ShadowLooper.idleMainLooper()
                val picker=ShadowAlertDialog.getLatestAlertDialog()
                val digit=views(picker.window!!.decorView).filterIsInstance<Button>().single { it.text=="1" }
                val focused=digit.textColors.getColorForState(intArrayOf(android.R.attr.state_focused),0)
                assertTrue(ColorUtils.calculateContrast(focused,TvStyle.accent)>=4.5)
                tools.close()
            }
        } finally { tools.destroy(); c.pause().stop().destroy() }
    }

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
