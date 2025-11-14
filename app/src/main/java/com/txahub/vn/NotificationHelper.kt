package com.txahub.vn

import android.app.ActivityManager
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
    
    private val soundManager: NotificationSoundManager by lazy { NotificationSoundManager(context) }
    private val groupingManager: AndroidAutoGroupingManager by lazy { AndroidAutoGroupingManager(context) }
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Tạo các notification channels cho Android 8.0+
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Tạo notification group nếu grouping được bật
            if (groupingManager.isGroupingEnabled()) {
                val group = android.app.NotificationChannelGroup(
                    groupingManager.getGroupId(),
                    groupingManager.getGroupName()
                )
                notificationManager.createNotificationChannelGroup(group)
            }
            
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
                // Đặt sound cho notification từ settings
                val soundUri = soundManager.getNotificationSoundUri()
                // Luôn set sound (không bao giờ null vì default sẽ dùng sound hệ thống)
                setSound(soundUri, null)
                // Gán vào group nếu grouping được bật
                if (groupingManager.isGroupingEnabled()) {
                    group = groupingManager.getGroupId()
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
                // Đặt sound cho background channel từ settings
                val soundUri = soundManager.getNotificationSoundUri()
                setSound(soundUri, null)
                // Gán vào group nếu grouping được bật
                if (groupingManager.isGroupingEnabled()) {
                    group = groupingManager.getGroupId()
                }
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
        
        // Lấy thông tin sound đang dùng TRƯỚC KHI tạo notification builder
        val soundUri = soundManager.getNotificationSoundUri()
        val soundType = soundManager.getSoundType()
        val soundDisplayName = soundManager.getSoundDisplayName()
        
        // Log thông tin notification và sound
        android.util.Log.d("NotificationHelper", "=== Sending Update Notification ===")
        android.util.Log.d("NotificationHelper", "Channel: $CHANNEL_ID_UPDATE")
        android.util.Log.d("NotificationHelper", "Sound type: $soundType")
        android.util.Log.d("NotificationHelper", "Sound URI: $soundUri")
        android.util.Log.d("NotificationHelper", "Sound display name: $soundDisplayName")
        
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_UPDATE)
            .setSmallIcon(android.R.drawable.stat_notify_sync) // Icon mặc định
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            // Luôn set priority MAX để hiển thị ngay cả khi bật "Không làm phiền"
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(downloadPendingIntent)
            // Set sound trực tiếp từ settings (override channel sound)
            .setSound(soundUri)
            // Set vibration và lights từ defaults (không override sound)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
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
        
        // Ghi log vào file
        val logWriter = LogWriter(context)
        logWriter.writeAppLog(
            "Sending update notification - Channel: $CHANNEL_ID_UPDATE, Sound type: $soundType, Sound URI: $soundUri, Sound name: $soundDisplayName",
            "NotificationHelper",
            android.util.Log.INFO
        )
        
        val notification = notificationBuilder.build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE, notification)
        
        android.util.Log.d("NotificationHelper", "Notification sent successfully")
        
        // Đọc thông báo bằng TTS nếu được bật
        val ttsManager = NotificationTTSManager(context)
        if (ttsManager.isTTSEnabled()) {
            // Khởi tạo TTS nếu chưa được khởi tạo
            ttsManager.initialize { success ->
                if (success && ttsManager.isAvailable()) {
                    val textToRead = "$title. $message"
                    ttsManager.speakNotification(textToRead, "update_notification_${System.currentTimeMillis()}")
                }
            }
        }
    }
    
    /**
     * Hủy thông báo cập nhật
     */
    fun cancelUpdateNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_UPDATE)
    }
    
    /**
     * Kiểm tra xem UpdateCheckService có đang chạy không
     */
    private fun isUpdateCheckServiceRunning(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION") // getRunningServices is deprecated but still needed for checking service status
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        return runningServices.any { it.service.className == UpdateCheckService::class.java.name }
    }
    
    /**
     * Cập nhật sound cho notification channel
     * Trên Android 8.0+, không thể update channel đã tồn tại, phải xóa và tạo lại
     * Nếu service đang chạy, sẽ tạm thời stop service để cập nhật channel
     */
    fun updateNotificationChannelSound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val soundUri = soundManager.getNotificationSoundUri()
            val soundType = soundManager.getSoundType()
            val soundDisplayName = soundManager.getSoundDisplayName()
            
            // Log thông tin sound
            android.util.Log.d("NotificationHelper", "Updating notification channels sound:")
            android.util.Log.d("NotificationHelper", "  - Sound type: $soundType")
            android.util.Log.d("NotificationHelper", "  - Sound URI: $soundUri")
            android.util.Log.d("NotificationHelper", "  - Sound display name: $soundDisplayName")
            
            // Update UPDATE channel
            val existingUpdateChannel = notificationManager.getNotificationChannel(CHANNEL_ID_UPDATE)
            if (existingUpdateChannel != null) {
                try {
                    // Lưu lại các settings quan trọng trước khi xóa
                    val importance = existingUpdateChannel.importance
                    val bypassDnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        existingUpdateChannel.canBypassDnd()
                    } else {
                        false
                    }
                    val vibrationEnabled = existingUpdateChannel.shouldVibrate()
                    val lightsEnabled = existingUpdateChannel.shouldShowLights()
                    val showBadge = existingUpdateChannel.canShowBadge()
                    
                    // Xóa channel cũ - có thể fail nếu có notification đang dùng
                    var canDeleteUpdateChannel = true
                    try {
                        notificationManager.deleteNotificationChannel(CHANNEL_ID_UPDATE)
                    } catch (e: SecurityException) {
                        // Không thể xóa channel vì đang được sử dụng
                        // Sound sẽ được set khi tạo notification mới
                        android.util.Log.w("NotificationHelper", "Cannot delete UPDATE channel (in use): ${e.message}")
                        canDeleteUpdateChannel = false
                    }
                    
                    // Nếu không thể xóa channel, bỏ qua việc cập nhật channel này
                    if (!canDeleteUpdateChannel) {
                        android.util.Log.d("NotificationHelper", "Skipping UPDATE channel update (channel in use)")
                    } else {
                        // Tạo channel mới với sound mới
                        val newUpdateChannel = NotificationChannel(
                            CHANNEL_ID_UPDATE,
                            CHANNEL_NAME_UPDATE,
                            importance
                        ).apply {
                            description = "Thông báo về bản cập nhật mới nhất của TXA Hub"
                            enableVibration(vibrationEnabled)
                            enableLights(lightsEnabled)
                            setShowBadge(showBadge)
                            // Cho phép hiển thị ngay cả khi bật "Không làm phiền" (Android 7.0+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                setBypassDnd(bypassDnd)
                            }
                            // Đặt sound mới
                            setSound(soundUri, null)
                        }
                        
                        // Tạo channel mới
                        notificationManager.createNotificationChannel(newUpdateChannel)
                        android.util.Log.d("NotificationHelper", "Updated UPDATE channel sound: $soundUri")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NotificationHelper", "Error updating UPDATE channel: ${e.message}", e)
                    // Không crash, chỉ log lỗi
                }
            }
            
            // Update BACKGROUND channel
            val existingBackgroundChannel = notificationManager.getNotificationChannel(CHANNEL_ID_BACKGROUND)
            if (existingBackgroundChannel != null) {
                // Lưu lại trạng thái service trước khi xử lý
                var wasServiceRunning = false
                try {
                    // Lưu lại các settings quan trọng trước khi xóa
                    val importance = existingBackgroundChannel.importance
                    val vibrationEnabled = existingBackgroundChannel.shouldVibrate()
                    val lightsEnabled = existingBackgroundChannel.shouldShowLights()
                    val showBadge = existingBackgroundChannel.canShowBadge()
                    
                    // Xóa channel cũ - có thể fail nếu service đang chạy
                    var canDeleteBackgroundChannel = true
                    try {
                        notificationManager.deleteNotificationChannel(CHANNEL_ID_BACKGROUND)
                    } catch (e: SecurityException) {
                        // Không thể xóa channel vì service đang chạy
                        // Sound sẽ được set khi tạo notification mới
                        android.util.Log.w("NotificationHelper", "Cannot delete BACKGROUND channel (service running): ${e.message}")
                        canDeleteBackgroundChannel = false
                    }
                    
                    // Nếu không thể xóa channel vì service đang chạy, tạm thời stop service
                    if (!canDeleteBackgroundChannel && isUpdateCheckServiceRunning()) {
                        wasServiceRunning = true
                        android.util.Log.d("NotificationHelper", "Service is running, stopping temporarily to update channel")
                        // Tạm thời stop service
                        UpdateCheckService.stop(context)
                        // Đợi một chút để service stop hoàn toàn
                        Thread.sleep(500)
                        // Thử xóa channel lại
                        try {
                            notificationManager.deleteNotificationChannel(CHANNEL_ID_BACKGROUND)
                            canDeleteBackgroundChannel = true
                            android.util.Log.d("NotificationHelper", "Successfully deleted BACKGROUND channel after stopping service")
                        } catch (e: SecurityException) {
                            android.util.Log.w("NotificationHelper", "Still cannot delete BACKGROUND channel: ${e.message}")
                        }
                    }
                    
                    // Nếu vẫn không thể xóa channel, bỏ qua việc cập nhật channel này
                    if (!canDeleteBackgroundChannel) {
                        android.util.Log.d("NotificationHelper", "Skipping BACKGROUND channel update (channel in use)")
                    } else {
                        // Tạo channel mới với sound mới
                        val newBackgroundChannel = NotificationChannel(
                            CHANNEL_ID_BACKGROUND,
                            CHANNEL_NAME_BACKGROUND,
                            importance
                        ).apply {
                            description = "Thông báo ứng dụng đang chạy nền, cái này khuyến nghị không nên tắt"
                            enableVibration(vibrationEnabled)
                            enableLights(lightsEnabled)
                            setShowBadge(showBadge)
                            // Đặt sound mới
                            setSound(soundUri, null)
                        }
                        
                        // Tạo channel mới
                        notificationManager.createNotificationChannel(newBackgroundChannel)
                        android.util.Log.d("NotificationHelper", "Updated BACKGROUND channel sound: $soundUri")
                    }
                    
                    // Nếu đã stop service để cập nhật channel, start lại service
                    if (wasServiceRunning && UpdateCheckService.hasBatteryOptimizationPermission(context)) {
                        android.util.Log.d("NotificationHelper", "Restarting UpdateCheckService after channel update")
                        // Đợi một chút để channel được tạo xong
                        Thread.sleep(200)
                        UpdateCheckService.startIfAllowed(context)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NotificationHelper", "Error updating BACKGROUND channel: ${e.message}", e)
                    // Không crash, chỉ log lỗi
                    // Nếu đã stop service nhưng có lỗi, vẫn cố start lại
                    if (wasServiceRunning && UpdateCheckService.hasBatteryOptimizationPermission(context)) {
                        UpdateCheckService.startIfAllowed(context)
                    }
                }
            }
            
            // Nếu cả 2 channels đều chưa tồn tại, tạo mới
            if (existingUpdateChannel == null && existingBackgroundChannel == null) {
                createNotificationChannels()
            }
        }
    }
    
    /**
     * Cập nhật notification channels grouping dựa trên cài đặt
     */
    fun updateNotificationChannelsGrouping() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val isGroupingEnabled = groupingManager.isGroupingEnabled()
            
            if (isGroupingEnabled) {
                // Tạo group nếu chưa có
                val group = android.app.NotificationChannelGroup(
                    groupingManager.getGroupId(),
                    groupingManager.getGroupName()
                )
                notificationManager.createNotificationChannelGroup(group)
                
                // Xóa và tạo lại channels để gán vào group
                try {
                    notificationManager.deleteNotificationChannel(CHANNEL_ID_UPDATE)
                    notificationManager.deleteNotificationChannel(CHANNEL_ID_BACKGROUND)
                } catch (e: Exception) {
                    android.util.Log.e("NotificationHelper", "Error deleting channels for grouping update", e)
                }
                
                // Tạo lại channels với group
                createNotificationChannels()
            } else {
                // Xóa group và tạo lại channels không có group
                try {
                    notificationManager.deleteNotificationChannelGroup(groupingManager.getGroupId())
                    notificationManager.deleteNotificationChannel(CHANNEL_ID_UPDATE)
                    notificationManager.deleteNotificationChannel(CHANNEL_ID_BACKGROUND)
                } catch (e: Exception) {
                    android.util.Log.e("NotificationHelper", "Error removing grouping", e)
                }
                
                // Tạo lại channels không có group
                createNotificationChannels()
            }
        }
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

