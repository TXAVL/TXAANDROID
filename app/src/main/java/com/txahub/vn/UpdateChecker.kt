package com.txahub.vn

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val changelog: String,
    val downloadUrl: String,
    val forceUpdate: Boolean = false
)

class UpdateChecker(private val context: Context) {
    
    private val API_URL = "https://software.txahub.click/product/txahubapp/lastest"
    private val logWriter = LogWriter(context)
    
    /**
     * Lấy version hiện tại của app
     */
    fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0_txa"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0_txa"
        }
    }
    
    /**
     * Lấy version code hiện tại
     */
    fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode.toInt()
        } catch (e: PackageManager.NameNotFoundException) {
            1
        }
    }
    
    /**
     * So sánh version
     * Trả về true nếu version mới hơn version hiện tại
     */
    private fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        // So sánh version code thay vì version name để đơn giản
        // Có thể cải thiện logic so sánh version name sau
        return try {
            val newCode = newVersion.split("_")[0].split(".").map { it.toInt() }
            val currentCode = currentVersion.split("_")[0].split(".").map { it.toInt() }
            
            when {
                newCode[0] > currentCode[0] -> true
                newCode[0] < currentCode[0] -> false
                newCode.size > 1 && currentCode.size > 1 -> {
                    when {
                        newCode[1] > currentCode[1] -> true
                        newCode[1] < currentCode[1] -> false
                        newCode.size > 2 && currentCode.size > 2 -> newCode[2] > currentCode[2]
                        else -> false
                    }
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Lấy changelog cho một version cụ thể
     */
    fun getChangelogForVersion(versionName: String, callback: (String?) -> Unit) {
        Thread {
            try {
                val url = URL(API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                
                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val responseString = response.toString()
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Ghi log success response
                    logWriter.writeApiLog(responseString, API_URL)
                    
                    try {
                        val json = JSONObject(responseString)
                        val latestVersion = json.getString("version_name")
                        
                        // Nếu version hiện tại trùng với version mới nhất, trả về changelog
                        if (latestVersion == versionName) {
                            callback(json.getString("changelog"))
                        } else {
                            // Nếu không phải version mới nhất, có thể trả về changelog mặc định hoặc null
                            callback(null)
                        }
                    } catch (e: Exception) {
                        // JSON parsing error
                        logWriter.writeApiLog("JSON Parse Error: ${e.message}\nResponse: $responseString", API_URL)
                        callback(null)
                    }
                } else {
                    // Xử lý error response (404, 500, etc.)
                    val errorMessage = try {
                        val errorJson = JSONObject(responseString)
                        errorJson.optString("message", errorJson.optString("error", "Unknown error"))
                    } catch (e: Exception) {
                        "HTTP $responseCode: ${responseString.take(200)}"
                    }
                    
                    logWriter.writeApiLog("API Error ($responseCode): $errorMessage\nResponse: $responseString", API_URL)
                    callback(null)
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                logWriter.writeApiLog("Exception in getChangelogForVersion: ${e.message}", API_URL)
                logWriter.writeAppLog("Exception in getChangelogForVersion: ${e.message}\n${e.stackTraceToString()}", "UpdateChecker", Log.ERROR)
                callback(null)
            }
        }.start()
    }
    
    /**
     * Kiểm tra cập nhật từ API
     */
    fun checkUpdate(callback: (UpdateInfo?) -> Unit) {
        Thread {
            try {
                val url = URL(API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                
                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val responseString = response.toString()
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Ghi log success response
                    logWriter.writeApiLog(responseString, API_URL)
                    
                    try {
                        val json = JSONObject(responseString)
                        
                        // Validate các trường bắt buộc
                        if (!json.has("version_name") || !json.has("version_code") || 
                            !json.has("release_date") || !json.has("changelog") || 
                            !json.has("download_url")) {
                            logWriter.writeApiLog("Missing required fields in API response: $responseString", API_URL)
                            callback(null)
                            connection.disconnect()
                            return@Thread
                        }
                        
                        val updateInfo = UpdateInfo(
                            versionName = json.getString("version_name"),
                            versionCode = json.getInt("version_code"),
                            releaseDate = json.getString("release_date"),
                            changelog = json.getString("changelog"),
                            downloadUrl = json.getString("download_url"),
                            forceUpdate = json.optBoolean("force_update", false)
                        )
                        
                        // Kiểm tra xem có phải bản mới hơn không
                        val currentVersion = getCurrentVersion()
                        val currentVersionCode = getCurrentVersionCode()
                        
                        // Chỉ báo update khi version thực sự mới hơn (không bằng)
                        val isVersionNewer = isNewerVersion(updateInfo.versionName, currentVersion)
                        val isCodeNewer = updateInfo.versionCode > currentVersionCode
                        
                        // Nếu version name hoặc version code bằng nhau thì không có update
                        val isSameVersion = updateInfo.versionName == currentVersion && 
                                           updateInfo.versionCode == currentVersionCode
                        
                        if (!isSameVersion && (isVersionNewer || isCodeNewer)) {
                            callback(updateInfo)
                        } else {
                            callback(null) // Không có bản cập nhật
                        }
                    } catch (e: Exception) {
                        // JSON parsing error hoặc missing fields
                        logWriter.writeApiLog("JSON Parse Error: ${e.message}\nResponse: $responseString", API_URL)
                        callback(null)
                    }
                } else {
                    // Xử lý error response (404, 500, etc.)
                    val errorMessage = try {
                        val errorJson = JSONObject(responseString)
                        errorJson.optString("message", errorJson.optString("error", "Unknown error"))
                    } catch (e: Exception) {
                        "HTTP $responseCode: ${responseString.take(200)}"
                    }
                    
                    logWriter.writeApiLog("API Error ($responseCode): $errorMessage\nResponse: $responseString", API_URL)
                    callback(null) // Lỗi kết nối
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                // Ghi log exception
                logWriter.writeApiLog("Exception in checkUpdate: ${e.message}\nStack: ${e.stackTraceToString()}", API_URL)
                logWriter.writeAppLog("Exception in checkUpdate: ${e.message}\n${e.stackTraceToString()}", "UpdateChecker", Log.ERROR)
                callback(null) // Lỗi
            }
        }.start()
    }
}

