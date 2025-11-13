package com.txahub.vn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID_UPDATE = "txahub_update_channel"
        private const val CHANNEL_NAME_UPDATE = "Cập nhật TXA Hub"
        private const val CHANNEL_ID_BACKGROUND = "txahub_background_channel"
        private const val CHANNEL_NAME_BACKGROUND = "TXA Hub đang chạy nền"
        private const val NOTIFICATION_ID_UPDATE = 1001
    }
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Tạo các notification channels cho Android 8.0+
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel cho thông báo cập nhật
            val updateChannel = NotificationChannel(
                CHANNEL_ID_UPDATE,
                CHANNEL_NAME_UPDATE,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo về bản cập nhật mới nhất của TXA Hub"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                // Cho phép hiển thị ngay cả khi bật "Không làm phiền" (Android 7.0+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setBypassDnd(true)
                }
            }
            
            // Channel cho thông báo chạy nền
            val backgroundChannel = NotificationChannel(
                CHANNEL_ID_BACKGROUND,
                CHANNEL_NAME_BACKGROUND,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo ứng dụng đang chạy nền, cái này khuyến nghị không nên tắt"
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            
            notificationManager.createNotificationChannel(updateChannel)
            notificationManager.createNotificationChannel(backgroundChannel)
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
        val appIconDrawable = try {
            context.packageManager.getApplicationIcon(context.packageName)
        } catch (e: Exception) {
            null
        }
        
        // Chuyển đổi Drawable thành Bitmap
        val appIconBitmap = appIconDrawable?.let { drawableToBitmap(it) }
        
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_UPDATE)
            .setSmallIcon(android.R.drawable.stat_notify_sync) // Icon mặc định
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            // Luôn set priority MAX để hiển thị ngay cả khi bật "Không làm phiền"
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(downloadPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound, vibration, lights
            .setCategory(NotificationCompat.CATEGORY_STATUS) // Cho phép customize trong settings
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Hiển thị trên lock screen
            .setFullScreenIntent(downloadPendingIntent, forceUpdate) // Full screen intent cho force update
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
        appIconBitmap?.let {
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
    
    /**
     * Chuyển đổi Drawable thành Bitmap
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}

