// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 26], qualifiers = "w320dp-h640dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class MobileHeaderTest {
    @Test
    fun everyHeaderLabelAndSearchFitWithoutScrollingAtLargeFontSizes() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val oldTouch = TvStyle.touch
        val oldMode = TvStyle.mode
        TvStyle.touch = true
        try {
            for (mode in TvStyle.Mode.entries) {
                TvStyle.apply(mode)
                for (scale in listOf(1f, 1.3f, 1.6f)) {
                    val config = Configuration(activity.resources.configuration).apply { fontScale = scale }
                    activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
                    for ((tab, labels) in listOf(
                        0 to listOf("推荐", "漫剧", "真人剧", "换一批"),
                        2 to listOf("综合", "真人剧", "漫剧", "AI 剧"),
                        4 to listOf("本机历史", "账户历史"),
                    )) {
                        var searches = 0
                        val shell = MobileCatalogShell(activity, tab, {}, { searches++ })
                        val choices = LinearLayout(activity)
                        val buttons = labels.map { label ->
                            mobileButton(activity, label) {}.apply {
                                mobileHeaderTab(this)
                                choices.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
                            }
                        }
                        buttons.first().isSelected = true
                        shell.setHeaderContent(choices)
                        activity.setContentView(shell)
                        val header = shell.getChildAt(0) as LinearLayout
                        val search = (0 until header.childCount).map { header.getChildAt(it) }.filterIsInstance<ImageButton>().single()
                        for (width in listOf(320, 360, 600)) {
                            repeat(2) {
                                shell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
                                shell.layout(0, 0, width, 640)
                            }
                            assertEquals(0, shell.headerContent.scrollX)
                            assertTrue(shell.content.height >= 400)
                            assertEquals(48, search.width)
                            assertEquals(48, search.height)
                            buttons.forEach { button ->
                                val hint = "$mode, font $scale, width $width, ${button.text}"
                                val rect = Rect(0, 0, button.width, button.height)
                                header.offsetDescendantRectToMyCoords(button, rect)
                                assertTrue(hint, button.width >= 48 && button.height >= 48)
                                assertTrue(hint, rect.left >= 0 && rect.right <= search.left)
                                assertTrue(hint, rect.top >= 0 && rect.bottom <= header.height)
                                assertEquals(hint, button.text.length, button.layout.getLineEnd(button.layout.lineCount - 1))
                                assertEquals(hint, 0, button.layout.getEllipsisCount(0))
                                assertTrue(hint, button.layout.height <= button.height - button.paddingTop - button.paddingBottom)
                            }
                        }
                        search.performClick()
                        assertEquals(1, searches)
                        assertFalse(search.isFocusableInTouchMode)
                    }
                }
            }
        } finally {
            TvStyle.touch = oldTouch
            TvStyle.apply(oldMode)
            controller.pause().stop().destroy()
        }
    }
}
