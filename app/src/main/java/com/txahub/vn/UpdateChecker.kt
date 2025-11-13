package com.txahub.vn

import android.content.Context
import android.content.pm.PackageManager
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
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val json = JSONObject(response.toString())
                    val latestVersion = json.getString("version_name")
                    
                    // Nếu version hiện tại trùng với version mới nhất, trả về changelog
                    if (latestVersion == versionName) {
                        callback(json.getString("changelog"))
                    } else {
                        // Nếu không phải version mới nhất, có thể trả về changelog mặc định hoặc null
                        callback(null)
                    }
                } else {
                    callback(null)
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
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
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val json = JSONObject(response.toString())
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
                    if (isNewerVersion(updateInfo.versionName, currentVersion) || 
                        updateInfo.versionCode > getCurrentVersionCode()) {
                        callback(updateInfo)
                    } else {
                        callback(null) // Không có bản cập nhật
                    }
                } else {
                    callback(null) // Lỗi kết nối
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                callback(null) // Lỗi
            }
        }.start()
    }
}

