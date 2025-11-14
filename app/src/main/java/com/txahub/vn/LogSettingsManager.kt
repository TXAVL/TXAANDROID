package com.txahub.vn

import android.content.Context
import android.content.SharedPreferences

/**
 * Quản lý cài đặt bật/tắt các loại log
 */
class LogSettingsManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "txahub_log_settings_prefs"
        
        // Keys cho từng loại log
        private const val KEY_LOG_API_ENABLED = "log_api_enabled"
        private const val KEY_LOG_APP_ENABLED = "log_app_enabled"
        private const val KEY_LOG_CRASH_ENABLED = "log_crash_enabled"
        private const val KEY_LOG_UPDATE_CHECK_ENABLED = "log_update_check_enabled"
        private const val KEY_LOG_PASSKEY_ENABLED = "log_passkey_enabled"
        
        // Mặc định: tất cả đều bật
        private const val DEFAULT_ENABLED = true
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Kiểm tra xem log API có được bật không
     */
    fun isApiLogEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOG_API_ENABLED, DEFAULT_ENABLED)
    }
    
    /**
     * Bật/tắt log API
     */
    fun setApiLogEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOG_API_ENABLED, enabled).apply()
    }
    
    /**
     * Kiểm tra xem log App có được bật không
     */
    fun isAppLogEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOG_APP_ENABLED, DEFAULT_ENABLED)
    }
    
    /**
     * Bật/tắt log App
     */
    fun setAppLogEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOG_APP_ENABLED, enabled).apply()
    }
    
    /**
     * Kiểm tra xem log Crash có được bật không
     */
    fun isCrashLogEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOG_CRASH_ENABLED, DEFAULT_ENABLED)
    }
    
    /**
     * Bật/tắt log Crash
     */
    fun setCrashLogEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOG_CRASH_ENABLED, enabled).apply()
    }
    
    /**
     * Kiểm tra xem log Update Check có được bật không
     */
    fun isUpdateCheckLogEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOG_UPDATE_CHECK_ENABLED, DEFAULT_ENABLED)
    }
    
    /**
     * Bật/tắt log Update Check
     */
    fun setUpdateCheckLogEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOG_UPDATE_CHECK_ENABLED, enabled).apply()
    }
    
    /**
     * Kiểm tra xem log Passkey có được bật không
     */
    fun isPasskeyLogEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOG_PASSKEY_ENABLED, DEFAULT_ENABLED)
    }
    
    /**
     * Bật/tắt log Passkey
     */
    fun setPasskeyLogEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOG_PASSKEY_ENABLED, enabled).apply()
    }
    
    /**
     * Reset tất cả về mặc định (bật tất cả)
     */
    fun resetToDefaults() {
        prefs.edit()
            .putBoolean(KEY_LOG_API_ENABLED, DEFAULT_ENABLED)
            .putBoolean(KEY_LOG_APP_ENABLED, DEFAULT_ENABLED)
            .putBoolean(KEY_LOG_CRASH_ENABLED, DEFAULT_ENABLED)
            .putBoolean(KEY_LOG_UPDATE_CHECK_ENABLED, DEFAULT_ENABLED)
            .putBoolean(KEY_LOG_PASSKEY_ENABLED, DEFAULT_ENABLED)
            .apply()
    }
}

