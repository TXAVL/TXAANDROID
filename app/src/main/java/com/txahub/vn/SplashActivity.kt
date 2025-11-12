package com.txahub.vn

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val SPLASH_DELAY: Long = 2000 // 2 giây

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Chuyển sang MainActivity sau delay
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            
            // Truyền deep link nếu có
            val data = this.intent?.data
            if (data != null) {
                intent.data = data
                intent.action = Intent.ACTION_VIEW
            }
            
            startActivity(intent)
            finish()
        }, SPLASH_DELAY)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

