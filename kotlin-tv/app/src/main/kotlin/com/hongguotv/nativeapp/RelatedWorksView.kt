// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.*
import com.hongguotv.core.RelatedWork
import com.hongguotv.core.Series

/** A remote-friendly, independently loaded strip below the episode grid. */
internal class RelatedWorksView(
    context: Context,
    private val upper: List<View>,
    private val loadCover: (ImageView, String) -> Unit,
    private val reserveLabel: (RelatedWork) -> String,
    private val open: (RelatedWork, View) -> Unit,
    private val reload: () -> Unit,
) : LinearLayout(context) {
    private var refreshButton: TextView? = null
    private val labels = mutableListOf<Pair<RelatedWork, TextView>>()
    private var focusedSeriesId: String? = null
    private var restoreFocus = false

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float = 15f) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(TvStyle.text)
            includeFontPadding = false
        }

    init {
        orientation = VERTICAL
        setPadding(0, dp(14), 0, dp(12))
        loading()
    }

    private fun reset(status: String) {
        restoreFocus = findFocus() != null
        removeAllViews()
        labels.clear()
        val heading =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        heading.addView(label("相关作品", 22f), LayoutParams(0, -2, 1f))
        val refresh =
            label("刷新", 15f).apply {
                id = View.generateViewId()
                isFocusable = true
                isFocusableInTouchMode = !TvStyle.touch
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = TvStyle.card(context, false)
                setOnFocusChangeListener { view, focused ->
                    view.background = TvStyle.card(context, focused)
                }
                setOnClickListener { reload() }
                nextFocusUpId = upper.lastOrNull()?.id ?: id
            }
        refreshButton = refresh
        heading.addView(refresh)
        addView(heading)
        if (status.isNotBlank())
            addView(
                label(status, 13f).apply {
                    setTextColor(TvStyle.muted)
                    setPadding(0, dp(8), 0, dp(8))
                }
            )
        upper.takeLast(10).forEach { it.nextFocusDownId = refresh.id }
        if (restoreFocus) refresh.requestFocus()
    }

    fun loading() {
        reset("正在加载相关作品…")
    }

    fun failed() {
        reset("相关作品暂时无法加载，可按刷新重试；当前剧集仍可正常观看。")
    }

    fun show(rows: List<RelatedWork>, current: Series? = null) {
        reset(if (rows.isEmpty()) "暂无相关剧集" else "左右浏览其他季及同 IP 作品 · 上键刷新 · 待上线作品可预约")
        if (rows.isEmpty()) return
        val ordered = if (current != null) relatedWithCurrent(current, rows) else rows
        val start = focusedSeriesId?.let { id -> ordered.indexOfFirst { it.series.id == id }.takeIf { it >= 0 } }
            ?: current?.let { relatedStartIndex(ordered, it.id) } ?: 0
        val scroll =
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                isFocusable = false
            }
        val line = LinearLayout(context).apply { orientation = HORIZONTAL }
        scroll.addView(line)
        addView(scroll)
        val cards =
            ordered.map { work ->
                val isCurrent = work.series.id == current?.id
                val card =
                    LinearLayout(context).apply {
                        orientation = VERTICAL
                        id = View.generateViewId()
                        tag = work.series.id
                        isFocusable = true
                        isFocusableInTouchMode = !TvStyle.touch
                        contentDescription = work.series.title + "，" + work.label + if (isCurrent) "，当前作品" else ""
                        isSelected = isCurrent
                        setPadding(dp(5), dp(5), dp(5), dp(9))
                        background = TvStyle.card(context, false)
                    }
                val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                card.addView(image, LayoutParams(-1, dp(176)))
                loadCover(image, work.series.cover)
                card.addView(
                    label(work.series.title, 14f).apply {
                        maxLines = 2
                        minLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    },
                    LayoutParams(-1, -2),
                )
                card.addView(label(work.season, 12f))
                val status = label(if (isCurrent) "当前作品" else if (work.upcoming) "待上线 · ${reserveLabel(work)}" else "已上线 · 查看剧集", 12f)
                if (isCurrent) status.setTextColor(SurfaceStyle.accentText)
                card.addView(status)
                if (!isCurrent) {
                    labels += work to status
                    card.setOnClickListener { open(work, card) }
                }
                card.setOnFocusChangeListener { view, focused ->
                    view.background = TvStyle.card(context, focused)
                    if (focused) {
                        focusedSeriesId = work.series.id
                        refreshButton?.nextFocusDownId = view.id
                        upper.takeLast(10).forEach { it.nextFocusDownId = view.id }
                        loadCover(image, work.series.cover)
                        view.requestRectangleOnScreen(
                            android.graphics.Rect(0, 0, view.width, view.height),
                            false,
                        )
                    }
                }
                line.addView(card, LayoutParams(dp(142), -2).apply { rightMargin = dp(12) })
                card
            }
        cards.forEachIndexed { index, card ->
            card.nextFocusLeftId = cards.getOrNull(index - 1)?.id ?: card.id
            card.nextFocusRightId = cards.getOrNull(index + 1)?.id ?: card.id
            card.nextFocusUpId = refreshButton?.id ?: upper.lastOrNull()?.id ?: card.id
            card.nextFocusDownId = card.id
        }
        refreshButton?.nextFocusDownId = cards[start].id
        upper.takeLast(10).forEach { view ->
            view.nextFocusDownId = cards[start].id
        }
        val shouldRestore = restoreFocus
        restoreFocus = false
        scroll.post {
            if (scroll.parent !== this) return@post
            // 只横向定位，不抢走播放／选集焦点，也不把外层详情页拉到底部。
            if (findFocus() == null || findFocus() === refreshButton) scroll.scrollTo(cards[start].left, 0)
            if (shouldRestore && findFocus() === refreshButton) cards[start].requestFocus()
        }
    }

    fun refreshLabels() {
        labels.forEach { (work, view) -> if (work.upcoming) view.text = "待上线 · ${reserveLabel(work)}" }
    }
}
