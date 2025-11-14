package com.txahub.vn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var updateChecker: UpdateChecker
    private lateinit var layoutPermissions: LinearLayout
    private lateinit var tvVersionInfo: TextView
    private lateinit var btnCheckUpdate: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSpeedTest: Button
    private lateinit var btnOpenLogFile: Button
    private val logWriter = LogWriter(this)
    private lateinit var speedTest: SpeedTest
    private lateinit var tvNotificationSound: TextView
    private lateinit var btnChangeNotificationSound: Button
    private lateinit var soundManager: NotificationSoundManager
    private lateinit var audioTrimmer: AudioTrimmer
    private lateinit var logSettingsManager: LogSettingsManager
    private var selectedSoundUri: Uri? = null
    private var isPickingForDefault = false
    
    // Activity Result Launchers thay thế startActivityForResult
    private val pickSoundLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                if (isPickingForDefault) {
                    handleSoundFileForDefault(uri)
                } else {
                    handleSoundFile(uri)
                }
            }
        }
    }
    
    // Launcher cho RingtoneManager (chọn nhạc chuông hệ thống)
    private val pickRingtoneLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                soundManager.setSoundType("system")
                soundManager.setCustomSoundUri(null)
                // Lưu URI của ringtone đã chọn (nếu cần)
                loadNotificationSoundSettings()
                Toast.makeText(this, "Đã đặt nhạc chuông hệ thống", Toast.LENGTH_SHORT).show()
                NotificationHelper(this).updateNotificationChannelSound()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        updateChecker = UpdateChecker(this)
        speedTest = SpeedTest(this)
        soundManager = NotificationSoundManager(this)
        audioTrimmer = AudioTrimmer(this)
        logSettingsManager = LogSettingsManager(this)
        
        setupViews()
        loadVersionInfo()
        loadPermissions()
        loadNotificationSoundSettings()
        loadLogSettings()
    }
    
    private fun setupViews() {
        layoutPermissions = findViewById(R.id.layoutPermissions)
        tvVersionInfo = findViewById(R.id.tvVersionInfo)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        progressBar = findViewById(R.id.progressBar)
        btnSpeedTest = findViewById(R.id.btnSpeedTest)
        
        // Nút back
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        // Nút check update
        btnCheckUpdate.setOnClickListener {
            checkForUpdate()
        }
        
        // Nút speedtest
        btnSpeedTest.setOnClickListener {
            runSpeedTest()
        }
        
        // Nút mở file log (chỉ hiện khi đã cấp quyền)
        btnOpenLogFile = findViewById(R.id.btnOpenLogFile)
        updateLogFileButtonVisibility()
        btnOpenLogFile.setOnClickListener {
            openLogFile()
        }
        
        // Notification sound settings
        tvNotificationSound = findViewById(R.id.tvNotificationSound)
        btnChangeNotificationSound = findViewById(R.id.btnChangeNotificationSound)
        btnChangeNotificationSound.setOnClickListener {
            showNotificationSoundDialog()
        }
        
        // Log settings
        setupLogSettings()
    }
    
    /**
     * Setup log settings UI
     */
    private fun setupLogSettings() {
        val switchLogApi = findViewById<SwitchCompat>(R.id.switchLogApi)
        val switchLogApp = findViewById<SwitchCompat>(R.id.switchLogApp)
        val switchLogCrash = findViewById<SwitchCompat>(R.id.switchLogCrash)
        val switchLogUpdateCheck = findViewById<SwitchCompat>(R.id.switchLogUpdateCheck)
        val btnResetLogSettings = findViewById<Button>(R.id.btnResetLogSettings)
        
        // Lắng nghe thay đổi
        switchLogApi.setOnCheckedChangeListener { _, isChecked ->
            logSettingsManager.setApiLogEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Đã bật Log API" else "Đã tắt Log API", Toast.LENGTH_SHORT).show()
        }
        
        switchLogApp.setOnCheckedChangeListener { _, isChecked ->
            logSettingsManager.setAppLogEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Đã bật Log ứng dụng" else "Đã tắt Log ứng dụng", Toast.LENGTH_SHORT).show()
        }
        
        switchLogCrash.setOnCheckedChangeListener { _, isChecked ->
            logSettingsManager.setCrashLogEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Đã bật Log Crash" else "Đã tắt Log Crash", Toast.LENGTH_SHORT).show()
        }
        
        switchLogUpdateCheck.setOnCheckedChangeListener { _, isChecked ->
            logSettingsManager.setUpdateCheckLogEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Đã bật Log Update Check" else "Đã tắt Log Update Check", Toast.LENGTH_SHORT).show()
        }
        
        // Nút reset
        btnResetLogSettings.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Đặt lại mặc định")
                .setMessage("Bạn có chắc muốn đặt lại tất cả cài đặt log về mặc định (bật tất cả)?")
                .setPositiveButton("Đặt lại") { _, _ ->
                    logSettingsManager.resetToDefaults()
                    loadLogSettings()
                    Toast.makeText(this, "Đã đặt lại mặc định", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }
    
    /**
     * Load và hiển thị log settings
     */
    private fun loadLogSettings() {
        val switchLogApi = findViewById<SwitchCompat>(R.id.switchLogApi)
        val switchLogApp = findViewById<SwitchCompat>(R.id.switchLogApp)
        val switchLogCrash = findViewById<SwitchCompat>(R.id.switchLogCrash)
        val switchLogUpdateCheck = findViewById<SwitchCompat>(R.id.switchLogUpdateCheck)
        
        // Tắt listener tạm thời để tránh trigger khi set giá trị
        switchLogApi.setOnCheckedChangeListener(null)
        switchLogApp.setOnCheckedChangeListener(null)
        switchLogCrash.setOnCheckedChangeListener(null)
        switchLogUpdateCheck.setOnCheckedChangeListener(null)
        
        // Set giá trị
        switchLogApi.isChecked = logSettingsManager.isApiLogEnabled()
        switchLogApp.isChecked = logSettingsManager.isAppLogEnabled()
        switchLogCrash.isChecked = logSettingsManager.isCrashLogEnabled()
        switchLogUpdateCheck.isChecked = logSettingsManager.isUpdateCheckLogEnabled()
        
        // Bật lại listener
        switchLogApi.setOnCheckedChangeListener { _, isChecked ->
            logSettingsManager.setApiLogEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Đã bật Log API" else "Đã tắt Log API", Toast.LENGTH_SHORT).show()
        }
        
        switchLogApp.setOnCheckedChangeListener { _, isChecked ->
            logSettingsManager.setAppLogEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Đã bật Log ứng dụng" else "Đã tắt Log ứng dụng", Toast.LENGTH_SHORT).show()
        }
        
        switchLogCrash.setOnCheckedChangeListener { _, isChecked ->
            logSettingsManager.setCrashLogEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Đã bật Log Crash" else "Đã tắt Log Crash", Toast.LENGTH_SHORT).show()
        }
        
        switchLogUpdateCheck.setOnCheckedChangeListener { _, isChecked ->
            logSettingsManager.setUpdateCheckLogEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Đã bật Log Update Check" else "Đã tắt Log Update Check", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Cập nhật hiển thị nút mở file log (chỉ hiện khi đã cấp quyền)
     */
    private fun updateLogFileButtonVisibility() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        btnOpenLogFile.visibility = if (hasPermission) View.VISIBLE else View.GONE
    }
    
    /**
     * Mở file log - hỏi loại log và hiển thị trong modal view
     */
    private fun openLogFile() {
        // Hỏi người dùng muốn mở loại log nào
        AlertDialog.Builder(this)
            .setTitle("Chọn loại log")
            .setItems(arrayOf("App Log", "Crash Log", "API Log", "Update Check Log", "Log nhạc chuông")) { _, which ->
                when (which) {
                    0 -> showLogViewer("app")
                    1 -> showLogViewer("crash")
                    2 -> showLogViewer("api")
                    3 -> showLogViewer("updatecheck")
                    4 -> showSoundLogViewer()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    /**
     * Hiển thị log viewer trong dialog
     */
    private fun showLogViewer(logType: String) {
        try {
            val logFolder = logWriter.getLogFolder()
            if (!logFolder.exists() || !logFolder.isDirectory) {
                Toast.makeText(this, "Không tìm thấy thư mục log", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Tìm file log theo loại
            val logFile = when (logType) {
                "app" -> logWriter.getLatestAppLogFile()
                "crash" -> logWriter.getLatestCrashLogFile()
                "api" -> logWriter.getLatestApiLogFile()
                "updatecheck" -> logWriter.getLatestUpdateCheckLogFile()
                else -> null
            }
            
            if (logFile == null || !logFile.exists()) {
                val logTypeName = when (logType) {
                    "app" -> "ứng dụng"
                    "crash" -> "crash"
                    "api" -> "API"
                    "updatecheck" -> "Update Check"
                    else -> ""
                }
                Toast.makeText(this, "Không tìm thấy file log $logTypeName", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Đọc nội dung file
            val content = logFile.readText()
            val lines = content.lines()
            val lineCount = lines.size
            
            // Tạo dialog với custom view
            val dialogView = layoutInflater.inflate(R.layout.dialog_log_viewer, null)
            val tvLogTitle = dialogView.findViewById<TextView>(R.id.tvLogTitle)
            val tvLogContent = dialogView.findViewById<TextView>(R.id.tvLogContent)
            val tvLineCount = dialogView.findViewById<TextView>(R.id.tvLineCount)
            val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
            
            tvLogTitle.text = "File: ${logFile.name}"
            
            // Hiển thị nội dung với số dòng
            val contentWithLineNumbers = StringBuilder()
            lines.forEachIndexed { index, line ->
                contentWithLineNumbers.append("${index + 1}: $line\n")
            }
            tvLogContent.text = contentWithLineNumbers.toString()
            
            tvLineCount.text = "Tổng số dòng: $lineCount"
            
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
            
            btnClose.setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
            
            // Đặt kích thước dialog
            val window = dialog.window
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                (resources.displayMetrics.heightPixels * 0.8).toInt()
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi khi đọc file log: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Hiển thị log về nhạc chuông (lọc từ App Log và Update Check Log)
     */
    private fun showSoundLogViewer() {
        try {
            val logFolder = logWriter.getLogFolder()
            if (!logFolder.exists() || !logFolder.isDirectory) {
                Toast.makeText(this, "Không tìm thấy thư mục log", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Lấy tất cả file log App và Update Check
            val appLogFiles = logFolder.listFiles { file ->
                file.isFile && file.name.startsWith("TXAAPP_app_") && file.name.endsWith(".txa")
            }?.sortedByDescending { it.lastModified() }?.take(5) ?: emptyList()
            
            val updateCheckLogFiles = logFolder.listFiles { file ->
                file.isFile && file.name.startsWith("TXAAPP_updatecheck_") && file.name.endsWith(".txa")
            }?.sortedByDescending { it.lastModified() }?.take(5) ?: emptyList()
            
            // Đọc và lọc các dòng liên quan đến sound
            val soundLogLines = mutableListOf<String>()
            val keywords = listOf("Sound", "sound", "nhạc chuông", "notification", "channel", "URI", "NotificationHelper", "UpdateCheckService")
            
            // Đọc từ App Log
            appLogFiles.forEach { file ->
                try {
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        if (keywords.any { line.contains(it, ignoreCase = true) }) {
                            soundLogLines.add("[${file.name}] Line ${index + 1}: $line")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsActivity", "Error reading ${file.name}", e)
                }
            }
            
            // Đọc từ Update Check Log
            updateCheckLogFiles.forEach { file ->
                try {
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        if (keywords.any { line.contains(it, ignoreCase = true) }) {
                            soundLogLines.add("[${file.name}] Line ${index + 1}: $line")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsActivity", "Error reading ${file.name}", e)
                }
            }
            
            if (soundLogLines.isEmpty()) {
                Toast.makeText(this, "Không tìm thấy log về nhạc chuông", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Sắp xếp theo thời gian (dòng mới nhất lên đầu)
            soundLogLines.reverse()
            
            // Tạo dialog với custom view
            val dialogView = layoutInflater.inflate(R.layout.dialog_log_viewer, null)
            val tvLogTitle = dialogView.findViewById<TextView>(R.id.tvLogTitle)
            val tvLogContent = dialogView.findViewById<TextView>(R.id.tvLogContent)
            val tvLineCount = dialogView.findViewById<TextView>(R.id.tvLineCount)
            val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
            
            tvLogTitle.text = "Log nhạc chuông (đã lọc)"
            
            // Hiển thị nội dung
            val content = soundLogLines.joinToString("\n")
            tvLogContent.text = content
            tvLineCount.text = "Tổng số dòng: ${soundLogLines.size}"
            
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
            
            btnClose.setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
            
            // Đặt kích thước dialog
            val window = dialog.window
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                (resources.displayMetrics.heightPixels * 0.8).toInt()
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi khi đọc log nhạc chuông: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "1.1_txa"
            val versionCode = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            } catch (e: Exception) {
                1L
            }
            
            val deviceName = getDeviceName()
            val osVersion = "Android ${Build.VERSION.RELEASE}"
            
            val info = """
                TXA Hub
                
                Phiên bản: $versionName
                Version Code: $versionCode
                
                Thiết bị: $deviceName
                Hệ điều hành: $osVersion
                
                Website: txahub.click
                Website sản phẩm: software.txahub.click
            """.trimIndent()
            
            tvVersionInfo.text = info
        } catch (e: Exception) {
            e.printStackTrace()
            tvVersionInfo.text = "Không thể lấy thông tin phiên bản"
        }
    }
    
    private fun loadPermissions() {
        try {
            // Kiểm tra xem view đã được khởi tạo chưa
            if (!::layoutPermissions.isInitialized) {
                return
            }
            
            layoutPermissions.removeAllViews()
            
            val permissions = listOf(
            PermissionItem(
                name = "notification",
                displayName = "Thông báo",
                checkFunction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        NotificationManagerCompat.from(this).areNotificationsEnabled()
                    }
                },
                settingsAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            ),
            PermissionItem(
                name = "storage",
                displayName = "Ghi file",
                checkFunction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        android.os.Environment.isExternalStorageManager()
                    } else {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                },
                settingsAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                } else {
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                }
            ),
            PermissionItem(
                name = "battery",
                displayName = "Tối ưu hóa pin",
                checkFunction = {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        pm.isIgnoringBatteryOptimizations(packageName)
                    } else {
                        true
                    }
                },
                settingsAction = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            ),
            PermissionItem(
                name = "ringtone",
                displayName = "Thay đổi nhạc chuông",
                checkFunction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.System.canWrite(this)
                    } else {
                        // Android < 6.0, quyền được cấp tự động
                        true
                    }
                },
                settingsAction = Settings.ACTION_MANAGE_WRITE_SETTINGS
            ),
            PermissionItem(
                name = "audio",
                displayName = "Ghi đè nhạc chuông và thông báo",
                checkFunction = {
                    // MODIFY_AUDIO_SETTINGS là quyền signature-level, nhưng có thể kiểm tra
                    // Thực tế, quyền này thường được cấp tự động cho app
                    // Kiểm tra xem có thể thay đổi audio settings không
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Settings.System.canWrite(this)
                        } else {
                            true
                        }
                    } catch (e: Exception) {
                        false
                    }
                },
                settingsAction = Settings.ACTION_MANAGE_WRITE_SETTINGS
            )
        )
        
        permissions.forEach { permissionItem ->
            val isGranted = permissionItem.checkFunction()
            
            val itemView = layoutInflater.inflate(R.layout.item_permission_settings, null)
            val tvName = itemView.findViewById<TextView>(R.id.tvPermissionName)
            val ivStatus = itemView.findViewById<ImageView>(R.id.ivPermissionStatus)
            val btnGrant = itemView.findViewById<Button>(R.id.btnGrantPermission)
            
            tvName.text = permissionItem.displayName
            ivStatus.setImageResource(
                if (isGranted) R.drawable.ic_check_green else R.drawable.ic_check_gray
            )
            btnGrant.isEnabled = !isGranted
            btnGrant.text = if (isGranted) "Đã cấp" else "Cấp quyền"
            
            btnGrant.setOnClickListener {
                openPermissionSettings(permissionItem)
            }
            
            layoutPermissions.addView(itemView)
        }
        } catch (e: Exception) {
            e.printStackTrace()
            // Hiển thị thông báo lỗi nếu có
            Toast.makeText(this, "Lỗi khi tải danh sách quyền: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openPermissionSettings(item: PermissionItem) {
        try {
            val intent = when {
                item.name == "ringtone" || item.name == "audio" -> {
                    // Quyền WRITE_SETTINGS cần yêu cầu đặc biệt trên Android 6.0+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (!Settings.System.canWrite(this)) {
                            // Mở Settings để cấp quyền WRITE_SETTINGS
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                        } else {
                            // Đã có quyền, không cần làm gì
                            Toast.makeText(this, "Đã có quyền thay đổi nhạc chuông", Toast.LENGTH_SHORT).show()
                            return
                        }
                    } else {
                        // Android < 6.0, quyền được cấp tự động
                        Toast.makeText(this, "Quyền đã được cấp tự động", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
                item.settingsAction == Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION -> {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                }
                item.settingsAction == Settings.ACTION_APP_NOTIFICATION_SETTINGS -> {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                }
                item.name == "battery" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    } else {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    }
                }
                else -> {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                }
            }
            startActivity(intent)
            Toast.makeText(this, "Vui lòng cấp quyền ${item.displayName}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể mở cài đặt", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkForUpdate() {
        btnCheckUpdate.isEnabled = false
        progressBar.visibility = View.VISIBLE
        
        updateChecker.checkUpdate { updateInfo ->
            runOnUiThread {
                btnCheckUpdate.isEnabled = true
                progressBar.visibility = View.GONE
                
                if (updateInfo != null) {
                    // Có bản cập nhật mới
                    if (updateInfo.forceUpdate) {
                        // Bắt buộc cập nhật
                        showForceUpdateDialog(updateInfo)
                    } else {
                        // Không bắt buộc, hiển thị thông báo
                        showUpdateAvailableDialog(updateInfo)
                    }
                } else {
                    // Đã là bản mới nhất
                    Toast.makeText(
                        this,
                        "Bạn đang sử dụng phiên bản mới nhất!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun showForceUpdateDialog(updateInfo: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle("Cập nhật bắt buộc")
            .setMessage("Phiên bản mới ${updateInfo.versionName} đã có sẵn. Vui lòng cập nhật để tiếp tục sử dụng.")
            .setCancelable(false)
            .setPositiveButton("Tải ngay") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                startActivity(intent)
            }
            .setNegativeButton("Thoát") { _, _ ->
                finish()
            }
            .show()
    }
    
    private fun showUpdateAvailableDialog(updateInfo: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle("Có bản cập nhật mới")
            .setMessage("Phiên bản ${updateInfo.versionName} đã có sẵn.\n\nNgày phát hành: ${updateInfo.releaseDate}\n\nBạn có muốn tải về không?")
            .setPositiveButton("Tải ngay") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                startActivity(intent)
            }
            .setNegativeButton("Skip for now", null)
            .show()
    }
    
    /**
     * Chạy speedtest
     */
    private fun runSpeedTest() {
        btnSpeedTest.isEnabled = false
        progressBar.visibility = View.VISIBLE
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Đang kiểm tra tốc độ mạng...")
            .setMessage("Vui lòng đợi...")
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        speedTest.runQuickSpeedTest(object : SpeedTest.SpeedTestCallback {
            override fun onProgress(downloaded: Long, total: Long, speed: Double) {
                runOnUiThread {
                    val percent = if (total > 0) (downloaded * 100 / total) else 0
                    dialog.setMessage("Đang tải: $percent%\nTốc độ: ${String.format("%.2f", speed)} Mbps")
                }
            }
            
            override fun onComplete(downloadSpeed: Double, uploadSpeed: Double, ping: Long) {
                runOnUiThread {
                    dialog.dismiss()
                    
                    val resultMessage = buildString {
                        appendLine("Kết quả kiểm tra tốc độ mạng:")
                        appendLine()
                        appendLine("Ping: ${if (ping >= 0) "${ping}ms" else "N/A"}")
                        appendLine("Download: ${String.format("%.2f", downloadSpeed)} Mbps")
                        appendLine("Upload: ${String.format("%.2f", uploadSpeed)} Mbps")
                    }
                    
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Kết quả SpeedTest")
                        .setMessage(resultMessage)
                        .setPositiveButton("OK", null)
                        .show()
                    
                    btnSpeedTest.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
            
            override fun onError(message: String) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this@SettingsActivity, "Lỗi: $message", Toast.LENGTH_LONG).show()
                    btnSpeedTest.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        })
    }
    
    private fun getDeviceName(): String {
        val model = Build.MODEL ?: Build.DEVICE ?: "Unknown"
        return model
            .replace(" ", "_")
            .replace("-", "_")
            .replace("/", "_")
            .replace("\\", "_")
            .replace("(", "")
            .replace(")", "")
            .replace("[", "")
            .replace("]", "")
            .replace("{", "")
            .replace("}", "")
            .trim()
    }
    
    data class PermissionItem(
        val name: String,
        val displayName: String,
        val checkFunction: () -> Boolean,
        val settingsAction: String?
    )
    
    /**
     * Load và hiển thị cài đặt nhạc chuông
     */
    private fun loadNotificationSoundSettings() {
        try {
            val soundName = soundManager.getSoundDisplayName()
            tvNotificationSound.text = "Hiện tại: $soundName"
        } catch (e: Exception) {
            e.printStackTrace()
            tvNotificationSound.text = "Lỗi khi tải cài đặt"
        }
    }
    
    /**
     * Hiển thị dialog chọn nhạc chuông
     */
    private fun showNotificationSoundDialog() {
        val items = mutableListOf<String>()
        items.add("Nhạc chuông mặc định của app")
        items.add("Nhạc chuông hệ thống")
        items.add("Chọn file nhạc")
        
        // Nếu đã có sound mặc định app, thêm option để thay đổi
        if (soundManager.getDefaultAppSoundUri() != null) {
            items.add("Đặt lại sound mặc định của app")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Chọn nhạc chuông thông báo")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> {
                        soundManager.setSoundType("default")
                        soundManager.setCustomSoundUri(null)
                        loadNotificationSoundSettings()
                        Toast.makeText(this, "Đã đặt nhạc chuông mặc định", Toast.LENGTH_SHORT).show()
                        // Cập nhật notification channel
                        NotificationHelper(this).updateNotificationChannelSound()
                    }
                    which == 1 -> {
                        // Hiển thị dialog chọn nhạc chuông hệ thống (bao gồm TXA Hub)
                        showSystemSoundDialog()
                    }
                    which == 2 -> {
                        showFilePickerDialog()
                    }
                    which == 3 && items.size > 3 -> {
                        // Đặt lại sound mặc định của app
                        showSetDefaultAppSoundDialog()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    /**
     * Hiển thị dialog chọn nhạc chuông hệ thống (bao gồm TXA Hub)
     */
    private fun showSystemSoundDialog() {
        val items = mutableListOf<String>()
        items.add("Mặc định hệ thống")
        items.add("TXA Hub")
        items.add("Chọn từ danh sách ringtones hệ thống")
        
        AlertDialog.Builder(this)
            .setTitle("Chọn nhạc chuông hệ thống")
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        // Mặc định hệ thống
                        soundManager.setSoundType("system")
                        soundManager.setCustomSoundUri(null)
                        loadNotificationSoundSettings()
                        Toast.makeText(this, "Đã đặt nhạc chuông mặc định hệ thống", Toast.LENGTH_SHORT).show()
                        NotificationHelper(this).updateNotificationChannelSound()
                    }
                    1 -> {
                        // TXA Hub (từ res/raw)
                        val rawSoundUri = soundManager.getRawSoundUri()
                        if (rawSoundUri != null) {
                            soundManager.setSoundType("system_txa")
                            soundManager.setCustomSoundUri(null)
                            loadNotificationSoundSettings()
                            Toast.makeText(this, "Đã đặt nhạc chuông TXA Hub", Toast.LENGTH_SHORT).show()
                            NotificationHelper(this).updateNotificationChannelSound()
                        } else {
                            Toast.makeText(this, "Không tìm thấy file nhạc chuông TXA Hub", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> {
                        // Chọn từ danh sách ringtones hệ thống
                        // Đảm bảo nhạc chuông của app đã được thêm vào MediaStore
                        val rawSoundUri = soundManager.getRawSoundUri()
                        if (rawSoundUri != null) {
                            soundManager.addAppSoundToMediaStore(rawSoundUri, "TXA Hub")
                        }
                        
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Chọn nhạc chuông thông báo")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, soundManager.getNotificationSoundUri())
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        }
                        pickRingtoneLauncher.launch(intent)
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    /**
     * Hiển thị dialog để đặt sound mặc định cho app
     */
    private fun showSetDefaultAppSoundDialog() {
        AlertDialog.Builder(this)
            .setTitle("Đặt sound mặc định cho app")
            .setMessage("Bạn muốn đặt sound mặc định cho app từ đâu?")
            .setItems(arrayOf(
                "Chọn file nhạc mới",
                "Dùng sound hiện tại (nếu có)",
                "Xóa sound mặc định (dùng hệ thống)"
            )) { _, which ->
                when (which) {
                    0 -> {
                        // Chọn file mới để đặt làm mặc định
                        pickSoundFileForDefault()
                    }
                    1 -> {
                        // Dùng sound custom hiện tại (nếu có) - copy vào folder default
                        val customUri = soundManager.getCustomSoundUri()
                        if (customUri != null) {
                            val defaultUri = soundManager.copySoundToDefaultFolder(customUri)
                            if (defaultUri != null) {
                                soundManager.setSoundType("default")
                                loadNotificationSoundSettings()
                                Toast.makeText(this, "Đã đặt sound mặc định cho app", Toast.LENGTH_SHORT).show()
                                NotificationHelper(this).updateNotificationChannelSound()
                            } else {
                                Toast.makeText(this, "Lỗi khi copy sound", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Chưa có sound tùy chỉnh", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> {
                        // Xóa sound mặc định - xóa tất cả file trong folder default
                        soundManager.clearDefaultAppSounds()
                        soundManager.setSoundType("default")
                        loadNotificationSoundSettings()
                        Toast.makeText(this, "Đã xóa sound mặc định, dùng hệ thống", Toast.LENGTH_SHORT).show()
                        NotificationHelper(this).updateNotificationChannelSound()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    /**
     * Mở file picker để chọn file nhạc làm mặc định cho app
     */
    private fun pickSoundFileForDefault() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "audio/mpeg",
                "audio/mp3",
                "audio/wav"
            ))
        }
        
        try {
            pickSoundLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở file picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Hiển thị dialog hướng dẫn trước khi chọn file
     */
    private fun showFilePickerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Chọn file nhạc chuông")
            .setMessage("""
                Yêu cầu file nhạc chuông:
                • Định dạng: MP3, WAV
                • Kích thước tối đa: 5MB
                • Thời lượng tối đa: 45 giây
                
                Bạn có muốn tiếp tục chọn file?
            """.trimIndent())
            .setPositiveButton("Chọn file") { _, _ ->
                pickSoundFile()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    /**
     * Mở file picker để chọn file nhạc
     */
    private fun pickSoundFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "audio/mpeg",
                "audio/mp3",
                "audio/wav"
            ))
        }
        
        try {
            pickSoundLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở file picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Xử lý file nhạc đã chọn (cho custom sound)
     */
    private fun handleSoundFile(uri: Uri) {
        try {
            // Validate file
            val (isValid, errorMessage, durationMs) = soundManager.validateSoundFile(uri)
            
            android.util.Log.d("SettingsActivity", "handleSoundFile: isValid=$isValid, durationMs=$durationMs, errorMessage=$errorMessage")
            
            if (!isValid) {
                AlertDialog.Builder(this)
                    .setTitle("File không hợp lệ")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
            
            // Kiểm tra nếu file quá dài, toast thông báo rồi hiển thị dialog cắt nhạc
            android.util.Log.d("SettingsActivity", "Checking duration: $durationMs > ${NotificationSoundManager.MAX_SOUND_DURATION_MS}")
            if (durationMs > NotificationSoundManager.MAX_SOUND_DURATION_MS) {
                val durationSeconds = durationMs / 1000.0
                val maxDurationSeconds = NotificationSoundManager.MAX_SOUND_DURATION_MS / 1000.0
                android.util.Log.d("SettingsActivity", "File too long, showing trim dialog: $durationSeconds seconds")
                Toast.makeText(
                    this,
                    "File có thời lượng ${String.format("%.1f", durationSeconds)} giây, vượt quá ${String.format("%.1f", maxDurationSeconds)} giây. Vui lòng cắt nhạc.",
                    Toast.LENGTH_LONG
                ).show()
                selectedSoundUri = uri
                isPickingForDefault = false
                showAudioTrimDialog(uri, durationMs)
            } else {
                // File hợp lệ, lưu trực tiếp
                android.util.Log.d("SettingsActivity", "File valid, saving directly")
                saveSoundFile(uri)
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Error in handleSoundFile: ${e.message}", e)
            AlertDialog.Builder(this)
                .setTitle("Lỗi")
                .setMessage("Lỗi khi xử lý file: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    /**
     * Xử lý file nhạc đã chọn (cho default sound)
     */
    private fun handleSoundFileForDefault(uri: Uri) {
        try {
            // Validate file
            val (isValid, errorMessage, durationMs) = soundManager.validateSoundFile(uri)
            
            android.util.Log.d("SettingsActivity", "handleSoundFileForDefault: isValid=$isValid, durationMs=$durationMs, errorMessage=$errorMessage")
            
            if (!isValid) {
                AlertDialog.Builder(this)
                    .setTitle("File không hợp lệ")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
            
            // Kiểm tra nếu file quá dài, toast thông báo rồi hiển thị dialog cắt nhạc
            android.util.Log.d("SettingsActivity", "Checking duration: $durationMs > ${NotificationSoundManager.MAX_SOUND_DURATION_MS}")
            if (durationMs > NotificationSoundManager.MAX_SOUND_DURATION_MS) {
                val durationSeconds = durationMs / 1000.0
                val maxDurationSeconds = NotificationSoundManager.MAX_SOUND_DURATION_MS / 1000.0
                android.util.Log.d("SettingsActivity", "File too long, showing trim dialog: $durationSeconds seconds")
                Toast.makeText(
                    this,
                    "File có thời lượng ${String.format("%.1f", durationSeconds)} giây, vượt quá ${String.format("%.1f", maxDurationSeconds)} giây. Vui lòng cắt nhạc.",
                    Toast.LENGTH_LONG
                ).show()
                selectedSoundUri = uri
                isPickingForDefault = true
                showAudioTrimDialog(uri, durationMs)
            } else {
                // File hợp lệ, lưu vào folder default
                android.util.Log.d("SettingsActivity", "File valid, saving to default folder")
                saveSoundFileForDefault(uri)
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Error in handleSoundFileForDefault: ${e.message}", e)
            AlertDialog.Builder(this)
                .setTitle("Lỗi")
                .setMessage("Lỗi khi xử lý file: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    /**
     * Hiển thị dialog cắt nhạc
     */
    private fun showAudioTrimDialog(uri: Uri, durationMs: Long) {
        val durationSeconds = durationMs / 1000.0
        val maxDurationSeconds = NotificationSoundManager.MAX_SOUND_DURATION_MS / 1000.0
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_audio_trim, null)
        val tvDurationInfo = dialogView.findViewById<TextView>(R.id.tvDurationInfo)
        val tvStartTime = dialogView.findViewById<TextView>(R.id.tvStartTime)
        val tvEndTime = dialogView.findViewById<TextView>(R.id.tvEndTime)
        val tvSelectedDuration = dialogView.findViewById<TextView>(R.id.tvSelectedDuration)
        val seekBarStart = dialogView.findViewById<android.widget.SeekBar>(R.id.seekBarStart)
        val seekBarEnd = dialogView.findViewById<android.widget.SeekBar>(R.id.seekBarEnd)
        val btnCancelTrim = dialogView.findViewById<Button>(R.id.btnCancelTrim)
        val btnConfirmTrim = dialogView.findViewById<Button>(R.id.btnConfirmTrim)
        
        tvDurationInfo.text = "Thời lượng file: ${String.format("%.1f", durationSeconds)} giây (tối đa: ${maxDurationSeconds} giây)"
        
        // Thiết lập seekbar
        val maxProgress = (durationSeconds * 10).toInt() // Độ chính xác 0.1 giây
        seekBarStart.max = maxProgress
        seekBarEnd.max = maxProgress
        seekBarEnd.progress = (maxDurationSeconds * 10).toInt().coerceAtMost(maxProgress)
        
        // Cập nhật thời gian khi seekbar thay đổi
        val updateTimes = {
            val startSeconds = seekBarStart.progress / 10.0
            val endSeconds = seekBarEnd.progress / 10.0
            
            tvStartTime.text = String.format("%.1f", startSeconds)
            tvEndTime.text = String.format("%.1f", endSeconds)
            tvSelectedDuration.text = "Độ dài đã chọn: ${String.format("%.1f", endSeconds - startSeconds)} giây"
            
            // Đảm bảo endTime >= startTime
            if (endSeconds <= startSeconds) {
                seekBarEnd.progress = (startSeconds * 10 + 1).toInt().coerceAtMost(maxProgress)
                val newEndSeconds = seekBarEnd.progress / 10.0
                tvEndTime.text = String.format("%.1f", newEndSeconds)
                tvSelectedDuration.text = "Độ dài đã chọn: ${String.format("%.1f", newEndSeconds - startSeconds)} giây"
            }
        }
        
        seekBarStart.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (progress >= seekBarEnd.progress) {
                        seekBarStart.progress = (seekBarEnd.progress - 1).coerceAtLeast(0)
                    }
                    updateTimes()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        seekBarEnd.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (progress <= seekBarStart.progress) {
                        seekBarEnd.progress = (seekBarStart.progress + 1).coerceAtMost(maxProgress)
                    }
                    updateTimes()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        updateTimes()
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        btnCancelTrim.setOnClickListener {
            dialog.dismiss()
            selectedSoundUri = null
        }
        
        btnConfirmTrim.setOnClickListener {
            val startSeconds = seekBarStart.progress / 10.0
            val endSeconds = seekBarEnd.progress / 10.0
            val startTimeMs = (startSeconds * 1000).toLong()
            val endTimeMs = (endSeconds * 1000).toLong()
            
            // Cắt nhạc
            trimAndSaveSound(uri, startTimeMs, endTimeMs, dialog)
        }
        
        dialog.show()
        
        // Đặt kích thước dialog
        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    /**
     * Cắt và lưu file nhạc
     */
    private fun trimAndSaveSound(uri: Uri, startTimeMs: Long, endTimeMs: Long, dialog: AlertDialog) {
        // Hiển thị progress
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Đang cắt nhạc...")
            .setMessage("Vui lòng đợi")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        Thread {
            try {
                // Tạo file tạm để lưu file đã cắt
                val soundFolder = File(filesDir, "notification_sounds")
                if (!soundFolder.exists()) {
                    soundFolder.mkdirs()
                }
                val tempFile = File(soundFolder, "trimmed_${System.currentTimeMillis()}.mp4")
                
                // Cắt nhạc
                val trimmedFile = audioTrimmer.trimAudio(uri, startTimeMs, endTimeMs, tempFile)
                
                runOnUiThread {
                    progressDialog.dismiss()
                    dialog.dismiss()
                    
                    if (trimmedFile != null) {
                        val trimmedUri = Uri.fromFile(trimmedFile)
                        if (isPickingForDefault) {
                            saveSoundFileForDefault(trimmedUri)
                        } else {
                            saveSoundFile(trimmedUri)
                        }
                        Toast.makeText(this, "Đã cắt và lưu nhạc chuông", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Lỗi khi cắt nhạc", Toast.LENGTH_SHORT).show()
                    }
                    selectedSoundUri = null
                    isPickingForDefault = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    dialog.dismiss()
                    Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                    selectedSoundUri = null
                }
            }
        }.start()
    }
    
    /**
     * Lưu file nhạc chuông tùy chỉnh (vào folder custom)
     */
    private fun saveSoundFile(uri: Uri) {
        // Copy file vào internal storage (folder custom)
        val copiedUri = soundManager.copySoundToInternalStorage(uri)
        if (copiedUri != null) {
            soundManager.setSoundType("custom")
            soundManager.setCustomSoundUri(copiedUri)
            loadNotificationSoundSettings()
            Toast.makeText(this, "Đã lưu nhạc chuông tùy chỉnh", Toast.LENGTH_SHORT).show()
            // Cập nhật notification channel
            NotificationHelper(this).updateNotificationChannelSound()
        } else {
            Toast.makeText(this, "Lỗi khi lưu file nhạc chuông", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Lưu file nhạc chuông vào folder default của app
     */
    private fun saveSoundFileForDefault(uri: Uri) {
        // Copy file vào folder default
        val copiedUri = soundManager.copySoundToDefaultFolder(uri)
        if (copiedUri != null) {
            soundManager.setSoundType("default")
            loadNotificationSoundSettings()
            Toast.makeText(this, "Đã đặt sound mặc định cho app", Toast.LENGTH_SHORT).show()
            // Cập nhật notification channel
            NotificationHelper(this).updateNotificationChannelSound()
        } else {
            Toast.makeText(this, "Lỗi khi lưu file nhạc chuông", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Cập nhật lại trạng thái quyền khi quay lại
        try {
            if (::layoutPermissions.isInitialized) {
                loadPermissions()
            }
            // Cập nhật hiển thị nút mở file log
            if (::btnOpenLogFile.isInitialized) {
                updateLogFileButtonVisibility()
            }
            // Cập nhật cài đặt nhạc chuông
            if (::tvNotificationSound.isInitialized) {
                loadNotificationSoundSettings()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

