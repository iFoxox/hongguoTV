// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import com.hongguotv.core.WebLogin

/** 官方页面负责手机号、验证码和人工验证；仅调整显示并打开官方登录弹窗，不读取或提交表单。 */
internal class OfficialWebLogin(
    activity: Activity,
    private val complete: (String) -> Unit,
    private val dismissed: () -> Unit,
) {
    private val popup = AppDialogs.dialog(activity)
    private val cookies = CookieManager.getInstance()
    internal val web = WebView(activity)
    private val message = TextView(activity)
    private val done = Button(activity)
    private val panOverlay = WebPagePanView(activity) { dx, dy -> web.scrollBy(dx, dy) }
    private val move = Button(activity)
    private var moving = false
    private var phoneMode = true
    private val modeButton = Button(activity)
    private val tools = LinearLayout(activity)
    private var pendingGeometry: Runnable? = null
    private var geometryEpoch = 0
    private var targetSignature = ""
    private var touching = false
    private var currentScale = activity.resources.displayMetrics.density
    private var disposed = false
    private var loadEpoch = 0
    private var preparedEpoch = -1
    private var challengeWaitingSince = 0L
    private var loginOpened = false
    private var openAttempts = 0
    private var pendingOpen: Runnable? = null
    val isShowing get() = popup.isShowing

    init {
        popup.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val density = activity.resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(SurfaceStyle.background)
        }
        val header = LinearLayout(activity).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(TextView(activity).apply {
            text = "红果官方网页登录"
            textSize = 19f
            setTextColor(TvStyle.text)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        modeButton.apply {
            text = "原网页"
            textSize = 14f
            setTextColor(TvStyle.text)
            isFocusableInTouchMode = !TvStyle.touch
            minHeight = dp(48)
            SurfaceStyle.button(this)
            setOnClickListener { setPhoneMode(!phoneMode) }
        }
        header.addView(modeButton, LinearLayout.LayoutParams(dp(88), -2))
        panel.addView(header)
        message.apply {
            text = "正在打开手机登录窗口。请在官网完成验证码与验证，登录后点「完成登录」。"
            textSize = 14f
            setTextColor(TvStyle.mobileMuted)
            setPadding(0, dp(8), 0, dp(8))
        }
        panel.addView(message)
        configureWeb()
        fun tool(label: String, action: () -> Unit): Button = Button(activity).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            minWidth = 0
            minHeight = dp(48)
            setPadding(dp(2), 0, dp(2), 0)
            setTextColor(TvStyle.text)
            isFocusableInTouchMode = !TvStyle.touch
            SurfaceStyle.button(this)
            setOnClickListener { action() }
            tools.addView(this, LinearLayout.LayoutParams(0, -1, 1f))
        }
        tool("缩小") { web.zoomOut() }
        tool("放大") { web.zoomIn() }
        tool("恢复全页") { fitPage() }
        move.apply {
            text = "移动页面"
            textSize = 13f
            minWidth = 0
            minHeight = dp(48)
            setPadding(dp(2), 0, dp(2), 0)
            setTextColor(TvStyle.text)
            isFocusableInTouchMode = !TvStyle.touch
            SurfaceStyle.button(this)
            setOnClickListener { setMoving(!moving) }
        }
        tools.addView(move, LinearLayout.LayoutParams(0, -1, 1f))
        tools.visibility = android.view.View.GONE
        panel.addView(tools)
        val pageArea = FrameLayout(activity)
        pageArea.addView(web, FrameLayout.LayoutParams(-1, -1))
        panOverlay.visibility = android.view.View.GONE
        pageArea.addView(panOverlay, FrameLayout.LayoutParams(-1, -1))
        panel.addView(pageArea, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(activity)
        actions.addView(Button(activity).apply {
            text = "关闭"
            textSize = 13f; minWidth = 0; minHeight = dp(48)
            setPadding(dp(6),dp(7),dp(6),dp(7))
            SurfaceStyle.button(this)
            isFocusableInTouchMode = !TvStyle.touch
            setTextColor(TvStyle.text)
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(0, -1, 1f))
        actions.addView(Button(activity).apply {
            text = "打开登录"
            textSize = 13f; minWidth = 0; minHeight = dp(48)
            setPadding(dp(6),dp(7),dp(6),dp(7))
            SurfaceStyle.button(this)
            isFocusableInTouchMode = !TvStyle.touch
            setTextColor(TvStyle.text)
            setOnClickListener {
                if (isOfficialPage(web.url.orEmpty())) {
                    loginOpened = false
                    startOpenLogin(loadEpoch)
                } else showError("请先返回红果官网，再点「打开登录」。")
            }
        }, LinearLayout.LayoutParams(0, -1, 1f))
        done.text = "完成登录"
        done.textSize = 13f; done.minWidth = 0; done.minHeight = dp(48)
        done.setPadding(dp(6),dp(7),dp(6),dp(7))
        SurfaceStyle.button(done)
        done.isFocusableInTouchMode = !TvStyle.touch
        done.setTextColor(SurfaceStyle.accentText)
        done.setOnClickListener {
            if (!done.isEnabled) return@setOnClickListener
            val header = cookies.getCookie(WebLogin.BASE).orEmpty()
            if (!header.split(';').any { it.trim().substringBefore('=') in setOf("sessionid", "sessionid_ss") && it.substringAfter('=', "").isNotBlank() }) {
                showError("尚未检测到官网登录，请先在网页中完成登录，再点「完成登录」。")
            } else complete(header)
        }
        actions.addView(done, LinearLayout.LayoutParams(0, -1, 1f))
        panel.addView(actions)
        popup.setContentView(panel)
        popup.setOnKeyListener { _, key, event ->
            if (key == android.view.KeyEvent.KEYCODE_BACK && web.canGoBack()) {
                if (event.action == android.view.KeyEvent.ACTION_UP) web.goBack()
                true
            } else false
        }
        popup.setOnDismissListener {
            if (!disposed) {
                disposed = true
                cancelOpenLogin()
                cancelGeometryWatch()
                web.stopLoading()
                web.webChromeClient = null
                (web.parent as? ViewGroup)?.removeView(web)
                web.removeAllViews()
                web.destroy()
                // 当前应用唯一的 WebView 仅用于本次登录。长期凭据由 AccountStore 加密保存。
                cookies.removeAllCookies(null)
                cookies.flush()
                WebStorage.getInstance().deleteAllData()
                dismissed()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility") // 官方登录和滑块由官网 JavaScript 实现，无原生桥接。
    private fun configureWeb() {
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(web, true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            // Mac UA + 真实触摸屏会被官网当作 iPad；使用 Windows 桌面 UA。
            userAgentString = WebLogin.BROWSER_UA
            cacheMode = WebSettings.LOAD_NO_CACHE
            saveFormData = false
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String) =
                if (allowsVerificationFrame(url)) false else !allowed(url)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                if (request.isForMainFrame) !allowed(request.url.toString())
                else request.url.scheme != "https"
            private fun allowed(url: String): Boolean {
                val allowed = allowsNavigation(url)
                if (!allowed) showError("此链接不属于支持的官网登录页面，已停止跳转。")
                return allowed
            }
            override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
                if (newScale.isFinite() && newScale > 0) currentScale = newScale
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                loadEpoch++
                touching = false
                cancelOpenLogin()
                cancelGeometryWatch()
                targetSignature = ""
                challengeWaitingSince = 0L
                setMoving(false)
                if (url != null && !allowsNavigation(url)) view.stopLoading()
            }
            override fun onPageCommitVisible(view: WebView, url: String?) {
                prepareOfficialPage(view,url)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                prepareOfficialPage(view,url)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError("官网加载失败，请关闭后重试，并检查 Android System WebView 是否已更新。")
            }
            // SSL 错误沿用系统默认取消，禁止忽略证书错误。
        }
        web.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> touching = true
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> touching = false
            }
            false
        }
        web.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right-left != oldRight-oldLeft || bottom-top != oldBottom-oldTop) {
                targetSignature = ""
                if (phoneMode && !disposed && isShowing && isOfficialPage(web.url.orEmpty())) {
                    web.evaluateJavascript(phoneLayoutScript(), null)
                }
            }
        }
    }

    fun show() {
        popup.show()
        popup.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(SurfaceStyle.background))
            setLayout(-1, -1)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        // 先清理上次临时网页数据，再加载；不复用其他账户的网页会话。
        cookies.removeAllCookies {
            if (!disposed) web.loadUrl(WebLogin.BASE + "/")
        }
    }

    private fun prepareOfficialPage(view: WebView, url: String?) {
        if (disposed || url == null || view.url?.substringBefore('#') != url.substringBefore('#') || !isOfficialPage(url) || preparedEpoch == loadEpoch) return
        val token = loadEpoch
        preparedEpoch = token
        val script = if (phoneMode) phoneLayoutScript() else desktopViewportScript(view.width / view.resources.displayMetrics.density)
        view.evaluateJavascript(script) {
            if (!disposed && token == loadEpoch) {
                if (phoneMode) startGeometryWatch(token)
                if (!loginOpened) startOpenLogin(token)
            }
        }
    }

    private fun phoneLayoutScript(): String {
        val density = web.resources.displayMetrics.density
        val width = (web.width / density).toInt().takeIf { it > 0 } ?: 360
        val height = (web.height / density).toInt().takeIf { it > 0 } ?: 480
        return OfficialLoginLayout.installScript(width, height)
    }

    internal fun setPhoneMode(enabled: Boolean) {
        if (disposed) return
        phoneMode = enabled
        touching = false
        modeButton.text = if (enabled) "原网页" else "手机登录"
        tools.visibility = if (enabled) android.view.View.GONE else android.view.View.VISIBLE
        cancelGeometryWatch()
        targetSignature = ""
        setMoving(false)
        if (!isOfficialPage(web.url.orEmpty())) return
        val token = loadEpoch
        val script = if (enabled) phoneLayoutScript() else OfficialLoginLayout.removeScript() + "\n" + desktopViewportScript(web.width / web.resources.displayMetrics.density)
        web.evaluateJavascript(script) {
            if (!disposed && token == loadEpoch && phoneMode == enabled) {
                if (enabled) startGeometryWatch(token) else fitPage()
            }
        }
    }

    private fun cancelGeometryWatch() {
        geometryEpoch++
        pendingGeometry?.let { web.removeCallbacks(it) }
        pendingGeometry = null
    }

    private fun startGeometryWatch(token: Int) {
        cancelGeometryWatch()
        val watcher = geometryEpoch
        fun measure() {
            if (disposed || !phoneMode || token != loadEpoch || watcher != geometryEpoch || !isShowing || !isOfficialPage(web.url.orEmpty())) return
            web.evaluateJavascript(OfficialLoginLayout.geometryScript()) { result ->
                if (disposed || !phoneMode || token != loadEpoch || watcher != geometryEpoch || !isShowing) return@evaluateJavascript
                if (!touching) runCatching { org.json.JSONObject(result.orEmpty()) }.getOrNull()?.let { geometry ->
                    val kind = geometry.optString("kind")
                    if (kind == "challenge-loading") {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (challengeWaitingSince == 0L) challengeWaitingSince = now
                        if (done.isEnabled && !moving) message.text =
                            if (now-challengeWaitingSince < 12000) "正在加载官方验证窗口…"
                            else "官方验证窗口仍未显示，可切换「原网页」检查，或关闭后重新打开。"
                    } else if (challengeWaitingSince != 0L) {
                        challengeWaitingSince = 0L
                        if (done.isEnabled && !moving) message.text = if (kind=="challenge") "请在官方验证窗口中手动完成验证。" else "请在官网完成登录后点「完成登录」。"
                    }
                    val width = geometry.optDouble("width")
                    val height = geometry.optDouble("height")
                    val x = geometry.optDouble("x")
                    val y = geometry.optDouble("y")
                    if (kind in listOf("login", "challenge") && listOf(width, height, x, y).all { it.isFinite() } && width > 0 && height > 0) {
                        val signature = "$kind:${width.toInt()}:${height.toInt()}:${web.width}:${web.height}"
                        if (targetSignature != signature && web.width > 0 && web.height > 0) {
                            targetSignature = signature
                            val density = web.resources.displayMetrics.density
                            val target = minOf(web.width / (width + 16), web.height / (height + 16), density.toDouble()).toFloat()
                            if (kind == "challenge") {
                                (web.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                                    ?.hideSoftInputFromWindow(web.windowToken, 0)
                            }
                            if (target > 0 && kotlin.math.abs(target / currentScale - 1f) > .02f) web.zoomBy((target / currentScale).coerceIn(.01f, 100f))
                            web.post {
                                if (!disposed && phoneMode && token == loadEpoch && watcher == geometryEpoch && !touching) web.scrollTo(maxOf(0, ((x-8)*target).toInt()), maxOf(0, ((y-8)*target).toInt()))
                            }
                        }
                    }
                }
                val next = Runnable { pendingGeometry = null; measure() }
                pendingGeometry = next
                web.postDelayed(next, 750)
            }
        }
        measure()
    }

    private fun cancelOpenLogin() {
        pendingOpen?.let { web.removeCallbacks(it) }
        pendingOpen = null
    }

    private fun startOpenLogin(token: Int) {
        cancelOpenLogin()
        openAttempts = 0
        tryOpenLogin(token)
    }

    private fun tryOpenLogin(token: Int) {
        if (disposed || token != loadEpoch || loginOpened || !isShowing || !isOfficialPage(web.url.orEmpty())) return
        openAttempts++
        web.evaluateJavascript(openLoginScript()) { result ->
            if (disposed || token != loadEpoch || !isShowing || loginOpened) return@evaluateJavascript
            when (result) {
                "\"opened\"" -> {
                    loginOpened = true
                    cancelOpenLogin()
                }
                "\"waiting\"", "\"requested\"" -> if (openAttempts < 60) {
                    val retry = Runnable { pendingOpen = null; tryOpenLogin(token) }
                    pendingOpen = retry
                    web.postDelayed(retry, 500)
                } else showError("未能自动打开登录窗口，可点下方「打开登录」重试，或点击官网右上角「登录」。")
            }
        }
    }

    private fun setMoving(enabled: Boolean) {
        moving = enabled
        panOverlay.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        move.text = if (enabled) "结束移动" else "移动页面"
        move.isSelected = enabled
        move.setTextColor(if (enabled) SurfaceStyle.accentText else TvStyle.text)
        message.text = if (enabled) "移动模式：拖动网页把验证窗口移入屏幕。移好后点「结束移动」，再手动操作滑块。"
            else if (phoneMode) "请在官网完成验证码与验证，登录后点「完成登录」。显示异常可切换「原网页」。"
            else "原网页模式：可缩放、恢复全页或移动页面。移好后结束移动，再操作滑块。"
    }

    private fun fitPage() {
        if (disposed) return
        setMoving(false)
        // WebView 回调的 scale 含屏幕 density；用物理宽度 / CSS 宽度计算目标值。
        // 只调整显示，不重载页面，不触碰验证码、表单或登录状态。
        val targetScale = web.width.toFloat() / DESKTOP_WIDTH
        if (targetScale > 0) web.zoomBy((targetScale / currentScale).coerceIn(.01f, 100f))
        web.post { if (!disposed) web.scrollTo(0, 0) }
    }

    fun checking() {
        done.isEnabled = false
        message.text = "正在验证官网登录状态…"
    }

    fun showError(text: String) {
        if (disposed) return
        done.isEnabled = true
        message.text = text
    }

    fun dismiss() {
        cancelOpenLogin()
        cancelGeometryWatch()
        popup.dismiss()
    }

    companion object {
        private const val DESKTOP_WIDTH = 1280

        internal fun isOfficialPage(value: String): Boolean {
            val uri = Uri.parse(value)
            return allowsNavigation(value) && uri.host in setOf("hongguoduanju.com", "www.hongguoduanju.com")
        }

        internal fun desktopViewportScript(widthDp: Float): String {
            val scale = ((widthDp.takeIf { it.isFinite() && it > 0 } ?: 360f) / DESKTOP_WIDTH).coerceIn(.1f, 1f)
            val content = "width=$DESKTOP_WIDTH, initial-scale=" +
                java.lang.String.format(java.util.Locale.US, "%.4f", scale) +
                ", minimum-scale=0.1, maximum-scale=5, user-scalable=yes"
            return """
                (function() {
                    if (location.protocol !== 'https:' ||
                        (location.hostname !== 'hongguoduanju.com' && location.hostname !== 'www.hongguoduanju.com')) return;
                    var viewport = document.querySelector('meta[name="viewport"]');
                    if (!viewport) {
                        viewport = document.createElement('meta');
                        viewport.name = 'viewport';
                        document.head.appendChild(viewport);
                    }
                    viewport.setAttribute('content', '$content');
                    window.dispatchEvent(new Event('resize'));
                })();
            """.trimIndent()
        }

        internal fun openLoginScript(): String = """
            (function() {
                if (location.protocol !== 'https:' ||
                    (location.hostname !== 'hongguoduanju.com' && location.hostname !== 'www.hongguoduanju.com')) return 'blocked';
                function visible(node) {
                    if (!node) return false;
                    var rect = node.getBoundingClientRect();
                    var style = window.getComputedStyle(node);
                    return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
                }
                var dialogs = document.querySelectorAll('[class*="pc-login-dialog-"]');
                for (var i = 0; i < dialogs.length; i++) {
                    if (visible(dialogs[i])) return 'opened';
                }
                // 其他模态窗口出现时等待，不将其误认为登录窗口，也不点击底下的页面。
                var others = document.querySelectorAll('[role="dialog"]');
                for (var n=0; n<others.length; n++) if (visible(others[n])) return 'waiting';
                var now=Date.now();
                if (window.__guoyingLoginRequestedAt && now-window.__guoyingLoginRequestedAt<1200) return 'waiting';
                window.__guoyingLoginRequestedAt=now;
                // 官网公开的打开登录事件；React 水合完成前可能没有监听，后续必须确认实际弹窗。
                window.dispatchEvent(new Event('hongguo:pc-open-login'));
                var entries = document.querySelectorAll('button[class*="pc-entry-"]');
                for (var j = 0; j < entries.length; j++) {
                    var entry = entries[j];
                    if (visible(entry) && !entry.disabled && !entry.closest('form') && entry.textContent.trim() === '登录') {
                        entry.click();
                        return 'requested';
                    }
                }
                return 'waiting';
            })();
        """.trimIndent()

        // API 23 的旧回调不提供主框架标记；仅放行官方 SDK 使用的这个 HTTPS 验证域。
        internal fun allowsVerificationFrame(value: String): Boolean {
            val uri = Uri.parse(value)
            return uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1,443) && uri.host == "rmc.bytedance.com"
        }

        internal fun allowsNavigation(value: String): Boolean {
            val uri = Uri.parse(value)
            if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) return false
            return uri.host in setOf("hongguoduanju.com", "www.hongguoduanju.com", "web.qznovel.com")
        }
    }
}
