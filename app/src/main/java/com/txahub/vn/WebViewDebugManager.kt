package com.txahub.vn

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebView

/**
 * Quản lý cài đặt WebView debugging
 */
class WebViewDebugManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "txahub_webview_debug_prefs"
        private const val KEY_WEBVIEW_DEBUG_ENABLED = "webview_debug_enabled"
        
        // Mặc định: tắt (chỉ bật trong debug build)
        private const val DEFAULT_ENABLED = false
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Kiểm tra xem WebView debugging có được bật không
     */
    fun isWebViewDebugEnabled(): Boolean {
        return prefs.getBoolean(KEY_WEBVIEW_DEBUG_ENABLED, DEFAULT_ENABLED)
    }
    
    /**
     * Bật/tắt WebView debugging
     */
    fun setWebViewDebugEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEBVIEW_DEBUG_ENABLED, enabled).apply()
        // Áp dụng ngay lập tức
        WebView.setWebContentsDebuggingEnabled(enabled)
    }
    
    /**
     * Áp dụng setting WebView debugging
     */
    fun applyWebViewDebugSetting() {
        val enabled = isWebViewDebugEnabled()
        WebView.setWebContentsDebuggingEnabled(enabled)
    }
}

