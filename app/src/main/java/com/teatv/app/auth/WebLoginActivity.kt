package com.teatv.app.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import com.teatv.app.MainActivity
import java.io.ByteArrayInputStream
import java.net.URLEncoder

/** Один веб-вход = два токена: кука auth-token (GQL) + implicit-токен своей заявки (Helix). */
class WebLoginActivity : Activity() {

    companion object {
        private const val REDIRECT = "http://localhost:4343/callback"
        private const val SCOPES = "user:read:email user:read:follows user:read:subscriptions channel:read:subscriptions user:read:emotes user:read:chat user:write:chat user:manage:chat_color moderator:read:chatters moderator:manage:chat_messages moderator:manage:banned_users moderator:read:banned_users moderator:read:chat_settings moderator:manage:chat_settings moderator:manage:announcements moderator:read:unban_requests moderator:manage:unban_requests moderator:read:shield_mode moderator:manage:shield_mode moderator:read:followers channel:read:redemptions channel:manage:redemptions channel:read:predictions channel:manage:predictions channel:manage:moderators channel:manage:vips channel:edit:commercial channel:manage:broadcast"

        private val DARK_JS = """
            (function(){
                if (document.getElementById('teatv-dark')) return;
                var s = document.createElement('style');
                s.id = 'teatv-dark';
                s.textContent =
                    'html{filter:invert(1) hue-rotate(180deg)!important;background:#111!important;}' +
                    'img,video,iframe,svg{filter:invert(1) hue-rotate(180deg)!important;}' +
                    'body{padding-top:20vh!important;}';
                (document.head||document.documentElement).appendChild(s);
            })();
        """.trimIndent()
    }

    private var handled = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.parseColor("#FF0E0E10")
        window.navigationBarColor = Color.parseColor("#FF0E0E10")

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)

        val web = WebView(this)
        web.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36" +
                    " (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
        web.setBackgroundColor(Color.parseColor("#FF0E0E10"))
        cm.setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url != null && handleRedirect(url)) {
                    view?.stopLoading()
                    return
                }
                saveCookieToken(cm)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(DARK_JS, null)
                saveCookieToken(cm)
                if (url != null && handleRedirect(url)) {
                    view?.stopLoading()
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val u = request?.url?.toString().orEmpty()
                if (handleRedirect(u)) {
                    view?.stopLoading()
                    return true
                }
                return false
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val u = url.orEmpty()
                if (handleRedirect(u)) {
                    view?.stopLoading()
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val u = request?.url?.toString().orEmpty()
                if (u.startsWith(REDIRECT)) {
                    runOnUiThread {
                        if (handleRedirect(u)) {
                            view?.stopLoading()
                        }
                    }
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                val u = request?.url?.toString().orEmpty()
                if (handleRedirect(u)) {
                    view?.stopLoading()
                    return
                }
                super.onReceivedError(view, request, error)
            }
        }

        val url = "https://id.twitch.tv/oauth2/authorize" +
                "?client_id=${TwitchAuth.OWN_CLIENT_ID}" +
                "&redirect_uri=${URLEncoder.encode(REDIRECT, "UTF-8")}" +
                "&response_type=token" +
                "&scope=${URLEncoder.encode(SCOPES, "UTF-8")}" +
                "&force_verify=true"
        web.loadUrl(url)
        setContentView(web)
    }

    private fun handleRedirect(u: String): Boolean {
        if (handled) return true
        if (!u.startsWith(REDIRECT)) return false

        val frag = Uri.parse(u).fragment.orEmpty()
        val params = frag.split('&').mapNotNull { p ->
            val i = p.indexOf('=')
            if (i < 0) null else p.substring(0, i) to Uri.decode(p.substring(i + 1))
        }.toMap()

        val token = params["access_token"]
        Log.d("TeaTV-Web", "redirect caught, helix tokenLen=${token?.length ?: 0}")

        if (!token.isNullOrBlank()) {
            handled = true
            TokenStore(applicationContext).helixToken = token
            saveCookieToken(CookieManager.getInstance())
            backToMain()
            return true
        }
        return false
    }

    private fun saveCookieToken(cm: CookieManager) {
        val rawCookies = cm.getCookie("https://twitch.tv").orEmpty()
        val token = Regex("(?:^|;\\s*)auth-token=([^;]+)")
            .find(rawCookies)?.groupValues?.get(1)

        if (!token.isNullOrBlank() && token.length >= 20) {
            TokenStore(applicationContext).accessToken = token
            Log.i("TeaTV-Auth", "✅ УСПЕХ: web authToken сохранен, длина=${token.length}")
        }
    }

    private fun backToMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                )
        )
        finish()
    }
}
