package com.txahub.vn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        updateChecker = UpdateChecker(this)
        speedTest = SpeedTest(this)
        
        setupViews()
        loadVersionInfo()
        loadPermissions()
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
            .setItems(arrayOf("App Log", "Crash Log")) { _, which ->
                when (which) {
                    0 -> showLogViewer("app")
                    1 -> showLogViewer("crash")
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
            val logFiles = logFolder.listFiles { file ->
                file.isFile && when (logType) {
                    "app" -> file.name.startsWith("TXAAPP_app_") && file.name.endsWith(".txa")
                    "crash" -> file.name.startsWith("TXAAPP_crash_") && file.name.endsWith(".txa")
                    else -> false
                }
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
            
            if (logFiles.isEmpty()) {
                Toast.makeText(this, "Không tìm thấy file log ${if (logType == "app") "ứng dụng" else "crash"}", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Lấy file mới nhất
            val logFile = logFiles.first()
            
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

