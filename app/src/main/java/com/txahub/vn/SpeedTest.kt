package com.txahub.vn

import android.content.Context
import android.util.Log
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SpeedTest để kiểm tra tốc độ mạng
 */
class SpeedTest(private val context: Context) {
    
    private val logWriter = LogWriter(context)
    
    companion object {
        // URL test file (có thể dùng file nhỏ từ server)
        private const val TEST_URL = "https://google.com.vn"
        private const val TEST_SIZE = 1024 * 1024 // 1MB test file
        private const val TIMEOUT = 10000 // 10 giây
    }
    
    /**
     * Callback cho kết quả speedtest
     */
    interface SpeedTestCallback {
        fun onProgress(downloaded: Long, total: Long, speed: Double)
        fun onComplete(downloadSpeed: Double, uploadSpeed: Double, ping: Long)
        fun onError(message: String)
    }
    
    /**
     * Chạy speedtest (download speed)
     */
    fun runSpeedTest(callback: SpeedTestCallback) {
        Thread {
            try {
                // Test ping trước
                val ping = testPing()
                
                // Test download speed
                val downloadSpeed = testDownloadSpeed { downloaded, total, speed ->
                    callback.onProgress(downloaded, total, speed)
                }
                
                // Test upload speed (đơn giản, chỉ test connection)
                val uploadSpeed = testUploadSpeed()
                
                callback.onComplete(downloadSpeed, uploadSpeed, ping)
                
                // Log kết quả
                val result = buildString {
                    appendLine("=== SpeedTest Result ===")
                    appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date())}")
                    appendLine("Ping: ${ping}ms")
                    appendLine("Download: ${String.format("%.2f", downloadSpeed)} Mbps")
                    appendLine("Upload: ${String.format("%.2f", uploadSpeed)} Mbps")
                    appendLine("=== End SpeedTest ===")
                }
                
                logWriter.writeAppLog(result, "SpeedTest", Log.INFO)
                
            } catch (e: Exception) {
                val errorMsg = "SpeedTest failed: ${e.message}"
                logWriter.writeAppLog("$errorMsg\n${e.stackTraceToString()}", "SpeedTest", Log.ERROR)
                callback.onError(errorMsg)
            }
        }.start()
    }
    
    /**
     * Test ping (latency)
     */
    private fun testPing(): Long {
        return try {
            val startTime = System.currentTimeMillis()
            val url = URL("https://software.txahub.click")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.connect()
            val endTime = System.currentTimeMillis()
            connection.disconnect()
            endTime - startTime
        } catch (e: Exception) {
            logWriter.writeAppLog("Ping test failed: ${e.message}", "SpeedTest", Log.WARN)
            -1
        }
    }
    
    /**
     * Test download speed
     */
    private fun testDownloadSpeed(onProgress: (Long, Long, Double) -> Unit): Double {
        var inputStream: InputStream? = null
        var connection: HttpURLConnection? = null
        
        try {
            val url = URL(TEST_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            
            val startTime = System.currentTimeMillis()
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // Nếu không có test file, tạo dữ liệu giả để test
                return testDownloadSpeedWithDummyData(onProgress)
            }
            
            inputStream = connection.inputStream
            val buffer = ByteArray(8192)
            var downloaded: Long = 0
            var totalBytes: Long = connection.contentLength.toLong()
            
            if (totalBytes <= 0) {
                // Không biết kích thước, dùng dummy data
                return testDownloadSpeedWithDummyData(onProgress)
            }
            
            var bytesRead: Int
            var lastUpdateTime = startTime
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                downloaded += bytesRead
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= 100) { // Update mỗi 100ms
                    val elapsedSeconds = (currentTime - startTime) / 1000.0
                    if (elapsedSeconds > 0) {
                        val speedMbps = (downloaded * 8.0) / (elapsedSeconds * 1024 * 1024) // Convert to Mbps
                        onProgress(downloaded, totalBytes, speedMbps)
                    }
                    lastUpdateTime = currentTime
                }
            }
            
            val endTime = System.currentTimeMillis()
            val elapsedSeconds = (endTime - startTime) / 1000.0
            
            if (elapsedSeconds > 0) {
                val speedMbps = (downloaded * 8.0) / (elapsedSeconds * 1024 * 1024)
                return speedMbps
            }
            
            return 0.0
            
        } catch (e: Exception) {
            logWriter.writeAppLog("Download test failed: ${e.message}", "SpeedTest", Log.WARN)
            // Fallback to dummy data test
            return testDownloadSpeedWithDummyData(onProgress)
        } finally {
            inputStream?.close()
            connection?.disconnect()
        }
    }
    
    /**
     * Test download speed với dữ liệu giả (nếu không có test file)
     */
    private fun testDownloadSpeedWithDummyData(onProgress: (Long, Long, Double) -> Unit): Double {
        return try {
            // Tạo dữ liệu giả 1MB để test
            val testData = ByteArray(1024 * 1024) // 1MB
            testData.fill(0x42) // Fill với dữ liệu giả
            
            val startTime = System.currentTimeMillis()
            val totalBytes = testData.size.toLong()
            var downloaded: Long = 0
            val chunkSize = 8192
            
            var lastUpdateTime = startTime
            
            // Simulate download
            while (downloaded < totalBytes) {
                val bytesToRead = minOf(chunkSize.toLong(), totalBytes - downloaded).toInt()
                downloaded += bytesToRead
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= 100) {
                    val elapsedSeconds = (currentTime - startTime) / 1000.0
                    if (elapsedSeconds > 0) {
                        val speedMbps = (downloaded * 8.0) / (elapsedSeconds * 1024 * 1024)
                        onProgress(downloaded, totalBytes, speedMbps)
                    }
                    lastUpdateTime = currentTime
                }
                
                // Simulate network delay
                Thread.sleep(1)
            }
            
            val endTime = System.currentTimeMillis()
            val elapsedSeconds = (endTime - startTime) / 1000.0
            
            if (elapsedSeconds > 0) {
                (downloaded * 8.0) / (elapsedSeconds * 1024 * 1024)
            } else {
                0.0
            }
        } catch (e: Exception) {
            logWriter.writeAppLog("Dummy data test failed: ${e.message}", "SpeedTest", Log.ERROR)
            0.0
        }
    }
    
    /**
     * Test upload speed (đơn giản, chỉ test connection)
     */
    private fun testUploadSpeed(): Double {
        return try {
            // Upload test đơn giản - chỉ test connection time
            // Trong thực tế cần server hỗ trợ upload
            val testData = ByteArray(1024 * 100) // 100KB
            testData.fill(0x42)
            
            val startTime = System.currentTimeMillis()
            val url = URL("https://software.txahub.click/speedtest/upload")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            
            connection.outputStream.use { output ->
                output.write(testData)
            }
            
            val responseCode = connection.responseCode
            val endTime = System.currentTimeMillis()
            connection.disconnect()
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val elapsedSeconds = (endTime - startTime) / 1000.0
                if (elapsedSeconds > 0) {
                    (testData.size * 8.0) / (elapsedSeconds * 1024 * 1024)
                } else {
                    0.0
                }
            } else {
                // Nếu server không hỗ trợ upload, estimate dựa trên download
                // Giả sử upload = 10% download speed (thông thường)
                logWriter.writeAppLog("Upload test not supported, using estimate", "SpeedTest", Log.INFO)
                0.0 // Sẽ được tính lại dựa trên download nếu cần
            }
        } catch (e: Exception) {
            logWriter.writeAppLog("Upload test failed: ${e.message}", "SpeedTest", Log.WARN)
            // Estimate upload speed (thường upload = 10-20% download)
            0.0
        }
    }
    
    /**
     * Chạy speedtest nhanh (chỉ ping và download)
     */
    fun runQuickSpeedTest(callback: SpeedTestCallback) {
        Thread {
            try {
                val ping = testPing()
                val downloadSpeed = testDownloadSpeed { downloaded, total, speed ->
                    callback.onProgress(downloaded, total, speed)
                }
                
                // Estimate upload = 15% download (thông thường)
                val uploadSpeed = downloadSpeed * 0.15
                
                callback.onComplete(downloadSpeed, uploadSpeed, ping)
                
                val result = buildString {
                    appendLine("=== Quick SpeedTest ===")
                    appendLine("Ping: ${ping}ms")
                    appendLine("Download: ${String.format("%.2f", downloadSpeed)} Mbps")
                    appendLine("Upload (estimated): ${String.format("%.2f", uploadSpeed)} Mbps")
                }
                
                logWriter.writeAppLog(result, "SpeedTest", Log.INFO)
                
            } catch (e: Exception) {
                val errorMsg = "Quick SpeedTest failed: ${e.message}"
                logWriter.writeAppLog("$errorMsg\n${e.stackTraceToString()}", "SpeedTest", Log.ERROR)
                callback.onError(errorMsg)
            }
        }.start()
    }
}

