// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 首页试播组件：组装分类栏、视频区和操作栏，并管理试播请求及遥控器焦点。

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import com.hongguotv.core.*
import java.io.Closeable
import java.util.concurrent.Executors

/** A separate trial player. Never calls Library.save or AccountController.upload. */
internal class HomeFeedScreen(
    private val activity: Activity,
    private val state: DramaFeedState,
    private val nav: List<View>,
    private val watch: (Series) -> Unit,
    private val favorite: (Series, View) -> Unit,
    private val favoriteLabel: (String) -> String,
    private val backend: FeedBackend,
    private val playback: TrialPlayback,
    private val resumes: () -> List<ResumeCard> = { emptyList() },
    private val openResume: (Series) -> Unit = {},
    private val cover: (ImageView, String) -> Unit = { _, _ -> },
) : LinearLayout(activity), Closeable {

    /** 继续观看卡片数据：作品、说明行与进度（0—1）。 */
    data class ResumeCard(val series: Series, val line: String, val fraction: Float)

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)
    private var generation = 0
    private var scope: RequestScope? = null
    private var job: java.util.concurrent.Future<*>? = null
    private var prefetchScope: RequestScope? = null
    private var prefetchJob: java.util.concurrent.Future<*>? = null
    private var prefetchTarget: String? = null

    private data class CachedTrial(
        val clip: TrialClip,
        val savedAt: Long = android.os.SystemClock.elapsedRealtime(),
    )

    private val cachedTrials = linkedMapOf<String, CachedTrial>()
    private val attemptedPrefetch = mutableSetOf<String>()
    private var prefetchGeneration = 0
    private var attemptedBatch = 0
    private var clip: TrialClip? = null
    private var closed = false
    private var active = false
    private var loading = false
    private var failed = false
    private val stage = FrameLayout(activity)
    private val title = label("发现下一部好剧", 25f)
    private val meta = label("第一集试播", 13f, TvStyle.accent)
    private val intro = label("喜欢这部作品，就选择观看全集。", 15f, TvStyle.muted)
    private val status = label("正在准备热门作品…", 15f)
    private val tag = label("热门发现", 12f, TvStyle.accent)
    private var mobileRecommendations: LinearLayout? = null
    private var mobileRecommendationKey = ""
    private var tvBrowser: TvPosterBrowser? = null
    private var browsed: Series? = null
    private val posterCover =
        ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
    private val actions = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
    private val buttons = mutableListOf<TextView>()
    private val pauseButton: TextView
    private val watchButton: TextView
    private lateinit var favoriteButton: TextView
    private val recommendButton: TextView
    private val comicButton: TextView
    private val realButton: TextView

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun widthDp() = resources.displayMetrics.widthPixels / resources.displayMetrics.density

    private fun label(value: String, size: Float, color: Int = TvStyle.text) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            includeFontPadding = false
        }

    private fun lp(w: Int = -1, h: Int = -2) = LayoutParams(w, h)

    private fun button(value: String, action: () -> Unit) =
        label(value, 14f).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = !TvStyle.touch
            isClickable = true
            gravity = Gravity.CENTER
            minHeight = dp(TvStyle.controlSize(44))
            if (TvStyle.touch) minimumWidth = dp(48)
            setPadding(
                dp(10),
                dp(if (TvStyle.touch) 6 else 10),
                dp(10),
                dp(if (TvStyle.touch) 6 else 10),
            )
            if (TvStyle.touch) textSize = 13f
            background = TvStyle.card(activity, false)
            if (TvStyle.touch) foreground = TvStyle.pressHighlight(activity)
            setOnFocusChangeListener { _, _ -> paintButton(this) }
            setOnClickListener { action() }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (TvStyle.touch && stage.layoutParams != null) {
            val height = (View.MeasureSpec.getSize(widthMeasureSpec) * 9f / 16f).toInt()
            if (stage.layoutParams.height != height)
                stage.layoutParams = stage.layoutParams.apply { this.height = height }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun paintButton(button: TextView) {
        button.background = TvStyle.card(activity, button.hasFocus())
        button.setTextColor(if (button.isSelected) TvStyle.accent else TvStyle.text)
        button.setTypeface(null, if (button.isSelected) Typeface.BOLD else Typeface.NORMAL)
        if (TvStyle.touch) {
            SurfaceStyle.button(button)
            if (button.isSelected) button.setTextColor(SurfaceStyle.accentText)
        }
        if (button === watchButton) {
            SurfaceStyle.button(button, accent = true)
            button.setTextColor(SurfaceStyle.onAccent)
        }
    }

    // 组装首页布局和点击事件；继续观看行、分类栏、内容区、操作栏按纵向排列。
    init {
        orientation = VERTICAL
        if (TvStyle.touch) intro.setTextColor(TvStyle.mobileMuted)
        stage.id = View.generateViewId()

        // 继续观看：横版剧照卡（飞牛影视式）；无记录时整行不显示，焦点链保持原状。
        val resumeEntries = resumes()
        val resumeCards = mutableListOf<View>()
        // 手机竖屏把继续观看卡缩窄，横向可以同时看到两张以上。
        val thumbHeight = if (widthDp() < TvStyle.NARROW_DP) dp(84) else dp(112)
        val resumeCardWidth =
            if (TvStyle.touch) dp(286) else if (widthDp() < TvStyle.NARROW_DP) dp(150) else dp(208)
        if (TvStyle.touch && resumeEntries.isNotEmpty()) {
            val section =
                label("继续观看", 16f, TvStyle.text).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(2), 0, 0, dp(7))
                }
            addView(section)
            val rail = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
            val lane = LinearLayout(activity)
            resumeEntries.forEach { entry ->
                val card =
                    LinearLayout(activity).apply {
                        orientation = VERTICAL
                        isFocusable = true
                        isFocusableInTouchMode = !TvStyle.touch
                        isClickable = true
                        id = View.generateViewId()
                    }
                val thumb = FrameLayout(activity)
                val image =
                    ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                cover(image, entry.series.cover)
                thumb.addView(image, FrameLayout.LayoutParams(-1, -1))
                val track =
                    View(activity).apply { setBackgroundColor(Color.argb(120, 255, 255, 255)) }
                thumb.addView(
                    track,
                    FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM).apply {
                        leftMargin = dp(6)
                        rightMargin = dp(6)
                        bottomMargin = dp(6)
                    },
                )
                val fill = View(activity).apply { setBackgroundColor(TvStyle.accent) }
                thumb.addView(
                    fill,
                    FrameLayout.LayoutParams(0, dp(3), Gravity.BOTTOM).apply {
                        leftMargin = dp(6)
                        bottomMargin = dp(6)
                        thumb.post {
                            (fill.layoutParams as FrameLayout.LayoutParams).width =
                                ((thumb.width - dp(12)) * entry.fraction.coerceIn(0f, 1f)).toInt()
                            fill.requestLayout()
                        }
                    },
                )
                card.addView(thumb, LayoutParams(-1, thumbHeight))
                if (entry.fraction <= 0f) {
                    track.visibility = View.GONE
                    fill.visibility = View.GONE
                }
                card.addView(
                    label(entry.series.title, 12.5f).apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(dp(1), dp(7), dp(1), 0)
                    }
                )
                card.addView(
                    label(entry.line, 11f, TvStyle.muted).apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(dp(1), dp(3), dp(1), 0)
                    }
                )
                if (TvStyle.touch) {
                    val captions = listOf(card.getChildAt(1), card.getChildAt(2))
                    card.removeAllViews()
                    card.orientation = HORIZONTAL
                    card.gravity = Gravity.CENTER_VERTICAL
                    card.setPadding(dp(8), dp(8), dp(8), dp(8))
                    card.addView(thumb, lp(dp(108), dp(72)))
                    val words =
                        LinearLayout(activity).apply {
                            orientation = VERTICAL
                            setPadding(dp(10), 0, 0, 0)
                        }
                    captions.forEach { words.addView(it) }
                    card.addView(words, lp(0, -2).apply { weight = 1f })
                    card.foreground = TvStyle.pressHighlight(activity)
                }
                card.background = TvStyle.card(activity, false)
                card.setOnFocusChangeListener { _, focused ->
                    card.background = TvStyle.card(activity, focused)
                }
                card.setOnClickListener { openResume(entry.series) }
                lane.addView(
                    card,
                    LinearLayout.LayoutParams(resumeCardWidth, -2).apply {
                        rightMargin = if (entry === resumeEntries.last()) 0 else dp(16)
                    },
                )
                resumeCards += card
            }
            rail.addView(lane, FrameLayout.LayoutParams(-2, -2).apply { rightMargin = dp(4) })
            addView(rail, lp(-1, -2).apply { bottomMargin = dp(6) })
        }

        val resumeViews = (0 until childCount).map { getChildAt(it) }
        val types = LinearLayout(activity)
        recommendButton =
            button("推荐") {
                if (!state.recommendation) {
                    clearCurrent()
                    clearCache()
                    state.recommend()
                    browsed = state.items.firstOrNull { it.id == state.browsingId } ?: state.current
                    describe()
                    if (state.current != null) loadCurrent() else loadPage(false)
                }
            }
        comicButton = button("漫剧") { changeType(DramaCategory.COMIC) }
        realButton = button("真人剧") { changeType(DramaCategory.REAL) }
        val refresh =
            button("换一批") {
                clearCurrent()
                clearCache()
                state.refresh()
                browsed = null
                describe()
                loadPage(false)
            }
        val typeChoices = listOf(recommendButton, comicButton, realButton, refresh)
        typeChoices.forEachIndexed { i, b ->
            types.addView(
                b,
                lp(0, if (TvStyle.touch) -1 else dp(40)).apply {
                    weight = 1f
                    rightMargin =
                        dp(
                            if (TvStyle.touch && i == typeChoices.lastIndex) 0
                            else if (TvStyle.touch) 4 else 8
                        )
                },
            )
            if (!TvStyle.sideNav) b.nextFocusUpId = resumeCards.firstOrNull()?.id ?: nav.first().id
            b.nextFocusDownId = stage.id
            b.nextFocusLeftId = typeChoices.getOrNull(i - 1)?.id ?: b.id
            b.nextFocusRightId = typeChoices.getOrNull(i + 1)?.id ?: b.id
        }
        resumeCards.forEach { it.nextFocusDownId = recommendButton.id }
        addView(types, lp(-1, -2))
        // 手机竖屏：试播区与信息栏改为上下排布，视频占满可用宽度。
        val narrow = TvStyle.touch || widthDp() < TvStyle.NARROW_DP
        val content =
            LinearLayout(activity).apply { orientation = if (narrow) VERTICAL else HORIZONTAL }
        stage.isFocusable = true
        stage.isFocusableInTouchMode = !TvStyle.touch
        stage.isClickable = true
        stage.setPadding(dp(3), dp(3), dp(3), dp(3))
        if (TvStyle.touch) stage.foreground = TvStyle.pressHighlight(activity)
        stage.background = TvStyle.card(activity, false)
        stage.addView(playback.view, FrameLayout.LayoutParams(-1, -1))
        tag.setPadding(dp(12), dp(8), dp(12), dp(8))
        tag.background = TvStyle.shape(activity, Color.argb(210, 11, 15, 21))
        stage.addView(
            tag,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(10), dp(10), dp(10), 0)
            },
        )
        status.setTextColor(Color.WHITE)
        status.gravity = Gravity.CENTER
        status.setPadding(dp(14), dp(12), dp(14), dp(12))
        status.background = TvStyle.shape(activity, Color.argb(215, 11, 15, 21))
        stage.addView(
            status,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                setMargins(dp(10), 0, dp(10), dp(10))
            },
        )
        if (narrow)
            content.addView(
                stage,
                lp(
                    -1,
                    ((activity.resources.displayMetrics.widthPixels * .9f - dp(10)) * 9f / 16f)
                        .toInt(),
                ),
            )
        else
            content.addView(
                stage,
                lp(0, -1).apply {
                    weight = 2.2f
                    rightMargin = dp(22)
                },
            )
        val info =
            LinearLayout(activity).apply {
                orientation = VERTICAL
                setPadding(0, dp(if (TvStyle.touch) 8 else 14), 0, dp(if (TvStyle.touch) 4 else 10))
            }
        title.typeface = Typeface.DEFAULT_BOLD
        title.maxLines = if (narrow) 2 else 3
        title.ellipsize = TextUtils.TruncateAt.END
        if (narrow) title.textSize = 19f
        info.addView(title)
        meta.maxLines = 2
        meta.setPadding(
            0,
            dp(if (TvStyle.touch) 4 else if (narrow) 8 else 12),
            0,
            dp(if (TvStyle.touch) 4 else if (narrow) 8 else 12),
        )
        info.addView(meta)
        intro.maxLines = if (TvStyle.touch) 2 else if (narrow) 3 else 6
        intro.ellipsize = TextUtils.TruncateAt.END
        info.addView(intro)
        if (!TvStyle.touch) info.addView(Space(activity), lp(1, 0).apply { weight = 1f })
        if (!TvStyle.touch)
            info.addView(
                label(
                        TvStyle.hint("↑ ↓  换一部\n确定  打开操作\n返回  顶部导航", "点按画面选择操作\n上下滑动列表换一部"),
                        13f,
                        TvStyle.muted,
                    )
                    .apply { setLineSpacing(dp(6).toFloat(), 1f) }
            )
        content.addView(info, if (narrow) lp(-1, -2) else lp(0, -1).apply { weight = 1f })
        addView(content, lp(-1, 0).apply { weight = 1f })
        pauseButton =
            button("暂停试播") {
                val selected = browsed
                if (!TvStyle.touch && selected != null && selected.id != state.current?.id) {
                    val index = state.items.indexOfFirst { it.id == selected.id }
                    if (index >= 0) move(index - state.index)
                } else toggle()
            }
        watchButton =
            button("观看全集") {
                (if (TvStyle.touch) state.current else browsed ?: state.current)?.let {
                    remember()
                    pause()
                    watch(it)
                }
            }
        favoriteButton =
            button("账户收藏") {
                (if (TvStyle.touch) state.current else browsed ?: state.current)?.let {
                    state.userPaused = true
                    playback.pause()
                    remember()
                    paintActions()
                    favorite(it, favoriteButton)
                }
            }
        buttons += listOf(pauseButton, watchButton, favoriteButton)
        buttons.forEachIndexed { index, b ->
            actions.addView(
                b,
                lp(0, if (TvStyle.touch) -1 else dp(44)).apply {
                    weight = 1f
                    if (index < buttons.lastIndex) rightMargin = dp(10)
                },
            )
            b.nextFocusLeftId = buttons.getOrNull(index - 1)?.id ?: b.id
            b.nextFocusRightId = buttons.getOrNull(index + 1)?.id ?: b.id
            b.nextFocusUpId = stage.id
            b.nextFocusDownId = b.id
        }
        actions.setPadding(0, dp(if (TvStyle.touch) 2 else 10), 0, 0)
        actions.visibility = if (TvStyle.touch) View.VISIBLE else View.GONE
        addView(actions)
        stage.nextFocusLeftId = stage.id
        stage.nextFocusRightId = stage.id
        stage.nextFocusUpId = stage.id
        stage.nextFocusDownId = stage.id
        TvStyle.navInto(
            nav,
            resumeCards.firstOrNull()
                ?: (if (state.recommendation) recommendButton
                else if (state.category == DramaCategory.COMIC) comicButton else realButton),
        )
        stage.setOnFocusChangeListener { _, focused ->
            stage.background = TvStyle.card(activity, focused)
        }
        stage.setOnClickListener { if (TvStyle.touch) toggle() else showActions() }
        stage.setOnKeyListener { _, key, event ->
            when (key) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                        move(if (key == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) showActions()
                    true
                }
                else -> false
            }
        }
        playback.status = { value ->
            if (!closed && clip != null)
                when (value) {
                    TrialStatus.BUFFERING -> {
                        status.visibility = View.VISIBLE
                        status.text = "正在加载第一集…"
                    }
                    TrialStatus.READY -> {
                        status.visibility = if (state.userPaused) View.VISIBLE else View.GONE
                        failed = false
                        status.text = if (state.userPaused) "试播已暂停" else "第一集试播 · 喜欢就看全集"
                        paintActions()
                        prefetchUpcoming()
                    }
                    TrialStatus.ENDED -> {
                        status.visibility = View.VISIBLE
                        remember()
                        state.ended = true
                        state.userPaused = true
                        status.text = "本集试播结束 · 看全集或按 ↓ 换一部"
                        paintActions()
                        if (active && stage.hasFocus()) showActions()
                    }
                    TrialStatus.ERROR -> {
                        status.visibility = View.VISIBLE
                        playback.pause()
                        failed = true
                        status.text = "试播暂不可用 · 可重试、换剧或查看全集"
                        paintActions()
                    }
                }
        }
        if (TvStyle.touch) {
            actions.removeView(pauseButton)
            stage.addView(
                pauseButton,
                FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = dp(8)
                    bottomMargin = dp(8)
                },
            )
            watchButton.layoutParams =
                (watchButton.layoutParams as LayoutParams).apply { weight = 1f }
            removeAllViews()
            val page = LinearLayout(activity).apply { orientation = VERTICAL }
            val scroll =
                ScrollView(activity).apply {
                    isFillViewport = false
                    isVerticalScrollBarEnabled = false
                    addView(page)
                }
            addView(scroll, lp(-1, -1))
            page.addView(types, lp(-1, -2))
            page.addView(content, lp(-1, -2))
            page.addView(actions, lp(-1, -2))
            val navigation = LinearLayout(activity)
            listOf("上一部" to -1, "下一部" to 1).forEach { (text, delta) ->
                navigation.addView(
                    mobileButton(activity, text) { move(delta) },
                    lp(0, -2).apply {
                        weight = 1f
                        setMargins(dp(3), 0, dp(3), dp(2))
                    },
                )
            }
            page.addView(navigation)
            resumeViews.forEach { page.addView(it) }
            val more = LinearLayout(activity).apply { orientation = VERTICAL }
            mobileRecommendations = more
            page.addView(more)
        } else {
            // 只重组 TV 的视图，保留触屏布局与现有播放、缓存和账号回调。
            removeAllViews()
            listOf(types, stage, info, actions).forEach {
                (it.parent as? android.view.ViewGroup)?.removeView(it)
            }
            while (info.childCount > 3) info.removeViewAt(3)
            title.textSize = 23f
            title.minLines = 2
            title.maxLines = 2
            meta.textSize = 12f
            meta.minLines = 1
            meta.maxLines = 1
            meta.setPadding(0, dp(6), 0, dp(6))
            intro.textSize = 14f
            intro.minLines = 2
            intro.maxLines = 2
            info.setPadding(0, dp(10), 0, 0)
            stage.addView(posterCover, 1, FrameLayout.LayoutParams(-1, -1))
            actions.visibility = View.VISIBLE
            tvBrowser =
                TvPosterBrowser(
                        activity,
                        types,
                        recommendButton,
                        stage,
                        info,
                        actions,
                        pauseButton,
                        cover,
                        ::browseSeries,
                        { series ->
                            browsed = series
                            pauseButton.performClick()
                            stage.requestFocus()
                        },
                        {
                            if (state.hasMore && !loading) {
                                cancelPrefetch()
                                loadPage(false, keepTrial = true)
                            }
                        },
                        resume =
                            if (resumeEntries.isEmpty()) null
                            else
                                ({
                                    AppDialogs.builder(activity)
                                        .setTitle("继续观看 · 本机记录")
                                        .setItems(
                                            resumeEntries
                                                .map { "${it.series.title} · ${it.line}" }
                                                .toTypedArray()
                                        ) { _, which ->
                                            openResume(resumeEntries[which].series)
                                        }
                                        .setNegativeButton("关闭", null)
                                        .show()
                                }),
                    )
                    .also { addView(it, lp(-1, -1)) }
            pauseButton.nextFocusLeftId = tvBrowser!!.grid.id
            stage.nextFocusLeftId = tvBrowser!!.grid.id
            typeChoices.forEach {
                it.nextFocusUpId = nav.first().id
                it.nextFocusDownId = tvBrowser!!.grid.id
            }
            TvStyle.navInto(nav, recommendButton)
        }
        describe()
        paintActions()
    }

    // 设置进入首页时的初始焦点，并在应用位于前台时启动试播流程。
    fun start(foreground: Boolean, focusNav: Boolean) {
        if (!TvStyle.touch) {
            if (focusNav) nav[0].requestFocus()
            else tvBrowser?.focusPosters() ?: stage.requestFocus()
        }
        if (foreground) resume()
    }

    private val prefetchPoll =
        object : Runnable {
            override fun run() {
                if (!active || closed) return
                prefetchUpcoming()
                main.postDelayed(this, 250)
            }
        }

    fun resume() {
        if (closed || active) return
        active = true
        main.removeCallbacks(prefetchPoll)
        main.postDelayed(prefetchPoll, 250)
        if (clip != null) {
            if (!state.userPaused && !state.ended) playback.resume()
            paintActions()
            prefetchUpcoming()
        } else if (state.current != null) loadCurrent() else loadPage(false)
    }

    fun pause() {
        if (closed) return
        remember()
        active = false
        main.removeCallbacks(prefetchPoll)
        playback.pause()
        cancelRequest()
        cancelPrefetch()
        paintActions()
    }

    fun back(): Boolean {
        if (TvStyle.touch) return false
        tvBrowser?.let {
            return it.returnToPosters()
        }
        if (actions.visibility != View.VISIBLE) return false
        actions.visibility = if (TvStyle.touch) View.VISIBLE else View.GONE
        stage.requestFocus()
        return true
    }

    fun refreshAccountLabel() {
        if (!closed) paintActions()
    }

    // 遥控器确定键或点击视频后显示操作栏，并将焦点移到可用操作。
    private fun showActions() {
        actions.visibility = View.VISIBLE
        paintActions()
        if (!TvStyle.touch) (if (watchButton.isEnabled) watchButton else pauseButton).requestFocus()
    }

    private fun paintActions() {
        val selected = if (TvStyle.touch) state.current else browsed ?: state.current
        pauseButton.text =
            when {
                !TvStyle.touch && selected != null && selected.id != state.current?.id -> "首集试播"
                failed || state.current == null -> "重试试播"
                state.ended -> "重新试播"
                state.userPaused -> "继续试播"
                else -> "暂停试播"
            }
        watchButton.isEnabled = selected != null
        favoriteButton.isEnabled = selected != null
        favoriteButton.text = selected?.let { favoriteLabel(it.id) } ?: "账户收藏"
        if (!TvStyle.touch) {
            val fullLabel = favoriteButton.text.toString()
            favoriteButton.contentDescription = fullLabel
            favoriteButton.text =
                when {
                    fullLabel.contains("登录") -> "登录收藏"
                    fullLabel.contains("处理中") -> "处理中"
                    fullLabel.contains("刷新") -> "刷新状态"
                    fullLabel.startsWith("取消") -> "取消收藏"
                    else -> "账户收藏"
                }
        }
        recommendButton.isSelected = state.recommendation
        comicButton.isSelected = !state.recommendation && state.category == DramaCategory.COMIC
        realButton.isSelected = !state.recommendation && state.category == DramaCategory.REAL
        paintButton(recommendButton)
        paintButton(comicButton)
        paintButton(realButton)
        if (TvStyle.touch) {
            pauseButton.contentDescription = pauseButton.text
            pauseButton.text = ""
            val icon =
                MobileIcon(
                        if (state.userPaused || failed || state.ended) "play" else "pause",
                        Color.WHITE,
                    )
                    .apply { setBounds(0, 0, dp(24), dp(24)) }
            pauseButton.setCompoundDrawables(null, icon, null, null)
            pauseButton.setPadding(dp(12), dp(12), dp(12), dp(12))
            pauseButton.setBackgroundColor(0x88000000.toInt())
            SurfaceStyle.button(watchButton, accent = true)
            watchButton.setTextColor(SurfaceStyle.onAccent)
            SurfaceStyle.button(favoriteButton)
        }
        if (!TvStyle.touch) paintButton(watchButton)
        stage.keepScreenOn = active && clip != null && !state.userPaused && !state.ended && !failed
    }

    private fun describe() {
        val series = state.current
        title.text = series?.title ?: "发现下一部好剧"
        meta.text =
            if (series == null) "第一集试播"
            else
                listOf("第一集试播", series.badge, series.tags)
                    .filter(String::isNotBlank)
                    .joinToString(" · ")
        intro.text =
            series?.description?.ifBlank { "先试试看，喜欢这部作品就选择观看全集。" } ?: "选择漫剧或真人剧，上下换一部，找到想追的故事。"
        renderMobileRecommendations()
        if (!TvStyle.touch) {
            val selected =
                browsed?.takeIf { previous -> state.items.any { it.id == previous.id } }
                    ?: state.items.firstOrNull { it.id == state.browsingId }
                    ?: series
            tvBrowser?.update(state.items, selected, state.hasMore)
            browseSeries(selected)
        }
        tag.text = if (state.recommendation) "推荐 · 第一集试播" else "${state.category.label}推荐 · 第一集试播"
        paintActions()
    }

    private fun renderMobileRecommendations() {
        val section = mobileRecommendations ?: return
        val entries = state.items.filter { it.id != state.current?.id }.take(8)
        val key = entries.joinToString { it.id }
        if (key == mobileRecommendationKey) return
        mobileRecommendationKey = key
        section.removeAllViews()
        if (entries.isEmpty()) return
        section.addView(
            label("更多好剧", 17f).apply {
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(10))
            }
        )
        val rail = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(activity)
        entries.forEach { series ->
            val card =
                LinearLayout(activity).apply {
                    orientation = VERTICAL
                    foreground = TvStyle.pressHighlight(activity)
                    setOnClickListener {
                        remember()
                        pause()
                        watch(series)
                    }
                }
            val image = ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            cover(image, series.cover)
            card.addView(image, lp(-1, dp(145)))
            card.addView(
                label(series.title, 12f).apply {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(5), 0, 0)
                }
            )
            SurfaceStyle.card(card)
            card.setPadding(dp(4), dp(4), dp(4), dp(6))
            row.addView(card, lp(dp(104), -2).apply { rightMargin = dp(10) })
        }
        rail.addView(row)
        section.addView(rail)
    }

    /** 海报移动只展示已有元数据与封面，不切换状态索引、不解析另一条播放流。 */
    private fun browseSeries(series: Series?) {
        browsed = series
        state.browsingId = series?.id
        if (series == null) {
            posterCover.visibility = View.GONE
            return
        }
        title.text = series.title
        val playing = clip?.detail?.series?.id == series.id
        meta.text =
            if (playing) "第一集试播 · 共 ${clip!!.episodeCount} 集 · ${series.tags}"
            else listOf(series.badge, series.tags).filter { it.isNotBlank() }.joinToString(" · ")
        intro.text = series.description.ifBlank { "确定试播第一集，或选择观看全集。" }
        if (!playing) {
            if (posterCover.tag != series.cover) {
                posterCover.tag = series.cover
                posterCover.setImageDrawable(null)
                cover(posterCover, series.cover)
            }
            posterCover.visibility = View.VISIBLE
            tag.text = "已选作品 · 确定试播第一集"
            if (series.id != state.current?.id) {
                remember()
                state.userPaused = true
                playback.pause()
            }
        } else {
            posterCover.visibility = View.GONE
            tag.text = "第一集试播"
        }
        paintActions()
    }

    private fun remember() {
        if (clip != null) state.position = playback.position()
    }

    private fun cancelRequest() {
        generation++
        scope?.cancel()
        scope = null
        job?.cancel(true)
        job = null
        loading = false
    }

    private fun cancelPrefetch() {
        prefetchGeneration++
        prefetchScope?.cancel()
        prefetchScope = null
        prefetchJob?.cancel(true)
        prefetchJob = null
        prefetchTarget = null
        attemptedPrefetch.clear()
        attemptedBatch = 0
    }

    private fun closeCached(cached: CachedTrial) {
        playback.remove(cached.clip.detail.series.id)
        cached.clip.close()
    }

    private fun clearCache() {
        cachedTrials.values.forEach(::closeCached)
        cachedTrials.clear()
    }

    private fun clearCurrent(
        retain: Boolean = false,
        keepPlayer: Boolean = false,
        keepPrefetch: Boolean = false,
    ) {
        remember()
        cancelRequest()
        if (!keepPrefetch) cancelPrefetch()
        if (!keepPlayer) playback.clear()
        clip?.let {
            if (retain && !failed) {
                cachedTrials.put(it.detail.series.id, CachedTrial(it))?.let(::closeCached)
            } else it.close()
        }
        clip = null
    }

    private fun takeCached(id: String): TrialClip? {
        val cached = cachedTrials.remove(id) ?: return null
        if (android.os.SystemClock.elapsedRealtime() - cached.savedAt > 90_000) {
            closeCached(cached)
            return null
        }
        return cached.clip
    }

    // 后台发起请求，主线程更新界面；页面关闭、切换或暂停后丢弃过期结果。
    private fun <T> request(action: () -> T, discard: (T) -> Unit = {}, done: (T) -> Unit) {
        cancelRequest()
        loading = true
        val token = generation
        val requestScope = RequestScope()
        scope = requestScope
        job =
            worker.submit {
                try {
                    val result = requestScope.run(action)
                    main.post {
                        if (closed || token != generation || !active) discard(result)
                        else {
                            loading = false
                            done(result)
                        }
                    }
                } catch (error: Exception) {
                    main.post {
                        if (closed || token != generation || !active) return@post
                        loading = false
                        failed = true
                        status.visibility = View.VISIBLE
                        val message =
                            error.message.orEmpty().replace(Regex("https?://\\S+"), "[地址]").take(90)
                        status.text = "暂时无法加载 · ${message.ifBlank { "请重试或换一部" }}"
                        paintActions()
                    }
                }
            }
    }

    // 按当前推荐模式请求下一页；结果写入状态后，再加载当前作品的试播资源。
    private fun loadPage(advance: Boolean, keepTrial: Boolean = false) {
        if (!active || closed || loading) return
        if (state.page >= 100 || (!state.hasMore && state.current != null)) {
            status.visibility = View.VISIBLE
            status.text = "本批作品已看完，可切换类型继续发现"
            return
        }
        status.visibility = View.VISIBLE
        status.text = "正在读取热门作品…"
        val page = state.page + 1
        val category = state.category
        val recommendation = state.recommendation
        val cursor = state.recommendationCursor
        request({
            if (recommendation) backend.recommendations(page, cursor)
            else backend.categoryRecommendations(category, page, cursor)
        }) { result ->
            state.append(result, page)
            if (advance) state.move(1)
            if (state.current == null) {
                status.text = "暂时没有可试播的作品，可切换类型或重试"
                failed = true
                paintActions()
            } else if (keepTrial && clip != null) {
                tvBrowser?.update(state.items, state.current, state.hasMore)
                status.visibility = View.GONE
            } else loadCurrent()
        }
    }

    // 优先复用缓存或正在进行的预加载，避免为同一部作品重复请求。
    private fun loadCurrent() {
        val series = state.current ?: return
        if (!active || closed) return
        clearCurrent(keepPrefetch = prefetchTarget == series.id)
        failed = false
        describe()
        status.visibility = View.VISIBLE
        status.text = "正在准备第一集试播…"
        trimCache()
        val cached = takeCached(series.id)
        if (cached != null) attach(cached)
        else if (prefetchTarget == series.id && prefetchJob != null) {
            // Promote the in-flight download instead of cancelling and resolving the same URL
            // again.
            loading = true
            android.util.Log.i("HongguoTV", "Home promoted in-flight preload")
        } else request({ backend.trial(series) }, { it.close() }, ::attach)
    }

    private fun attach(next: TrialClip) {
        clip = next
        val series = next.detail.series
        meta.text =
            "第一集试播 · 共 ${next.episodeCount} 集" +
                (series.tags.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        if (series.description.isNotBlank()) intro.text = series.description
        if (!TvStyle.touch) {
            posterCover.visibility =
                if (browsed == null || browsed?.id == series.id) View.GONE else View.VISIBLE
            if (browsed != null && browsed?.id != series.id) browseSeries(browsed)
        }
        val autoplay = active && !state.userPaused && !state.ended
        playback.setCurrentIndex(state.offset + state.index)
        if (!playback.select(next, state.position, autoplay)) {
            playback.load(next, state.position, autoplay)
            cachedTrials.values.forEach { queueCached(it.clip) }
        }
        paintActions()
    }

    private fun queueCached(clip: TrialClip) {
        val index = state.items.indexOfFirst { it.id == clip.detail.series.id }
        if (index >= 0) playback.queue(clip, state.offset + index)
    }

    private fun trimCache() {
        val allowed =
            state.items
                .subList(
                    (state.index - 1).coerceAtLeast(0),
                    minOf(state.index + 4, state.items.size),
                )
                .map { it.id }
                .toSet()
        val now = android.os.SystemClock.elapsedRealtime()
        val stale =
            cachedTrials
                .filter { it.key !in allowed || now - it.value.savedAt > 90_000 }
                .keys
                .toList()
        stale.forEach { cachedTrials.remove(it)?.let(::closeCached) }
    }

    /** One speculative download at a time, after the current player is ready. */
    private fun prefetchUpcoming() {
        if (
            closed ||
                !active ||
                clip == null ||
                failed ||
                loading ||
                prefetchJob != null ||
                !playback.canPrefetch()
        )
            return
        trimCache()
        val upcoming = state.items.drop(state.index + 1).take(3)
        val next = upcoming.firstOrNull { it.id !in cachedTrials && it.id !in attemptedPrefetch }
        if (next == null) {
            if (
                upcoming.size < 3 &&
                    state.hasMore &&
                    state.page < 100 &&
                    attemptedBatch != state.page + 1
            )
                prefetchBatch()
            return
        }
        attemptedPrefetch += next.id
        prefetchTarget = next.id
        val token = prefetchGeneration
        val requestScope = RequestScope()
        prefetchScope = requestScope
        prefetchJob =
            worker.submit {
                val prepared =
                    runCatching { requestScope.run { backend.preload(next) } }.getOrNull()
                main.post {
                    if (closed || !active || token != prefetchGeneration) prepared?.close()
                    else {
                        prefetchJob = null
                        prefetchScope = null
                        prefetchTarget = null
                        if (state.current?.id == next.id && clip == null) {
                            loading = false
                            if (prepared != null) attach(prepared) else loadCurrent()
                        } else if (prepared != null) {
                            cachedTrials.put(next.id, CachedTrial(prepared))?.let(::closeCached)
                            queueCached(prepared)
                        }
                        prefetchUpcoming()
                    }
                }
            }
    }

    private fun prefetchBatch() {
        val page = state.page + 1
        attemptedBatch = page
        val category = state.category
        val recommendation = state.recommendation
        val cursor = state.recommendationCursor
        val token = prefetchGeneration
        val requestScope = RequestScope()
        prefetchScope = requestScope
        prefetchJob =
            worker.submit {
                val result =
                    runCatching {
                            requestScope.run {
                                if (recommendation) backend.recommendations(page, cursor)
                                else backend.categoryRecommendations(category, page, cursor)
                            }
                        }
                        .getOrNull()
                main.post {
                    if (closed || !active || token != prefetchGeneration) return@post
                    prefetchJob = null
                    prefetchScope = null
                    if (result != null && page == state.page + 1) {
                        state.append(result, page)
                        tvBrowser?.update(state.items, state.current, state.hasMore)
                        prefetchUpcoming()
                    }
                }
            }
    }

    private fun move(delta: Int) {
        if (closed || !active) return
        if (delta < 0 && state.index == 0) {
            status.visibility = View.VISIBLE
            status.text = "已到本次浏览的最早作品"
            return
        }
        if (delta > 0 && state.next == null) {
            if (loading) return
            if (!state.hasMore) {
                status.visibility = View.VISIBLE
                status.text = "本批作品已看完，可切换类型继续发现"
                return
            }
            clearCurrent()
            loadPage(true)
            return
        }
        val target = state.items.getOrNull(state.index + delta) ?: return
        val ready = takeCached(target.id)
        clearCurrent(
            retain = true,
            keepPlayer = ready != null,
            keepPrefetch = target.id == prefetchTarget,
        )
        if (state.move(delta)) {
            browsed = state.current
            tvBrowser?.selectSeries(state.current)
            if (ready != null) {
                failed = false
                describe()
                status.visibility = View.VISIBLE
                status.text = "正在切换作品…"
                attach(ready)
                trimCache()
            } else loadCurrent()
        }
    }

    private fun toggle() {
        if (loading) return
        if (failed || state.current == null || clip == null) {
            if (state.current == null) state.refresh()
            state.userPaused = false
            state.ended = false
            if (state.current == null) loadPage(false) else loadCurrent()
            return
        } else if (state.ended) {
            state.position = 0
            state.ended = false
            state.userPaused = false
            playback.restart()
        } else {
            state.userPaused = !state.userPaused
            if (state.userPaused) {
                remember()
                playback.pause()
            } else if (active) playback.resume()
        }
        status.visibility = if (state.userPaused) View.VISIBLE else View.GONE
        status.text = if (state.userPaused) "试播已暂停" else "第一集试播 · 喜欢就看全集"
        paintActions()
    }

    private fun changeType(category: DramaCategory) {
        if (!state.recommendation && state.category == category) return
        clearCurrent()
        state.select(category)
        browsed = state.items.firstOrNull { it.id == state.browsingId } ?: state.current
        clearCache()
        actions.visibility = if (TvStyle.touch) View.VISIBLE else View.GONE
        if (!TvStyle.touch) stage.requestFocus()
        describe()
        if (state.current != null) loadCurrent() else loadPage(false)
    }

    override fun close() {
        if (closed) return
        main.removeCallbacks(prefetchPoll)
        clearCurrent()
        clearCache()
        closed = true
        active = false
        stage.keepScreenOn = false
        playback.release()
        backend.close()
        worker.shutdownNow()
    }
}
