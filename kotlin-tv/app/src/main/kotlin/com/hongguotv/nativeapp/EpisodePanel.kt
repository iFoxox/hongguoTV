// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 剧集选择面板：组织选集界面，并将选择结果交回播放流程。

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*

/** TV 一页 50 集；触屏保留现有 20 集分组。详情与播放选集共用。 */
internal object EpisodeGrouping {
    val size: Int get() = if (TvStyle.touch) 20 else 50
}

/** A remote-accessible side panel; selecting a number is the only action that changes episodes. */
class EpisodePanel(
    private val activity: Activity,
    private val count: Int,
    private val current: Int,
    private val locate: (Int, View, (Int) -> Unit) -> Unit,
    private val select: (Int) -> Unit,
    private val closed: () -> Unit,
    private val lastWatched: Int? = null,
    private val lastCompleted: Boolean = false,
) {
    private val dialog = AppDialogs.dialog(activity)
    private var group = current / EpisodeGrouping.size
    private var focused = current
    private var renderedTvColumns = 0

    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()

    private fun panelWidth(): Int = minOf(
        dp(if (TvStyle.touch) 600 else 520),
        (activity.resources.displayMetrics.widthPixels * if (TvStyle.touch) 1f else if (activity.resources.configuration.screenWidthDp < TvStyle.NARROW_DP) .94f else .8f).toInt(),
    )

    private fun tvColumns(): Int {
        val paint = TextPaint().apply { textSize = 14f * activity.resources.displayMetrics.scaledDensity }
        val digits = "8".repeat(maxOf(4, count.toString().length))
        val minimum = paint.measureText(digits) + dp(12)
        return ((panelWidth() - dp(28)) / minimum).toInt().coerceIn(1, 10)
    }

    private fun label(value: String, size: Float = 17f) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(TvStyle.text)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

    private fun button(value: String, active: Boolean = false, action: () -> Unit) =
        label(value, 16f).apply {
            id = View.generateViewId()
            gravity = Gravity.CENTER
            minHeight = dp(TvStyle.controlSize(46))
            if (TvStyle.touch) minimumWidth = dp(48)
            isFocusable = true
            isFocusableInTouchMode = !TvStyle.touch
            if (TvStyle.touch) foreground = TvStyle.pressHighlight(activity)
            isSelected = active
            fun paint(focus: Boolean) {
                setTextColor(if (focus || active) SurfaceStyle.accentText else TvStyle.text)
                background = TvStyle.card(activity,focus)
            }
            paint(false)
            setOnFocusChangeListener { _, hasFocus -> paint(hasFocus) }
            setOnClickListener { action() }
        }

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setOnDismissListener { closed() }
        render()
        dialog.show()
        onWindowChanged()
    }

    fun onWindowChanged() {
        if (!dialog.isShowing) return
        if (!TvStyle.touch && renderedTvColumns != tvColumns()) render()
        val phone = activity.resources.configuration.screenWidthDp < TvStyle.NARROW_DP
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(if (TvStyle.touch) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.END or Gravity.CENTER_VERTICAL)
            if (TvStyle.touch) setDimAmount(.35f)
            attributes =
                attributes.apply {
                    x =
                        if (phone) 0
                        else (activity.resources.displayMetrics.widthPixels * .05f).toInt()
                }
            setLayout(
                panelWidth(),
                (activity.resources.displayMetrics.heightPixels * if (TvStyle.touch && phone) .74f else .9f).toInt(),
            )
        }
    }

    fun dismiss() = dialog.dismiss()

    private fun renderMobile() {
        val content=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(14),dp(10),dp(14),dp(12))
            background=SurfaceStyle.surface(activity)
        }
        content.addView(View(activity).apply { background=TvStyle.shape(activity,TvStyle.outline) },
            LinearLayout.LayoutParams(dp(36),dp(4)).apply { gravity=Gravity.CENTER_HORIZONTAL; bottomMargin=dp(8) })
        val heading=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        heading.addView(label("选集",20f),LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(mobileButton(activity,"","close") { dismiss() }.apply { contentDescription="关闭选集"; setBackgroundColor(Color.TRANSPARENT) })
        content.addView(heading)
        val summary=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        summary.addView(label("共 $count 集 · 正在播放第 ${current+1} 集",12f),LinearLayout.LayoutParams(0,-2,1f))
        summary.addView(mobileButton(activity,"跳转") { anchor -> locate(focused,anchor) { target -> focused=target; group=target/20; render() } })
        content.addView(summary)
        val strip=HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled=false }
        val groups=LinearLayout(activity)
        var selectedGroup: View?=null
        (0..(count-1).coerceAtLeast(0)/20).forEach { index ->
            val tab=mobileButton(activity,"${index*20+1}—${minOf((index+1)*20,count)}",color=if(index==group) Color.WHITE else TvStyle.text) {
                group=index; render()
            }
            if(index==group) { SurfaceStyle.button(tab,accent=true); tab.setTextColor(SurfaceStyle.onAccent); selectedGroup=tab }
            groups.addView(tab,LinearLayout.LayoutParams(-2,-2).apply { rightMargin=dp(8) })
        }
        strip.addView(groups); content.addView(strip,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8); bottomMargin=dp(8) })
        strip.post { selectedGroup?.let { strip.scrollTo(it.left,0) } }
        val scroll=ScrollView(activity)
        val grid=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        (group*20 until minOf((group+1)*20,count)).toList().chunked(5).forEach { indexes ->
            val row=LinearLayout(activity)
            indexes.forEach { index ->
                row.addView(mobileButton(activity,"${index+1}",color=if(index==current) Color.WHITE else TvStyle.text) { select(index) }.apply {
                    contentDescription="第 ${index+1} 集" + if(index==current) "，当前播放" else ""
                    if(index==current) { SurfaceStyle.button(this,accent=true); setTextColor(SurfaceStyle.onAccent) }
                },LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
            }
            repeat(5-indexes.size) { row.addView(Space(activity),LinearLayout.LayoutParams(0,1,1f)) }
            grid.addView(row)
        }
        scroll.addView(grid); content.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        content.addView(label("点击集数立即播放",12f).apply { gravity=Gravity.CENTER; setTextColor(TvStyle.muted) })
        dialog.setContentView(content)
    }

    private fun render() {
        if (TvStyle.touch) { renderMobile(); return }

        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                SurfaceStyle.card(this)
            }
        content.addView(label("选集  /  正在看第 ${current+1} 集", 20f))
        val actions = LinearLayout(activity)
        val close = button("关闭") { dismiss() }
        lateinit var jump: TextView
        jump =
            button("跳转集数") {
                locate(focused, jump) { target ->
                    focused = target
                    group = target / EpisodeGrouping.size
                    render()
                }
            }
        listOf(close, jump).forEach {
            actions.addView(
                it,
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                },
            )
        }
        content.addView(actions)
        val paging = LinearLayout(activity)
        val previous =
            button("‹ 上一组") {
                    group--
                    focused = group * EpisodeGrouping.size
                    render()
                }
                .apply {
                    isEnabled = group > 0
                    isFocusable = isEnabled
                    alpha = if (isEnabled) 1f else .4f
                }
        val next =
            button("下一组 ›") {
                    group++
                    focused = group * EpisodeGrouping.size
                    render()
                }
                .apply {
                    isEnabled = (group + 1) * EpisodeGrouping.size < count
                    isFocusable = isEnabled
                    alpha = if (isEnabled) 1f else .4f
                }
        listOf(previous, next).forEach {
            paging.addView(
                it,
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                },
            )
        }
        content.addView(paging)
        content.addView(label("第 ${group*EpisodeGrouping.size+1}—${minOf((group+1)*EpisodeGrouping.size,count)} 集 / 共 $count 集", 14f))
        val scroll = ScrollView(activity)
        val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(grid)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val cells = mutableListOf<TextView>()
        val columns = tvColumns()
        renderedTvColumns = columns
        (group * EpisodeGrouping.size until minOf((group + 1) * EpisodeGrouping.size, count)).toList().chunked(columns).forEach { indexes ->
            val row = LinearLayout(activity).apply { isBaselineAligned = false }
            indexes.forEach { index ->
                val cell =
                    button(
                        "${index+1}" +
                            when {
                                index == current -> "\n当前"
                                index == lastWatched -> if (lastCompleted) "\n已看" else "\n上次"
                                else -> ""
                            },
                        index == current,
                    ) {
                        select(index)
                    }
                cell.textSize = 14f
                cell.includeFontPadding = false
                cell.minHeight = dp(38)
                cell.maxLines = 2
                cell.setPadding(dp(2), dp(2), dp(2), dp(2))
                val paintFocus = cell.onFocusChangeListener
                cell.setOnFocusChangeListener { view, hasFocus ->
                    paintFocus?.onFocusChange(view, hasFocus)
                    if (hasFocus) focused = index
                }
                cell.contentDescription = "第 ${index+1} 集" + when {
                    index == current -> "，当前播放"
                    index == lastWatched -> if (lastCompleted) "，已看" else "，上次观看"
                    else -> ""
                }
                row.addView(
                    cell,
                    LinearLayout.LayoutParams(0, -2, 1f).apply {
                        setMargins(dp(3), dp(2), dp(3), dp(2))
                    },
                )
                cells += cell
            }
            repeat(columns - indexes.size) {
                row.addView(Space(activity), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
            grid.addView(row)
        }
        cells.forEachIndexed { i, cell ->
            cell.nextFocusLeftId = cells[if (i % columns == 0) i else i - 1].id
            cell.nextFocusRightId = cells[if (i % columns == columns - 1 || i == cells.lastIndex) i else i + 1].id
            cell.nextFocusUpId =
                if (i >= columns) cells[i - columns].id
                else if (i < (columns + 1) / 2 && previous.isFocusable) previous.id
                else if (next.isFocusable) next.id else jump.id
            cell.nextFocusDownId =
                cells[
                        if (i + columns < cells.size) i + columns
                        else if (i / columns < cells.lastIndex / columns) cells.lastIndex else i]
                    .id
        }
        previous.nextFocusUpId = close.id
        next.nextFocusUpId = jump.id
        previous.nextFocusDownId = cells.first().id
        next.nextFocusDownId = cells[minOf(columns - 1, cells.lastIndex)].id
        close.nextFocusDownId = if (previous.isFocusable) previous.id else cells.first().id
        jump.nextFocusDownId =
            if (next.isFocusable) next.id else cells[minOf(columns - 1, cells.lastIndex)].id
        close.nextFocusLeftId = close.id
        close.nextFocusRightId = jump.id
        jump.nextFocusLeftId = close.id
        jump.nextFocusRightId = jump.id
        dialog.setContentView(content)
        cells[(focused - group * EpisodeGrouping.size).coerceIn(0, cells.lastIndex)].requestFocus()
    }
}
