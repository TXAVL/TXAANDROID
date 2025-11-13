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
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionRequestActivity : AppCompatActivity() {
    
    private lateinit var layoutPermissions: LinearLayout
    private val requiredPermissions = mutableListOf<PermissionItem>()
    
    data class PermissionItem(
        val name: String,
        val displayName: String,
        val permission: String? = null,
        val settingsAction: String? = null,
        val checkFunction: () -> Boolean
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_request)
        
        layoutPermissions = findViewById(R.id.layoutPermissions)
        
        setupRequiredPermissions()
        updatePermissionStatus()
    }
    
    override fun onResume() {
        super.onResume()
        // Cập nhật lại trạng thái quyền khi quay lại app
        updatePermissionStatus()
        
        // Kiểm tra xem đã đủ quyền chưa
        if (areAllPermissionsGranted()) {
            // Đủ quyền, chuyển sang check update
            proceedToCheckUpdate()
        }
    }
    
    private fun setupRequiredPermissions() {
        requiredPermissions.clear()
        
        // Quyền thông báo
        requiredPermissions.add(
            PermissionItem(
                name = "notification",
                displayName = "Thông báo",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else null,
                settingsAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                checkFunction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        // Android < 13
                        android.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
                    }
                }
            )
        )
        
        // Quyền ghi file
        requiredPermissions.add(
            PermissionItem(
                name = "storage",
                displayName = "Ghi file",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    null // Cần MANAGE_EXTERNAL_STORAGE
                } else {
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                settingsAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                } else {
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                },
                checkFunction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        android.os.Environment.isExternalStorageManager()
                    } else {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                }
            )
        )
        
        // Quyền tối ưu hóa pin
        requiredPermissions.add(
            PermissionItem(
                name = "battery",
                displayName = "Tối ưu hóa pin",
                permission = null,
                settingsAction = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                checkFunction = {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        pm.isIgnoringBatteryOptimizations(packageName)
                    } else {
                        true
                    }
                }
            )
        )
    }
    
    private fun updatePermissionStatus() {
        layoutPermissions.removeAllViews()
        
        requiredPermissions.forEach { permissionItem ->
            val isGranted = permissionItem.checkFunction()
            
            val itemView = layoutInflater.inflate(R.layout.item_permission, null)
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
                requestPermission(permissionItem)
            }
            
            layoutPermissions.addView(itemView)
        }
    }
    
    private fun requestPermission(item: PermissionItem) {
        if (item.permission != null) {
            // Yêu cầu quyền runtime
            ActivityCompat.requestPermissions(
                this,
                arrayOf(item.permission),
                getRequestCode(item.name)
            )
        } else {
            // Mở settings
            openPermissionSettings(item)
        }
    }
    
    private fun openPermissionSettings(item: PermissionItem) {
        try {
            val intent = when {
                item.settingsAction == Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION -> {
                    // Android 11+ - Quản lý tất cả file
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                }
                item.settingsAction == Settings.ACTION_APP_NOTIFICATION_SETTINGS -> {
                    // Cài đặt thông báo
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                }
                item.name == "battery" -> {
                    // Tối ưu hóa pin
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
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Đã cấp quyền thành công", Toast.LENGTH_SHORT).show()
            updatePermissionStatus()
        } else {
            Toast.makeText(this, "Chưa cấp quyền. Vui lòng cấp quyền trong cài đặt", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getRequestCode(permissionName: String): Int {
        return when (permissionName) {
            "notification" -> 2001
            "storage" -> 2002
            "battery" -> 2003
            else -> 2000
        }
    }
    
    private fun areAllPermissionsGranted(): Boolean {
        return requiredPermissions.all { it.checkFunction() }
    }
    
    private fun proceedToCheckUpdate() {
        val intent = Intent(this, SplashActivity::class.java).apply {
            // Truyền deep link nếu có
            val data = this@PermissionRequestActivity.intent?.data
            if (data != null) {
                this.data = data
                this.action = Intent.ACTION_VIEW
            }
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}

