package com.txahub.vn

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class AudioTrimmer(private val context: Context) {
    
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 128000
    }
    
    /**
     * Cắt file audio từ startTime đến endTime (tính bằng milliseconds)
     * @param inputUri URI của file audio gốc
     * @param startTimeMs Thời gian bắt đầu (ms)
     * @param endTimeMs Thời gian kết thúc (ms)
     * @param outputFile File output
     * @return File đã cắt hoặc null nếu lỗi
     */
    fun trimAudio(
        inputUri: Uri,
        startTimeMs: Long,
        endTimeMs: Long,
        outputFile: File
    ): File? {
        return try {
            var extractor: MediaExtractor? = null
            var muxer: MediaMuxer? = null
            
            try {
                extractor = MediaExtractor()
                extractor.setDataSource(context, inputUri)
                
                // Tìm track audio
                var audioTrackIndex = -1
                var audioFormat: MediaFormat? = null
                
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("audio/") == true) {
                        audioTrackIndex = i
                        audioFormat = format
                        break
                    }
                }
                
                if (audioTrackIndex == -1 || audioFormat == null) {
                    android.util.Log.e("AudioTrimmer", "No audio track found")
                    return null
                }
                
                extractor.selectTrack(audioTrackIndex)
                
                // Tạo muxer
                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                
                // Thêm track vào muxer
                val muxerTrackIndex = muxer.addTrack(audioFormat)
                muxer.start()
                
                // Chuyển đổi thời gian từ milliseconds sang microseconds
                val startTimeUs = startTimeMs * 1000
                val endTimeUs = endTimeMs * 1000
                
                // Seek đến vị trí bắt đầu
                extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                
                val bufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                val buffer = ByteArray(bufferSize)
                val bufferInfo = android.media.MediaCodec.BufferInfo()
                
                var isEOS = false
                var presentationTimeUs: Long = 0
                
                while (!isEOS) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    
                    if (sampleSize < 0) {
                        isEOS = true
                        bufferInfo.size = 0
                    } else {
                        presentationTimeUs = extractor.sampleTime
                        
                        // Kiểm tra nếu đã vượt quá endTime
                        if (presentationTimeUs >= endTimeUs) {
                            isEOS = true
                            bufferInfo.size = 0
                        } else {
                            bufferInfo.presentationTimeUs = presentationTimeUs - startTimeUs
                            bufferInfo.flags = extractor.sampleFlags
                            bufferInfo.size = sampleSize
                            
                            muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                            extractor.advance()
                        }
                    }
                }
                
                muxer.stop()
                outputFile
            } finally {
                extractor?.release()
                muxer?.release()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioTrimmer", "Error trimming audio", e)
            null
        }
    }
    
    /**
     * Lấy thời lượng của file audio (milliseconds)
     */
    fun getAudioDuration(uri: Uri): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationString = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationString?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            android.util.Log.e("AudioTrimmer", "Error getting duration", e)
            0L
        }
    }
}

