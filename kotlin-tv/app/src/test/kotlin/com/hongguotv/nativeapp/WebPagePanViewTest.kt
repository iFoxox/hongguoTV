// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 26])
class WebPagePanViewTest {
    @Test fun nativeDraggingMovesPageAndCancellationStopsMovement() {
        val c = Robolectric.buildActivity(Activity::class.java).setup()
        val moves = mutableListOf<Pair<Int, Int>>()
        val view = WebPagePanView(c.get()) { dx, dy -> moves += dx to dy }
        fun touch(action: Int, x: Float, y: Float) {
            MotionEvent.obtain(0, 20, action, x, y, 0).let {
                assertTrue(view.onTouchEvent(it)); it.recycle()
            }
        }
        try {
            touch(MotionEvent.ACTION_DOWN, 250f, 300f)
            touch(MotionEvent.ACTION_MOVE, 249f, 299f)
            assertTrue(moves.isEmpty()) // 点击和轻微抖动不平移，也不会触发网页按钮。
            touch(MotionEvent.ACTION_MOVE, 150f, 200f)
            assertEquals(listOf(100 to 100), moves)
            touch(MotionEvent.ACTION_MOVE, 170f, 220f)
            assertEquals(-20 to -20, moves.last())
            touch(MotionEvent.ACTION_CANCEL, 170f, 220f)
            touch(MotionEvent.ACTION_MOVE, 10f, 10f)
            assertEquals(2, moves.size)
        } finally { c.pause().stop().destroy() }
    }
}
