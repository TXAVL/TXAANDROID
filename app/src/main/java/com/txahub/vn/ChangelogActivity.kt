package com.txahub.vn

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ChangelogActivity : AppCompatActivity() {
    
    private lateinit var updateChecker: UpdateChecker
    private lateinit var layoutChangelogList: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var btnClose: Button
    private var currentVersion: String = ""
    private val expandedItems = mutableSetOf<String>() // Lưu các item đã mở rộng
    
    companion object {
        const val EXTRA_VERSION_NAME = "version_name"
        const val EXTRA_CHANGELOG = "changelog"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changelog)
        
        updateChecker = UpdateChecker(this)
        currentVersion = getCurrentVersion()
        
        setupViews()
        loadAllChangelogs()
    }
    
    private fun setupViews() {
        layoutChangelogList = findViewById(R.id.layoutChangelogList)
        progressBar = findViewById(R.id.progressBar)
        btnClose = findViewById(R.id.btnClose)
        
        btnClose.setOnClickListener {
            finish()
        }
    }
    
    private fun loadAllChangelogs() {
        progressBar.visibility = View.VISIBLE
        layoutChangelogList.removeAllViews()
        
        updateChecker.getAllChangelogs { changelogs ->
            runOnUiThread {
                progressBar.visibility = View.GONE
                
                if (changelogs.isEmpty()) {
                    // Hiển thị thông báo không có changelog
                    val emptyView = TextView(this).apply {
                        text = "Chưa có thông tin changelog"
                        textSize = 16f
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, 40, 0, 40)
                    }
                    layoutChangelogList.addView(emptyView)
                    return@runOnUiThread
                }
                
                // Tạo item cho mỗi version
                changelogs.forEach { versionChangelog ->
                    val itemView = createChangelogItem(versionChangelog)
                    layoutChangelogList.addView(itemView)
                }
            }
        }
    }
    
    private fun createChangelogItem(versionChangelog: VersionChangelog): View {
        val itemView = layoutInflater.inflate(R.layout.item_changelog_version, null)
        
        val layoutVersionHeader = itemView.findViewById<LinearLayout>(R.id.layoutVersionHeader)
        val tvVersionName = itemView.findViewById<TextView>(R.id.tvVersionName)
        val tvCurrentBadge = itemView.findViewById<TextView>(R.id.tvCurrentBadge)
        val ivExpand = itemView.findViewById<ImageView>(R.id.ivExpand)
        val layoutChangelogContent = itemView.findViewById<LinearLayout>(R.id.layoutChangelogContent)
        val tvReleaseDate = itemView.findViewById<TextView>(R.id.tvReleaseDate)
        val webViewChangelog = itemView.findViewById<WebView>(R.id.webViewChangelog)
        
        // Set version name
        tvVersionName.text = versionChangelog.versionName
        
        // Hiển thị badge "(hiện tại)" nếu là version hiện tại
        val isCurrentVersion = versionChangelog.versionName == currentVersion
        if (isCurrentVersion) {
            tvCurrentBadge.visibility = View.VISIBLE
        } else {
            tvCurrentBadge.visibility = View.GONE
        }
        
        // Set release date
        tvReleaseDate.text = "Ngày phát hành: ${versionChangelog.releaseDate}"
        
        // Load changelog vào WebView
        val displayChangelog = if (versionChangelog.changelog.isBlank() || versionChangelog.changelog.trim().isEmpty()) {
            """
            <div style="text-align: center; padding: 20px;">
                <p style="font-size: 14px; color: #999;">Chưa có thông tin changelog cho phiên bản này.</p>
            </div>
            """.trimIndent()
        } else {
            versionChangelog.changelog
        }
        
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        padding: 8px;
                        line-height: 1.6;
                        color: #333;
                        margin: 0;
                    }
                    h2 { color: #1976D2; margin-top: 16px; margin-bottom: 8px; }
                    ul, ol { padding-left: 20px; margin: 8px 0; }
                    li { margin: 4px 0; }
                    p { margin: 8px 0; }
                </style>
            </head>
            <body>
                $displayChangelog
            </body>
            </html>
        """.trimIndent()
        
        // Setup WebView
        webViewChangelog.settings.javaScriptEnabled = true
        webViewChangelog.settings.domStorageEnabled = false
        webViewChangelog.settings.loadsImagesAutomatically = false
        webViewChangelog.isVerticalScrollBarEnabled = false
        webViewChangelog.isHorizontalScrollBarEnabled = false
        
        // Set fixed height cho WebView để tránh vấn đề layout
        val layoutParams = webViewChangelog.layoutParams
        layoutParams.height = (resources.displayMetrics.density * 300).toInt() // ~300dp
        webViewChangelog.layoutParams = layoutParams
        
        webViewChangelog.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        
        // Kiểm tra xem item này đã được mở rộng chưa
        val itemKey = versionChangelog.versionName
        val isExpanded = expandedItems.contains(itemKey)
        if (isExpanded) {
            layoutChangelogContent.visibility = View.VISIBLE
            ivExpand.rotation = 180f
        } else {
            layoutChangelogContent.visibility = View.GONE
            ivExpand.rotation = 0f
        }
        
        // Click vào header để toggle expand/collapse
        layoutVersionHeader.setOnClickListener {
            val wasExpanded = expandedItems.contains(itemKey)
            
            if (wasExpanded) {
                // Collapse
                expandedItems.remove(itemKey)
                layoutChangelogContent.visibility = View.GONE
                ivExpand.rotation = 0f
            } else {
                // Expand
                expandedItems.add(itemKey)
                layoutChangelogContent.visibility = View.VISIBLE
                ivExpand.rotation = 180f
            }
            
            // Animation
            val animation = if (wasExpanded) {
                AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
            } else {
                AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
            }
            layoutChangelogContent.startAnimation(animation)
        }
        
        return itemView
    }
    
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "1.1_txa"
        } catch (e: Exception) {
            "1.1_txa"
        }
    }
}
