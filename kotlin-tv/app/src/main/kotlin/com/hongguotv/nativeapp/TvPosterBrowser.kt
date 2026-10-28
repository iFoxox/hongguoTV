// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.hongguotv.core.Series

/** B 方案：左侧回收式三列海报墙，右侧保留唯一试播播放器。选中只更新信息，确认才换流。 */
internal class TvPosterBrowser(
    context: Context,
    source: View,
    private val upper: View,
    stage: View,
    private val info: View,
    actions: View,
    private val primary: View,
    private val loadCover: (ImageView, String) -> Unit,
    private val browse: (Series) -> Unit,
    private val play: (Series) -> Unit,
    more: () -> Unit,
    resume: (() -> Unit)? = null,
) : LinearLayout(context) {
    private var entries = emptyList<Series>()
    private var selectedId: String? = null
    private var posterHeight = dp(132)
    private var waitingForFocus = false
    private var binding = false
    private val preview = LinearLayout(context).apply { orientation = VERTICAL }
    val grid =
        GridView(context).apply {
            id = View.generateViewId()
            tag = "tv-home:posters"
            numColumns = 3
            horizontalSpacing = dp(10)
            verticalSpacing = dp(10)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(4), dp(4), dp(4), dp(4))
            selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        }
    private val nextPage =
        TextView(context).apply {
            id = View.generateViewId()
            tag = "tv-home:more"
            text = "更多作品"
            setTextColor(TvStyle.text)
            textSize = 14f
            gravity = Gravity.CENTER
            minHeight = dp(40)
            isFocusable = true
            isClickable = true
            SurfaceStyle.button(this)
            setOnClickListener { more() }
        }
    private val adapter =
        object : BaseAdapter() {
            override fun getCount() = entries.size

            override fun getItem(position: Int) = entries[position]

            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val card =
                    convertView as? LinearLayout
                        ?: LinearLayout(context).apply {
                            orientation = VERTICAL
                            gravity = Gravity.CENTER_HORIZONTAL
                            setPadding(dp(4), dp(4), dp(4), dp(6))
                            // GridView 持有焦点与选中位置，子卡片不形成另一套焦点链。
                            addView(
                                ImageView(context).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                }
                            )
                            addView(
                                TextView(context).apply {
                                    textSize = 14f
                                    setTextColor(TvStyle.text)
                                    typeface = Typeface.DEFAULT_BOLD
                                    maxLines = 1
                                    ellipsize = TextUtils.TruncateAt.END
                                    setPadding(0, dp(6), 0, 0)
                                },
                                LayoutParams(-1, -2),
                            )
                            addView(
                                TextView(context).apply {
                                    textSize = 11f
                                    setTextColor(TvStyle.muted)
                                    maxLines = 1
                                    ellipsize = TextUtils.TruncateAt.END
                                    setPadding(0, dp(3), 0, 0)
                                },
                                LayoutParams(-1, -2),
                            )
                        }
                val series = entries[position]
                val image = card.getChildAt(0) as ImageView
                image.layoutParams =
                    LayoutParams((posterHeight * TvStyle.POSTER_ASPECT).toInt(), posterHeight)
                if (image.tag != series.cover) {
                    image.tag = series.cover
                    image.setImageDrawable(null)
                    loadCover(image, series.cover)
                }
                (card.getChildAt(1) as TextView).text = series.title
                (card.getChildAt(2) as TextView).text = series.tags.ifBlank { series.badge }
                card.contentDescription = "${series.title}，${series.tags}，确定试播第一集"
                card.background =
                    TvStyle.card(context, grid.hasFocus() && position == grid.selectedItemPosition)
                return card
            }
        }

    init {
        orientation = VERTICAL
        addView(source, LayoutParams(-1, -2))
        val body = LinearLayout(context)
        val left = LinearLayout(context).apply { orientation = VERTICAL }
        left.addView(grid, LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(context)
        footer.addView(nextPage, LayoutParams(0, dp(40), 1f))
        if (resume != null) {
            footer.addView(
                TextView(context).apply {
                    id = View.generateViewId()
                    text = "继续观看"
                    setTextColor(TvStyle.text)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isClickable = true
                    SurfaceStyle.button(this)
                    setOnClickListener { resume() }
                    nextFocusUpId = grid.id
                    nextFocusLeftId = nextPage.id
                    nextFocusRightId = primary.id
                    nextPage.nextFocusRightId = id
                },
                LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(8) },
            )
        }
        left.addView(footer, LayoutParams(-1, dp(40)).apply { topMargin = dp(8) })
        body.addView(left, LayoutParams(0, -1, .46f).apply { rightMargin = dp(20) })
        preview.addView(stage, LayoutParams(-1, 0, 1f))
        preview.addView(info, LayoutParams(-1, -2))
        preview.addView(actions, LayoutParams(-1, -2))
        body.addView(preview, LayoutParams(0, -1, .54f))
        addView(body, LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        grid.adapter = adapter
        grid.nextFocusUpId = upper.id
        grid.nextFocusRightId = primary.id
        nextPage.nextFocusUpId = grid.id
        if (resume == null) nextPage.nextFocusRightId = primary.id
        grid.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    if (binding) return
                    entries.getOrNull(position)?.let {
                        selectedId = it.id
                        browse(it)
                    }
                    paintVisibleCards()
                }
            }
        grid.setOnItemClickListener { _, _, position, _ -> entries.getOrNull(position)?.let(play) }
        grid.setOnFocusChangeListener { _, focused ->
            if (focused) entries.getOrNull(grid.selectedItemPosition)?.let(browse)
            paintVisibleCards()
        }
        grid.setOnKeyListener { _, key, event ->
            if (event.action != KeyEvent.ACTION_DOWN) false
            else
                when {
                    key == KeyEvent.KEYCODE_DPAD_UP && grid.selectedItemPosition < 3 ->
                        upper.requestFocus()
                    key == KeyEvent.KEYCODE_DPAD_RIGHT &&
                        (grid.selectedItemPosition % 3 == 2 ||
                            grid.selectedItemPosition == entries.lastIndex) ->
                        primary.requestFocus()
                    key == KeyEvent.KEYCODE_DPAD_LEFT && grid.selectedItemPosition % 3 == 0 -> true
                    key == KeyEvent.KEYCODE_DPAD_DOWN &&
                        grid.selectedItemPosition + 3 >= entries.size -> nextPage.requestFocus()
                    else -> false
                }
        }
        primary.nextFocusLeftId = grid.id
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val compact = MeasureSpec.getSize(heightMeasureSpec) < dp(300)
        (info as? LinearLayout)?.let {
            (it.getChildAt(0) as? TextView)?.apply {
                minLines = if (compact) 1 else 2
                maxLines = if (compact) 1 else 2
            }
            it.getChildAt(2)?.visibility = if (compact) GONE else VISIBLE
        }
        val available = (MeasureSpec.getSize(heightMeasureSpec) - dp(108)).coerceAtLeast(dp(150))
        val column = ((width - dp(20)) * .46f - dp(40)) / 3
        val rows = if (available >= dp(400)) 2 else 1
        val nextHeight =
            minOf((column / TvStyle.POSTER_ASPECT).toInt(), available / rows - dp(64))
                .coerceAtLeast(dp(60))
        if (nextHeight != posterHeight) {
            posterHeight = nextHeight
            adapter.notifyDataSetChanged()
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun update(items: List<Series>, current: Series?, hasMore: Boolean) {
        if (items != entries) {
            val keep = selectedId ?: current?.id
            entries = items
            val position = items.indexOfFirst { it.id == keep }.coerceAtLeast(0)
            selectedId = items.getOrNull(position)?.id
            binding = true
            adapter.notifyDataSetChanged()
            grid.setSelection(position)
            binding = false
        }
        nextPage.text = if (hasMore) "更多作品" else "本批已显示完"
        nextPage.contentDescription = if (hasMore) "加载更多作品" else "本批已显示完，可在上方换一批继续"
        // 无下一页仍可聚焦阅读说明，但不再请求网络。
        if (waitingForFocus && entries.isNotEmpty()) focusPosters()
    }

    fun focusPosters(): Boolean {
        waitingForFocus = entries.isEmpty()
        if (waitingForFocus) return upper.requestFocus()
        waitingForFocus = false
        return grid.requestFocus()
    }

    fun selectSeries(series: Series?) {
        val position = entries.indexOfFirst { it.id == series?.id }
        if (position >= 0) {
            selectedId = series?.id
            grid.setSelection(position)
        }
    }

    fun returnToPosters(): Boolean {
        if (preview.hasFocus()) {
            focusPosters()
            return true
        }
        return false
    }

    private fun paintVisibleCards() {
        for (i in 0 until grid.childCount) {
            grid.getChildAt(i).background =
                TvStyle.card(
                    context,
                    grid.hasFocus() && grid.firstVisiblePosition + i == grid.selectedItemPosition,
                )
        }
    }
}
