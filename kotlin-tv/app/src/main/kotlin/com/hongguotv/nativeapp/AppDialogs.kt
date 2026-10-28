// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** 所有应用弹窗在创建时选择当前外观，不再继承 Activity 固定的深色主题。 */
internal object AppDialogs {
    private fun theme() = if (TvStyle.mode == TvStyle.Mode.LIGHT) R.style.AppDialogLight else R.style.AppDialogDark

    fun builder(context: Context): AlertDialog.Builder = object : AlertDialog.Builder(context, theme()) {
        override fun create(): AlertDialog = super.create().also { popup ->
            popup.setOnShowListener { decorate(popup) }
        }
    }

    fun dialog(context: Context): Dialog = Dialog(context,theme())

    private fun decorate(popup: AlertDialog) {
        val context=popup.context
        val density=context.resources.displayMetrics.density
        fun dp(n:Int)=(n*density).toInt()
        val accent=if(TvStyle.touch) SurfaceStyle.accentText else TvStyle.accent
        fun colors(view:View) {
            when(view) {
                is EditText -> {
                    view.setTextColor(TvStyle.text)
                    view.setHintTextColor(TvStyle.mobileMuted)
                    view.backgroundTintList=ColorStateList.valueOf(accent)
                }
                is Button -> Unit // 下方统一设置标准操作按钮。
                is TextView -> {
                    val current=view.currentTextColor
                    view.setTextColor(when(current) {
                        TvStyle.accent, SurfaceStyle.accentText -> accent
                        TvStyle.muted, TvStyle.mobileMuted, Color.LTGRAY -> TvStyle.mobileMuted
                        else -> TvStyle.text
                    })
                    if(view is CheckedTextView) view.checkMarkTintList=ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),
                        intArrayOf(accent,TvStyle.mobileMuted),
                    )
                }
            }
            if(view is ViewGroup) for(i in 0 until view.childCount) colors(view.getChildAt(i))
        }
        popup.window?.let { window ->
            window.setBackgroundDrawable(SurfaceStyle.surface(context))
            window.setDimAmount(.32f)
            if(TvStyle.touch) window.setLayout(minOf(dp(560),(context.resources.displayMetrics.widthPixels*.92f).toInt()),-2)
            colors(window.decorView)
        }
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
