package com.txahub.vn

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val SPLASH_DELAY: Long = 2000 // 2 giây
    private lateinit var updateChecker: UpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        updateChecker = UpdateChecker(this)
        
        // Kiểm tra quyền trước, sau đó mới check update
        checkPermissions()
    }
    
    private fun checkPermissions() {
        // Kiểm tra các quyền cần thiết
        val hasNotification = checkNotificationPermission()
        val hasStorage = checkStoragePermission()
        val hasBattery = checkBatteryOptimization()
        
        if (!hasNotification || !hasStorage || !hasBattery) {
            // Chưa đủ quyền, hiển thị PermissionRequestActivity
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, PermissionRequestActivity::class.java)
                // Truyền deep link nếu có
                val data = this.intent?.data
                if (data != null) {
                    intent.data = data
                    intent.action = Intent.ACTION_VIEW
                }
                startActivity(intent)
                finish()
            }, SPLASH_DELAY)
        } else {
            // Đã đủ quyền, check update
            checkForUpdate()
        }
    }
    
    private fun checkNotificationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.content.pm.PackageManager.PERMISSION_GRANTED == 
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            android.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
    }
    
    private fun checkStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            android.content.pm.PackageManager.PERMISSION_GRANTED == 
                checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    
    private fun checkBatteryOptimization(): Boolean {
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun checkForUpdate() {
        updateChecker.checkUpdate { updateInfo ->
            if (updateInfo != null) {
                // Có bản cập nhật mới
                if (updateInfo.forceUpdate) {
                    // Bắt buộc cập nhật - chặn và yêu cầu tải
                    showForceUpdateDialog(updateInfo)
                } else {
                    // Không bắt buộc, tiếp tục như bình thường
                    proceedToNextScreen()
                }
            } else {
                // Không có bản cập nhật, tiếp tục như bình thường
                proceedToNextScreen()
            }
        }
    }

    private fun showForceUpdateDialog(updateInfo: UpdateInfo) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Cập nhật bắt buộc")
                .setMessage("Phiên bản mới ${updateInfo.versionName} đã có sẵn. Vui lòng cập nhật để tiếp tục sử dụng.")
                .setCancelable(false)
                .setPositiveButton("Tải ngay") { _, _ ->
                    // Mở trình duyệt đến link tải
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateInfo.downloadUrl))
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Thoát") { _, _ ->
                    finish()
                }
                .show()
        }
    }

    private fun proceedToNextScreen() {
        Handler(Looper.getMainLooper()).postDelayed({
            // Kiểm tra xem có cần hiển thị changelog không
            val currentVersion = updateChecker.getCurrentVersion()
            val prefs = getSharedPreferences("txahub_prefs", MODE_PRIVATE)
            val hiddenKey = "hidden_changelog_version_$currentVersion"
            val isHidden = prefs.getBoolean(hiddenKey, false)
            
            if (!isHidden) {
                // Lấy changelog từ API cho version hiện tại
                updateChecker.getChangelogForVersion(currentVersion) { changelog ->
                    runOnUiThread {
                        val intent = Intent(this, ChangelogActivity::class.java).apply {
                            putExtra(ChangelogActivity.EXTRA_VERSION_NAME, currentVersion)
                            putExtra(ChangelogActivity.EXTRA_CHANGELOG, changelog ?: "")
                        }
                        
                        // Truyền deep link nếu có
                        val data = this@SplashActivity.intent?.data
                        if (data != null) {
                            intent.data = data
                            intent.action = Intent.ACTION_VIEW
                        }
                        
                        startActivity(intent)
                        finish()
                    }
                }
            } else {
                // Chuyển thẳng sang MainActivity
                val intent = Intent(this, MainActivity::class.java)
                
                // Truyền deep link nếu có
                val data = this.intent?.data
                if (data != null) {
                    intent.data = data
                    intent.action = Intent.ACTION_VIEW
                }
                
                startActivity(intent)
                finish()
            }
        }, SPLASH_DELAY)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

