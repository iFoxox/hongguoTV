// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk=[23,26],qualifiers="w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DialogFirmwareBackgroundTest {
    @Test @Config(sdk=[29],qualifiers="w960dp-h540dp-land-night-mdpi")
    fun systemNightModeCannotForceInvertEitherAppDialogTheme() {
        val mode=TvStyle.mode
        val c=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            for(next in TvStyle.Mode.entries) {
                TvStyle.apply(next)
                val popup=AppDialogs.builder(c.get()).setTitle("账号登录").setMessage("扫码后在手机确认登录").show()
                try {
                    ShadowLooper.idleMainLooper()
                    val attrs=popup.context.obtainStyledAttributes(intArrayOf(android.R.attr.forceDarkAllowed))
                    try { assertFalse(attrs.getBoolean(0,true)) } finally { attrs.recycle() }
                    assertFalse(popup.window!!.decorView.isForceDarkAllowed)
                    val content=popup.findViewById<View>(android.R.id.content)!!
                    assertEquals(SurfaceStyle.surface,(content.background as ColorDrawable).color)
                } finally { popup.dismiss() }
            }
        } finally { c.pause().stop().destroy(); TvStyle.apply(mode) }
    }

    @Test fun unnamedFirmwareChromeIsRepairedBeforeDrawingWithoutChangingCustomContent() {
        val mode=TvStyle.mode
        val touch=TvStyle.touch
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        controller.get().setTheme(R.style.AppTheme)
        try {
            TvStyle.touch=false
            for(next in TvStyle.Mode.entries) {
                TvStyle.apply(next)
                val custom=FrameLayout(controller.get()).apply { setBackgroundColor(Color.WHITE) }
                val label=TextView(controller.get()).apply { text="手机扫码登录说明" }
                custom.addView(label)
                val popup=AppDialogs.builder(controller.get()).setTitle("红果账号").setView(custom)
                    .setNegativeButton("关闭",null).show()
                try {
                    ShadowLooper.idleMainLooper()
                    val content=popup.findViewById<ViewGroup>(android.R.id.content)!!
                    // 模拟固件在标准面板之外加一层无标准资源名的容器，并在显示后重新染黑。
                    val panel=content.getChildAt(0)
                    val params=panel.layoutParams
                    content.removeView(panel)
                    val firmware=FrameLayout(popup.context).apply { id=View.generateViewId() }
                    firmware.addView(panel)
                    content.addView(firmware,params)
                    repeat(2) {
                        firmware.setBackgroundColor(Color.BLACK)
                        firmware.backgroundTintList=ColorStateList.valueOf(Color.BLACK)
                        content.setBackgroundColor(Color.TRANSPARENT)
                        label.setTextColor(if(next==TvStyle.Mode.LIGHT) Color.WHITE else Color.BLACK)
                        content.viewTreeObserver.dispatchOnPreDraw()
                        assertNull("系统额外容器的染色必须清除",firmware.backgroundTintList)
                        assertEquals(Color.TRANSPARENT,(firmware.background as ColorDrawable).color)
                        assertEquals(SurfaceStyle.surface,(content.background as ColorDrawable).color)
                        assertEquals(TvStyle.text,label.currentTextColor)
                        assertEquals("保留二维码等应用内容的原始背景",Color.WHITE,(custom.background as ColorDrawable).color)
                    }
                    popup.dismiss(); ShadowLooper.idleMainLooper()
                    firmware.setBackgroundColor(Color.BLACK)
                    content.viewTreeObserver.dispatchOnPreDraw()
                    assertEquals("关闭后不得继续监听绘制",Color.BLACK,(firmware.background as ColorDrawable).color)
                } finally { popup.dismiss() }
            }
        } finally { controller.pause().stop().destroy(); TvStyle.touch=touch; TvStyle.apply(mode) }
    }
}
