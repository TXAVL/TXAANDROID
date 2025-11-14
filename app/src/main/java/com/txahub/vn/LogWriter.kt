package com.txahub.vn

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class LogWriter(private val context: Context) {
    
    companion object {
        private const val LOG_FOLDER = "TXAAPP"
        private const val LOG_FILE_PREFIX_API = "TXAAPP_api_"
        private const val LOG_FILE_PREFIX_APP = "TXAAPP_app_"
        private const val LOG_FILE_PREFIX_CRASH = "TXAAPP_crash_"
        private const val LOG_FILE_PREFIX_UPDATE_CHECK = "TXAAPP_updatecheck_"
        private const val LOG_FILE_EXTENSION = ".txa"
        private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024 // 5MB per file
        private const val MAX_LOG_FILES = 20 // Giữ tối đa 20 file log
    }
    
    /**
     * Kiểm tra quyền ghi file
     */
    fun hasWritePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ cần kiểm tra MANAGE_EXTERNAL_STORAGE hoặc sử dụng scoped storage
            Environment.isExternalStorageManager()
        } else {
            // Android 10 trở xuống
            val writePermission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(context, writePermission) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Ghi log API response vào file
     */
    fun writeApiLog(response: String, apiUrl: String = "") {
        // Kiểm tra xem log API có được bật không
        val logSettings = LogSettingsManager(context)
        if (!logSettings.isApiLogEnabled()) {
            return
        }
        
        if (!hasWritePermission()) {
            // Hiển thị toast nếu chưa cấp quyền
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Chưa cấp quyền ghi file. Không thể ghi log.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }
        
        try {
            // Tạo folder nếu chưa có
            val logFolder = getLogFolder()
            if (!logFolder.exists()) {
                logFolder.mkdirs()
            }
            
            // Tạo tên file theo format: TXAAPP_api_(time hiện tại).txa
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${LOG_FILE_PREFIX_API}${timestamp}${LOG_FILE_EXTENSION}"
            val logFile = File(logFolder, fileName)
            
            // Ghi log
            FileWriter(logFile, true).use { writer ->
                writer.append("=== API Log ===\n")
                writer.append("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                if (apiUrl.isNotEmpty()) {
                    writer.append("URL: $apiUrl\n")
                }
                writer.append("Response:\n$response\n")
                writer.append("=== End Log ===\n\n")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Lỗi khi ghi log: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Lấy folder log
     */
    fun getLogFolder(): File {
        return when {
            // Android 15+ (API 35+) - Lưu vào Downloads/TXAAPP/
            android.os.Build.VERSION.SDK_INT >= 35 -> {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(downloadsDir, LOG_FOLDER)
            }
            // Android 11-14 (API 30-34) - Sử dụng app-specific directory
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R -> {
                File(context.getExternalFilesDir(null), LOG_FOLDER)
            }
            // Android 10 trở xuống - Lưu vào external storage root
            else -> {
                File(Environment.getExternalStorageDirectory(), LOG_FOLDER)
            }
        }
    }
    
    /**
     * Lấy file log mới nhất
     */
    fun getLatestLogFile(): File? {
        try {
            val logFolder = getLogFolder()
            if (!logFolder.exists() || !logFolder.isDirectory) {
                return null
            }
            
            val logFiles = logFolder.listFiles { file ->
                file.isFile && (file.name.startsWith(LOG_FILE_PREFIX_API) || 
                               file.name.startsWith(LOG_FILE_PREFIX_APP) || 
                               file.name.startsWith(LOG_FILE_PREFIX_CRASH)) && 
                               file.name.endsWith(LOG_FILE_EXTENSION)
            }
            
            return logFiles?.maxByOrNull { it.lastModified() }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Lấy file log app mới nhất
     */
    fun getLatestAppLogFile(): File? {
        return try {
            val logFolder = getLogFolder()
            if (!logFolder.exists() || !logFolder.isDirectory) {
                return null
            }
            
            val logFiles = logFolder.listFiles { file ->
                file.isFile && file.name.startsWith(LOG_FILE_PREFIX_APP) && 
                               file.name.endsWith(LOG_FILE_EXTENSION)
            }
            
            logFiles?.maxByOrNull { it.lastModified() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Lấy file log crash mới nhất
     */
    fun getLatestCrashLogFile(): File? {
        return try {
            val logFolder = getLogFolder()
            if (!logFolder.exists() || !logFolder.isDirectory) {
                return null
            }
            
            val logFiles = logFolder.listFiles { file ->
                file.isFile && file.name.startsWith(LOG_FILE_PREFIX_CRASH) && 
                               file.name.endsWith(LOG_FILE_EXTENSION)
            }
            
            logFiles?.maxByOrNull { it.lastModified() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Lấy file log API mới nhất
     */
    fun getLatestApiLogFile(): File? {
        return try {
            val logFolder = getLogFolder()
            if (!logFolder.exists() || !logFolder.isDirectory) {
                return null
            }
            
            val logFiles = logFolder.listFiles { file ->
                file.isFile && file.name.startsWith(LOG_FILE_PREFIX_API) && 
                               file.name.endsWith(LOG_FILE_EXTENSION)
            }
            
            logFiles?.maxByOrNull { it.lastModified() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Ghi log cho UpdateCheckService (theo ngày)
     */
    fun writeUpdateCheckLog(message: String, level: String = "INFO") {
        // Kiểm tra xem log Update Check có được bật không
        val logSettings = LogSettingsManager(context)
        if (!logSettings.isUpdateCheckLogEnabled()) {
            return
        }
        
        if (!hasWritePermission()) {
            return
        }
        
        try {
            val logFolder = getLogFolder()
            if (!logFolder.exists()) {
                logFolder.mkdirs()
            }
            
            // Tạo tên file theo format: TXAAPP_updatecheck_YYYYMMDD.txa (1 file mỗi ngày)
            val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            val dateStr = dateFormat.format(java.util.Date())
            val fileName = "${LOG_FILE_PREFIX_UPDATE_CHECK}${dateStr}${LOG_FILE_EXTENSION}"
            val logFile = File(logFolder, fileName)
            
            // Kiểm tra kích thước file, nếu quá lớn thì tạo file mới
            val actualFile = if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                File(logFolder, "${LOG_FILE_PREFIX_UPDATE_CHECK}${dateStr}_${timestamp}${LOG_FILE_EXTENSION}")
            } else {
                logFile
            }
            
            FileWriter(actualFile, true).use { writer ->
                writer.append("[$level] ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())} $message\n")
            }
            
            // Dọn dẹp file log cũ
            cleanupOldLogFiles(logFolder)
            
        } catch (e: Exception) {
            android.util.Log.e("LogWriter", "Failed to write update check log", e)
        }
    }
    
    /**
     * Lấy file log UpdateCheck mới nhất
     */
    fun getLatestUpdateCheckLogFile(): File? {
        return try {
            val logFolder = getLogFolder()
            if (!logFolder.exists() || !logFolder.isDirectory) {
                return null
            }
            
            val logFiles = logFolder.listFiles { file ->
                file.isFile && file.name.startsWith(LOG_FILE_PREFIX_UPDATE_CHECK) && 
                               file.name.endsWith(LOG_FILE_EXTENSION)
            }
            
            logFiles?.maxByOrNull { it.lastModified() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Lấy đường dẫn đầy đủ của folder log (để hiển thị cho người dùng)
     */
    fun getLogFolderPath(): String {
        return getLogFolder().absolutePath
    }
    
    /**
     * Ghi log ứng dụng (exceptions, errors, info, warnings)
     */
    fun writeAppLog(message: String, tag: String = "APP", level: Int = android.util.Log.INFO) {
        // Kiểm tra xem log App có được bật không
        val logSettings = LogSettingsManager(context)
        if (!logSettings.isAppLogEnabled()) {
            return
        }
        
        if (!hasWritePermission()) {
            return
        }
        
        try {
            val logFolder = getLogFolder()
            if (!logFolder.exists()) {
                logFolder.mkdirs()
            }
            
            // Tạo tên file theo format: TXAAPP_app_YYYYMMDD.txa (1 file mỗi ngày)
            val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            val dateStr = dateFormat.format(java.util.Date())
            val fileName = "${LOG_FILE_PREFIX_APP}${dateStr}${LOG_FILE_EXTENSION}"
            val logFile = File(logFolder, fileName)
            
            // Kiểm tra kích thước file, nếu quá lớn thì tạo file mới
            val actualFile = if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                File(logFolder, "${LOG_FILE_PREFIX_APP}${dateStr}_${timestamp}${LOG_FILE_EXTENSION}")
            } else {
                logFile
            }
            
            val levelStr = when (level) {
                android.util.Log.ERROR -> "ERROR"
                android.util.Log.WARN -> "WARN"
                android.util.Log.INFO -> "INFO"
                android.util.Log.DEBUG -> "DEBUG"
                android.util.Log.VERBOSE -> "VERBOSE"
                else -> "UNKNOWN"
            }
            
            FileWriter(actualFile, true).use { writer ->
                writer.append("[$levelStr] ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())} [$tag] $message\n")
            }
            
            // Dọn dẹp file log cũ
            cleanupOldLogFiles(logFolder)
            
        } catch (e: Exception) {
            android.util.Log.e("LogWriter", "Failed to write app log", e)
        }
    }
    
    /**
     * Ghi log crash riêng
     */
    fun writeCrashLog(crashInfo: String) {
        // Kiểm tra xem log Crash có được bật không
        val logSettings = LogSettingsManager(context)
        if (!logSettings.isCrashLogEnabled()) {
            return
        }
        
        if (!hasWritePermission()) {
            return
        }
        
        try {
            val logFolder = getLogFolder()
            if (!logFolder.exists()) {
                logFolder.mkdirs()
            }
            
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val fileName = "${LOG_FILE_PREFIX_CRASH}${timestamp}${LOG_FILE_EXTENSION}"
            val logFile = File(logFolder, fileName)
            
            FileWriter(logFile, true).use { writer ->
                writer.append(crashInfo)
                writer.append("\n\n")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("LogWriter", "Failed to write crash log", e)
        }
    }
    
    /**
     * Dọn dẹp file log cũ, chỉ giữ lại MAX_LOG_FILES file mới nhất
     */
    private fun cleanupOldLogFiles(logFolder: File) {
        try {
            val allLogFiles = logFolder.listFiles { file ->
                file.isFile && file.name.endsWith(LOG_FILE_EXTENSION)
            } ?: return
            
            if (allLogFiles.size > MAX_LOG_FILES) {
                // Sắp xếp theo thời gian sửa đổi, xóa file cũ nhất
                allLogFiles.sortBy { it.lastModified() }
                val filesToDelete = allLogFiles.take(allLogFiles.size - MAX_LOG_FILES)
                filesToDelete.forEach { it.delete() }
            }
        } catch (e: Exception) {
            android.util.Log.e("LogWriter", "Failed to cleanup old log files", e)
        }
    }
    
    /**
     * Lấy tất cả file log
     */
    fun getAllLogFiles(): List<File> {
        return try {
            val logFolder = getLogFolder()
            if (!logFolder.exists() || !logFolder.isDirectory) {
                return emptyList()
            }
            
            logFolder.listFiles { file ->
                file.isFile && file.name.endsWith(LOG_FILE_EXTENSION)
            }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

