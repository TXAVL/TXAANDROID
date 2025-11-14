package com.txahub.vn

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class NotificationSoundManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "txahub_notification_sound_prefs"
        private const val KEY_SOUND_TYPE = "sound_type" // "default", "system", "custom"
        private const val KEY_CUSTOM_SOUND_URI = "custom_sound_uri"
        private const val KEY_DEFAULT_APP_SOUND_URI = "default_app_sound_uri" // Sound mặc định của app
        private const val SOUND_FOLDER = "notification_sounds" // Folder cho sound mặc định của app
        private const val CUSTOM_SOUND_FOLDER = "notification_sounds_custom" // Folder cho sound tùy chỉnh
        
        // Giới hạn file nhạc chuông
        const val MAX_SOUND_DURATION_MS = 5000L // 5 giây
        private const val MAX_SOUND_SIZE_BYTES = 500 * 1024 // 500KB
        private val ALLOWED_MIME_TYPES = listOf(
            "audio/mpeg",
            "audio/mp3",
            "audio/wav"
        )
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Lấy loại nhạc chuông hiện tại
     */
    fun getSoundType(): String {
        return prefs.getString(KEY_SOUND_TYPE, "default") ?: "default"
    }
    
    /**
     * Lưu loại nhạc chuông
     */
    fun setSoundType(type: String) {
        prefs.edit().putString(KEY_SOUND_TYPE, type).apply()
    }
    
    /**
     * Lấy URI của custom sound
     */
    fun getCustomSoundUri(): Uri? {
        val uriString = prefs.getString(KEY_CUSTOM_SOUND_URI, null)
        return uriString?.let { Uri.parse(it) }
    }
    
    /**
     * Lưu URI của custom sound
     */
    fun setCustomSoundUri(uri: Uri) {
        prefs.edit().putString(KEY_CUSTOM_SOUND_URI, uri.toString()).apply()
    }
    
    /**
     * Lấy URI của nhạc chuông để sử dụng trong notification
     */
    fun getNotificationSoundUri(): Uri? {
        return when (getSoundType()) {
            "default" -> {
                // Sử dụng sound mặc định của app (nếu đã set) hoặc sound hệ thống
                getDefaultAppSoundUri() ?: Settings.System.DEFAULT_NOTIFICATION_URI
            }
            "system" -> Settings.System.DEFAULT_NOTIFICATION_URI
            "custom" -> getCustomSoundUri()
            else -> Settings.System.DEFAULT_NOTIFICATION_URI
        }
    }
    
    /**
     * Lấy URI của sound mặc định app (nếu đã set)
     * Nếu có nhiều file trong folder, random chọn 1 file
     */
    fun getDefaultAppSoundUri(): Uri? {
        val soundFolder = File(context.filesDir, SOUND_FOLDER)
        if (!soundFolder.exists() || !soundFolder.isDirectory) {
            return null
        }
        
        // Lấy tất cả file trong folder
        val soundFiles = soundFolder.listFiles { _, name ->
            name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true)
        }?.filter { it.isFile } ?: emptyList()
        
        if (soundFiles.isEmpty()) {
            return null
        }
        
        // Nếu có nhiều hơn 1 file, random chọn
        val selectedFile = if (soundFiles.size > 1) {
            soundFiles.random()
        } else {
            soundFiles[0]
        }
        
        return Uri.fromFile(selectedFile)
    }
    
    /**
     * Set sound mặc định cho app
     */
    fun setDefaultAppSoundUri(uri: Uri?) {
        if (uri != null) {
            prefs.edit().putString(KEY_DEFAULT_APP_SOUND_URI, uri.toString()).apply()
        } else {
            prefs.edit().remove(KEY_DEFAULT_APP_SOUND_URI).apply()
        }
    }
    
    /**
     * Lấy tên hiển thị của nhạc chuông hiện tại
     */
    fun getSoundDisplayName(): String {
        return when (getSoundType()) {
            "default" -> {
                val soundFolder = File(context.filesDir, SOUND_FOLDER)
                val soundFiles = soundFolder.listFiles { _, name ->
                    name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true)
                }?.filter { it.isFile } ?: emptyList()
                
                if (soundFiles.isNotEmpty()) {
                    val count = soundFiles.size
                    if (count > 1) {
                        "Nhạc chuông mặc định của app (${count} file - random)"
                    } else {
                        val fileName = soundFiles[0].name
                        "Nhạc chuông mặc định của app ($fileName)"
                    }
                } else {
                    "Nhạc chuông mặc định của app (hệ thống)"
                }
            }
            "system" -> {
                // Lấy tên nhạc chuông hệ thống (khó lấy chính xác, dùng tên chung)
                "Nhạc chuông hệ thống"
            }
            "custom" -> {
                val uri = getCustomSoundUri()
                if (uri != null) {
                    // Lấy tên file từ URI
                    val fileName = uri.lastPathSegment ?: "Tùy chỉnh"
                    fileName.substringAfterLast("/", fileName)
                } else {
                    "Tùy chỉnh"
                }
            }
            else -> "Không xác định"
        }
    }
    
    /**
     * Validate file nhạc chuông trước khi lưu
     * @return Triple<Boolean, String, Long> - (isValid, errorMessage, durationMs)
     * Nếu file quá dài, vẫn trả về isValid=true nhưng durationMs > MAX_SOUND_DURATION_MS
     */
    fun validateSoundFile(uri: Uri): Triple<Boolean, String, Long> {
        return try {
            // Kiểm tra MIME type
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
                return Triple(false, "File không phải định dạng âm thanh hợp lệ. Chỉ chấp nhận: MP3, WAV", 0L)
            }
            
            // Kiểm tra kích thước file
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return Triple(false, "Không thể đọc file", 0L)
            }
            
            // Đọc toàn bộ file để lấy kích thước chính xác
            var fileSize = 0L
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                fileSize += bytesRead
            }
            inputStream.close()
            
            if (fileSize > MAX_SOUND_SIZE_BYTES) {
                return Triple(false, "File quá lớn. Kích thước tối đa: ${MAX_SOUND_SIZE_BYTES / 1024}KB", 0L)
            }
            
            // Lấy thời lượng (không reject nếu quá dài, chỉ trả về để xử lý cắt)
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            
            val durationMs = if (durationString != null) {
                durationString.toLongOrNull() ?: 0L
            } else {
                0L
            }
            
            // File hợp lệ, nhưng có thể cần cắt nếu quá dài
            Triple(true, "", durationMs)
        } catch (e: Exception) {
            Triple(false, "Lỗi khi kiểm tra file: ${e.message}", 0L)
        }
    }
    
    /**
     * Copy file nhạc chuông tùy chỉnh vào internal storage của app (folder riêng)
     */
    fun copySoundToInternalStorage(uri: Uri): Uri? {
        return try {
            val soundFolder = File(context.filesDir, CUSTOM_SOUND_FOLDER)
            if (!soundFolder.exists()) {
                soundFolder.mkdirs()
            }
            
            // Lấy extension từ URI hoặc mặc định là .mp3
            val originalFileName = uri.lastPathSegment ?: "custom_sound_${System.currentTimeMillis()}.mp3"
            val extension = if (originalFileName.contains(".")) {
                originalFileName.substringAfterLast(".")
            } else {
                "mp3"
            }
            val fileName = "custom_sound_${System.currentTimeMillis()}.$extension"
            val destFile = File(soundFolder, fileName)
            
            // Copy file
            val inputStream = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(destFile)
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            // Trả về URI của file đã copy
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error copying sound file", e)
            null
        }
    }
    
    /**
     * Copy file nhạc chuông từ raw resource vào internal storage
     * @param rawResourceId ID của file trong res/raw/ (ví dụ: R.raw.chuoog)
     * @param fileName Tên file khi lưu vào internal storage
     * @return URI của file đã copy hoặc null nếu lỗi
     */
    fun copyRawSoundToInternalStorage(rawResourceId: Int, fileName: String): Uri? {
        return try {
            val soundFolder = File(context.filesDir, SOUND_FOLDER)
            if (!soundFolder.exists()) {
                soundFolder.mkdirs()
            }
            
            val destFile = File(soundFolder, fileName)
            
            // Nếu file đã tồn tại, không copy lại
            if (destFile.exists()) {
                return Uri.fromFile(destFile)
            }
            
            // Copy từ raw resource
            val inputStream = context.resources.openRawResource(rawResourceId)
            val outputStream = FileOutputStream(destFile)
            
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            // Trả về URI của file đã copy
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error copying raw sound file", e)
            null
        }
    }
    
    /**
     * Copy file nhạc chuông vào folder default của app (không phải custom)
     */
    fun copySoundToDefaultFolder(uri: Uri): Uri? {
        return try {
            val soundFolder = File(context.filesDir, SOUND_FOLDER)
            if (!soundFolder.exists()) {
                soundFolder.mkdirs()
            }
            
            // Lấy extension từ URI hoặc mặc định là .mp3
            val originalFileName = uri.lastPathSegment ?: "default_sound_${System.currentTimeMillis()}.mp3"
            val extension = if (originalFileName.contains(".")) {
                originalFileName.substringAfterLast(".")
            } else {
                "mp3"
            }
            val fileName = "default_sound_${System.currentTimeMillis()}.$extension"
            val destFile = File(soundFolder, fileName)
            
            // Copy file
            val inputStream = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(destFile)
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            // Trả về URI của file đã copy
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error copying sound to default folder", e)
            null
        }
    }
    
    /**
     * Xóa tất cả file trong folder default app sounds
     */
    fun clearDefaultAppSounds() {
        try {
            val soundFolder = File(context.filesDir, SOUND_FOLDER)
            if (soundFolder.exists() && soundFolder.isDirectory) {
                soundFolder.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error clearing default app sounds", e)
        }
    }
    
    /**
     * Khởi tạo sound mặc định của app từ raw resource (chỉ chạy lần đầu)
     */
    fun initializeDefaultAppSound(rawResourceId: Int, fileName: String) {
        // Chỉ setup nếu chưa có sound mặc định (folder rỗng)
        val soundFolder = File(context.filesDir, SOUND_FOLDER)
        val soundFiles = soundFolder.listFiles { _, name ->
            name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true)
        }?.filter { it.isFile } ?: emptyList()
        
        if (soundFiles.isEmpty()) {
            val soundUri = copyRawSoundToInternalStorage(rawResourceId, fileName)
            if (soundUri != null) {
                android.util.Log.d("NotificationSoundManager", "Default app sound initialized: $soundUri")
            }
        }
    }
}

