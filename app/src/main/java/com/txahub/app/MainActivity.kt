package com.txahub.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val WEB_URL = "https://txahub.click"
    private val USER_AGENT_SUFFIX = " TXAAPP_>>>>>>>"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupWebView()

        // Xử lý deep link
        handleIntent(intent)

        // Load trang web
        webView.loadUrl(WEB_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = webView.settings

        // Bật JavaScript
        webSettings.javaScriptEnabled = true

        // Bật DOM Storage
        webSettings.domStorageEnabled = true

        // Bật database storage
        webSettings.databaseEnabled = true

        // Cho phép truy cập file
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true

        // Cải thiện hiệu suất
        webSettings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webSettings.loadsImagesAutomatically = true
        webSettings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Bật zoom
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false

        // User Agent - QUAN TRỌNG: Thêm chuỗi TXAAPP_>>>>>>> để web nhận diện
        val originalUserAgent = webSettings.userAgentString
        val customUserAgent = "$originalUserAgent$USER_AGENT_SUFFIX"
        webSettings.userAgentString = customUserAgent

        // Dark mode support (nếu có)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(
                webSettings,
                WebSettingsCompat.FORCE_DARK_AUTO
            )
        }

        // WebViewClient để xử lý navigation
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false

                // Xử lý deep link txahub://
                if (url.startsWith("txahub://")) {
                    handleDeepLink(url)
                    return true
                }

                // Cho phép WebView xử lý các URL khác
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Inject JavaScript nếu cần (ví dụ: unlock features)
                injectUnlockScript()
            }
        }

        // WebChromeClient để xử lý progress và các sự kiện khác
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                // Có thể hiển thị progress bar nếu cần
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                // Có thể cập nhật title của activity
            }
        }
    }

    /**
     * Inject JavaScript để unlock các tính năng app-only
     * Script này sẽ được chạy sau khi trang web load xong
     */
    private fun injectUnlockScript() {
        val unlockScript = """
            (function() {
                // Gửi event để web biết đang chạy trong app
                window.dispatchEvent(new CustomEvent('txaapp:ready', {
                    detail: { isApp: true }
                }));
                
                // Unlock các tính năng app-only
                if (window.txaAppUnlock) {
                    window.txaAppUnlock();
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(unlockScript, null)
    }

    /**
     * Xử lý deep link txahub://
     */
    private fun handleDeepLink(url: String) {
        try {
            val uri = Uri.parse(url)
            val path = uri.host ?: uri.path

            when (path) {
                "home", "main" -> {
                    webView.loadUrl(WEB_URL)
                }
                "open" -> {
                    val targetUrl = uri.getQueryParameter("url")
                    if (targetUrl != null) {
                        webView.loadUrl(targetUrl)
                    } else {
                        webView.loadUrl(WEB_URL)
                    }
                }
                else -> {
                    // Load URL mặc định
                    webView.loadUrl(WEB_URL)
                }
            }

            Toast.makeText(this, "Đã mở từ deep link: $url", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            webView.loadUrl(WEB_URL)
        }
    }

    /**
     * Xử lý intent khi app được mở từ deep link hoặc external link
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null) {
                when {
                    data.scheme == "txahub" -> {
                        handleDeepLink(data.toString())
                    }
                    data.host == "txahub.click" -> {
                        webView.loadUrl(data.toString())
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Xử lý nút back
     */
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

