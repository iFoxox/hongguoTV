// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

/** 手机选项自然换行或等宽分列；每一项完整测量，不靠横向裁切隐藏类别。 */
internal class MobileChoices(context: Context, private val columns: Int = 0) : ViewGroup(context) {
    private val positions = mutableMapOf<View, Rect>()
    private val gap = (2 * resources.displayMetrics.density).toInt()

    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        positions.clear()
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
            resources.displayMetrics.widthPixels else MeasureSpec.getSize(widthMeasureSpec)
        val available = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val fixedWidth = if (columns > 0) ((available - gap * (columns - 1)) / columns).coerceAtLeast(0) else 0
        var y = paddingTop
        var rowWidth = 0
        val row = mutableListOf<View>()
        fun finishRow() {
            if (row.isEmpty()) return
            val height = row.maxOf { it.measuredHeight }
            var x = paddingLeft
            row.forEach { child ->
                // 同行较长文案换行时，短按钮也占满这一行，避免表面高低错位。
                if (child.measuredHeight != height) child.measure(
                    MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
                )
                val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - paddingRight - (x - paddingLeft) - child.measuredWidth else x
                val top = y + (height - child.measuredHeight) / 2
                positions[child] = Rect(left,top,left+child.measuredWidth,top+child.measuredHeight)
                x += child.measuredWidth + gap
            }
            y += height + gap
            row.clear(); rowWidth=0
        }
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            child.measure(MeasureSpec.makeMeasureSpec(if(columns>0) fixedWidth else available,
                if(columns>0) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
            val needed = child.measuredWidth + if(row.isEmpty()) 0 else gap
            if (row.isNotEmpty() && ((columns>0 && row.size==columns) || rowWidth+needed>available)) finishRow()
            rowWidth += child.measuredWidth + if(row.isEmpty()) 0 else gap
            row += child
        }
        finishRow()
        val height = (y - if(positions.isEmpty()) 0 else gap) + paddingBottom
        setMeasuredDimension(resolveSize(width,widthMeasureSpec),resolveSize(height,heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        positions.forEach { (child,rect) -> child.layout(rect.left,rect.top,rect.right,rect.bottom) }
    }
}
