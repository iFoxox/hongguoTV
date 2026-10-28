// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** 硬件能力与界面模式独立：兼容漏报触摸屏的手机，也允许用户覆盖自动判断。 */
internal object InteractionMode {
    enum class Mode(val label: String) {
        AUTO("自动"), MOBILE("手机 / 平板"), TV("TV / 遥控器")
    }

    const val TEST_BUILD = "tv-mobile-M26-dialog-contrast-20261028"
    private const val PREFS = "interaction_mode"
    private const val KEY = "mode"

    fun selected(context: Context): Mode {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return Mode.entries.firstOrNull { it.name == stored } ?: Mode.AUTO
    }

    fun save(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }

    fun useMobile(context: Context): Boolean {
        when (selected(context)) {
            Mode.MOBILE -> return true
            Mode.TV -> return false
            Mode.AUTO -> Unit
        }
        val config = context.resources.configuration
        val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val pm = context.packageManager
        val television = manager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            config.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        if (television) return false
        return pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) ||
            pm.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH) ||
            config.touchscreen == Configuration.TOUCHSCREEN_FINGER
    }

    fun label(context: Context): String {
        val effective = if (useMobile(context)) "手机" else "TV"
        return if (selected(context) == Mode.AUTO) "自动（当前$effective）" else selected(context).label
    }
}
