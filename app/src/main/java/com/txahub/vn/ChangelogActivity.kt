package com.txahub.vn

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ChangelogActivity : AppCompatActivity() {
    
    private lateinit var prefs: SharedPreferences
    private var versionName: String = ""
    private var changelog: String = ""
    
    companion object {
        const val EXTRA_VERSION_NAME = "version_name"
        const val EXTRA_CHANGELOG = "changelog"
        const val PREFS_NAME = "txahub_prefs"
        const val KEY_HIDDEN_VERSION = "hidden_changelog_version_"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changelog)
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        versionName = intent.getStringExtra(EXTRA_VERSION_NAME) ?: getCurrentVersion()
        changelog = intent.getStringExtra(EXTRA_CHANGELOG) ?: ""
        
        // Kiểm tra xem đã ẩn changelog cho phiên bản này chưa
        val hiddenKey = KEY_HIDDEN_VERSION + versionName
        if (prefs.getBoolean(hiddenKey, false)) {
            // Đã ẩn, chuyển thẳng sang MainActivity
            goToMainActivity()
            return
        }
        
        setupViews()
    }
    
    private fun setupViews() {
        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        val webViewChangelog = findViewById<WebView>(R.id.webViewChangelog)
        val cbHideThisVersion = findViewById<CheckBox>(R.id.cbHideThisVersion)
        val btnContinue = findViewById<Button>(R.id.btnContinue)
        
        tvVersion.text = "Phiên bản $versionName"
        
        // Load changelog vào WebView
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        padding: 16px;
                        line-height: 1.6;
                        color: #333;
                    }
                    h2 { color: #1976D2; margin-top: 20px; }
                    ul { padding-left: 20px; }
                    li { margin: 8px 0; }
                </style>
            </head>
            <body>
                $changelog
            </body>
            </html>
        """.trimIndent()
        
        webViewChangelog.settings.javaScriptEnabled = true
        webViewChangelog.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        
        btnContinue.setOnClickListener {
            // Lưu trạng thái ẩn nếu checkbox được tích
            if (cbHideThisVersion.isChecked) {
                val hiddenKey = KEY_HIDDEN_VERSION + versionName
                prefs.edit().putBoolean(hiddenKey, true).apply()
            }
            
            goToMainActivity()
        }
    }
    
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "1.1_txa"
        } catch (e: Exception) {
            "1.1_txa"
        }
    }
    
    private fun goToMainActivity() {
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
}

