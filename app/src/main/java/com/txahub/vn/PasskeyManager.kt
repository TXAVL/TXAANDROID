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
                
                // Parse challenge
                val challengeBase64 = config.getString("challenge")
                val challenge = Base64.decode(challengeBase64, Base64.URL_SAFE or Base64.NO_WRAP)
                
                // Parse RP (Relying Party)
                val rp = config.getJSONObject("rp")
                val rpId = rp.getString("id")
                val rpName = rp.getString("name")
                
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
                
                val createPublicKeyCredentialRequest = CreatePublicKeyCredentialRequest(
                    requestJson.toString()
                )
                
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
                Log.e(TAG, "Error creating passkey", e)
                handleCreateException(e, callback, webView)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error creating passkey", e)
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
                
                // Parse challenge
                val challengeBase64 = config.getString("challenge")
                val challenge = Base64.decode(challengeBase64, Base64.URL_SAFE or Base64.NO_WRAP)
                
                // Parse rpId
                val rpId = config.getString("rpId")
                
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
                
                val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(
                    requestJson.toString()
                )
                
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
                Log.e(TAG, "Error getting passkey", e)
                handleGetException(e, callback, webView)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error getting passkey", e)
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

