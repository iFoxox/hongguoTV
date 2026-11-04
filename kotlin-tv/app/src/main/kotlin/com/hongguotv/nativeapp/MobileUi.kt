// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.hongguotv.core.RelatedWork
import com.hongguotv.core.Series

/** 同一套 24 单位描边图标，避免设备字体把符号渲染成 emoji。 */
internal class MobileIcon(private val name: String, private val color: Int, private val offset: (() -> Pair<Float, Float>)? = null) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        offset?.invoke()?.let { (x,y) -> canvas.translate(x,y) }
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        paint.color = color
        fun path(vararg xy: Float) {
            val p = Path(); p.moveTo(xy[0], xy[1])
            for (i in 2 until xy.size step 2) p.lineTo(xy[i], xy[i + 1])
            canvas.drawPath(p, paint)
        }
        when (name) {
            "home" -> { path(3f,11f,12f,3f,21f,11f); path(5f,10f,5f,21f,10f,21f,10f,15f,14f,15f,14f,21f,19f,21f,19f,10f) }
            "search" -> { canvas.drawCircle(10f,10f,6f,paint); path(15f,15f,21f,21f) }
            "rank" -> { canvas.drawRect(4f,13f,7f,21f,paint); canvas.drawRect(10f,4f,13f,21f,paint); canvas.drawRect(16f,9f,19f,21f,paint) }
            "login" -> { canvas.drawRoundRect(9f,3f,21f,21f,3f,3f,paint); path(2f,12f,14f,12f); path(10f,8f,14f,12f,10f,16f) }
            "scan" -> { path(3f,8f,3f,3f,8f,3f); path(16f,3f,21f,3f,21f,8f); path(3f,16f,3f,21f,8f,21f); path(16f,21f,21f,21f,21f,16f); path(7f,12f,17f,12f) }
            "user" -> { canvas.drawCircle(12f,7f,4f,paint); canvas.drawRoundRect(4f,14f,20f,21f,4f,4f,paint) }
            "history" -> { canvas.drawCircle(12f,12f,9f,paint); path(12f,6f,12f,12f,16f,14f) }
            "chevron" -> path(9f,5f,16f,12f,9f,19f)
            "back" -> path(15f,4f,7f,12f,15f,20f)
            "play", "next", "previous" -> {
                val reverse = name == "previous"
                if (reverse) { canvas.translate(24f,0f); canvas.scale(-1f,1f) }
                path(7f,5f,17f,12f,7f,19f,7f,5f)
                if (name != "play") path(20f,5f,20f,19f)
            }
            "pause" -> { path(8f,5f,8f,19f); path(16f,5f,16f,19f) }
            "full" -> { path(9f,4f,4f,4f,4f,9f); path(15f,4f,20f,4f,20f,9f); path(4f,15f,4f,20f,9f,20f); path(15f,20f,20f,20f,20f,15f) }
            "favorite" -> path(12f,3f,15f,9f,22f,10f,17f,15f,18f,22f,12f,18f,6f,22f,7f,15f,2f,10f,9f,9f,12f,3f)
            "comments", "danmaku" -> { canvas.drawRoundRect(3f,4f,21f,17f,3f,3f,paint); path(7f,17f,7f,21f,12f,17f); path(7f,9f,17f,9f); path(7f,13f,14f,13f) }
            "episodes" -> for (y in listOf(5f,11f,17f)) { canvas.drawRect(3f,y,6f,y+2,paint); path(10f,y+1,21f,y+1) }
            "settings" -> { canvas.drawCircle(12f,12f,4f,paint); for(i in 0..7) { val a=i*Math.PI/4; path((12+8*kotlin.math.cos(a)).toFloat(),(12+8*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat()) }; canvas.drawCircle(12f,12f,8f,paint) }
            "close" -> { path(6f,6f,18f,18f); path(18f,6f,6f,18f) }
            else -> { canvas.drawCircle(12f,12f,1f,paint); canvas.drawCircle(12f,5f,1f,paint); canvas.drawCircle(12f,19f,1f,paint) }
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** TextView 将复合图标贴在内容边缘；补上空余空间的一半，使图标与文本组成居中的整体。 */
internal fun mobileIconOffset(view: TextView, icon: Drawable): Pair<Float, Float> {
    val layout = view.layout ?: return 0f to 0f
    val hasText = !view.text.isNullOrEmpty()
    val gap = if (hasText) view.compoundDrawablePadding else 0
    val drawables = view.compoundDrawables
    val horizontal = (view.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL
    val vertical = (view.gravity and Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL
    val textWidth = if (hasText) (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f else 0f
    val x = if (drawables[0] === icon && horizontal)
        ((view.width - view.paddingLeft - view.paddingRight - icon.bounds.width() - gap - textWidth) / 2f).coerceAtLeast(0f) else 0f
    val y = if (drawables[1] === icon && vertical)
        ((view.height - view.paddingTop - view.paddingBottom - icon.bounds.height() - gap - if (hasText) layout.height else 0) / 2f).coerceAtLeast(0f) else 0f
    return x to y
}

/** 标题中的阿拉伯或常见中文季数；歧义或无法识别时不推断。 */
internal fun titleSeasonNumber(title: String): Int? {
    val matches=Regex("第([0-9零〇一二两三四五六七八九十百]+)季").findAll(title).toList()
    if(matches.size!=1) return null
    val value=matches.single().groupValues[1]
    value.toIntOrNull()?.let { return it.takeIf { number -> number in 1..999 } }
    val digits=mapOf('零' to 0,'〇' to 0,'一' to 1,'二' to 2,'两' to 2,'三' to 3,'四' to 4,'五' to 5,'六' to 6,'七' to 7,'八' to 8,'九' to 9)
    var total=0; var digit=0
    value.forEach { ch ->
        when(ch) {
            '十' -> { total+=(if(digit==0) 1 else digit)*10; digit=0 }
            '百' -> { total+=(if(digit==0) 1 else digit)*100; digit=0 }
            else -> digit=digits[ch] ?: return null
        }
    }
    return (total+digit).takeIf { it in 1..999 }
}

/** 官网有时不返回当前季；仅当相邻季有官方标记时补入当前季。 */
internal fun relatedWithCurrent(current: Series, rows: List<RelatedWork>): List<RelatedWork> {
    if(rows.any { it.series.id==current.id }) return rows
    val number=titleSeasonNumber(current.title)
    val seasonPattern=Regex("^第\\s*([0-9]+)\\s*季$")
    fun season(work:RelatedWork)=seasonPattern.matchEntire(work.season)?.groupValues?.get(1)?.toIntOrNull()
    val previous=if(number!=null) rows.indexOfFirst { season(it)==number-1 } else -1
    val next=if(number!=null) rows.indexOfFirst { season(it)==number+1 } else -1
    val position=when { previous>=0 -> previous+1; next>=0 -> next; else -> 0 }
    val label=if(number!=null && (previous>=0 || next>=0)) "第${number}季" else ""
    return rows.toMutableList().apply { add(position,RelatedWork(current,label,false)) }
}

/** 按接口季标识查找上一季；没有可靠季标识时从开头展示，不把同 IP 推断为上一季。 */
internal fun relatedStartIndex(rows: List<RelatedWork>, currentId: String): Int {
    val current = rows.indexOfFirst { it.series.id == currentId }
    if (current < 0) return 0
    val seasonPattern = Regex("^第\\s*([0-9]+)\\s*季$")
    val number = seasonPattern.matchEntire(rows[current].season)?.groupValues?.get(1)?.toIntOrNull()
    if (number != null && number > 1) {
        val previous = rows.indexOfFirst { seasonPattern.matchEntire(it.season)?.groupValues?.get(1)?.toIntOrNull() == number-1 }
        if (previous >= 0) return previous
    }
    return 0
}

internal fun mobileButton(context: Context, title: String, icon: String? = null, color: Int = TvStyle.text, action: (View) -> Unit): TextView =
    TextView(context).apply {
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        id = View.generateViewId(); text = title; textSize = 13f
        includeFontPadding = false
        gravity = Gravity.CENTER; setTextColor(color)
        minHeight = dp(48); minWidth = dp(48)
        isFocusable = true; isFocusableInTouchMode = false
        setPadding(dp(8), dp(7), dp(8), dp(7))
        if (icon != null) {
            lateinit var drawable: MobileIcon
            drawable = MobileIcon(icon, color) { mobileIconOffset(this, drawable) }.apply { setBounds(0,0,dp(18),dp(18)) }
            setCompoundDrawables(drawable,null,null,null); compoundDrawablePadding = if(title.isEmpty()) 0 else dp(4)
        }
        SurfaceStyle.button(this)
        setOnClickListener(action)
    }

/** 固定顶栏里的等宽文字按钮；大字号在格内换行，保留完整标签和点击范围。 */
internal fun mobileHeaderTab(view: TextView) {
    fun dp(n: Int) = (n * view.resources.displayMetrics.density).toInt()
    view.textSize = 13f
    view.gravity = Gravity.CENTER
    view.minWidth = dp(48)
    view.minHeight = dp(48)
    view.maxLines = 2
    view.ellipsize = null
    view.setPadding(dp(4), dp(6), dp(4), dp(6))
    SurfaceStyle.headerTab(view)
}

/** 保留七个业务索引；历史是底部一级入口，搜索与分类通过顶栏和“我的”进入。 */
internal class MobileCatalogShell(context: Context, tab: Int, open: (Int) -> Unit, search: () -> Unit) : LinearLayout(context) {
    val content = LinearLayout(context).apply { orientation=VERTICAL }
    val headerContent = FrameLayout(context)
    val navigation: List<View>
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()

    fun setHeaderContent(view: View) {
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        headerContent.removeAllViews()
        headerContent.addView(view, FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL))
    }

    init {
        orientation=VERTICAL
        content.setPadding(dp(12),dp(4),dp(12),0)
        setBackgroundColor(SurfaceStyle.background)
        val header=LinearLayout(context).apply {
            gravity=Gravity.CENTER_VERTICAL
            background=SurfaceStyle.chrome()
            minimumHeight=dp(52)
            setPadding(dp(8),dp(2),dp(8),dp(2))
        }
        val heading=TextView(context).apply {
            text=listOf("果映 TV","找剧","排行榜","收藏","观看历史","账户预约","我的")[tab]
            textSize=18f; gravity=Gravity.CENTER; setTextColor(TvStyle.text); setTypeface(null,Typeface.BOLD)
            maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
        }
        if(tab in listOf(1,3,5))
            header.addView(mobileButton(context,"","back") { open(if(tab==1) 0 else 6) }.apply { contentDescription="返回" },
                LayoutParams(dp(48),dp(48)))
        else if(tab == 6) header.addView(Space(context),LayoutParams(dp(48),dp(48)))
        header.addView(headerContent,LayoutParams(0,-2,1f))
        if(tab !in listOf(0,2,4)) setHeaderContent(heading)
        header.addView(ImageButton(context).apply {
            contentDescription="找剧：关键词与分类筛选"
            setImageDrawable(MobileIcon("search",TvStyle.text))
            scaleType=ImageView.ScaleType.CENTER_INSIDE
            isFocusable=true; isFocusableInTouchMode=false
            SurfaceStyle.headerTab(this)
            // 与分类共用扁平背景和按下反馈，48dp 点击范围内将 18dp 图标居中。
            setPadding(dp(15),dp(15),dp(15),dp(15))
            setOnClickListener { search() }
        }, LayoutParams(dp(48),dp(48)))
        addView(header,LayoutParams(-1,-2))
        addView(View(context).apply { setBackgroundColor(SurfaceStyle.divider) },LayoutParams(-1,dp(1)))
        addView(content,LayoutParams(-1,0,1f))
        addView(View(context).apply { setBackgroundColor(SurfaceStyle.divider) },LayoutParams(-1,dp(1)))
        val bar=LinearLayout(context).apply { background=SurfaceStyle.chrome(); gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(2),0,dp(2)) }
        val indices=listOf(0,4,2,6)
        val active=when(tab) { 1 -> 0; 3,5 -> 6; else -> tab }
        val buttons=indices.mapIndexed { i,index ->
            val selected=active==index
            val label=listOf("首页","观看历史","排行榜","我的")[i]
            LinearLayout(context).apply {
                id=View.generateViewId(); orientation=VERTICAL; gravity=Gravity.CENTER
                isFocusable=true; isFocusableInTouchMode=false; isSelected=selected
                contentDescription=label
                minimumHeight=dp(56)
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
                val icon=ImageView(context).apply {
                    setImageDrawable(MobileIcon(listOf("home","history","rank","user")[i],if(selected) Color.WHITE else TvStyle.mobileMuted))
                    setPadding(dp(10),dp(10),dp(10),dp(10))
                    isDuplicateParentStateEnabled=true
                    importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    SurfaceStyle.button(this,accent=selected,circle=true)
                }
                addView(icon,LayoutParams(dp(40),dp(40)))
                addView(TextView(context).apply {
                    text=label; textSize=11f; gravity=Gravity.CENTER
                    setTextColor(if(selected) SurfaceStyle.accentText else TvStyle.mobileMuted)
                    includeFontPadding=false; setPadding(0,dp(1),0,dp(1))
                    importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },LayoutParams(-1,-2))
                setOnClickListener { open(index) }
                bar.addView(this,LayoutParams(0,-2,1f))
            }
        }
        addView(bar,LayoutParams(-1,-2))
        navigation=listOf(buttons[0],buttons[0],buttons[2],buttons[3],buttons[1],buttons[3],buttons[3])
    }
}

/** 只在主动全屏期间覆盖方向，退出时恢复进入前的 Activity 方向策略。 */
internal class MobileFullscreenOrientation(private val current: () -> Int, private val apply: (Int) -> Unit) {
    private var previous: Int? = null
    fun enter(orientation: Int) {
        if (previous != null) return
        previous = current()
        apply(orientation)
    }
    fun restore() {
        val value = previous ?: return
        previous = null
        apply(value)
    }
}

/** 手机专属观看外壳。尺寸与全屏只改变视图，不创建或重新解析播放器。 */
internal class MobilePlayerView(context: Context, private val action: (Action, View) -> Unit, private val select: (Int) -> Unit) : LinearLayout(context) {
    enum class Action { BACK, PLAY, PREVIOUS, NEXT, EPISODES, FAVORITE, COMMENTS, DANMAKU, SETTINGS, FULLSCREEN, LANDSCAPE_FULLSCREEN, RETRY_RELATED, TOGGLE }
    val video = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
    val overlay = LinearLayout(context).apply { orientation=VERTICAL; gravity=Gravity.BOTTOM }
    val title = TextView(context).apply { textSize=20f; setTextColor(TvStyle.text); setTypeface(null,Typeface.BOLD) }
    val status = TextView(context).apply { textSize=12f; setTextColor(Color.WHITE) }
    val seek = SeekBar(context).apply { max=1000; contentDescription="播放进度"; progressTintList=android.content.res.ColorStateList.valueOf(TvStyle.accent); thumbTintList=progressTintList }
    val errors = LinearLayout(context).apply { orientation=VERTICAL }
    private val top = LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
    private val body = LinearLayout(context).apply { orientation=VERTICAL }
    private val scroll = ScrollView(context).apply { isFillViewport=false }
    private val metadata = TextView(context).apply { textSize=13f; setTextColor(TvStyle.mobileMuted) }
    private val episodes = LinearLayout(context)
    private val synopsis = TextView(context).apply { textSize=14f; setTextColor(TvStyle.mobileMuted); maxLines=3; ellipsize=android.text.TextUtils.TruncateAt.END }
    private val recommendations = LinearLayout(context).apply { orientation=VERTICAL }
    private val play = control("", "pause",Action.PLAY,Color.WHITE)
    private val previous = control("上一集","previous",Action.PREVIOUS)
    private val next = control("下一集","next",Action.NEXT)
    private val favorite = control("收藏","favorite",Action.FAVORITE)
    private val fullscreenButton = control("", "full",Action.FULLSCREEN,Color.WHITE)
    private var expanded = false
    private var landscape = false
    private var landscapeDismissed = false
    private var lastWide = false
    private var windowWidth=0
    private var windowHeight=0
    val fullscreen get() = expanded || landscape
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun control(label:String, icon:String, type:Action, color:Int=TvStyle.text) = mobileButton(context,label,icon,color) { action(type,it) }
    init {
        orientation=VERTICAL; setBackgroundColor(SurfaceStyle.background)
        top.background=SurfaceStyle.chrome()
        top.addView(control("","back",Action.BACK).apply { contentDescription="返回" })
        top.addView(TextView(context).apply { text="正在播放"; textSize=17f; gravity=Gravity.CENTER; setTextColor(TvStyle.text) },LayoutParams(0,-2,1f))
        top.addView(control("","settings",Action.SETTINGS).apply { contentDescription="播放设置" })
        addView(top,LayoutParams(-1,-2))
        addView(video,LayoutParams(-1,dp(210)))
        scroll.addView(body); addView(scroll,LayoutParams(-1,0,1f))
        body.setPadding(dp(12),dp(8),dp(12),dp(16))
        body.addView(title); body.addView(metadata,LayoutParams(-1,-2).apply { topMargin=dp(7); bottomMargin=dp(14) })
        body.addView(errors)
        val fullscreenChoices=LinearLayout(context)
        listOf("竖屏全屏" to Action.FULLSCREEN,"横屏全屏" to Action.LANDSCAPE_FULLSCREEN).forEach { (label,mode) ->
            fullscreenChoices.addView(control(label,"full",mode),LayoutParams(0,-1,1f))
        }
        body.addView(fullscreenChoices)
        val transport=LinearLayout(context)
        listOf(previous,control("选集","episodes",Action.EPISODES),next).forEach {
            it.textSize=12f; it.setPadding(dp(6),dp(7),dp(6),dp(7))
            transport.addView(it,LayoutParams(0,-2,1f).apply { setMargins(dp(3),0,dp(3),0) })
        }
        body.addView(transport)
        val options=LinearLayout(context)
        listOf(favorite,control("评论","comments",Action.COMMENTS),control("弹幕","danmaku",Action.DANMAKU),control("设置","settings",Action.SETTINGS)).forEach {
            // 图标在标签上方，320dp 或大字体时仍为四等分触控目标。
            val icon=it.compoundDrawables[0]; it.setCompoundDrawables(null,icon,null,null); it.textSize=12f
            options.addView(it,LayoutParams(0,-1,1f))
        }
        body.addView(options,LayoutParams(-1,-2).apply { topMargin=dp(4); bottomMargin=dp(6) })
        body.addView(control("选集 · 查看全部","episodes",Action.EPISODES).apply { gravity=Gravity.START or Gravity.CENTER_VERTICAL; setBackgroundColor(Color.TRANSPARENT) })
        body.addView(episodes)
        body.addView(TextView(context).apply { text="剧情简介"; textSize=17f; setTextColor(TvStyle.text); setTypeface(null,Typeface.BOLD); setPadding(0,dp(12),0,dp(6)) })
        synopsis.setOnClickListener { synopsis.maxLines=if(synopsis.maxLines==3) Int.MAX_VALUE else 3 }
        body.addView(synopsis); body.addView(recommendations)
        showRelatedLoading()
        val middle=FrameLayout(context)
        play.contentDescription="播放或暂停"; play.setBackgroundColor(0x55000000); play.setCompoundDrawables(null,play.compoundDrawables[0],null,null)
        middle.addView(play,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER))
        overlay.addView(middle,LayoutParams(-1,0,1f))
        val timeline=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(12),0,dp(4),0); setBackgroundColor(0xAA000000.toInt()) }
        timeline.addView(status,LayoutParams(0,-2,1f))
        timeline.addView(fullscreenButton.apply { contentDescription="竖屏全屏"; setBackgroundColor(Color.TRANSPARENT) })
        overlay.addView(timeline); overlay.addView(seek,LayoutParams(-1,dp(48)))
        overlay.setPadding(0,0,0,dp(2))
    }
    fun attachVideo(view: View) {
        video.addView(view,FrameLayout.LayoutParams(-1,-1))
        video.setOnClickListener { action(Action.TOGGLE,it) }
        video.addView(overlay,FrameLayout.LayoutParams(-1,-1))
    }
    fun setFullscreenInsets(left: Int, top: Int, right: Int, bottom: Int) {
        overlay.setPadding(left, top, right, bottom + dp(2))
    }
    fun update(series:Series, index:Int, count:Int, playing:Boolean, favoriteLabel:String) {
        title.text=series.title; metadata.text="第 ${index+1} 集 / 共 $count 集"
        synopsis.text=series.description.ifBlank { "暂无剧情简介" }
        previous.isEnabled=index>0; previous.alpha=if(index>0) 1f else .35f
        next.isEnabled=index<count-1; next.alpha=if(index<count-1) 1f else .35f
        favorite.text=favoriteLabel
        lateinit var icon: MobileIcon
        icon=MobileIcon(if(playing) "pause" else "play",Color.WHITE) { mobileIconOffset(play,icon) }.apply { setBounds(0,0,dp(22),dp(22)) }
        play.setCompoundDrawables(null,icon,null,null)
        play.contentDescription=if(playing) "暂停" else "播放"
        if (episodes.tag != "$index:$count") {
            episodes.tag="$index:$count"; episodes.removeAllViews()
            val start=(index-2).coerceAtLeast(0).coerceAtMost((count-5).coerceAtLeast(0))
            (start until minOf(start+5,count)).forEach { number ->
                val selected=number==index
                episodes.addView(mobileButton(context,"${number+1}",color=if(selected) Color.WHITE else TvStyle.text) { select(number) }.apply {
                    if(selected) { SurfaceStyle.button(this,accent=true); setTextColor(SurfaceStyle.onAccent) }
                },LayoutParams(0,-2,1f).apply { setMargins(dp(3),0,dp(3),0) })
            }
        }
    }
    private fun relatedHeading() {
        recommendations.removeAllViews()
        recommendations.addView(TextView(context).apply { text="相关作品"; textSize=17f; setTextColor(TvStyle.text); setTypeface(null,Typeface.BOLD); setPadding(0,dp(12),0,dp(8)) })
    }
    private fun relatedStatus(value:String) {
        recommendations.addView(TextView(context).apply { text=value; textSize=13f; setTextColor(TvStyle.mobileMuted); setPadding(0,dp(4),0,dp(8)) })
    }
    fun showRelatedLoading() { relatedHeading(); relatedStatus("正在加载相关作品…") }
    fun showRelatedFailed() {
        relatedHeading(); relatedStatus("相关作品暂时无法加载，可重试")
        recommendations.addView(mobileButton(context,"重试") { action(Action.RETRY_RELATED,it) })
    }
    fun showRelatedWorks(current:Series, rows:List<RelatedWork>, cover:(ImageView,String)->Unit, open:(RelatedWork,View)->Unit) {
        relatedHeading()
        if(rows.none { it.series.id!=current.id }) { relatedStatus("暂无相关作品"); return }
        relatedStatus("其他季及同 IP 作品 · 左右滑动查看更多")
        val rail=HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled=false }
        val row=LinearLayout(context)
        val ordered=relatedWithCurrent(current,rows)
        ordered.forEach { work ->
            val isCurrent=work.series.id==current.id
            val label=if(isCurrent) listOf(work.season,"当前播放").filter { it.isNotBlank() }.joinToString(" · ") else listOf(work.season,if(work.upcoming) "待上线 · 预约" else "已上线").filter { it.isNotBlank() }.joinToString(" · ")
            val card=LinearLayout(context).apply {
                orientation=VERTICAL; tag=work.series.id; contentDescription="${work.series.title}，$label"
                if(!isCurrent) { foreground=TvStyle.pressHighlight(context); setOnClickListener { open(work,it) } }
            }
            val image=ImageView(context).apply { scaleType=ImageView.ScaleType.CENTER_CROP; contentDescription=work.series.title }
            cover(image,work.series.cover); card.addView(image,LayoutParams(-1,dp(145)))
            card.addView(TextView(context).apply { text=work.series.title; textSize=12f; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END; setTextColor(TvStyle.text) })
            card.addView(TextView(context).apply { text=label; textSize=11f; setTextColor(if(isCurrent || work.upcoming) SurfaceStyle.accentText else TvStyle.mobileMuted); setPadding(0,dp(3),0,0) })
            row.addView(card,LayoutParams(dp(104),-2).apply { rightMargin=dp(10) })
        }
        rail.addView(row); recommendations.addView(rail)
        val start=relatedStartIndex(ordered,current.id)
        rail.post { row.getChildAt(start)?.let { rail.scrollTo(it.left,0) } }
    }
    fun toggleFullscreen() { if(fullscreen) { expanded=false; landscape=false; landscapeDismissed=true } else expanded=true; resize() }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val w=View.MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight
        val h=View.MeasureSpec.getSize(heightMeasureSpec)-paddingTop-paddingBottom
        updateWindow(w,h)
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
    fun updateWindow(w:Int,h:Int) {
        if(w!=windowWidth || h!=windowHeight) {
            windowWidth=w; windowHeight=h
            val wide=w>h
            if(wide!=lastWide) { landscapeDismissed=false; lastWide=wide }
            landscape=wide && !landscapeDismissed
            resize()
        }
    }
    private fun resize() {
        fullscreenButton.contentDescription=if(fullscreen) "退出全屏" else "竖屏全屏"
        top.visibility=if(fullscreen) GONE else VISIBLE
        scroll.visibility=if(fullscreen) GONE else VISIBLE
        video.layoutParams=LayoutParams(-1,if(fullscreen) 0 else minOf((windowWidth*9f/16f).toInt(),(windowHeight*.48f).toInt()).coerceAtLeast(dp(100)),if(fullscreen) 1f else 0f)
    }
}
