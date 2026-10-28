// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

/** 只重排官方短信表单，并读取弹窗几何；不读取输入值、不操作验证码或滑块。 */
internal object OfficialLoginLayout {
    private const val STYLE_ID = "guoying-phone-login-layout"

    fun installScript(widthDp: Int, heightDp: Int): String {
        val width = widthDp.coerceIn(240, 1600)
        val height = heightDp.coerceIn(120, 2400)
        return """
            (function() {
                if (location.protocol !== 'https:' ||
                    (location.hostname !== 'hongguoduanju.com' && location.hostname !== 'www.hongguoduanju.com')) return 'blocked';
                var style = document.getElementById('$STYLE_ID');
                if (!style) { style = document.createElement('style'); style.id = '$STYLE_ID'; document.head.appendChild(style); }
                style.textContent = `
                    [class*="pc-backdrop-"] { position:fixed!important; left:0!important; top:0!important; right:auto!important; bottom:auto!important; width:${width}px!important; height:${height}px!important; padding:0!important; box-sizing:border-box!important; align-items:flex-start!important; justify-content:center!important; overflow-y:auto!important; background:#222424!important; }
                    [class*="pc-login-dialog-"] { position:relative!important; width:100%!important; max-width:560px!important; height:auto!important; min-height:0!important; max-height:${height}px!important; margin:0!important; padding:20px 16px!important; border-radius:0!important; box-sizing:border-box!important; overflow-y:auto!important; }
                    [class*="pc-login-dialog-"]:after, [class*="pc-dialog-decoration-"] { display:none!important; }
                    [class*="pc-login-dialog-"] [class*="dialog-title-"] { position:static!important; font-size:20px!important; line-height:28px!important; margin:0 40px 12px 0!important; }
                    [class*="pc-login-dialog-"] [class*="pc-agreement-note-"] { position:relative!important; left:auto!important; top:auto!important; right:auto!important; bottom:auto!important; white-space:normal!important; flex-wrap:wrap!important; margin:12px 0 20px!important; width:auto!important; min-height:44px!important; padding:8px 0 8px 44px!important; box-sizing:border-box!important; line-height:22px!important; color:#c3c5c5!important; }
                    [class*="pc-login-dialog-"] [class*="pc-agreement-note__input-"] { position:absolute!important; left:0!important; top:calc(50% - 22px)!important; width:44px!important; height:44px!important; margin:0!important; padding:0!important; opacity:0!important; z-index:2!important; pointer-events:auto!important; cursor:pointer!important; }
                    [class*="pc-login-dialog-"] [class*="pc-agreement-note__check-"] { position:absolute!important; left:14px!important; top:calc(50% - 8px)!important; width:16px!important; height:16px!important; margin:0!important; pointer-events:none!important; }
                    [class*="pc-login-dialog-"] [class*="pc-agreement-note-"] a { color:#ffad70!important; }
                    [class*="pc-login-dialog-"] .web-login-container, [class*="pc-login-dialog-"] .web-login, [class*="pc-login-dialog-"] .web-login-union__login { width:100%!important; height:auto!important; min-width:0!important; display:block!important; padding:0!important; }
                    [class*="pc-login-dialog-"] .web-login-union__login__form { width:100%!important; min-width:0!important; padding:0!important; flex:none!important; box-sizing:border-box!important; }
                    [class*="pc-login-dialog-"] .web-login-union__login__form__content { width:100%!important; min-width:0!important; }
                    [class*="pc-login-dialog-"] .web-login-union__login__form__title { font-size:16px!important; line-height:24px!important; margin:0 0 16px!important; }
                    [class*="pc-login-dialog-"] .web-login-union__login__scan-code, [class*="pc-login-dialog-"] .web-login-union__footer { display:none!important; }
                    [class*="pc-login-dialog-"] .web-login-normal-input, [class*="pc-login-dialog-"] .web-login-button-input { width:100%!important; min-width:0!important; box-sizing:border-box!important; height:48px!important; }
                    [class*="pc-login-dialog-"] input:not([type="checkbox"]) { min-width:0!important; font-size:16px!important; height:24px!important; line-height:24px!important; }
                    [class*="pc-login-dialog-"] .web-login-button-input button { min-height:44px!important; }
                    [class*="pc-login-dialog-"] [class*="pc-agreement-note-"] label { min-height:44px!important; }
                    [class*="pc-login-dialog-"] .web-login-button-input__input-wrapper { flex:1!important; min-width:0!important; }
                    [class*="pc-login-dialog-"] .web-login-mobile-code__mobile-input-wrapper, [class*="pc-login-dialog-"] .web-login-mobile-code__code-input-wrapper { margin:0 0 16px!important; }
                    [class*="pc-login-dialog-"] .web-login-mobile-code__button-wrapper { margin:24px 0 0!important; }
                    [class*="pc-login-dialog-"] button[aria-label="关闭登录弹窗"] { min-width:44px!important; min-height:44px!important; right:4px!important; top:4px!important; }
                `;
                var viewport = document.querySelector('meta[name="viewport"]');
                if (viewport) viewport.setAttribute('content', 'width=1280, initial-scale=1, minimum-scale=0.1, maximum-scale=5, user-scalable=yes');
                window.dispatchEvent(new Event('resize'));
                return 'installed';
            })();
        """.trimIndent()
    }

    fun removeScript(): String = """
        (function() {
            var style = document.getElementById('$STYLE_ID');
            if (style && style.parentNode) style.parentNode.removeChild(style);
        })();
    """.trimIndent()

    fun geometryScript(): String = """
        (function() {
            if (location.protocol !== 'https:' ||
                (location.hostname !== 'hongguoduanju.com' && location.hostname !== 'www.hongguoduanju.com')) return null;
            function rect(node) {
                var r = node.getBoundingClientRect(), s = window.getComputedStyle(node);
                if (!r.width || !r.height || s.display === 'none' || s.visibility === 'hidden') return null;
                return {x:r.left+window.scrollX, y:r.top+window.scrollY, width:r.width, height:r.height};
            }
            // 仅测量验证码外层窗口。跨域 iframe 内部、图片、滑块及答案均不访问。
            var roots = document.querySelectorAll('[id*="captcha"], [class*="captcha"], [id*="verify"], [class*="verify"]');
            var nodes=[], pending=false;
            for (var k=0; k<Math.min(roots.length,150); k++) {
                var root=roots[k];
                if (nodes.indexOf(root)<0) nodes.push(root);
                // 官方 SDK 的 iframe 没有 id/class，必须从验证码容器中寻找。
                // 这里只读取 iframe 元素自身的边界，绝不访问 contentDocument/contentWindow。
                var frames=root.querySelectorAll('iframe');
                for (var f=0; f<Math.min(frames.length,10); f++) {
                    if (nodes.indexOf(frames[f])<0) nodes.push(frames[f]);
                    if (rect(root) && !rect(frames[f])) pending=true;
                }
            }
            var challenge = null;
            for (var i=0; i<Math.min(nodes.length,300); i++) {
                var node=nodes[i];
                if (/^(IMG|CANVAS|INPUT|BUTTON|SVG|PATH)$/.test(node.tagName)) continue;
                var r=rect(node), frame=node.tagName==='IFRAME';
                if (r && ((frame && r.width>=160 && r.width<=4096 && r.height>=100 && r.height<=4096) ||
                    (r.width >= 240 && r.width <= 800 && r.height >= 180 && r.height <= 1000)) &&
                    (!challenge || r.width*r.height > challenge.width*challenge.height)) challenge=r;
            }
            if (challenge) { challenge.kind='challenge'; return challenge; }
            if (pending) return {kind:'challenge-loading'};
            var login = document.querySelector('[class*="pc-login-dialog-"]');
            if (!login) return null;
            var result=rect(login);
            if (result) result.kind='login';
            return result;
        })();
    """.trimIndent()
}
