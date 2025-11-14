package com.txahub.vn

import android.content.Context
import android.content.SharedPreferences

/**
 * Quản lý cài đặt notification grouping cho Android Auto
 */
class AndroidAutoGroupingManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "txahub_android_auto_grouping_prefs"
        private const val KEY_GROUPING_ENABLED = "grouping_enabled"
        private const val GROUP_ID = "txahub_notification_group"
        private const val GROUP_NAME = "TXA Hub"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Kiểm tra xem notification grouping có được bật không
     */
    fun isGroupingEnabled(): Boolean {
        return prefs.getBoolean(KEY_GROUPING_ENABLED, true) // Mặc định là bật
    }
    
    /**
     * Bật/tắt notification grouping
     */
    fun setGroupingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GROUPING_ENABLED, enabled).apply()
    }
    
    /**
     * Lấy ID của notification group
     */
    fun getGroupId(): String {
        return GROUP_ID
    }
    
    /**
     * Lấy tên của notification group
     */
    fun getGroupName(): String {
        return GROUP_NAME
    }
}

