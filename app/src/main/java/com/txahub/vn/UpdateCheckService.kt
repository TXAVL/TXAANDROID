package com.txahub.vn

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class UpdateCheckService : Service() {
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateChecker: UpdateChecker by lazy { UpdateChecker(this) }
    private val notificationHelper: NotificationHelper by lazy { NotificationHelper(this) }
    private val checkInterval: Long = 3000 // 3 giây
    private val prefs by lazy { getSharedPreferences("txahub_prefs", Context.MODE_PRIVATE) }
    private var lastNotifiedVersion: String = ""
    
    private val checkRunnable = object : Runnable {
        override fun run() {
            // Kiểm tra cập nhật
            updateChecker.checkUpdate { updateInfo ->
                if (updateInfo != null) {
                    // Có bản cập nhật mới
                    // Chỉ thông báo nếu chưa thông báo cho version này
                    if (lastNotifiedVersion != updateInfo.versionName) {
                        // Kiểm tra quyền thông báo trước khi hiển thị
                        if (notificationHelper.hasNotificationPermission()) {
                            notificationHelper.showUpdateNotification(
                                versionName = updateInfo.versionName,
                                downloadUrl = updateInfo.downloadUrl,
                                forceUpdate = updateInfo.forceUpdate
                            )
                            lastNotifiedVersion = updateInfo.versionName
                            
                            // Lưu vào SharedPreferences để nhớ đã thông báo
                            prefs.edit().putString("last_notified_version", updateInfo.versionName).apply()
                        }
                    }
                }
            }
            
            // Lên lịch check lại sau 3 giây
            handler.postDelayed(this, checkInterval)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Lấy version đã thông báo lần cuối
        lastNotifiedVersion = prefs.getString("last_notified_version", "") ?: ""
        
        // Bắt đầu check update định kỳ
        handler.post(checkRunnable)
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
    }
    
    companion object {
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
}

