package com.txahub.vn

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var drawerLayout: DrawerLayout
    private val WEB_URL = "https://txahub.click"
    private lateinit var logWriter: LogWriter
    private var networkMonitor: NetworkMonitor? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logWriter = LogWriter(this)
        logWriter.writeAppLog("MainActivity onCreate", "MainActivity", android.util.Log.INFO)
        
        drawerLayout = findViewById(R.id.drawerLayout)
        webView = findViewById(R.id.webView)
        
        try {
            setupSidebar()
            setupWebView()

            // Xử lý deep link
            handleIntent(intent)

            // Setup back button handler
            setupBackButtonHandler()

            // Load trang web (nếu không có deep link)
            if (intent?.action != Intent.ACTION_VIEW || intent.data?.scheme != "txahub") {
                webView.loadUrl(WEB_URL)
            }
            
            // Start network monitoring
            networkMonitor = NetworkMonitor(this)
            networkMonitor?.startMonitoring()
            
            // Start background update check service nếu có quyền tối ưu hóa pin
            UpdateCheckService.startIfAllowed(this)
        } catch (e: Exception) {
            logWriter.writeAppLog("Error in onCreate: ${e.message}\n${e.stackTraceToString()}", "MainActivity", android.util.Log.ERROR)
            throw e
        }
    }
    
    private fun setupSidebar() {
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        
        // Menu button - đổi icon khi mở/đóng drawer
        btnMenu.setOnClickListener {
            if (drawerLayout.isDrawerOpen(Gravity.START)) {
                drawerLayout.closeDrawer(Gravity.START)
            } else {
                drawerLayout.openDrawer(Gravity.START)
            }
        }
        
        // Lắng nghe sự kiện mở/đóng drawer để đổi icon
        drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: android.view.View, slideOffset: Float) {}
            
            override fun onDrawerOpened(drawerView: android.view.View) {
                // Đổi icon thành close (X)
                btnMenu.setImageResource(R.drawable.ic_menu_close)
            }
            
            override fun onDrawerClosed(drawerView: android.view.View) {
                // Đổi icon thành hamburger menu
                btnMenu.setImageResource(R.drawable.ic_menu_hamburger)
            }
            
            override fun onDrawerStateChanged(newState: Int) {}
        })
        
        // Version text in header
        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "1.1_txa"
            tvAppVersion.text = "Version $versionName"
        } catch (e: Exception) {
            tvAppVersion.text = "Version 1.1_txa"
        }
        
        // Menu: Vào app chính
        val menuAppMain = findViewById<LinearLayout>(R.id.menuAppMain)
        menuAppMain.setOnClickListener {
            // Reload trang web chính
            webView.loadUrl(WEB_URL)
            drawerLayout.closeDrawer(Gravity.START)
            Toast.makeText(this, "Đã chuyển đến app chính", Toast.LENGTH_SHORT).show()
        }
        
        // Menu: Changelog
        val menuChangelog = findViewById<LinearLayout>(R.id.menuChangelog)
        menuChangelog.setOnClickListener {
            openChangelog()
            drawerLayout.closeDrawer(Gravity.START)
        }
        
        // Menu: Settings
        val menuVersionInfo = findViewById<LinearLayout>(R.id.menuVersionInfo)
        menuVersionInfo.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            drawerLayout.closeDrawer(Gravity.START)
        }
    }
    
    /**
     * Mở ChangelogActivity với changelog của phiên bản hiện tại
     */
    private fun openChangelog() {
        val updateChecker = UpdateChecker(this)
        val currentVersion = updateChecker.getCurrentVersion()
        
        // Lấy changelog từ API
        updateChecker.getChangelogForVersion(currentVersion) { changelog ->
            runOnUiThread {
                val intent = Intent(this, ChangelogActivity::class.java).apply {
                    putExtra(ChangelogActivity.EXTRA_VERSION_NAME, currentVersion)
                    putExtra(ChangelogActivity.EXTRA_CHANGELOG, changelog ?: "")
                }
                startActivity(intent)
            }
        }
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

        // User Agent - QUAN TRỌNG: Tạo chuỗi mới hoàn toàn TXAAPP_(tên thiết bị + hệ điều hành) để web nhận diện
        // Bao gồm tên app, phiên bản app và phiên bản trình duyệt từ user agent gốc
        val deviceName = getDeviceName()
        val osVersion = "Android_${Build.VERSION.RELEASE}"
        
        // Lấy user agent gốc từ WebView
        val defaultUserAgent = webSettings.userAgentString ?: ""
        
        // Cắt chuỗi user agent gốc để lấy phiên bản trình duyệt (ví dụ: Mozilla/5.0, Chrome/120.0.0.0, Firefox/121.0)
        val browserVersion = extractBrowserVersion(defaultUserAgent)
        
        // Lấy tên app và phiên bản app
        val appName = "TXA Hub"
        val appVersion = try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "1.1_txa"
        } catch (e: Exception) {
            "1.1_txa"
        }
        
        // Tạo user agent mới: TXAAPP_AppName/AppVersion DeviceName OSVersion BrowserVersion
        val customUserAgent = "TXAAPP_${appName}/${appVersion} ${deviceName} ${osVersion} ${browserVersion}"
        webSettings.userAgentString = customUserAgent


        // Thêm JavaScript Interface để web có thể gọi các quyền app
        webView.addJavascriptInterface(AppJavaScriptInterface(), "TXAApp")

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

                // Xử lý external links (Telegram, WhatsApp, etc.)
                if (isExternalAppLink(url)) {
                    handleExternalLink(url)
                    return true
                }

                // Chỉ cho phép load các URL thuộc txahub.click hoặc https/http
                if (url.startsWith("https://txahub.click") || 
                    url.startsWith("http://txahub.click") ||
                    url.startsWith("https://") ||
                    url.startsWith("http://")) {
                    return false // Cho phép WebView load
                }

                // Chặn các URL khác
                return true
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
     * Chuyển đổi txahub://path thành https://txahub.click/path
     * Ví dụ: txahub://dashboard -> https://txahub.click/dashboard
     */
    private fun handleDeepLink(url: String) {
        try {
            val uri = Uri.parse(url)
            // Với txahub://dashboard, host = "dashboard", path = null
            // Với txahub://verify-mail/abc123, host = "verify-mail", pathSegments = ["abc123"]
            val host = uri.host ?: ""
            val pathSegments = uri.pathSegments
            
            // Xây dựng full path
            val fullPath = buildString {
                if (host.isNotEmpty()) {
                    append("/$host")
                }
                pathSegments.forEach { segment ->
                    append("/$segment")
                }
            }
            
            // Xây dựng URL đích
            val targetUrl = when {
                host.isEmpty() || host == "home" || host == "main" -> {
                    WEB_URL
                }
                else -> {
                    // Chuyển đổi txahub://path thành https://txahub.click/path
                    // Ví dụ: txahub://dashboard -> https://txahub.click/dashboard
                    // Ví dụ: txahub://verify-mail/abc123 -> https://txahub.click/verify-mail/abc123
                    val finalPath = if (fullPath.isEmpty()) "/$host" else fullPath
                    val queryString = uri.query?.let { "?$it" } ?: ""
                    val fragment = uri.fragment?.let { "#$it" } ?: ""
                    "$WEB_URL$finalPath$queryString$fragment"
                }
            }
            
            logWriter.writeAppLog("Deep link: $url -> $targetUrl", "MainActivity", android.util.Log.INFO)
            webView.loadUrl(targetUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            logWriter.writeAppLog("Error handling deep link: ${e.message}\nURL: $url", "MainActivity", android.util.Log.ERROR)
            webView.loadUrl(WEB_URL)
        }
    }

    /**
     * Kiểm tra xem URL có phải là external app link không (Telegram, WhatsApp, etc.)
     */
    private fun isExternalAppLink(url: String): Boolean {
        val externalSchemes = listOf(
            "tg://", "telegram://", "whatsapp://", "viber://",
            "fb://", "facebook://", "instagram://", "twitter://",
            "youtube://", "mailto:", "tel:", "sms:"
        )
        return externalSchemes.any { url.startsWith(it, ignoreCase = true) }
    }

    /**
     * Xử lý external links - thử mở bằng app khác hoặc báo lỗi
     */
    private fun handleExternalLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (intent.resolveActivity(packageManager) != null) {
                // Có app có thể xử lý link này, mở bằng app đó
                startActivity(intent)
            } else {
                // Không có app nào có thể xử lý, hiển thị thông báo lỗi
                showExternalLinkError(url)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logWriter.writeAppLog("Error handling external link: ${e.message}\nURL: $url", "MainActivity", android.util.Log.ERROR)
            showExternalLinkError(url)
        }
    }

    /**
     * Hiển thị dialog báo lỗi không mở được external link
     */
    private fun showExternalLinkError(url: String) {
        AlertDialog.Builder(this)
            .setTitle("Không thể mở liên kết")
            .setMessage("Ứng dụng không thể mở liên kết này trong app:\n\n$url\n\nVui lòng mở bằng trình duyệt hoặc app tương ứng.")
            .setPositiveButton("Đóng", null)
            .show()
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
                    isExternalAppLink(data.toString()) -> {
                        handleExternalLink(data.toString())
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
     * Setup xử lý nút back
     */
    private fun setupBackButtonHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }
    
    /**
     * Xử lý nút back (deprecated, dùng cho Android cũ)
     */
    @Deprecated("Deprecated in Java", ReplaceWith("setupBackButtonHandler()"))
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Fallback cho Android cũ (API < 33)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                super.onBackPressed()
            }
        } else {
            // Android 13+ sử dụng OnBackPressedDispatcher
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try {
            networkMonitor?.stopMonitoring()
            webView.destroy()
            logWriter.writeAppLog("MainActivity onDestroy", "MainActivity", android.util.Log.INFO)
        } catch (e: Exception) {
            logWriter.writeAppLog("Error in onDestroy: ${e.message}", "MainActivity", android.util.Log.ERROR)
        }
        super.onDestroy()
    }

    /**
     * Lấy tên thiết bị từ Build.MODEL hoặc Build.DEVICE
     * Loại bỏ các ký tự đặc biệt để tạo user agent hợp lệ
     */
    private fun getDeviceName(): String {
        val model = Build.MODEL ?: Build.DEVICE ?: "Unknown"
        // Loại bỏ khoảng trắng và ký tự đặc biệt, thay bằng dấu gạch dưới
        return model
            .replace(" ", "_")
            .replace("-", "_")
            .replace("/", "_")
            .replace("\\", "_")
            .replace("(", "")
            .replace(")", "")
            .replace("[", "")
            .replace("]", "")
            .replace("{", "")
            .replace("}", "")
            .trim()
    }
    
    /**
     * Cắt chuỗi user agent gốc để lấy phiên bản trình duyệt
     * Ví dụ: "Mozilla/5.0 ... Chrome/120.0.0.0 ..." -> "Chrome/120.0.0.0"
     * Hoặc: "Mozilla/5.0 ... Firefox/121.0 ..." -> "Firefox/121.0"
     */
    private fun extractBrowserVersion(userAgent: String): String {
        if (userAgent.isEmpty()) {
            return "WebView"
        }
        
        // Tìm các trình duyệt phổ biến
        val browserPatterns = listOf(
            "Chrome/([\\d.]+)",
            "Firefox/([\\d.]+)",
            "Safari/([\\d.]+)",
            "Edge/([\\d.]+)",
            "Opera/([\\d.]+)",
            "Version/([\\d.]+).*Safari"
        )
        
        for (pattern in browserPatterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(userAgent)
            if (match != null) {
                val browserName = when {
                    pattern.contains("Chrome") -> "Chrome"
                    pattern.contains("Firefox") -> "Firefox"
                    pattern.contains("Safari") && !pattern.contains("Version") -> "Safari"
                    pattern.contains("Edge") -> "Edge"
                    pattern.contains("Opera") -> "Opera"
                    pattern.contains("Version") -> "Safari"
                    else -> "Browser"
                }
                val version = match.groupValues[1]
                return "$browserName/$version"
            }
        }
        
        // Nếu không tìm thấy, trả về phần đầu của user agent (Mozilla/5.0)
        val mozillaMatch = Regex("Mozilla/([\\d.]+)").find(userAgent)
        return if (mozillaMatch != null) {
            "Mozilla/${mozillaMatch.groupValues[1]}"
        } else {
            "WebView"
        }
    }

    /**
     * JavaScript Interface để web có thể gọi các quyền và tính năng của app
     */
    @SuppressLint("JavascriptInterface")
    inner class AppJavaScriptInterface {
        
        /**
         * Kiểm tra quyền thông báo
         * @return "granted", "denied", hoặc "not_supported"
         */
        @JavascriptInterface
        fun checkNotificationPermission(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                when {
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> "granted"
                    else -> "denied"
                }
            } else {
                // Android < 13, kiểm tra qua NotificationManagerCompat
                if (NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()) {
                    "granted"
                } else {
                    "denied"
                }
            }
        }

        /**
         * Yêu cầu quyền thông báo
         */
        @JavascriptInterface
        fun requestNotificationPermission() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        1001
                    )
                } else {
                    // Android < 13, mở cài đặt thông báo
                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                }
            }
        }

        /**
         * Kiểm tra quyền camera
         */
        @JavascriptInterface
        fun checkCameraPermission(): String {
            return when {
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> "granted"
                else -> "denied"
            }
        }

        /**
         * Yêu cầu quyền camera
         */
        @JavascriptInterface
        fun requestCameraPermission() {
            runOnUiThread {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.CAMERA),
                    1002
                )
            }
        }

        /**
         * Kiểm tra quyền vị trí
         */
        @JavascriptInterface
        fun checkLocationPermission(): String {
            val fineLocation = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarseLocation = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            return when {
                fineLocation -> "granted"
                coarseLocation -> "granted"
                else -> "denied"
            }
        }

        /**
         * Yêu cầu quyền vị trí
         */
        @JavascriptInterface
        fun requestLocationPermission() {
            runOnUiThread {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    1003
                )
            }
        }

        /**
         * Lấy danh sách các quyền app có
         * @return JSON string với danh sách quyền
         */
        @JavascriptInterface
        fun getAvailablePermissions(): String {
            val permissions = mutableMapOf<String, String>()
            permissions["notification"] = checkNotificationPermission()
            permissions["camera"] = checkCameraPermission()
            permissions["location"] = checkLocationPermission()
            
            // Chuyển đổi thành JSON string
            val json = permissions.entries.joinToString(", ") { "\"${it.key}\":\"${it.value}\"" }
            return "{$json}"
        }

        /**
         * Hiển thị toast message
         */
        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * Lấy version app
         */
        @JavascriptInterface
        fun getAppVersion(): String {
            return try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                packageInfo.versionName ?: "1.1_txa"
            } catch (e: Exception) {
                "1.1_txa"
            }
        }
    }
}

