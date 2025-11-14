package com.txahub.vn

import android.app.Application
import android.os.Build
import android.util.Log

class TXAApplication : Application() {
    
    private lateinit var logWriter: LogWriter
    
    override fun onCreate() {
        super.onCreate()
        
        logWriter = LogWriter(this)
        
        // Setup global exception handler để bắt tất cả crashes
        setupGlobalExceptionHandler()
        
        // Tạo app shortcuts
        ShortcutHelper(this).createShortcuts()
        
        // Khởi tạo sound mặc định của app từ raw resource
        initializeDefaultNotificationSound()
        
        // Cập nhật notification channel sound để đảm bảo sound đúng ngay từ đầu
        NotificationHelper(this).updateNotificationChannelSound()
        
        // Log app start
        logWriter.writeAppLog("App Started", "Application onCreate", Log.INFO)
    }
    
    /**
     * Setup global exception handler để bắt tất cả uncaught exceptions
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                // Log crash với đầy đủ thông tin
                val crashInfo = buildString {
                    appendLine("=== CRASH REPORT ===")
                    appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${exception.javaClass.name}")
                    appendLine("Message: ${exception.message}")
                    appendLine("Stack Trace:")
                    exception.stackTraceToString().split("\n").forEach { line ->
                        appendLine("  $line")
                    }
                    appendLine("Device Info:")
                    appendLine("  Model: ${Build.MODEL}")
                    appendLine("  Manufacturer: ${Build.MANUFACTURER}")
                    appendLine("  Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("  App Version: ${getAppVersion()}")
                    appendLine("=== END CRASH REPORT ===")
                }
                
                logWriter.writeAppLog(crashInfo, "CRASH", Log.ERROR)
                logWriter.writeCrashLog(crashInfo)
                
                // Ghi log vào Android Logcat
                Log.e("TXAApp", "CRASH: ${exception.message}", exception)
                
            } catch (e: Exception) {
                // Nếu log crash cũng bị lỗi, ghi vào logcat
                Log.e("TXAApp", "Failed to log crash", e)
            }
            
            // Gọi default handler để hiển thị crash dialog
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
    
    /**
     * Lấy version của app
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Khởi tạo sound mặc định của app từ raw resource
     */
    private fun initializeDefaultNotificationSound() {
        try {
            // Lấy resource ID của file chuong.mp3 trong res/raw/
            val resourceId = resources.getIdentifier("chuong", "raw", packageName)
            if (resourceId != 0) {
                val soundManager = NotificationSoundManager(this)
                soundManager.initializeDefaultAppSound(resourceId, "chuong.mp3")
            } else {
                Log.w("TXAApplication", "Default sound file chuong.mp3 not found in res/raw/")
            }
        } catch (e: Exception) {
            Log.e("TXAApplication", "Error initializing default notification sound", e)
        }
    }
}

