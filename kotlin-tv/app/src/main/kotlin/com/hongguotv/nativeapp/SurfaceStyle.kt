// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.TextView

/** TV 与手机共用的冷灰底、纸白卡片和柔和按钮；TV 额外保留清晰的遥控器焦点。 */
internal object SurfaceStyle {
    private val dark get() = TvStyle.mode == TvStyle.Mode.DARK
    val background get() = if (dark) Color.rgb(0x18,0x1C,0x23) else Color.rgb(0xE5,0xE8,0xEF)
    val surface get() = if (dark) Color.rgb(0x28,0x2E,0x38) else Color.rgb(0xFA,0xFC,0xFE)
    val raised get() = if (dark) Color.rgb(0x35,0x3D,0x49) else Color.rgb(0xEB,0xEF,0xF5)
    val divider get() = if (dark) Color.rgb(0x37,0x3F,0x4B) else Color.rgb(0xDF,0xE3,0xEA)
    val accentText get() = if (dark) Color.rgb(0xFF,0xAB,0x70) else Color.rgb(0xAE,0x42,0x13)
    val onAccent = Color.rgb(0x40,0x20,0x0F)

    fun chrome(): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (dark) intArrayOf(Color.rgb(0x2D,0x34,0x3F),Color.rgb(0x22,0x28,0x31))
        else intArrayOf(Color.rgb(0xF8,0xFA,0xFD),Color.rgb(0xEC,0xEF,0xF5)),
    )

    fun surface(context: Context, focused: Boolean = false): Drawable = SoftSurface(context, accent=false, circle=false, card=true, forcedFocus=focused)

    fun button(view: View, accent: Boolean = false, circle: Boolean = false, highlightSelection: Boolean = false) {
        view.background = SoftSurface(view.context, accent, circle, card=false, selectionFill=highlightSelection)
        view.foreground = null
        view.elevation = 0f
    }

    /** 顶栏只有选中项着色，保留点击反馈，不把整排分类画成凸起按钮。 */
    fun headerTab(view: View) {
        fun shape(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 10f * view.resources.displayMetrics.density
        }
        val selected = if (dark) Color.rgb(0x50,0x39,0x2B) else Color.rgb(0xFF,0xEA,0xDB)
        val surface = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), shape(selected))
            addState(intArrayOf(android.R.attr.state_focused), shape(raised))
            addState(intArrayOf(), shape(Color.TRANSPARENT))
        }
        view.background = RippleDrawable(ColorStateList.valueOf(raised), surface, shape(Color.WHITE))
        view.foreground = null
        view.elevation = 0f
        if (view is TextView) view.setTextColor(ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(accentText, TvStyle.text),
        ))
    }

    fun card(view: View) {
        view.background = SoftSurface(view.context, accent=false, circle=false, card=true)
        view.elevation = 0f
    }

    /** 阴影与高光绘在控件边界内；API 23 不依赖模糊、硬件阴影或额外动画。 */
    private class SoftSurface(context: Context, private val accent: Boolean, private val circle: Boolean, private val card: Boolean, private val forcedFocus: Boolean = false, private val selectionFill: Boolean = false) : Drawable() {
        private val density = context.resources.displayMetrics.density
        private val compact = TvStyle.touch && !card
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val area = RectF()
        private var pressed = false
        private var focused = forcedFocus
        private var selected = false
        private var enabled = true
        private var opacity = 255
        private var filter: ColorFilter? = null
        override fun isStateful() = true
        override fun onStateChange(state: IntArray): Boolean {
            val nextPressed = android.R.attr.state_pressed in state
            val nextEnabled = android.R.attr.state_enabled in state
            val nextFocus = forcedFocus || android.R.attr.state_focused in state
            val nextSelected = android.R.attr.state_selected in state
            if (nextPressed == pressed && nextEnabled == enabled && nextFocus == focused && nextSelected == selected) return false
            pressed=nextPressed; enabled=nextEnabled; focused=nextFocus; selected=nextSelected
            invalidateSelf(); return true
        }
        override fun draw(canvas: Canvas) {
            val gap = (if(card) 3f else 4f) * density
            // 圆形与方形图标保持对称边界；文字按钮仅收紧纵向表面。
            val verticalGap = if(compact && !circle && bounds.width()!=bounds.height()) 7f*density else gap
            area.set(bounds.left+gap,bounds.top+verticalGap,bounds.right-gap,bounds.bottom-verticalGap)
            if(area.width()<=0 || area.height()<=0) return
            if(pressed) area.inset(density*.6f,density*.6f)
            val radius = if(circle) minOf(area.width(),area.height())/2 else (if(card) 12f else 11f)*density
            paint.style=Paint.Style.FILL; paint.shader=null; paint.colorFilter=filter
            // 逐层降低不透明度形成柔和的下方投影，避免零偏移的发光边框。
            for(layer in 6 downTo 1) {
                val spread=layer*density*.42f
                val shadow=RectF(area).apply { inset(-spread,-spread); offset(0f,density*1.3f) }
                paint.color=if(dark) Color.BLACK else Color.rgb(0x8E,0x99,0xAC)
                paint.alpha=((if(pressed) 2 else 4)*opacity/255f).toInt()
                canvas.drawRoundRect(shadow,radius+spread,radius+spread,paint)
            }
            val top: Int
            val bottom: Int
            if(accent) {
                top=Color.rgb(0xFA,0x8A,0x37); bottom=Color.rgb(0xDE,0x5B,0x15)
            } else if((!TvStyle.touch || selectionFill) && selected && !dark) {
                top=Color.rgb(0xFF,0xFA,0xF4); bottom=Color.rgb(0xF4,0xE2,0xD4)
            } else if(selectionFill && selected && dark) {
                top=Color.rgb(0x50,0x39,0x2B); bottom=Color.rgb(0x3E,0x2F,0x28)
            } else if(dark) {
                top=Color.rgb(0x35,0x3D,0x49); bottom=Color.rgb(0x26,0x2C,0x36)
            } else {
                top=Color.WHITE; bottom=if(card) Color.rgb(0xF9,0xFB,0xFE) else Color.rgb(0xEB,0xEF,0xF5)
            }
            paint.shader=LinearGradient(0f,area.top,0f,area.bottom,
                if(pressed) intArrayOf(bottom,top) else intArrayOf(top,bottom),null,Shader.TileMode.CLAMP)
            paint.alpha=if(enabled || card) opacity else (opacity*.45f).toInt()
            canvas.drawRoundRect(area,radius,radius,paint)
            paint.shader=null
            paint.style=Paint.Style.STROKE; paint.strokeWidth=density*.65f
            paint.color=if(accent) Color.argb(130,255,205,155) else if(dark) Color.rgb(0x43,0x4D,0x5C) else Color.rgb(0xD6,0xDC,0xE5)
            paint.alpha=if(enabled || card) opacity else (opacity*.45f).toInt()
            canvas.drawRoundRect(area,radius,radius,paint)
            if (!TvStyle.touch && focused && enabled) {
                // 海报有 4dp 内边距，外侧描边始终露出，避免被子图像盖住。
                val focusArea=RectF(bounds).apply { inset(density*1.7f,density*1.7f) }
                paint.color=TvStyle.accent; paint.alpha=opacity; paint.strokeWidth=density*3f
                canvas.drawRoundRect(focusArea,radius+density,radius+density,paint)
                if(accent) {
                    paint.color=Color.WHITE; paint.strokeWidth=density*1.1f
                    canvas.drawRoundRect(area,radius,radius,paint)
                }
            }
            paint.style=Paint.Style.FILL
        }
        override fun setAlpha(alpha: Int) { opacity=alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { filter=colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
}
