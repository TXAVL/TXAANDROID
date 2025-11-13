package com.txahub.vn

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

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
            NotificationManagerCompat.from(this).areNotificationsEnabled()
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
            runOnUiThread {
                if (updateInfo != null) {
                    // Có bản cập nhật mới
                    if (updateInfo.forceUpdate) {
                        // Bắt buộc cập nhật - chặn và yêu cầu tải, không có nút Skip
                        showForceUpdateDialog(updateInfo)
                    } else {
                        // Không bắt buộc, hiển thị dialog với nút "Skip for now"
                        showOptionalUpdateDialog(updateInfo)
                    }
                } else {
                    // Không có bản cập nhật, tiếp tục như bình thường
                    proceedToNextScreen()
                }
            }
        }
    }

    private fun showForceUpdateDialog(updateInfo: UpdateInfo) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Cập nhật bắt buộc")
                .setMessage("Phiên bản mới ${updateInfo.versionName} đã có sẵn. Vui lòng cập nhật để tiếp tục sử dụng.")
                .setCancelable(false) // Không cho phép đóng bằng nút back
                .setPositiveButton("Tải ngay") { _, _ ->
                    // Mở trình duyệt đến link tải
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateInfo.downloadUrl))
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Thoát") { _, _ ->
                    finish()
                }
                .setOnKeyListener { _, keyCode, _ ->
                    // Chặn nút back khi force update
                    if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                        true
                    } else {
                        false
                    }
                }
                .show()
        }
    }
    
    private fun showOptionalUpdateDialog(updateInfo: UpdateInfo) {
        runOnUiThread {
            val prefs = getSharedPreferences("txahub_prefs", MODE_PRIVATE)
            val skipKey = "skipped_update_version_${updateInfo.versionName}"
            val isSkipped = prefs.getBoolean(skipKey, false)
            
            // Nếu đã skip version này rồi, không hiện dialog nữa
            // Nhưng service nền vẫn sẽ thông báo qua notification
            if (isSkipped) {
                proceedToNextScreen()
                return@runOnUiThread
            }
            
            AlertDialog.Builder(this)
                .setTitle("Có bản cập nhật mới")
                .setMessage("Phiên bản ${updateInfo.versionName} đã có sẵn.\n\nNgày phát hành: ${updateInfo.releaseDate}\n\nBạn có muốn tải về không?Bấm vào sẽ mở link và tải thủ công!")
                .setCancelable(true) // Cho phép đóng bằng nút back
                .setPositiveButton("Tải ngay") { _, _ ->
                    // Mở trình duyệt đến link tải
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateInfo.downloadUrl))
                    startActivity(intent)
                    // Không lưu skip khi tải
                    proceedToNextScreen()
                }
                .setNegativeButton("Skip for now") { _, _ ->
                    // Lưu trạng thái skip cho version này
                    // Lần sau khởi động lại sẽ không hiện dialog nữa
                    // Nhưng service nền vẫn sẽ thông báo qua notification mỗi 5 phút
                    prefs.edit().putBoolean(skipKey, true).apply()
                    proceedToNextScreen()
                }
                .setOnDismissListener {
                    // Khi đóng dialog (bằng back button), không lưu skip
                    // Lần sau vẫn hiện dialog
                    proceedToNextScreen()
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

