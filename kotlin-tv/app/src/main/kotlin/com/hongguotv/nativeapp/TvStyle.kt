// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// TV 界面的公共主题调色板及形状；调整公共外观时优先查看此处。
// 双主题：默认浅色（Infuse 风格），可在「设置 → 外观」切换深色并持久化。

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

/** Shared native colors and shapes; no blur, bitmap backgrounds or focus animation. */
object TvStyle {
    enum class Mode { LIGHT, DARK }

    // 宽屏使用飞牛影视式左侧边栏导航，窄屏退回顶部导航；由 MainActivity 按屏宽设置。
    var sideNav = false

    private const val PREFS = "tv_style"
    private const val KEY_MODE = "theme_mode"

    var mode: Mode = Mode.LIGHT
        private set

    var background = Color.rgb(0xF6, 0xF6, 0xF8)
        private set
    var surface = Color.rgb(0xFF, 0xFF, 0xFF)
        private set
    var raised = Color.rgb(0xEC, 0xEC, 0xF0)
        private set
    var text = Color.rgb(0x1D, 0x1D, 0x1F)
        private set
    var muted = Color.rgb(0x86, 0x86, 0x8B)
        private set
    var outline = Color.rgb(0xE3, 0xE3, 0xE8)
        private set

    // 橙色与品牌果实的暖橙一致，是全应用唯一强调色。
    val accent = Color.rgb(0xFF, 0x6B, 0x2C)
    val onAccent = Color.WHITE
    val accentSoft = Color.argb(0x22, 0xFF, 0x6B, 0x2C)

    const val POSTER_ASPECT = .7f

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_MODE, null)
        apply(if (stored == Mode.DARK.name) Mode.DARK else Mode.LIGHT)
    }

    fun apply(next: Mode) {
        mode = next
        if (next == Mode.LIGHT) {
            background = Color.rgb(0xF6, 0xF6, 0xF8)
            surface = Color.rgb(0xFF, 0xFF, 0xFF)
            raised = Color.rgb(0xEC, 0xEC, 0xF0)
            text = Color.rgb(0x1D, 0x1D, 0x1F)
            muted = Color.rgb(0x86, 0x86, 0x8B)
            outline = Color.rgb(0xE3, 0xE3, 0xE8)
        } else {
            background = Color.rgb(0x14, 0x14, 0x16)
            surface = Color.rgb(0x1F, 0x1F, 0x23)
            raised = Color.rgb(0x2A, 0x2A, 0x30)
            text = Color.rgb(0xF5, 0xF5, 0xF7)
            muted = Color.rgb(0x98, 0x98, 0x9F)
            outline = Color.rgb(0x2E, 0x2E, 0x33)
        }
    }

    /** 切换主题并持久化，返回切换后的模式；界面由调用方重建。 */
    fun toggle(context: Context): Mode {
        val next = if (mode == Mode.LIGHT) Mode.DARK else Mode.LIGHT
        apply(next)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, next.name)
            .apply()
        return next
    }

    fun modeLabel(): String = if (mode == Mode.LIGHT) "浅色" else "深色"

    fun shape(context: Context, color: Int, border: Int = Color.TRANSPARENT, radius: Int = 10) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * context.resources.displayMetrics.density
            setStroke((2 * context.resources.displayMetrics.density).toInt(), border)
        }

    fun card(context: Context, focused: Boolean) =
        shape(
            context,
            surface,
            if (focused) accent else outline,
        )

    /** 导航项指向内容入口：侧栏用右、顶栏用下。 */
    fun navInto(nav: List<View>, entry: View) = navIntoId(nav, entry.id)

    /** 同上，但直接使用目标控件 id（用于 firstId() 之类的间接入口）。 */
    fun navIntoId(nav: List<View>, entryId: Int) {
        nav.forEach {
            if (sideNav) it.nextFocusRightId = entryId else it.nextFocusDownId = entryId
        }
    }

    /** 内容控件指回当前导航项：侧栏用左、顶栏用上。 */
    fun contentToNav(view: View, navItem: View) {
        if (sideNav) view.nextFocusLeftId = navItem.id else view.nextFocusUpId = navItem.id
    }
}
