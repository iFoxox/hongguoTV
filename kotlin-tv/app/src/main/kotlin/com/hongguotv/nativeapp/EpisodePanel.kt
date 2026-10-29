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

/** TV 一页 120 集；触屏保留现有 20 集分组。详情与播放选集共用。 */
internal object EpisodeGrouping {
    val size: Int get() = if (TvStyle.touch) 20 else 120
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
    private var focusOnGroups = false

    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()

    private fun panelWidth(): Int = minOf(
        dp(if (TvStyle.touch) 600 else 900),
        (activity.resources.displayMetrics.widthPixels * if (TvStyle.touch) 1f else .94f).toInt(),
    )

    private fun groupWidth() = minOf(dp(148), (panelWidth() * .26f).toInt())

    private fun tvColumns(): Int {
        val paint = TextPaint().apply { textSize = 14f * activity.resources.displayMetrics.scaledDensity }
        val digits = "8".repeat(maxOf(4, count.toString().length))
        val minimum = paint.measureText(digits) + dp(12)
        return ((panelWidth() - dp(40) - groupWidth()) / minimum).toInt().coerceIn(1, 15)
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
        if (!TvStyle.touch && renderedTvColumns != tvColumns()) render(focusOnGroups)
        val phone = activity.resources.configuration.screenWidthDp < TvStyle.NARROW_DP
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(if (TvStyle.touch) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.CENTER)
            if (TvStyle.touch) setDimAmount(.35f)
            attributes =
                attributes.apply {
                    x =
                        if (!TvStyle.touch || phone) 0
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

    private fun render(focusGroup: Boolean = false) {
        if (TvStyle.touch) { renderMobile(); return }

        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                SurfaceStyle.card(this)
            }
        val actions = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(label("选集 · 正在看第 ${current+1} 集", 20f), LinearLayout.LayoutParams(0, -2, 1f))
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
        listOf(jump, close).forEach {
            actions.addView(
                it,
                LinearLayout.LayoutParams(-2, -2).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                },
            )
        }
        content.addView(actions)
        val body = LinearLayout(activity)
        val groupScroll = ScrollView(activity).apply { isVerticalScrollBarEnabled = false }
        val groupList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        groupScroll.addView(groupList)
        body.addView(groupScroll, LinearLayout.LayoutParams(groupWidth(), -1))
        val right = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(right, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(12) })
        content.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val groupTabs = mutableListOf<TextView>()
        var ready = false
        (0..(count - 1) / EpisodeGrouping.size).forEach { index ->
            val tab = button("${index * EpisodeGrouping.size + 1}—${minOf((index + 1) * EpisodeGrouping.size, count)}", index == group) {
                if (group != index) { group = index; focused = group * EpisodeGrouping.size }
                render()
            }.apply {
                textSize = 14f
                contentDescription = "第 ${index * EpisodeGrouping.size + 1} 至 ${minOf((index + 1) * EpisodeGrouping.size, count)} 集分组"
            }
            val paintFocus = tab.onFocusChangeListener
            tab.setOnFocusChangeListener { view, hasFocus ->
                paintFocus?.onFocusChange(view, hasFocus)
                if (hasFocus && ready) {
                    focusOnGroups = true
                    if (group != index) {
                        group = index
                        focused = group * EpisodeGrouping.size
                        render(true)
                    }
                }
            }
            groupList.addView(tab, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
            groupTabs += tab
        }
        right.addView(label("第 ${group*EpisodeGrouping.size+1}—${minOf((group+1)*EpisodeGrouping.size,count)} 集 / 共 $count 集", 14f))
        val scroll = ScrollView(activity)
        val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(grid)
        right.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
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
                    if (hasFocus) {
                        focused = index
                        focusOnGroups = false
                        groupTabs[group].nextFocusRightId = view.id
                    }
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
            cell.nextFocusLeftId = if (i % columns == 0) groupTabs[group].id else cells[i - 1].id
            cell.nextFocusRightId = cells[if (i % columns == columns - 1 || i == cells.lastIndex) i else i + 1].id
            cell.nextFocusUpId =
                if (i >= columns) cells[i - columns].id else jump.id
            cell.nextFocusDownId =
                cells[
                        if (i + columns < cells.size) i + columns
                        else if (i / columns < cells.lastIndex / columns) cells.lastIndex else i]
                    .id
        }
        val entry = cells[(focused - group * EpisodeGrouping.size).coerceIn(0, cells.lastIndex)]
        groupTabs.forEachIndexed { index, tab ->
            tab.nextFocusUpId = groupTabs.getOrNull(index - 1)?.id ?: jump.id
            tab.nextFocusDownId = groupTabs.getOrNull(index + 1)?.id ?: tab.id
            tab.nextFocusLeftId = tab.id
            tab.nextFocusRightId = entry.id
        }
        close.nextFocusDownId = entry.id
        jump.nextFocusDownId = groupTabs[group].id
        close.nextFocusLeftId = jump.id
        close.nextFocusRightId = close.id
        jump.nextFocusLeftId = jump.id
        jump.nextFocusRightId = close.id
        dialog.setContentView(content)
        focusOnGroups = focusGroup
        if (focusGroup) groupTabs[group].requestFocus() else entry.requestFocus()
        ready = true
        groupList.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                groupList.removeOnLayoutChangeListener(this)
                groupScroll.post {
                    if (groupScroll.isAttachedToWindow) groupTabs[group].requestRectangleOnScreen(
                        android.graphics.Rect(0, 0, groupTabs[group].width, groupTabs[group].height), true,
                    )
                }
            }
        })
    }
}
