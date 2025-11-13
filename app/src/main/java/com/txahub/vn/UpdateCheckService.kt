package com.txahub.vn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class UpdateCheckService : Service() {
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateChecker: UpdateChecker by lazy { UpdateChecker(this) }
    private val notificationHelper: NotificationHelper by lazy { NotificationHelper(this) }
    private val checkInterval: Long = 30 * 1000 // 30 giây
    private val notificationInterval: Long = 5 * 60 * 1000 // Thông báo mỗi 5 phút (giữ nguyên để không spam)
    private val prefs by lazy { getSharedPreferences("txahub_prefs", Context.MODE_PRIVATE) }
    private var lastNotifiedVersion: String = ""
    private var lastNotificationTime: Long = 0
    
    companion object {
        const val CHANNEL_ID_BACKGROUND = "txahub_background_channel"
        private const val CHANNEL_NAME_BACKGROUND = "TXA Hub đang chạy nền"
        private const val NOTIFICATION_ID_BACKGROUND = 1002
        private const val ACTION_HIDE_NOTIFICATION = "com.txahub.vn.HIDE_BACKGROUND_NOTIFICATION"
        
        /**
         * Kiểm tra xem có quyền tối ưu hóa pin không
         */
        fun hasBatteryOptimizationPermission(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        }
        
        /**
         * Start service nếu có quyền
         */
        fun startIfAllowed(context: Context) {
            if (hasBatteryOptimizationPermission(context)) {
                val intent = Intent(context, UpdateCheckService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
        
        /**
         * Stop service
         */
        fun stop(context: Context) {
            val intent = Intent(context, UpdateCheckService::class.java)
            context.stopService(intent)
        }
    }
    
    private val checkRunnable = object : Runnable {
        override fun run() {
            // Kiểm tra cập nhật
            updateChecker.checkUpdate { updateInfo ->
                // Đảm bảo chạy trên main thread
                handler.post {
                    if (updateInfo != null) {
                        // Có bản cập nhật mới
                        val currentTime = System.currentTimeMillis()
                        val shouldNotify = when {
                            // Nếu là version mới chưa từng thông báo
                            lastNotifiedVersion != updateInfo.versionName -> {
                                android.util.Log.d("UpdateCheckService", "Should notify: New version ${updateInfo.versionName} (last: $lastNotifiedVersion)")
                                true
                            }
                            // Nếu đã thông báo version này rồi, nhưng đã qua 5 phút
                            currentTime - lastNotificationTime >= notificationInterval -> {
                                android.util.Log.d("UpdateCheckService", "Should notify: Time interval passed (${(currentTime - lastNotificationTime) / 1000}s)")
                                true
                            }
                            // Còn lại thì không thông báo
                            else -> {
                                android.util.Log.d("UpdateCheckService", "Should NOT notify: Same version, time not passed yet")
                                false
                            }
                        }
                        
                        if (shouldNotify) {
                            // Kiểm tra quyền thông báo trước khi hiển thị
                            val hasPermission = notificationHelper.hasNotificationPermission()
                            android.util.Log.d("UpdateCheckService", "Has notification permission: $hasPermission")
                            
                            if (hasPermission) {
                                try {
                                    notificationHelper.showUpdateNotification(
                                        versionName = updateInfo.versionName,
                                        downloadUrl = updateInfo.downloadUrl,
                                        forceUpdate = updateInfo.forceUpdate
                                    )
                                    android.util.Log.d("UpdateCheckService", "Notification sent for version ${updateInfo.versionName}")
                                    
                                    lastNotifiedVersion = updateInfo.versionName
                                    lastNotificationTime = currentTime
                                    
                                    // Lưu vào SharedPreferences
                                    prefs.edit()
                                        .putString("last_notified_version", updateInfo.versionName)
                                        .putLong("last_notification_time", currentTime)
                                        .apply()
                                } catch (e: Exception) {
                                    android.util.Log.e("UpdateCheckService", "Error showing notification: ${e.message}", e)
                                }
                            } else {
                                android.util.Log.w("UpdateCheckService", "No notification permission, cannot show update notification")
                            }
                        }
                    } else {
                        // Không có update, reset thông báo để lần sau có update mới sẽ thông báo ngay
                        android.util.Log.d("UpdateCheckService", "No update available, resetting notification state")
                        lastNotifiedVersion = ""
                        lastNotificationTime = 0
                        prefs.edit()
                            .putString("last_notified_version", "")
                            .putLong("last_notification_time", 0)
                            .apply()
                    }
                }
            }
            
            // Lên lịch check lại sau 30 giây
            handler.postDelayed(this, checkInterval)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Xử lý action ẩn thông báo
        if (intent?.action == ACTION_HIDE_NOTIFICATION) {
            prefs.edit().putBoolean("hide_background_notification", true).apply()
            // Vẫn phải giữ foreground notification (không thể stop vì service đang chạy foreground)
            // Chỉ cập nhật notification để ít nổi bật hơn
            createForegroundNotification(hideNotification = true)
            return START_STICKY
        }
        
        // Lấy version đã thông báo lần cuối và thời gian
        lastNotifiedVersion = prefs.getString("last_notified_version", "") ?: ""
        lastNotificationTime = prefs.getLong("last_notification_time", 0)
        
        // Kiểm tra xem có ẩn thông báo background không
        val hideNotification = prefs.getBoolean("hide_background_notification", false)
        
        // QUAN TRỌNG: LUÔN phải gọi startForeground() khi dùng startForegroundService()
        // Nếu không sẽ bị crash với RemoteServiceException
        createForegroundNotification(hideNotification = hideNotification)
        
        // Bắt đầu check update định kỳ
        handler.post(checkRunnable)
        return START_STICKY
    }
    
    /**
     * Tạo foreground notification không thể xóa
     * @param hideNotification Nếu true, tạo notification tối thiểu (vẫn phải có để service chạy foreground)
     */
    private fun createForegroundNotification(hideNotification: Boolean = false) {
        // Đảm bảo channel được tạo (có thể NotificationHelper chưa được khởi tạo)
        ensureNotificationChannel()
        
        // Intent để mở app
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            2,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_BACKGROUND)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true) // Không thể xóa bằng swipe
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        if (hideNotification) {
            // Notification tối thiểu khi người dùng đã chọn ẩn
            notificationBuilder
                .setContentTitle("TXA Hub")
                .setContentText("Đang chạy nền")
                .setSilent(true) // Không có âm thanh
        } else {
            // Notification đầy đủ
            // Intent để ẩn thông báo
            val hideIntent = Intent(this, UpdateCheckService::class.java).apply {
                action = ACTION_HIDE_NOTIFICATION
            }
            val hidePendingIntent = PendingIntent.getService(
                this,
                3,
                hideIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            notificationBuilder
                .setContentTitle("TXA Hub đang chạy nền")
                .setContentText("Ứng dụng đang chạy nền để kiểm tra cập nhật")
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Ẩn thông báo",
                    hidePendingIntent
                )
        }
        
        val notification = notificationBuilder.build()
        
        // QUAN TRỌNG: LUÔN phải gọi startForeground() khi dùng startForegroundService()
        startForeground(NOTIFICATION_ID_BACKGROUND, notification)
    }
    
    /**
     * Đảm bảo notification channel được tạo trước khi sử dụng
     */
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID_BACKGROUND)
            
            if (existingChannel == null) {
                // Channel chưa tồn tại, tạo mới
                val channel = NotificationChannel(
                    CHANNEL_ID_BACKGROUND,
                    CHANNEL_NAME_BACKGROUND,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Thông báo ứng dụng đang chạy nền"
                    enableVibration(false)
                    enableLights(false)
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
    }
}

