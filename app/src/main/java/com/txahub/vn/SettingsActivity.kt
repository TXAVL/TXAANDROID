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
    private lateinit var btnOpenLogFile: Button
    private val logWriter = LogWriter(this)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        updateChecker = UpdateChecker(this)
        
        setupViews()
        loadVersionInfo()
        loadPermissions()
    }
    
    private fun setupViews() {
        layoutPermissions = findViewById(R.id.layoutPermissions)
        tvVersionInfo = findViewById(R.id.tvVersionInfo)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        progressBar = findViewById(R.id.progressBar)
        
        // Nút back
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        // Nút check update
        btnCheckUpdate.setOnClickListener {
            checkForUpdate()
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
     * Mở file log mới nhất bằng text editor mặc định
     */
    private fun openLogFile() {
        try {
            val logFile = logWriter.getLatestLogFile()
            if (logFile == null || !logFile.exists()) {
                Toast.makeText(this, "Không tìm thấy file log", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Tạo URI để mở file
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ cần sử dụng FileProvider
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    logFile
                )
            } else {
                // Android < 7.0
                Uri.fromFile(logFile)
            }
            
            // Mở file bằng text editor mặc định
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Toast.makeText(this, "Đã mở file log: ${logFile.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Không tìm thấy ứng dụng để mở file log", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi khi mở file log: ${e.message}", Toast.LENGTH_SHORT).show()
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
            .setNegativeButton("Để sau", null)
            .show()
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

