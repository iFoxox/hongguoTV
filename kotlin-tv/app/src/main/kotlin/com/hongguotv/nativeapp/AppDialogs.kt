// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
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

    private class Styling(private val popup: Dialog) : View.OnAttachStateChangeListener,
        ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnPreDrawListener {
        private var observer: ViewTreeObserver? = null
        private var bound = false
        fun bind() {
            if (bound) return
            popup.window?.decorView?.addOnAttachStateChangeListener(this)
            bound = true
        }
        fun start() {
            decorate(popup)
            fitOptions(popup)
            if (observer?.isAlive == true) return
            popup.window?.decorView?.viewTreeObserver?.let {
                observer = it
                it.addOnGlobalLayoutListener(this)
                it.addOnPreDrawListener(this)
            }
        }
        override fun onViewAttachedToWindow(view: View) = start()
        fun stop() {
            observer?.takeIf { it.isAlive }?.let {
                it.removeOnGlobalLayoutListener(this)
                it.removeOnPreDrawListener(this)
            }
            observer = null
        }
        override fun onViewDetachedFromWindow(view: View) = stop()
        override fun onGlobalLayout() {
            fitOptions(popup)
            // 列表回收及网络加载会在 onShow 之后创建新文字，不能只着色首次的视图树。
            chrome(popup)
            popup.window?.decorView?.let(::colors)
        }
        override fun onPreDraw(): Boolean {
            // 固件可能在布局后重设背景；绘制前再校正，避免显示黑底深字的一帧。
            chrome(popup)
            popup.window?.decorView?.let(::colors)
            return true
        }
    }

    private fun fitOptions(popup: Dialog) {
        if (TvStyle.touch || popup !is AlertDialog) return
        val list = popup.listView ?: return
        val adapter = list.adapter ?: return
        if (adapter.count == 0) return
        val window = popup.window ?: return
        val decor = window.decorView
        val metrics = popup.context.resources.displayMetrics
        fun dp(n: Int) = (n * metrics.density).toInt()
        val width = (list.width.takeIf { it > 0 }
            ?: window.attributes.width.takeIf { it > 0 }
            ?: (metrics.widthPixels * .88f).toInt()) - list.paddingLeft - list.paddingRight
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(1), View.MeasureSpec.EXACTLY)
        // ListView 在高度未指定时可能只测量第一行；显式累计全部选项，避免大屏菜单藏项。
        var height = list.paddingTop + list.paddingBottom
        for (i in 0 until adapter.count) {
            val row = adapter.getView(i, null, list)
            row.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            height += row.measuredHeight
            if (i > 0) height += list.dividerHeight
        }
        val chromeHeight = (decor.height - list.height).coerceAtLeast(dp(112))
        val available = ((metrics.heightPixels * .94f).toInt() - chromeHeight).coerceAtLeast(dp(48))
        val target = minOf(height, available)
        list.isVerticalScrollBarEnabled = height > available
        list.isScrollbarFadingEnabled = false
        if (list.layoutParams.height != target) list.layoutParams = list.layoutParams.apply { this.height = target }
        // 固件可能同时把列表外层设为一行高度或 0dp+weight，列表自己变高仍会被裁切。
        var parent = list.parent as? ViewGroup
        while (parent != null && parent !== decor && parent.id != android.R.id.content) {
            val params = parent.layoutParams
            if (params != null && params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                if (params is LinearLayout.LayoutParams) params.weight = 0f
                parent.layoutParams = params
            }
            parent = parent.parent as? ViewGroup
        }
    }

    private fun background(view: View, color: Int) {
        if (view.backgroundTintList != null) view.backgroundTintList = null
        if ((view.background as? ColorDrawable)?.color != color) view.setBackgroundColor(color)
    }

    private fun chrome(popup: Dialog) {
        val decor = popup.window?.decorView ?: return
        // 应用已自行提供浅／深主题，不能再由 Android 10+ 按系统夜间模式反色。
        if (Build.VERSION.SDK_INT >= 29) decor.isForceDarkAllowed = false
        if (decor.backgroundTintList != null) decor.backgroundTintList = null
        val content = decor.findViewById<View>(android.R.id.content)
        content?.let { background(it, SurfaceStyle.surface) }
        if (popup !is AlertDialog) return
        fun panels(view: View) {
            if (view !is ViewGroup) return
            if (view !== decor && view !== content) background(view, Color.TRANSPARENT)
            // custom 是公开的 Android ID。应用自己的二维码、按钮及内容底色必须保留；
            // ListView 的 selector 和行背景也保留，以免破坏遥控器焦点与勾选反馈。
            if (view.id == android.R.id.custom || view is ListView) return
            for (i in 0 until view.childCount) panels(view.getChildAt(i))
        }
        panels(decor)
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
            // 内容区也必须有不透明底板；不能只处理窗口或依赖固件内部的私有面板名称。
            chrome(popup)
            if (popup is AlertDialog) {
                if(TvStyle.touch) window.setLayout(minOf(dp(560),(context.resources.displayMetrics.widthPixels*.92f).toInt()),-2)
                else if (popup.listView != null) window.setLayout(
                    minOf(dp(760),(context.resources.displayMetrics.widthPixels*.88f).toInt()),-2)
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
