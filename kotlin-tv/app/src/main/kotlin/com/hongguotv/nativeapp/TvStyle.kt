// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// TV 界面的公共主题调色板及形状；调整公共外观时优先查看此处。
// 双主题：默认浅色（Infuse 风格），可在「设置 → 外观」切换深色并持久化。

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View

/** Shared native colors and shapes; no blur, bitmap backgrounds or focus animation. */
object TvStyle {
    enum class Mode { LIGHT, DARK }

    // 保留历史布局的焦点兼容开关；当前 TV 的所有屏宽统一使用顶部导航。
    var sideNav = false

    // 触屏设备（手机 / 平板）使用触屏版文案与窗口边距；电视遥控器保持原交互。
    var touch = false

    // 手机竖屏阈值：低于该宽度时纵向堆叠、压缩导航与操作行。
    const val NARROW_DP = 600

    /** 触屏控件至少 48dp；电视保持现有尺寸。 */
    fun controlSize(remoteDp: Int) = if (touch) maxOf(48, remoteDp) else remoteDp

    val mobileMuted: Int
        get() = if (mode == Mode.LIGHT) Color.rgb(0x62, 0x62, 0x6A) else Color.rgb(0xB6, 0xB6, 0xBE)

    fun isNarrow(widthDp: Int) = widthDp < NARROW_DP

    /** 海报墙列数：按可用宽度自适应；手机竖屏 3 列、平板 4 列、电视 6 列。 */
    fun posterColumns(widthDp: Int): Int =
        when {
            widthDp < 520 -> 3
            widthDp < 900 -> 4
            else -> 6
        }

    /** 触屏设备的操作提示与遥控器提示不同，按当前设备选择对应文案。 */
    fun hint(remoteText: String, touchText: String) = if (touch) touchText else remoteText

    /**
     * 触屏按压反馈：按下时叠加一层半透明强调色，不做 Ripple 扩散动画。
     * 静态状态切换即可给出清晰反馈，也避免低端机/无刷新时钟环境下动画帧堆积。
     */
    fun pressHighlight(context: Context): Drawable =
        StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                GradientDrawable().apply {
                    setColor(Color.argb(0x33, 0xFF, 0x6B, 0x2C))
                    cornerRadius = 10 * context.resources.displayMetrics.density
                },
            )
            addState(intArrayOf(), android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }

    private const val PREFS = "tv_style"
    private const val KEY_MODE = "theme_mode"

    var mode: Mode = Mode.LIGHT
        private set

    var background = Color.rgb(0xE5, 0xE8, 0xEF)
        private set
    var surface = Color.rgb(0xFA, 0xFC, 0xFE)
        private set
    var raised = Color.rgb(0xEB, 0xEF, 0xF5)
        private set
    var text = Color.rgb(0x1D, 0x1D, 0x1F)
        private set
    var muted = Color.rgb(0x62, 0x62, 0x6A)
        private set
    var outline = Color.rgb(0xDF, 0xE3, 0xEA)
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
        background = SurfaceStyle.background
        surface = SurfaceStyle.surface
        raised = SurfaceStyle.raised
        outline = SurfaceStyle.divider
        text = if (next == Mode.LIGHT) Color.rgb(0x1D,0x1D,0x1F) else Color.rgb(0xF5,0xF5,0xF7)
        muted = mobileMuted
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

    fun card(context: Context, focused: Boolean): Drawable =
        SurfaceStyle.surface(context, focused = !touch && focused)

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
