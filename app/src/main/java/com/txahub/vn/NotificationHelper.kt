package com.txahub.vn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "txahub_update_channel"
        private const val CHANNEL_NAME = "Cập nhật TXA Hub"
        private const val NOTIFICATION_ID_UPDATE = 1001
    }
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Tạo notification channel cho Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo về bản cập nhật mới của TXA Hub"
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Kiểm tra quyền thông báo
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.content.pm.PackageManager.PERMISSION_GRANTED == 
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
    
    /**
     * Hiển thị thông báo có bản cập nhật mới
     */
    fun showUpdateNotification(versionName: String, downloadUrl: String, forceUpdate: Boolean) {
        if (!hasNotificationPermission()) {
            return // Không có quyền, không hiển thị
        }
        
        // Intent để mở link tải
        val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val downloadPendingIntent = PendingIntent.getActivity(
            context,
            0,
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Intent để mở app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val title = if (forceUpdate) {
            "Cập nhật bắt buộc - TXA Hub"
        } else {
            "Có bản cập nhật mới - TXA Hub"
        }
        
        val message = if (forceUpdate) {
            "Phiên bản $versionName đã có sẵn. Vui lòng cập nhật ngay để tiếp tục sử dụng."
        } else {
            "Phiên bản $versionName đã có sẵn. Nhấn để tải về."
        }
        
        // Lấy icon app để dùng làm large icon
        val appIcon = try {
            context.packageManager.getApplicationIcon(context.packageName)
        } catch (e: Exception) {
            null
        }
        
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync) // Icon mặc định
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(if (forceUpdate) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(downloadPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound, vibration, lights
            .addAction(
                android.R.drawable.ic_dialog_info,
                "Tải ngay",
                downloadPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Mở app",
                openAppPendingIntent
            )
        
        // Thêm large icon nếu có
        appIcon?.let {
            notificationBuilder.setLargeIcon(it)
        }
        
        val notification = notificationBuilder.build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE, notification)
    }
    
    /**
     * Hủy thông báo cập nhật
     */
    fun cancelUpdateNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_UPDATE)
    }
}

