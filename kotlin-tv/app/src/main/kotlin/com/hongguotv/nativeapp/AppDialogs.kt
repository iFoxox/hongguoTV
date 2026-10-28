// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.*

/** 所有应用弹窗在创建时选择当前外观，不再继承 Activity 固定的深色主题。 */
internal object AppDialogs {
    private fun theme() = if (TvStyle.mode == TvStyle.Mode.LIGHT) R.style.AppDialogLight else R.style.AppDialogDark

    fun builder(context: Context): AlertDialog.Builder = object : AlertDialog.Builder(context, theme()) {
        override fun create(): AlertDialog = super.create().also { popup ->
            val styling = Styling(popup)
            popup.setOnShowListener { styling.bind(); styling.start() }
        }
    }

    fun dialog(context: Context): Dialog = object : Dialog(context, theme()) {
        private val styling = Styling(this)
        override fun onStart() { super.onStart(); styling.bind(); styling.start() }
        override fun onStop() { styling.stop(); super.onStop() }
    }

    private class Styling(private val popup: Dialog) : View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
        private var observer: ViewTreeObserver? = null
        private var bound = false
        fun bind() {
            if (bound) return
            popup.window?.decorView?.addOnAttachStateChangeListener(this)
            bound = true
        }
        fun start() {
            decorate(popup)
            if (observer?.isAlive == true) return
            popup.window?.decorView?.viewTreeObserver?.let {
                observer = it
                it.addOnGlobalLayoutListener(this)
            }
        }
        override fun onViewAttachedToWindow(view: View) = start()
        fun stop() {
            observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this)
            observer = null
        }
        override fun onViewDetachedFromWindow(view: View) = stop()
        override fun onGlobalLayout() {
            // 列表回收及网络加载会在 onShow 之后创建新文字，不能只着色首次的视图树。
            popup.window?.decorView?.let(::colors)
        }
    }

    private fun colors(view: View) {
        val accent = SurfaceStyle.accentText
        when (view) {
            is EditText -> {
                if (view.currentTextColor != TvStyle.text) view.setTextColor(TvStyle.text)
                if (view.currentHintTextColor != TvStyle.mobileMuted) view.setHintTextColor(TvStyle.mobileMuted)
                if (view.backgroundTintList?.defaultColor != accent) view.backgroundTintList = ColorStateList.valueOf(accent)
            }
            // CheckBox 也继承 Button，必须先处理，否则会漏掉使用 Activity 深色主题创建的文字。
            is CompoundButton -> {
                if (view.currentTextColor != TvStyle.text) view.setTextColor(TvStyle.text)
                if (view.buttonTintList?.defaultColor != TvStyle.mobileMuted)
                    view.buttonTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(accent, TvStyle.mobileMuted),
                    )
            }
            is Button -> Unit // 保留自定义键盘的焦点颜色；标准操作按钮由 decorate 设置。
            is TextView -> {
                val target = when (view.currentTextColor) {
                    SurfaceStyle.onAccent -> SurfaceStyle.onAccent
                    TvStyle.accent, SurfaceStyle.accentText -> accent
                    TvStyle.muted, TvStyle.mobileMuted, Color.LTGRAY -> TvStyle.mobileMuted
                    else -> TvStyle.text
                }
                if (view.currentTextColor != target) view.setTextColor(target)
                if (view is CheckedTextView && view.checkMarkTintList?.defaultColor != TvStyle.mobileMuted)
                    view.checkMarkTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(accent, TvStyle.mobileMuted),
                    )
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) colors(view.getChildAt(i))
    }

    private fun decorate(popup: Dialog) {
        val context=popup.context
        val density=context.resources.displayMetrics.density
        fun dp(n:Int)=(n*density).toInt()
        val accent=SurfaceStyle.accentText
        popup.window?.let { window ->
            // Window 的 drawable state 可能没有 state_enabled；不能复用会因此降至
            // 45% 透明度的按钮／卡片表面，否则底图透入后浅色正文会失去对比度。
            window.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(SurfaceStyle.surface)
                cornerRadius=dp(16).toFloat()
            })
            window.setDimAmount(.32f)
            // 部分 TV 固件会给 AlertDialog 内部面板单独铺系统底色，遮住 Window 的主题底板。
            window.decorView.findViewById<View>(android.R.id.content)?.setBackgroundColor(Color.TRANSPARENT)
            if (popup is AlertDialog) {
                listOf("parentPanel", "topPanel", "contentPanel", "customPanel", "buttonPanel").forEach { name ->
                    val id = context.resources.getIdentifier(name, "id", "android")
                    if (id != 0) window.decorView.findViewById<View>(id)?.setBackgroundColor(
                        if (name == "parentPanel") SurfaceStyle.surface else Color.TRANSPARENT
                    )
                }
                if(TvStyle.touch) window.setLayout(minOf(dp(560),(context.resources.displayMetrics.widthPixels*.92f).toInt()),-2)
            }
            colors(window.decorView)
        }
        if (popup !is AlertDialog) return
        listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL).forEach { which ->
            popup.getButton(which)?.let { button ->
                button.setTextColor(if(which==AlertDialog.BUTTON_NEGATIVE) TvStyle.mobileMuted else accent)
                SurfaceStyle.button(button)
                if(TvStyle.touch) {
                    button.minHeight=dp(48)
                    button.minWidth=dp(64)
                    button.textSize=14f
                    button.isAllCaps=false
                    button.isFocusableInTouchMode=false
                    button.setPadding(dp(14),dp(10),dp(14),dp(10))
                }
            }
        }
    }
}
