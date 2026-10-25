package com.teatv.app.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class TwitchIntegrityWebView(private val context: Context) {

    private var webView: WebView? = null
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0
    private val tokenLifetimeMs = 10 * 60 * 1000L // 10 минут

    companion object {
        private const val TAG = "TeaTV-Integrity"
        private const val JS_INTERFACE = "IntegrityInterface"
    }

    suspend fun getToken(): String? {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            Log.d(TAG, "Using cached integrity token")
            return cachedToken
        }
        Log.d(TAG, "Generating new integrity token via WebView")
        return generateToken()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun generateToken(): String? = suspendCancellableCoroutine { cont ->
        try {
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            view?.evaluateJavascript(getDiagnosticScript(), null)
                        }, 2000)
                    }
                }

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onDiagnostic(jsonData: String?) {
                            Log.d(TAG, "DIAGNOSTIC DATA FROM TWITCH: $jsonData")
                            val token = extractTokenFromDiagnostic(jsonData)
                            handleResult(token, cont)
                        }

                        @JavascriptInterface
                        fun onError(error: String?) {
                            Log.e(TAG, "Integrity generation error: $error")
                            handleResult(null, cont)
                        }
                    },
                    JS_INTERFACE
                )
            }
            webView = wv
            wv.loadUrl("https://www.twitch.tv/")

            // Таймаут 15 секунд - запускаем в GlobalScope с правильным контекстом
            GlobalScope.launch(Dispatchers.IO) {
                delay(15_000)
                if (!cont.isCompleted) {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "Integrity generation timeout")
                        handleResult(null, cont)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create WebView", e)
            if (!cont.isCompleted) handleResult(null, cont)
        }
    }

    private fun handleResult(token: String?, cont: kotlinx.coroutines.CancellableContinuation<String?>) {
        Handler(Looper.getMainLooper()).post {
            if (!token.isNullOrBlank()) {
                cachedToken = token
                tokenExpiry = System.currentTimeMillis() + tokenLifetimeMs
                Log.d(TAG, "SUCCESS: Found integrity token, length=${token.length}")
            } else {
                Log.e(TAG, "FAILED: Could not extract integrity token")
            }

            if (!cont.isCompleted) {
                cont.resume(token)
            }
            destroyWebView()
        }
    }

    private fun getDiagnosticScript(): String {
        return """
            (function() {
                try {
                    var result = {
                        cookies: document.cookie,
                        localStorageKeys: Object.keys(localStorage),
                        suspiciousValues: {}
                    };
                    
                    for (var i = 0; i < localStorage.length; i++) {
                        var key = localStorage.key(i);
                        if (key && (key.toLowerCase().includes('integ') || key.toLowerCase().includes('client') || key.toLowerCase().includes('paseto'))) {
                            result.suspiciousValues[key] = localStorage.getItem(key).substring(0, 50) + '...';
                        }
                    }
                    
                    IntegrityInterface.onDiagnostic(JSON.stringify(result));
                } catch (e) {
                    IntegrityInterface.onError(e.toString());
                }
            })();
        """
    }

    private fun extractTokenFromDiagnostic(jsonData: String?): String? {
        if (jsonData.isNullOrBlank()) return null
        try {
            val json = org.json.JSONObject(jsonData)

            val cookies = json.optString("cookies", "")
            val cookieMatch = Regex("client-integrity=([^;]+)").find(cookies)
            if (cookieMatch != null) return cookieMatch.groupValues[1]

            val values = json.optJSONObject("suspiciousValues")
            if (values != null) {
                val keys = values.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.contains("integ", ignoreCase = true) || key.contains("client", ignoreCase = true)) {
                        return values.optString(key).replace("...", "")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse diagnostic JSON", e)
        }
        return null
    }

    private fun destroyWebView() {
        webView?.let { wv ->
            try {
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.removeAllViews()
                wv.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying WebView", e)
            }
        }
        webView = null
    }

    fun clear() {
        cachedToken = null
        tokenExpiry = 0
        Handler(Looper.getMainLooper()).post {
            destroyWebView()
        }
    }
}