package com.txahub.vn

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.*
import androidx.credentials.exceptions.*
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.GetCredentialRequest
import androidx.credentials.Credential
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.GetCredentialResponse
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * Manager để xử lý Passkey (WebAuthn/FIDO2) operations
 * Hỗ trợ tạo và lấy Passkey credentials
 */
class PasskeyManager(private val context: Context) {
    
    private val credentialManager = CredentialManager.create(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val logWriter = LogWriter(context)
    
    companion object {
        private const val TAG = "PasskeyManager"
    }
    
    /**
     * Tạo Passkey mới (Registration)
     * @param config JSON string chứa cấu hình từ web:
     *   {
     *     "challenge": "base64_encoded_challenge",
     *     "rp": {
     *       "name": "TXA Hub",
     *       "id": "txahub.click"
     *     },
     *     "user": {
     *       "id": "base64_user_id",
     *       "name": "user@example.com",
     *       "displayName": "User Name"
     *     },
     *     "pubKeyCredParams": [...],
     *     "timeout": 60000,
     *     "attestation": "none"
     *   }
     * @param callback JavaScript callback function name để trả kết quả
     */
    fun createPasskey(configJson: String, callback: String, webView: android.webkit.WebView) {
        coroutineScope.launch {
            try {
                val config = JSONObject(configJson)
                
                // 1. Lấy và validate RP ID
                val rpId = config.optString("rpId") 
                    ?: config.optJSONObject("rp")?.optString("id")
                    ?: throw IllegalArgumentException("RP ID not found in config")
                
                // 2. Lấy origin và hostname từ config
                val origin = config.optString("origin", "")
                val hostname = config.optString("hostname", "")
                
                // 3. Validate RP ID
                if (hostname.isNotEmpty() && rpId != hostname) {
                    val warningMsg = "RP ID ($rpId) does not match hostname ($hostname)"
                    Log.w(TAG, warningMsg)
                    logWriter.writePasskeyLog(warningMsg, "WARN")
                    // Có thể vẫn tiếp tục nếu RP ID là domain chính
                }
                
                // 4. Convert origin nếu cần (txahub:// → https://txahub.click)
                val credentialOrigin = when {
                    origin.startsWith("txahub://") -> "https://txahub.click"
                    origin.isNotEmpty() -> origin
                    else -> "https://txahub.click" // Fallback
                }
                
                // 5. Log debug
                val logMessage = "=== Creating Passkey ===\nRP ID: $rpId\nOrigin: $origin\nHostname: $hostname\nCredential Origin: $credentialOrigin"
                Log.d(TAG, logMessage)
                logWriter.writePasskeyLog(logMessage, "DEBUG")
                
                // Parse challenge
                val challengeBase64 = config.getString("challenge")
                val challenge = Base64.decode(challengeBase64, Base64.URL_SAFE or Base64.NO_WRAP)
                
                // Parse RP (Relying Party)
                val rp = config.getJSONObject("rp")
                val rpName = rp.optString("name", "TXA Hub")
                
                // Parse User
                val user = config.getJSONObject("user")
                val userIdBase64 = user.getString("id")
                val userId = Base64.decode(userIdBase64, Base64.URL_SAFE or Base64.NO_WRAP)
                val userName = user.getString("name")
                val userDisplayName = user.optString("displayName", userName)
                
                // Parse pubKeyCredParams (không cần parse, giữ nguyên JSON)
                val pubKeyCredParams = config.getJSONArray("pubKeyCredParams")
                
                // Parse timeout (optional)
                val timeout = config.optLong("timeout", 60000)
                
                // Parse attestation (optional)
                val attestation = config.optString("attestation", "none")
                
                // Tạo PublicKeyCredentialCreationOptions
                val requestJson = JSONObject().apply {
                    put("challenge", Base64.encodeToString(challenge, Base64.URL_SAFE or Base64.NO_WRAP))
                    put("rp", JSONObject().apply {
                        put("id", rpId)
                        put("name", rpName)
                    })
                    put("user", JSONObject().apply {
                        put("id", Base64.encodeToString(userId, Base64.URL_SAFE or Base64.NO_WRAP))
                        put("name", userName)
                        put("displayName", userDisplayName)
                    })
                    put("pubKeyCredParams", pubKeyCredParams)
                    put("timeout", timeout)
                    put("attestation", attestation)
                    // Thêm các trường khác nếu cần
                    if (config.has("excludeCredentials")) {
                        put("excludeCredentials", config.getJSONArray("excludeCredentials"))
                    }
                    if (config.has("authenticatorSelection")) {
                        put("authenticatorSelection", config.getJSONObject("authenticatorSelection"))
                    }
                }
                
                // 6. Tạo request cho Credential Manager với origin
                val createPublicKeyCredentialRequest = try {
                    // Thử tạo với origin parameter nếu API hỗ trợ
                    CreatePublicKeyCredentialRequest::class.java
                        .getConstructor(String::class.java, String::class.java)
                        .newInstance(requestJson.toString(), credentialOrigin)
                } catch (e: Exception) {
                    // Fallback: tạo không có origin (API cũ)
                    val warningMsg = "CreatePublicKeyCredentialRequest không hỗ trợ origin parameter, dùng constructor cũ"
                    Log.w(TAG, warningMsg)
                    logWriter.writePasskeyLog(warningMsg, "WARN")
                    CreatePublicKeyCredentialRequest(requestJson.toString())
                }
                
                val result = credentialManager.createCredential(
                    request = createPublicKeyCredentialRequest,
                    context = context as android.app.Activity
                )
                
                // result là CreateCredentialResponse, lấy credential từ result
                // Sử dụng reflection để truy cập property credential
                val credential = try {
                    val credentialField = result.javaClass.getDeclaredField("credential")
                    credentialField.isAccessible = true
                    credentialField.get(result) as? Credential
                } catch (e: Exception) {
                    // Fallback: thử truy cập trực tiếp nếu có
                    (result as? CreateCredentialResponse)?.let {
                        it.javaClass.getMethod("getCredential").invoke(it) as? Credential
                    }
                } ?: throw Exception("Cannot access credential from result")
                
                if (credential is PublicKeyCredential) {
                    val responseJson = credential.authenticationResponseJson
                    Log.d(TAG, "Passkey created successfully")
                    logWriter.writePasskeyLog("Passkey created successfully", "INFO")
                    
                    // Gửi kết quả về web
                    val jsCode = """
                        if(window.TXAApp && window.TXAApp.$callback) {
                            window.TXAApp.$callback({
                                success: true,
                                data: $responseJson
                            });
                        }
                    """.trimIndent()
                    webView.evaluateJavascript(jsCode, null)
                } else {
                    throw Exception("Unexpected credential type")
                }
                
            } catch (e: CreateCredentialException) {
                val errorMsg = "Error creating passkey: ${e.message}"
                Log.e(TAG, errorMsg, e)
                logWriter.writePasskeyLog("$errorMsg\n${e.stackTraceToString()}", "ERROR")
                handleCreateException(e, callback, webView)
            } catch (e: Exception) {
                val errorMsg = "Unexpected error creating passkey: ${e.message}"
                Log.e(TAG, errorMsg, e)
                logWriter.writePasskeyLog("$errorMsg\n${e.stackTraceToString()}", "ERROR")
                val jsCode = """
                    if(window.TXAApp && window.TXAApp.$callback) {
                        window.TXAApp.$callback({
                            success: false,
                            error: "${e.message ?: "Unknown error"}"
                        });
                    }
                """.trimIndent()
                webView.evaluateJavascript(jsCode, null)
            }
        }
    }
    
    /**
     * Lấy Passkey (Authentication)
     * @param config JSON string chứa cấu hình từ web:
     *   {
     *     "challenge": "base64_encoded_challenge",
     *     "rpId": "txahub.click",
     *     "allowCredentials": [...],
     *     "timeout": 60000,
     *     "userVerification": "preferred"
     *   }
     * @param callback JavaScript callback function name để trả kết quả
     */
    fun getPasskey(configJson: String, callback: String, webView: android.webkit.WebView) {
        coroutineScope.launch {
            try {
                val config = JSONObject(configJson)
                
                // 1. Lấy và validate RP ID
                val rpId = config.optString("rpId") 
                    ?: throw IllegalArgumentException("RP ID not found in config")
                
                // 2. Lấy origin và hostname từ config
                val origin = config.optString("origin", "")
                val hostname = config.optString("hostname", "")
                
                // 3. Convert origin nếu cần
                val credentialOrigin = when {
                    origin.startsWith("txahub://") -> "https://txahub.click"
                    origin.isNotEmpty() -> origin
                    else -> "https://txahub.click" // Fallback
                }
                
                // 4. Log debug
                val logMessage = "=== Getting Passkey ===\nRP ID: $rpId\nOrigin: $origin\nHostname: $hostname\nCredential Origin: $credentialOrigin"
                Log.d(TAG, logMessage)
                logWriter.writePasskeyLog(logMessage, "DEBUG")
                
                // Parse challenge
                val challengeBase64 = config.getString("challenge")
                val challenge = Base64.decode(challengeBase64, Base64.URL_SAFE or Base64.NO_WRAP)
                
                // Parse allowCredentials (optional)
                val allowCredentials = config.optJSONArray("allowCredentials")
                
                // Parse timeout (optional)
                val timeout = config.optLong("timeout", 60000)
                
                // Parse userVerification (optional)
                val userVerification = config.optString("userVerification", "preferred")
                
                // Tạo GetPublicKeyCredentialRequest
                val requestJson = JSONObject().apply {
                    put("challenge", Base64.encodeToString(challenge, Base64.URL_SAFE or Base64.NO_WRAP))
                    put("rpId", rpId)
                    put("timeout", timeout)
                    put("userVerification", userVerification)
                    if (allowCredentials != null) {
                        put("allowCredentials", allowCredentials)
                    }
                }
                
                // 5. Tạo request cho Credential Manager với origin
                val getPublicKeyCredentialOption = try {
                    // Thử tạo với origin parameter nếu API hỗ trợ
                    GetPublicKeyCredentialOption::class.java
                        .getConstructor(String::class.java, String::class.java)
                        .newInstance(requestJson.toString(), credentialOrigin)
                } catch (e: Exception) {
                    // Fallback: tạo không có origin (API cũ)
                    val warningMsg = "GetPublicKeyCredentialOption không hỗ trợ origin parameter, dùng constructor cũ"
                    Log.w(TAG, warningMsg)
                    logWriter.writePasskeyLog(warningMsg, "WARN")
                    GetPublicKeyCredentialOption(requestJson.toString())
                }
                
                val getCredentialRequest = GetCredentialRequest(
                    listOf(getPublicKeyCredentialOption)
                )
                
                val result = credentialManager.getCredential(
                    request = getCredentialRequest,
                    context = context as android.app.Activity
                )
                
                // result là GetCredentialResponse, lấy credential từ result
                // Sử dụng reflection để truy cập property credential
                val credential = try {
                    val credentialField = result.javaClass.getDeclaredField("credential")
                    credentialField.isAccessible = true
                    credentialField.get(result) as? Credential
                } catch (e: Exception) {
                    // Fallback: thử truy cập trực tiếp nếu có
                    (result as? GetCredentialResponse)?.let {
                        it.javaClass.getMethod("getCredential").invoke(it) as? Credential
                    }
                } ?: throw Exception("Cannot access credential from result")
                
                if (credential is PublicKeyCredential) {
                    val responseJson = credential.authenticationResponseJson
                    Log.d(TAG, "Passkey retrieved successfully")
                    logWriter.writePasskeyLog("Passkey retrieved successfully", "INFO")
                    
                    // Gửi kết quả về web
                    val jsCode = """
                        if(window.TXAApp && window.TXAApp.$callback) {
                            window.TXAApp.$callback({
                                success: true,
                                data: $responseJson
                            });
                        }
                    """.trimIndent()
                    webView.evaluateJavascript(jsCode, null)
                } else {
                    throw Exception("Unexpected credential type")
                }
                
            } catch (e: GetCredentialException) {
                val errorMsg = "Error getting passkey: ${e.message}"
                Log.e(TAG, errorMsg, e)
                logWriter.writePasskeyLog("$errorMsg\n${e.stackTraceToString()}", "ERROR")
                handleGetException(e, callback, webView)
            } catch (e: Exception) {
                val errorMsg = "Unexpected error getting passkey: ${e.message}"
                Log.e(TAG, errorMsg, e)
                logWriter.writePasskeyLog("$errorMsg\n${e.stackTraceToString()}", "ERROR")
                val jsCode = """
                    if(window.TXAApp && window.TXAApp.$callback) {
                        window.TXAApp.$callback({
                            success: false,
                            error: "${e.message ?: "Unknown error"}"
                        });
                    }
                """.trimIndent()
                webView.evaluateJavascript(jsCode, null)
            }
        }
    }
    
    /**
     * Xử lý exception khi tạo Passkey
     */
    private fun handleCreateException(
        e: CreateCredentialException,
        callback: String,
        webView: android.webkit.WebView
    ) {
        val errorCode = when (e) {
            is CreateCredentialCancellationException -> "NotAllowedError"
            is CreateCredentialInterruptedException -> "NotAllowedError"
            is CreateCredentialProviderConfigurationException -> "NotSupportedError"
            is CreateCredentialUnknownException -> "UnknownError"
            is CreateCredentialUnsupportedException -> "NotSupportedError"
            else -> "UnknownError"
        }
        
        val errorMessage = e.errorMessage ?: "Unknown error"
        
        val jsCode = """
            if(window.TXAApp && window.TXAApp.$callback) {
                window.TXAApp.$callback({
                    success: false,
                    error: {
                        code: "$errorCode",
                        message: "$errorMessage"
                    }
                });
            }
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }
    
    /**
     * Xử lý exception khi lấy Passkey
     */
    private fun handleGetException(
        e: GetCredentialException,
        callback: String,
        webView: android.webkit.WebView
    ) {
        val errorCode = when (e) {
            is GetCredentialCancellationException -> "NotAllowedError"
            is GetCredentialInterruptedException -> "NotAllowedError"
            is GetCredentialProviderConfigurationException -> "NotSupportedError"
            is GetCredentialUnknownException -> "UnknownError"
            is GetCredentialUnsupportedException -> "NotSupportedError"
            else -> "UnknownError"
        }
        
        val errorMessage = e.errorMessage ?: "Unknown error"
        
        val jsCode = """
            if(window.TXAApp && window.TXAApp.$callback) {
                window.TXAApp.$callback({
                    success: false,
                    error: {
                        code: "$errorCode",
                        message: "$errorMessage"
                    }
                });
            }
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }
    
    /**
     * Kiểm tra xem Passkey có được hỗ trợ không
     */
    fun isPasskeySupported(): Boolean {
        return try {
            // Kiểm tra xem CredentialManager có sẵn không
            CredentialManager.create(context)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Cleanup
     */
    fun cleanup() {
        coroutineScope.cancel()
    }
}

