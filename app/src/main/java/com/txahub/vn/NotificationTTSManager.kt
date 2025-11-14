package com.txahub.vn

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Quản lý Text-to-Speech để đọc thông báo
 */
class NotificationTTSManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "txahub_tts_prefs"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_TTS_LANGUAGE = "tts_language"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    /**
     * Khởi tạo TTS
     */
    fun initialize(callback: (Boolean) -> Unit) {
        if (tts != null && isInitialized) {
            callback(true)
            return
        }
        
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("vi", "VN")) ?: TextToSpeech.LANG_MISSING_DATA
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback về tiếng Anh nếu không hỗ trợ tiếng Việt
                    tts?.setLanguage(Locale.US)
                }
                isInitialized = true
                callback(true)
            } else {
                Log.e("NotificationTTSManager", "TTS initialization failed")
                callback(false)
            }
        }
    }
    
    /**
     * Kiểm tra xem TTS có được bật không
     */
    fun isTTSEnabled(): Boolean {
        return prefs.getBoolean(KEY_TTS_ENABLED, false)
    }
    
    /**
     * Bật/tắt TTS
     */
    fun setTTSEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
        if (!enabled && tts != null) {
            tts?.stop()
        }
    }
    
    /**
     * Đọc văn bản
     */
    fun speak(text: String, utteranceId: String = "notification_${System.currentTimeMillis()}") {
        if (!isTTSEnabled() || !isInitialized) {
            return
        }
        
        tts?.let { textToSpeech ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
        }
    }
    
    /**
     * Đọc thông báo với câu cuối cùng "APP ĐƯỢC BUILD BỞI TÊ ÍCH A"
     */
    fun speakNotification(text: String, utteranceId: String = "notification_${System.currentTimeMillis()}") {
        if (!isTTSEnabled() || !isInitialized) {
            return
        }
        
        tts?.let { textToSpeech ->
            val finalText = "$text. APP ĐƯỢC BUILD BỞI TÊ ÍCH A"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(finalText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                textToSpeech.speak(finalText, TextToSpeech.QUEUE_FLUSH, null)
            }
        }
    }
    
    /**
     * Dừng đọc
     */
    fun stop() {
        tts?.stop()
    }
    
    /**
     * Giải phóng tài nguyên
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
    
    /**
     * Kiểm tra xem TTS có sẵn không
     */
    fun isAvailable(): Boolean {
        return isInitialized && tts != null
    }
}

