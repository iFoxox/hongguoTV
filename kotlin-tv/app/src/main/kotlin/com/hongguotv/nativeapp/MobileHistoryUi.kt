// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.*
import android.widget.*

/** 手机历史的两个来源左右并排；沿用原业务状态，不创建新的记录分区。 */
internal class MobileHistoryTabs(context: Context, account: Boolean, select: (Boolean) -> Unit) : LinearLayout(context) {
    val tabs: List<TextView>
    init {
        orientation = HORIZONTAL
        tabs = listOf(false,true).map { remote ->
            mobileButton(context, if(remote) "账户历史" else "本机历史", color=
                if(account==remote) SurfaceStyle.accentText else TvStyle.mobileMuted) { if(account!=remote) select(remote) }.apply {
                isSelected = account==remote
                mobileHeaderTab(this)
                addView(this, LayoutParams(0,-2,1f))
            }
        }
    }
}

internal class MobileHistoryEmpty(
    context: Context, account: Boolean, title: String, description: String,
    actionLabel: String?, action: () -> Unit,
) : LinearLayout(context) {
    init {
        val density=resources.displayMetrics.density
        fun dp(n:Int)=(n*density).toInt()
        orientation=VERTICAL; gravity=Gravity.CENTER
        setPadding(dp(16),dp(28),dp(16),dp(32))
        addView(ImageView(context).apply {
            setImageDrawable(HistoryEmptyArt(account,TvStyle.mobileMuted))
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(dp(112),dp(96)).apply { bottomMargin=dp(20) })
        fun label(value:String,size:Float,color:Int)=TextView(context).apply {
            text=value; textSize=size; setTextColor(color); gravity=Gravity.CENTER
        }
        addView(label(title,19f,TvStyle.text).apply { setTypeface(null,Typeface.BOLD) },LayoutParams(-1,-2))
        addView(label(description,14f,TvStyle.mobileMuted),LayoutParams(-1,-2).apply { topMargin=dp(10); bottomMargin=dp(24) })
        if(actionLabel!=null) addView(mobileButton(context,actionLabel,color=if(account) SurfaceStyle.onAccent else SurfaceStyle.accentText) { action() }.apply {
            minWidth=dp(160)
            if(account) SurfaceStyle.button(this,accent=true)
        },LayoutParams(-2,-2))
        if(account) addView(label("本机记录与账户记录分别保存",12f,TvStyle.mobileMuted),LayoutParams(-1,-2).apply { topMargin=dp(16) })
    }
}

private class HistoryEmptyArt(private val account:Boolean, private val color:Int):Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=2f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    override fun draw(canvas:Canvas) {
        val save=canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/112f,bounds.height()/96f)
        paint.color=color;paint.alpha=120
        if(account) {
            canvas.drawCircle(47f,27f,16f,paint)
            canvas.drawRoundRect(20f,51f,74f,77f,12f,12f,paint)
            canvas.drawRoundRect(70f,62f,92f,85f,4f,4f,paint)
            canvas.drawArc(RectF(75f,51f,87f,72f),180f,180f,false,paint)
        } else {
            canvas.drawRoundRect(21f,9f,71f,76f,7f,7f,paint)
            for(y in listOf(26f,39f,52f)) canvas.drawLine(32f,y,57f,y,paint)
            canvas.drawCircle(77f,69f,18f,paint)
            canvas.drawLine(77f,58f,77f,69f,paint);canvas.drawLine(77f,69f,86f,74f,paint)
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha:Int) { paint.alpha=alpha }
    override fun setColorFilter(filter:ColorFilter?) { paint.colorFilter=filter }
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

/** 两个官方登录入口使用与原型一致的卡片；ListView 保留原生点击与无障碍行为。 */
internal class MobileAccountChoices(private val context:Context,private val labels:Array<String>):BaseAdapter() {
    override fun getCount()=labels.size
    override fun getItem(position:Int)=labels[position]
    override fun getItemId(position:Int)=position.toLong()
    override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
        val density=context.resources.displayMetrics.density
        fun dp(n:Int)=(n*density).toInt()
        return FrameLayout(context).apply {
            setPadding(dp(4),dp(4),dp(4),dp(4))
            val row=LinearLayout(context).apply {
                gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(72);setPadding(dp(10),dp(8),dp(10),dp(8))
                SurfaceStyle.card(this)
            }
            row.addView(ImageView(context).apply {
                setImageDrawable(MobileIcon(if(position==0) "login" else "scan",SurfaceStyle.accentText));setPadding(dp(10),dp(10),dp(10),dp(10))
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },LinearLayout.LayoutParams(dp(42),dp(42)))
            val copy=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,dp(6),0) }
            copy.addView(TextView(context).apply { text=labels[position];textSize=15f;setTextColor(TvStyle.text);setTypeface(null,Typeface.BOLD) })
            copy.addView(TextView(context).apply {
                text=if(position==0) "在官方页面完成登录验证" else "使用已登录的红果 App 扫码"
                textSize=12f;setTextColor(TvStyle.mobileMuted);setPadding(0,dp(5),0,0)
            })
            row.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(ImageView(context).apply { setImageDrawable(MobileIcon("chevron",TvStyle.mobileMuted));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(18),dp(18)))
            addView(row,FrameLayout.LayoutParams(-1,-2))
        }
    }
}
