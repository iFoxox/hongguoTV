// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.roundToInt

/** 用户明确选择移动页面时，拖动由原生层处理，避免网页遮罩吞掉平移手势。 */
internal class WebPagePanView(context: Context, private val pan: (Int, Int) -> Unit) : View(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var tracking = false

    init {
        isClickable = true
        contentDescription = "移动网页。移动到验证窗口后，请先关闭移动模式，再操作滑块。"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount != 1) {
            tracking = false
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = true
                dragging = false
                lastX = event.x
                lastY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dx = lastX - event.x
                val dy = lastY - event.y
                if (dragging || abs(dx) > slop || abs(dy) > slop) {
                    dragging = true
                    pan(dx.roundToInt(), dy.roundToInt())
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (tracking && !dragging && event.actionMasked == MotionEvent.ACTION_UP) performClick()
                tracking = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
