// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.app.Dialog
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.core.graphics.ColorUtils
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23,26],qualifiers="w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DialogReadabilityTest {
    private var oldMode=TvStyle.Mode.LIGHT
    private var oldTouch=false
    @Before fun remote() { oldMode=TvStyle.mode; oldTouch=TvStyle.touch; TvStyle.touch=false }
    @After fun restore() { TvStyle.touch=oldTouch; TvStyle.apply(oldMode) }
    private fun views(view:View):List<View> = listOf(view) +
        if(view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply { isAccessible=true }
    private fun drain(account:AccountController) {
        (field(account,"worker").get(account) as ExecutorService).submit {}.get(5,TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()
    }
    private fun readable(dialog:Dialog) {
        val decor=dialog.window!!.decorView
        decor.viewTreeObserver.dispatchOnGlobalLayout()
        val theme=dialog.context.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackgroundFloating))
        try { assertEquals(SurfaceStyle.surface,theme.getColor(0,0)) } finally { theme.recycle() }
        for(text in views(decor).filterIsInstance<TextView>().filter { it.visibility==View.VISIBLE && it.text.isNotBlank() }) {
            assertTrue("${TvStyle.mode}: ${text.text}", ColorUtils.calculateContrast(text.currentTextColor,SurfaceStyle.surface)>=4.5)
        }
        if(dialog is AlertDialog) {
            for(name in listOf("parentPanel","topPanel","contentPanel","customPanel","buttonPanel")) {
                val id=dialog.context.resources.getIdentifier(name,"id","android")
                if(id==0) continue
                val panel=decor.findViewById<View>(id) ?: continue
                assertEquals(name,Color.TRANSPARENT,
                    (panel.background as ColorDrawable).color)
            }
        }
    }

    @Test fun dynamicLabelsAndRestoreCheckboxesUseActualDialogThemeAndStopAfterDismissal() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        c.get().setTheme(R.style.AppTheme)
        try {
            for(mode in TvStyle.Mode.entries) {
                TvStyle.apply(mode)
                val content=LinearLayout(c.get())
                val popup=AppDialogs.builder(c.get()).setTitle("备份与恢复").setView(content).setPositiveButton("确认",null).show()
                ShadowLooper.idleMainLooper()
                val stale=if(mode==TvStyle.Mode.LIGHT) Color.WHITE else Color.BLACK
                val late=TextView(c.get()).apply { text="异步加载的登录说明"; setTextColor(stale) }
                val checkbox=CheckBox(c.get()).apply { text="同时恢复播放设置"; setTextColor(stale) }
                content.addView(late); content.addView(checkbox)
                readable(popup)
                assertEquals(TvStyle.text,late.currentTextColor)
                assertEquals(TvStyle.text,checkbox.currentTextColor)
                checkbox.performClick(); assertTrue(checkbox.isChecked)
                assertEquals(SurfaceStyle.accentText,checkbox.buttonTintList!!.getColorForState(intArrayOf(android.R.attr.state_checked),0))
                popup.dismiss(); ShadowLooper.idleMainLooper()
                late.setTextColor(stale)
                popup.window!!.decorView.viewTreeObserver.dispatchOnGlobalLayout()
                assertEquals(stale,late.currentTextColor)
            }
        } finally { c.pause().stop().destroy() }
    }

    @Test fun realAccountMenuAndQrLoginInstructionsRemainReadable() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        c.get().setTheme(R.style.AppTheme)
        try {
            val image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
            image.eraseColor(Color.WHITE)
            val png=ByteArrayOutputStream().apply { image.compress(Bitmap.CompressFormat.PNG,100,this) }.toByteArray()
            image.recycle()
            val encoded=android.util.Base64.encodeToString(png,android.util.Base64.NO_WRAP)
            for(mode in TvStyle.Mode.entries) {
                TvStyle.apply(mode)
                val account=AccountController(c.get()) {}
                try {
                    drain(account)
                    val http=OkHttpClient.Builder().addInterceptor { chain ->
                        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                            .body("""{"message":"success","data":{"token":"fixture-only","qrcode":"$encoded","expire_time":300}}""".toResponseBody()).build()
                    }.build()
                    field(account,"http").set(account,http)
                    account.show(); ShadowLooper.idleMainLooper()
                    val menu=ShadowAlertDialog.getLatestAlertDialog()
                    readable(menu)
                    menu.listView.performItemClick(menu.listView.adapter.getView(0,null,menu.listView),0,0)
                    drain(account)
                    val qr=ShadowAlertDialog.getLatestAlertDialog()
                    assertNotSame(menu,qr)
                    readable(qr)
                    assertTrue(views(qr.window!!.decorView).filterIsInstance<TextView>().any { it.text.contains("扫码后请在手机确认登录") })
                    val bitmap=views(qr.window!!.decorView).filterIsInstance<ImageView>().single { it.drawable!=null }
                    assertEquals(Color.WHITE,(bitmap.background as ColorDrawable).color)
                } finally { account.destroy(); ShadowLooper.idleMainLooper() }
            }
        } finally { c.pause().stop().destroy() }
    }

    @Test fun updaterCheckboxAndCustomDanmakuDialogFollowBothThemes() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        c.get().setTheme(R.style.AppTheme)
        try {
            for(mode in TvStyle.Mode.entries) {
                TvStyle.apply(mode)
                val updater=AppUpdater(c.get()) { true }
                val social=TvSocial(c.get(),{null},{null})
                try {
                    // 只打开真实更新界面，不发起更新检查或下载。
                    field(updater,"active").setBoolean(updater,true)
                    updater.show(null); ShadowLooper.idleMainLooper()
                    val popup=ShadowAlertDialog.getLatestAlertDialog()
                    readable(popup)
                    val auto=views(popup.window!!.decorView).filterIsInstance<CheckBox>().single()
                    assertEquals(TvStyle.text,auto.currentTextColor)
                    popup.dismiss()
                    social.settings(View(c.get())); ShadowLooper.idleMainLooper()
                    val custom=ShadowDialog.getLatestDialog()
                    readable(custom)
                    custom.dismiss()
                } finally { updater.destroy(); social.destroy() }
            }
        } finally { c.pause().stop().destroy() }
    }

    @Test fun plainDialogCanRequestFeaturesAndRethemesNewViewsAfterShowing() {
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            for(mode in TvStyle.Mode.entries) {
                TvStyle.apply(mode)
                val d=AppDialogs.dialog(c.get())
                d.requestWindowFeature(Window.FEATURE_NO_TITLE)
                val content=LinearLayout(c.get())
                d.setContentView(content); d.show(); ShadowLooper.idleMainLooper()
                content.addView(TextView(c.get()).apply { text="新的评论内容"; setTextColor(if(mode==TvStyle.Mode.LIGHT) Color.WHITE else Color.BLACK) })
                readable(d)
                val background=d.window!!.decorView.background as GradientDrawable
                background.state=intArrayOf()
                assertEquals(255,background.alpha)
                assertEquals(SurfaceStyle.surface,Shadows.shadowOf(background).lastSetColor)
                d.dismiss()
            }
        } finally { c.pause().stop().destroy() }
    }
}
