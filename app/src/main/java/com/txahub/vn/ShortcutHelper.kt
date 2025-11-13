package com.txahub.vn

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Helper để tạo App Shortcuts (khi bấm giữ icon app)
 */
class ShortcutHelper(private val context: Context) {
    
    companion object {
        private const val SHORTCUT_DASHBOARD = "shortcut_dashboard"
        private const val SHORTCUT_PROFILE = "shortcut_profile"
        private const val SHORTCUT_PREFERENCES = "shortcut_preferences"
    }
    
    /**
     * Tạo và đăng ký shortcuts
     */
    fun createShortcuts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            
            val shortcuts = listOf(
                createShortcut(
                    id = SHORTCUT_DASHBOARD,
                    shortLabel = "Dashboard",
                    longLabel = "Mở Dashboard",
                    deepLink = "txahub://dashboard",
                    iconRes = android.R.drawable.ic_menu_view
                ),
                createShortcut(
                    id = SHORTCUT_PROFILE,
                    shortLabel = "Tài khoản",
                    longLabel = "Mở Tài khoản",
                    deepLink = "txahub://account",
                    iconRes = android.R.drawable.ic_menu_myplaces
                ),
                createShortcut(
                    id = SHORTCUT_PREFERENCES,
                    shortLabel = "Cài đặt",
                    longLabel = "Mở Cài đặt tài khoản",
                    deepLink = "txahub://account-preferences",
                    iconRes = android.R.drawable.ic_menu_preferences
                )
            )
            
            shortcutManager?.dynamicShortcuts = shortcuts
        } else {
            // Fallback cho Android cũ - dùng ShortcutManagerCompat
            createShortcutsCompat()
        }
    }
    
    /**
     * Tạo shortcut cho Android 7.1+
     */
    @android.annotation.TargetApi(Build.VERSION_CODES.N_MR1)
    private fun createShortcut(
        id: String,
        shortLabel: String,
        longLabel: String,
        deepLink: String,
        iconRes: Int
    ): ShortcutInfo {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
            setPackage(context.packageName)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        return ShortcutInfo.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(Icon.createWithResource(context, iconRes))
            .setIntent(intent)
            .build()
    }
    
    /**
     * Tạo shortcuts cho Android cũ (compat)
     */
    private fun createShortcutsCompat() {
        val shortcuts = listOf(
            ShortcutInfoCompat.Builder(context, SHORTCUT_DASHBOARD)
                .setShortLabel("Dashboard")
                .setLongLabel("Mở Dashboard")
                .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_view))
                .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse("txahub://dashboard")).apply {
                    setPackage(context.packageName)
                })
                .build(),
            ShortcutInfoCompat.Builder(context, SHORTCUT_PROFILE)
                .setShortLabel("Tài khoản")
                .setLongLabel("Mở Tài khoản")
                .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_myplaces))
                .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse("txahub://account")).apply {
                    setPackage(context.packageName)
                })
                .build(),
            ShortcutInfoCompat.Builder(context, SHORTCUT_PREFERENCES)
                .setShortLabel("Cài đặt")
                .setLongLabel("Mở Cài đặt tài khoản")
                .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_preferences))
                .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse("txahub://account-preferences")).apply {
                    setPackage(context.packageName)
                })
                .build()
        )
        
        ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
    }
    
    /**
     * Xóa tất cả shortcuts
     */
    fun removeShortcuts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            shortcutManager?.removeAllDynamicShortcuts()
        } else {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        }
    }
}

