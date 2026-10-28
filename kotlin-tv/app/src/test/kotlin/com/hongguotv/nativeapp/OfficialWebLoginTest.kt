// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.webkit.WebSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 26])
class OfficialWebLoginTest {
    @Test fun loginControlsKeepReadableEqualHeightAtLargeFontOnSmallPhone() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val config = android.content.res.Configuration(controller.get().resources.configuration).apply { fontScale=1.3f }
        controller.get().resources.updateConfiguration(config,controller.get().resources.displayMetrics)
        val oldTouch = TvStyle.touch
        TvStyle.touch = true
        val page = OfficialWebLogin(controller.get(), {}, {})
        try {
            page.setPhoneMode(false); page.show()
            val field=OfficialWebLogin::class.java.getDeclaredField("popup").apply { isAccessible=true }
            val root=(field.get(page) as android.app.Dialog).findViewById<android.view.View>(android.R.id.content)
            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(320,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(640,android.view.View.MeasureSpec.EXACTLY))
            root.layout(0,0,320,640)
            fun views(v:android.view.View):List<android.view.View> = listOf(v)+if(v is android.view.ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
            val buttons=views(root).filterIsInstance<android.widget.Button>()
            for (names in listOf(listOf("关闭","打开登录","完成登录"),listOf("缩小","放大","恢复全页","移动页面"))) {
                val row=names.map { name -> buttons.single { it.text==name } }
                assertEquals(1,row.map { it.height }.distinct().size)
                row.forEach { button ->
                    assertTrue("${button.text} touch height",button.height>=48)
                    assertEquals(button.text.length,button.layout.getLineEnd(button.layout.lineCount-1))
                    assertTrue("${button.text} text fits",button.layout.height<=button.height-button.paddingTop-button.paddingBottom)
                }
            }
        } finally { page.dismiss(); TvStyle.touch=oldTouch; controller.pause().stop().destroy() }
    }

    @Test fun navigationRejectsLookalikeHostsInsecureSchemesAndUnexpectedPorts() {
        listOf("https://hongguoduanju.com/", "https://hongguoduanju.com:443/passport/", "https://web.qznovel.com/wap-hongguo/dr-privacy-v3-ad.html").forEach {
            assertTrue(it, OfficialWebLogin.allowsNavigation(it))
        }
        listOf("http://hongguoduanju.com", "https://hongguoduanju.com.evil.test/", "https://evil.test/", "https://evil.test@hongguoduanju.com/", "https://hongguoduanju.com:8080/", "file:///data/test", "javascript:alert(1)", "intent://login").forEach {
            assertFalse(it, OfficialWebLogin.allowsNavigation(it))
        }
    }

    @Test fun embeddedPageEnablesOfficialScriptsWithoutFileAccessAndDisposesOnClose() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        var dismissed = 0
        val page = OfficialWebLogin(controller.get(), { fail("Must not submit without user action") }, { dismissed++ })
        try {
            assertTrue(page.web.settings.javaScriptEnabled)
            assertTrue(page.web.settings.domStorageEnabled)
            assertFalse(page.web.settings.allowFileAccess)
            assertFalse(page.web.settings.allowContentAccess)
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, page.web.settings.mixedContentMode)
            assertFalse(page.web.settings.javaScriptCanOpenWindowsAutomatically)
            page.show()
            assertTrue(page.isShowing)
            fun buttons(view: android.view.View): List<android.widget.Button> =
                (if (view is android.widget.Button) listOf(view) else emptyList()) +
                    (if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) } else emptyList())
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            val dialogField = OfficialWebLogin::class.java.getDeclaredField("popup").apply { isAccessible = true }
            val dialog = dialogField.get(page) as android.app.Dialog
            buttons(dialog.window!!.decorView).single { it.text == "完成登录" }.performClick()
            assertTrue(page.isShowing) // 未登录时仅提示，不关闭页面或发出验证请求。
            page.dismiss()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertFalse(page.isShowing)
            assertEquals(1, dismissed)
            page.dismiss()
            assertEquals(1, dismissed)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun desktopIdentityAvoidsOfficialIpadDetectionOnTouchscreens() {
        // 官网 2026-10-27 的 nm / ud / Rs 判断：触屏 Mac 或 <640 视口会进入手机版。
        fun officialMobile(ua: String, touchPoints: Int, width: Int): Boolean =
            Regex("Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini|Mobile|Tablet|Windows Phone", RegexOption.IGNORE_CASE).containsMatchIn(ua) ||
                (ua.contains("Macintosh", ignoreCase = true) && touchPoints > 0) || width < 640
        val ua = com.hongguotv.core.WebLogin.BROWSER_UA
        assertTrue(officialMobile("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", 5, 1280))
        assertTrue(officialMobile(ua, 5, 360))
        assertFalse(officialMobile(ua, 5, 1280))
    }

    @Test fun finishedOfficialPageGetsWideZoomableViewportButExternalAndStalePagesDoNot() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val page = OfficialWebLogin(controller.get(), {}, {})
        try {
            page.setPhoneMode(false)
            val shadow = org.robolectric.Shadows.shadowOf(page.web)
            val client = shadow.webViewClient
            val official = "https://hongguoduanju.com/"
            page.web.loadUrl(official)
            client.onPageFinished(page.web, "https://web.qznovel.com/privacy")
            assertNull(shadow.lastEvaluatedJavascript)
            val density = page.web.resources.displayMetrics.density
            page.web.layout(0, 0, (360 * density).toInt(), (640 * density).toInt())
            client.onPageFinished(page.web, official)
            val script = shadow.lastEvaluatedJavascript
            assertTrue(script.contains("width=1280"))
            assertTrue(script.contains("initial-scale=0.2813"))
            assertTrue(script.contains("user-scalable=yes"))
            assertTrue(script.contains("new Event('resize')"))
            assertFalse(script.contains(".click("))
            assertFalse(script.contains("document.cookie"))
            page.web.loadUrl("https://web.qznovel.com/privacy")
            client.onPageFinished(page.web, official)
            assertEquals(script, shadow.lastEvaluatedJavascript)
            assertFalse(OfficialWebLogin.isOfficialPage("https://web.qznovel.com/privacy"))
            assertFalse(OfficialWebLogin.isOfficialPage("https://hongguoduanju.com.evil.test/"))
        } finally { page.show(); page.dismiss(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); controller.pause().stop().destroy() }
    }

    @Test fun recoveryToolsStayNativeAndNeverReloadOrSubmitThePage() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        var submitted = false
        val page = OfficialWebLogin(controller.get(), { submitted = true }, {})
        try {
            page.setPhoneMode(false)
            page.show()
            val dialogField = OfficialWebLogin::class.java.getDeclaredField("popup").apply { isAccessible = true }
            val dialog = dialogField.get(page) as android.app.Dialog
            fun views(view: android.view.View): List<android.view.View> = listOf(view) +
                if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
            val all = views(dialog.window!!.decorView)
            fun button(text: String) = all.filterIsInstance<android.widget.Button>().single { it.text == text }
            val overlay = all.filterIsInstance<WebPagePanView>().single()
            val shadow = org.robolectric.Shadows.shadowOf(page.web)
            page.web.loadUrl("https://hongguoduanju.com/")
            val url = shadow.lastLoadedUrl
            assertEquals(android.view.View.GONE, overlay.visibility)
            button("移动页面").performClick()
            assertEquals(android.view.View.VISIBLE, overlay.visibility)
            button("结束移动").performClick()
            assertEquals(android.view.View.GONE, overlay.visibility)
            button("移动页面").performClick()
            page.web.layout(0, 0, 360, 640)
            shadow.webViewClient.onScaleChanged(page.web, 1f, 2f)
            button("恢复全页").performClick()
            assertEquals(android.view.View.GONE, overlay.visibility)
            assertNotNull(button("移动页面"))
            button("缩小").performClick()
            button("放大").performClick()
            assertEquals(url, shadow.lastLoadedUrl)
            assertNull(shadow.lastEvaluatedJavascript) // 工具不向官网注入点击、验证码或刷新操作。
            assertFalse(submitted)
        } finally { page.dismiss(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); controller.pause().stop().destroy() }
    }

    @Test fun autoOpenWaitsForEntryStopsAfterClickAndIgnoresStaleCallbacks() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val page = OfficialWebLogin(controller.get(), { fail("Auto-open must not complete sign-in") }, {})
        try {
            page.setPhoneMode(false)
            page.show()
            val shadow = org.robolectric.Shadows.shadowOf(page.web)
            val client = shadow.webViewClient
            val url = "https://hongguoduanju.com/"
            page.web.loadUrl(url)
            client.onPageStarted(page.web, url, null)
            client.onPageCommitVisible(page.web, url)
            shadow.lastEvaluatedJavascriptCallback.onReceiveValue("null")
            assertEquals(OfficialWebLogin.openLoginScript(), shadow.lastEvaluatedJavascript)
            shadow.lastEvaluatedJavascriptCallback.onReceiveValue("\"waiting\"")
            val oldCallback = shadow.lastEvaluatedJavascriptCallback
            client.onPageStarted(page.web, "https://web.qznovel.com/privacy", null)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
            oldCallback.onReceiveValue("\"waiting\"")
            client.onPageStarted(page.web, url, null)
            client.onPageFinished(page.web, url)
            shadow.lastEvaluatedJavascriptCallback.onReceiveValue("null")
            shadow.lastEvaluatedJavascriptCallback.onReceiveValue("\"requested\"")
            val opened = OfficialWebLogin::class.java.getDeclaredField("loginOpened").apply { isAccessible=true }
            assertFalse(opened.getBoolean(page))
            org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
            shadow.lastEvaluatedJavascriptCallback.onReceiveValue("\"opened\"")
            assertTrue(opened.getBoolean(page))
            val confirmedScript=shadow.lastEvaluatedJavascript
            client.onPageFinished(page.web, url)
            assertEquals(confirmedScript, shadow.lastEvaluatedJavascript)
            // 打开后即使 onPageFinished 再到达，也不重新点击或干预用户已关闭的弹窗。
            page.dismiss()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        } finally { page.dismiss(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); controller.pause().stop().destroy() }
    }

    @Test fun phoneModeHidesDisplayToolsAndFitsModalWithoutSubmittingOrReloading() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val page = OfficialWebLogin(controller.get(), { fail("Layout must not submit login") }, {})
        try {
            page.show()
            val shadow = org.robolectric.Shadows.shadowOf(page.web)
            val url = "https://hongguoduanju.com/"
            page.web.loadUrl(url)
            val density = page.web.resources.displayMetrics.density
            page.web.layout(0, 0, (360*density).toInt(), (480*density).toInt())
            val client = shadow.webViewClient
            client.onPageFinished(page.web, url)
            assertTrue(shadow.lastEvaluatedJavascript.contains("guoying-phone-login-layout"))
            assertTrue(shadow.lastEvaluatedJavascript.contains("width:360px"))
            assertTrue(shadow.lastEvaluatedJavascript.contains("initial-scale=1"))
            val dialog = OfficialWebLogin::class.java.getDeclaredField("popup").apply { isAccessible=true }.get(page) as android.app.Dialog
            fun all(view:android.view.View):List<android.view.View> = listOf(view) +
                if(view is android.view.ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
            val buttons = all(dialog.window!!.decorView).filterIsInstance<android.widget.Button>()
            val zoom = buttons.single { it.text == "缩小" }
            assertEquals(android.view.View.GONE, (zoom.parent as android.view.View).visibility)
            buttons.single { it.text == "原网页" }.performClick()
            assertEquals(android.view.View.VISIBLE, (zoom.parent as android.view.View).visibility)
            assertTrue(shadow.lastEvaluatedJavascript.contains("removeChild(style)"))
            buttons.single { it.text == "手机登录" }.performClick()
            assertEquals(android.view.View.GONE, (zoom.parent as android.view.View).visibility)
            assertTrue(shadow.lastEvaluatedJavascript.contains("guoying-phone-login-layout"))
            assertEquals(url, shadow.lastLoadedUrl)
        } finally { page.dismiss(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); controller.pause().stop().destroy() }
    }

    @Test fun phoneGeometryIgnoresOldModeCallbacksAndActiveTouch() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val page = OfficialWebLogin(controller.get(), { fail("Geometry must not sign in") }, {})
        try {
            page.show()
            page.web.loadUrl("https://hongguoduanju.com/")
            page.web.layout(0,0,360,480)
            val shadow = org.robolectric.Shadows.shadowOf(page.web)
            val start = OfficialWebLogin::class.java.getDeclaredMethod("startGeometryWatch", Int::class.javaPrimitiveType).apply { isAccessible=true }
            val signature = OfficialWebLogin::class.java.getDeclaredField("targetSignature").apply { isAccessible=true }
            val touching = OfficialWebLogin::class.java.getDeclaredField("touching").apply { isAccessible=true }
            start.invoke(page,OfficialWebLogin::class.java.getDeclaredField("loadEpoch").apply { isAccessible=true }.getInt(page))
            assertEquals(OfficialLoginLayout.geometryScript(),shadow.lastEvaluatedJavascript)
            val stale = shadow.lastEvaluatedJavascriptCallback
            page.setPhoneMode(false)
            page.setPhoneMode(true)
            stale.onReceiveValue("""{"kind":"challenge","x":500,"y":100,"width":340,"height":440}""")
            assertEquals("",signature.get(page))
            start.invoke(page,OfficialWebLogin::class.java.getDeclaredField("loadEpoch").apply { isAccessible=true }.getInt(page))
            assertEquals(OfficialLoginLayout.geometryScript(),shadow.lastEvaluatedJavascript)
            touching.setBoolean(page,true)
            shadow.lastEvaluatedJavascriptCallback.onReceiveValue("""{"kind":"challenge","x":500,"y":100,"width":340,"height":440}""")
            assertEquals("",signature.get(page)) // 手指正在操作时不自动改变缩放或位置。
            touching.setBoolean(page,false)
            // 浮动 Dialog 在 Robolectric 中不会给 WebView 提供真实窗口尺寸，明确设定渲染边界。
            org.robolectric.util.ReflectionHelpers.setField(page.web,"mLeft",0)
            org.robolectric.util.ReflectionHelpers.setField(page.web,"mTop",0)
            org.robolectric.util.ReflectionHelpers.setField(page.web,"mRight",360)
            org.robolectric.util.ReflectionHelpers.setField(page.web,"mBottom",480)
            start.invoke(page,OfficialWebLogin::class.java.getDeclaredField("loadEpoch").apply { isAccessible=true }.getInt(page))
            assertEquals(OfficialLoginLayout.geometryScript(),shadow.lastEvaluatedJavascript)
            assertTrue("Before geometry: ${page.web.width}x${page.web.height}",page.web.width > 0 && page.web.height > 0)
            shadow.lastEvaluatedJavascriptCallback.onReceiveValue("""{"kind":"challenge","x":500,"y":100,"width":340,"height":440}""")
            assertTrue("signature=${signature.get(page)}, view=${page.web.width}x${page.web.height}, showing=${page.isShowing}", signature.get(page).toString().startsWith("challenge:"))
        } finally { page.dismiss(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); controller.pause().stop().destroy() }
    }

    @Test fun officialVerificationFrameIsAllowedByLegacyCallbackWithoutPageScriptPrivileges() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        val page=OfficialWebLogin(controller.get(),{}, {})
        try {
            val client=org.robolectric.Shadows.shadowOf(page.web).webViewClient
            val frame="https://rmc.bytedance.com/captcha"
            assertTrue(OfficialWebLogin.allowsVerificationFrame(frame))
            assertFalse(client.shouldOverrideUrlLoading(page.web,frame))
            assertFalse(OfficialWebLogin.isOfficialPage(frame))
            assertFalse(OfficialWebLogin.allowsNavigation(frame))
            listOf("http://rmc.bytedance.com/","https://rmc.bytedance.com.evil.test/","https://other@rmc.bytedance.com/","https://rmc.bytedance.com:8080/").forEach {
                assertFalse(it,OfficialWebLogin.allowsVerificationFrame(it))
            }
        } finally { page.show();page.dismiss();org.robolectric.shadows.ShadowLooper.idleMainLooper();controller.pause().stop().destroy() }
    }

}
