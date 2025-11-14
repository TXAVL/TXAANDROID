package com.txahub.vn

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.Normalizer
import java.util.regex.Pattern

class NotificationSoundManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "txahub_notification_sound_prefs"
        private const val KEY_SOUND_TYPE = "sound_type" // "default", "system", "custom"
        private const val KEY_CUSTOM_SOUND_URI = "custom_sound_uri"
        private const val KEY_DEFAULT_APP_SOUND_URI = "default_app_sound_uri" // Sound mặc định của app
        private const val SOUND_FOLDER = "notification_sounds" // Folder cho sound mặc định của app
        private const val CUSTOM_SOUND_FOLDER = "notification_sounds_custom" // Folder cho sound tùy chỉnh
        
        // Giới hạn file nhạc chuông
        const val MAX_SOUND_DURATION_MS = 45_000L // 45 giây
        private const val MAX_SOUND_SIZE_BYTES = 5 * 1024 * 1024 // 5MB
        private val ALLOWED_MIME_TYPES = listOf(
            "audio/mpeg",
            "audio/mp3",
            "audio/wav"
        )
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Chuyển đổi chuỗi có dấu sang không dấu (a-z, 0-9, A-Z)
     * Ví dụ: "chuông.mp3" -> "chuong.mp3", "nhạc êm dịu.mp3" -> "nhac_em_diu.mp3"
     */
    private fun removeVietnameseAccents(text: String): String {
        // Chuyển đổi Unicode NFD (Normalization Form Decomposed)
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        // Loại bỏ các ký tự dấu (diacritical marks)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        val withoutAccents = pattern.matcher(normalized).replaceAll("")
        // Thay thế khoảng trắng và ký tự đặc biệt bằng dấu gạch dưới
        return withoutAccents
            .replace(" ", "_")
            .replace("[^a-zA-Z0-9._]".toRegex(), "_")
            .replace("_{2,}".toRegex(), "_") // Loại bỏ nhiều dấu gạch dưới liên tiếp
            .trim('_')
    }
    
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
    fun setCustomSoundUri(uri: Uri?) {
        if (uri != null) {
            prefs.edit().putString(KEY_CUSTOM_SOUND_URI, uri.toString()).apply()
        } else {
            prefs.edit().remove(KEY_CUSTOM_SOUND_URI).apply()
        }
    }
    
    /**
     * Lấy URI của sound từ raw resource (chuong.mp3)
     * Nếu file đã được copy vào external storage, trả về URI từ đó
     * Nếu chưa, trả về null
     */
    fun getRawSoundUri(): Uri? {
        // Kiểm tra xem file đã được copy vào external storage chưa
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val soundFolder = File(baseDir, SOUND_FOLDER)
        val soundFile = File(soundFolder, "chuong.mp3")
        
        if (soundFile.exists()) {
            return try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    soundFile
                )
            } catch (e: Exception) {
                android.util.Log.e("NotificationSoundManager", "Error getting URI from FileProvider", e)
                Uri.fromFile(soundFile)
            }
        }
        
        // Nếu chưa có, thử lấy từ raw resource trực tiếp
        return try {
            val resourceId = context.resources.getIdentifier("chuong", "raw", context.packageName)
            if (resourceId != 0) {
                Uri.parse("android.resource://${context.packageName}/$resourceId")
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error getting raw sound URI", e)
            null
        }
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
            "system_txa" -> {
                // Sound TXA Hub từ raw resource
                getRawSoundUri() ?: Settings.System.DEFAULT_NOTIFICATION_URI
            }
            "custom" -> getCustomSoundUri()
            else -> Settings.System.DEFAULT_NOTIFICATION_URI
        }
    }
    
    /**
     * Lấy URI của sound mặc định app (nếu đã set)
     * Đọc tất cả file .mp3 và .wav trong folder notification_sounds
     * Nếu có nhiều file, random chọn 1 file mỗi lần gọi
     * Hỗ trợ file có tên có dấu (tự động chuyển sang không dấu khi copy từ raw resource)
     * 
     * Lưu ý: File được lưu trong external storage để có thể truy cập từ máy tính khi cắm cáp USB
     */
    fun getDefaultAppSoundUri(): Uri? {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir // Fallback về internal nếu external không khả dụng
        val soundFolder = File(baseDir, SOUND_FOLDER)
        if (!soundFolder.exists() || !soundFolder.isDirectory) {
            return null
        }
        
        // Lấy tất cả file .mp3 và .wav trong folder (kể cả file có tên có dấu)
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
        
        // Sử dụng FileProvider để tạo content:// URI (bắt buộc từ Android 7.0+)
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                selectedFile
            )
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error getting URI from FileProvider", e)
            // Fallback: thử dùng Uri.fromFile (có thể không hoạt động trên Android 7.0+)
            Uri.fromFile(selectedFile)
        }
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
                val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                val soundFolder = File(baseDir, SOUND_FOLDER)
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
            "system_txa" -> {
                // Nhạc chuông TXA Hub từ raw resource
                "TXA Hub"
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
     * Copy file nhạc chuông tùy chỉnh vào external storage của app (folder riêng)
     * Có thể truy cập từ máy tính khi cắm cáp USB tại: Android/data/com.txahub.vn.app/files/notification_sounds_custom/
     */
    fun copySoundToInternalStorage(uri: Uri): Uri? {
        return try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val soundFolder = File(baseDir, CUSTOM_SOUND_FOLDER)
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
            
            // Trả về URI của file đã copy (dùng FileProvider)
            try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    destFile
                )
            } catch (e: Exception) {
                android.util.Log.e("NotificationSoundManager", "Error getting URI from FileProvider", e)
                // Fallback
                Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error copying sound file", e)
            null
        }
    }
    
    /**
     * Copy file nhạc chuông từ raw resource vào external storage
     * @param rawResourceId ID của file trong res/raw/ (ví dụ: R.raw.chuong)
     * @param fileName Tên file khi lưu vào external storage (tự động chuyển có dấu sang không dấu)
     * @return URI của file đã copy hoặc null nếu lỗi
     * 
     * Lưu ý: File được lưu trong external storage để có thể truy cập từ máy tính khi cắm cáp USB
     */
    fun copyRawSoundToInternalStorage(rawResourceId: Int, fileName: String): Uri? {
        return try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val soundFolder = File(baseDir, SOUND_FOLDER)
            if (!soundFolder.exists()) {
                soundFolder.mkdirs()
            }
            
            // Tự động chuyển đổi tên file có dấu sang không dấu
            val normalizedFileName = removeVietnameseAccents(fileName)
            val destFile = File(soundFolder, normalizedFileName)
            
            // Nếu file đã tồn tại, không copy lại
            if (destFile.exists()) {
                return try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        destFile
                    )
                } catch (e: Exception) {
                    android.util.Log.e("NotificationSoundManager", "Error getting URI from FileProvider", e)
                    Uri.fromFile(destFile)
                }
            }
            
            // Copy từ raw resource
            val inputStream = context.resources.openRawResource(rawResourceId)
            val outputStream = FileOutputStream(destFile)
            
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            // Trả về URI của file đã copy (dùng FileProvider)
            try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    destFile
                )
            } catch (e: Exception) {
                android.util.Log.e("NotificationSoundManager", "Error getting URI from FileProvider", e)
                // Fallback
                Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error copying raw sound file", e)
            null
        }
    }
    
    /**
     * Copy file nhạc chuông vào folder default của app (không phải custom)
     * Lưu trong external storage để có thể truy cập từ máy tính khi cắm cáp USB
     */
    fun copySoundToDefaultFolder(uri: Uri): Uri? {
        return try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val soundFolder = File(baseDir, SOUND_FOLDER)
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
            
            // Trả về URI của file đã copy (dùng FileProvider)
            try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    destFile
                )
            } catch (e: Exception) {
                android.util.Log.e("NotificationSoundManager", "Error getting URI from FileProvider", e)
                // Fallback
                Uri.fromFile(destFile)
            }
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
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val soundFolder = File(baseDir, SOUND_FOLDER)
            if (soundFolder.exists() && soundFolder.isDirectory) {
                soundFolder.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error clearing default app sounds", e)
        }
    }
    
    /**
     * Khởi tạo sound mặc định của app từ raw resource (chỉ chạy lần đầu)
     * 
     * Lưu ý: Để thêm nhiều file cho random, đặt file trong res/raw/ với tên hợp lệ:
     * - Chỉ chứa a-z, 0-9, A-Z, dấu gạch dưới (ví dụ: chuong.mp3, sound1.mp3, nhac_em_diu.mp3)
     * - Nếu fileName có dấu, sẽ tự động chuyển sang không dấu khi copy vào external storage
     * - Sau khi copy, getDefaultAppSoundUri() sẽ tự động random chọn 1 file nếu có nhiều file
     * - File được lưu trong external storage: Android/data/com.txahub.vn.app/files/notification_sounds/
     *   Có thể truy cập từ máy tính khi cắm cáp USB
     */
    fun initializeDefaultAppSound(rawResourceId: Int, fileName: String) {
        // Chỉ setup nếu chưa có sound mặc định (folder rỗng)
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val soundFolder = File(baseDir, SOUND_FOLDER)
        val soundFiles = soundFolder.listFiles { _, name ->
            name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true)
        }?.filter { it.isFile } ?: emptyList()
        
        if (soundFiles.isEmpty()) {
            val soundUri = copyRawSoundToInternalStorage(rawResourceId, fileName)
            if (soundUri != null) {
                android.util.Log.d("NotificationSoundManager", "Default app sound initialized: $soundUri")
            }
        }
        // Không gọi addAppSoundToMediaStore ở đây nữa, sẽ gọi addAllAppSoundsToMediaStore() sau khi khởi tạo tất cả file
    }
    
    /**
     * Xóa tất cả entry của app trong MediaStore (bắt đầu bằng "TXA Hub")
     */
    private fun removeAllAppSoundsFromMediaStore() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val deleted = context.contentResolver.delete(
                    collection,
                    "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?",
                    arrayOf("TXA Hub%")
                )
                android.util.Log.d("NotificationSoundManager", "Removed $deleted app sounds from MediaStore")
            } else {
                // Android 9-: Xóa file trong thư mục Notifications
                val notificationsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS)
                if (notificationsDir.exists() && notificationsDir.isDirectory) {
                    notificationsDir.listFiles { _, name ->
                        name.startsWith("TXA Hub", ignoreCase = true)
                    }?.forEach { it.delete() }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error removing app sounds from MediaStore", e)
        }
    }
    
    /**
     * Thêm TẤT CẢ nhạc chuông của app vào MediaStore
     * Mỗi file sẽ có tên riêng: "TXA Hub - [tên file]" (ví dụ: "TXA Hub - chuong", "TXA Hub - onlol")
     */
    fun addAllAppSoundsToMediaStore() {
        try {
            // Xóa tất cả entry cũ trước
            removeAllAppSoundsFromMediaStore()
            
            // Đảm bảo file đã được copy vào external storage
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val soundFolder = File(baseDir, SOUND_FOLDER)
            
            if (!soundFolder.exists() || !soundFolder.isDirectory) {
                android.util.Log.w("NotificationSoundManager", "Sound folder not found: ${soundFolder.absolutePath}")
                return
            }
            
            // Tìm TẤT CẢ file trong folder
            val soundFiles = soundFolder.listFiles { _, name ->
                name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true)
            }?.filter { it.isFile } ?: emptyList()
            
            if (soundFiles.isEmpty()) {
                android.util.Log.w("NotificationSoundManager", "No sound files found in folder: ${soundFolder.absolutePath}")
                return
            }
            
            android.util.Log.d("NotificationSoundManager", "Adding ${soundFiles.size} sounds to MediaStore")
            
            // Thêm từng file vào MediaStore với tên riêng
            for (soundFile in soundFiles) {
                val fileNameWithoutExt = soundFile.nameWithoutExtension
                val displayName = "TXA Hub - $fileNameWithoutExt"
                addSingleSoundToMediaStore(soundFile, displayName)
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error adding all app sounds to MediaStore", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Thêm một file nhạc chuông vào MediaStore
     */
    private fun addSingleSoundToMediaStore(soundFile: File, displayName: String): Uri? {
        return try {
            android.util.Log.d("NotificationSoundManager", "Adding sound to MediaStore: ${soundFile.absolutePath}, displayName: $displayName")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ sử dụng MediaStore API
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.TITLE, displayName)
                    put(MediaStore.Audio.Media.MIME_TYPE, if (soundFile.name.endsWith(".wav", ignoreCase = true)) "audio/wav" else "audio/mpeg")
                    put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
                    put(MediaStore.Audio.Media.IS_RINGTONE, 0)
                    put(MediaStore.Audio.Media.IS_ALARM, 0)
                    put(MediaStore.Audio.Media.IS_MUSIC, 0)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS)
                }
                
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                
                // Kiểm tra xem đã có file với tên này chưa
                val existingUri = context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Audio.Media._ID),
                    "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                    arrayOf(displayName),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        android.content.ContentUris.withAppendedId(collection, id)
                    } else null
                }
                
                val mediaStoreUri = if (existingUri != null) {
                    // Cập nhật file đã tồn tại
                    context.contentResolver.update(existingUri, contentValues, null, null)
                    existingUri
                } else {
                    // Tạo mới
                    context.contentResolver.insert(collection, contentValues)
                }
                
                if (mediaStoreUri != null) {
                    // Copy file vào MediaStore
                    context.contentResolver.openOutputStream(mediaStoreUri)?.use { outputStream ->
                        soundFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    android.util.Log.d("NotificationSoundManager", "Added app sound to MediaStore: $mediaStoreUri")
                    return mediaStoreUri
                }
            } else {
                // Android 9-: Copy vào thư mục Notifications và scan media
                val notificationsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS)
                if (!notificationsDir.exists()) {
                    notificationsDir.mkdirs()
                }
                
                val extension = if (soundFile.name.endsWith(".wav", ignoreCase = true)) ".wav" else ".mp3"
                val destFile = File(notificationsDir, "$displayName$extension")
                soundFile.copyTo(destFile, overwrite = true)
                
                // Scan media để hệ thống nhận diện
                val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = Uri.fromFile(destFile)
                }
                context.sendBroadcast(mediaScanIntent)
                
                android.util.Log.d("NotificationSoundManager", "Added app sound to notifications folder: ${destFile.absolutePath}")
                return Uri.fromFile(destFile)
            }
            
            null
        } catch (e: Exception) {
            android.util.Log.e("NotificationSoundManager", "Error adding sound to MediaStore: ${soundFile.name}", e)
            null
        }
    }
    
    /**
     * Thêm nhạc chuông của app vào MediaStore để xuất hiện trong RingtoneManager
     * @deprecated Sử dụng addAllAppSoundsToMediaStore() thay thế
     */
    @Deprecated("Use addAllAppSoundsToMediaStore() instead")
    fun addAppSoundToMediaStore(sourceUri: Uri?, displayName: String): Uri? {
        // Gọi addAllAppSoundsToMediaStore để thêm tất cả file
        addAllAppSoundsToMediaStore()
        return null
    }
}

