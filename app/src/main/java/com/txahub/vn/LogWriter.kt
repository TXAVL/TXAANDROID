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
        private const val LOG_FILE_PREFIX = "TXAAPP_api_"
        private const val LOG_FILE_EXTENSION = ".txa"
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
            val fileName = "${LOG_FILE_PREFIX}${timestamp}${LOG_FILE_EXTENSION}"
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
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ sử dụng app-specific directory
            File(context.getExternalFilesDir(null), LOG_FOLDER)
        } else {
            // Android 10 trở xuống
            File(Environment.getExternalStorageDirectory(), LOG_FOLDER)
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
                file.isFile && file.name.startsWith(LOG_FILE_PREFIX) && file.name.endsWith(LOG_FILE_EXTENSION)
            }
            
            return logFiles?.maxByOrNull { it.lastModified() }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Lấy đường dẫn đầy đủ của folder log (để hiển thị cho người dùng)
     */
    fun getLogFolderPath(): String {
        return getLogFolder().absolutePath
    }
}

