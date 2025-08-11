package com.daasuu.llmsample.data.model_manager

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    data class DownloadProgress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progress: Float
    )
    
    suspend fun downloadModel(
        url: String,
        destinationFile: File,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // ディレクトリを作成
            destinationFile.parentFile?.mkdirs()
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L
            
            connection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytes: Int
                    
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloadedBytes += bytes
                        
                        val progress = if (totalBytes > 0) {
                            downloadedBytes.toFloat() / totalBytes
                        } else {
                            0f
                        }
                        
                        onProgress(DownloadProgress(downloadedBytes, totalBytes, progress))
                    }
                }
            }
            
            Result.success(destinationFile)
        } catch (e: Exception) {
            // エラー時はファイルを削除
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            Result.failure(e)
        }
    }
    
    fun downloadModelFlow(
        url: String,
        destinationFile: File
    ): Flow<DownloadProgress> = flow {
        // ディレクトリを作成
        destinationFile.parentFile?.mkdirs()
        
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        
        val totalBytes = connection.contentLength.toLong()
        var downloadedBytes = 0L
        
        connection.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(8192)
                var bytes: Int
                
                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    downloadedBytes += bytes
                    
                    val progress = if (totalBytes > 0) {
                        downloadedBytes.toFloat() / totalBytes
                    } else {
                        0f
                    }
                    
                    emit(DownloadProgress(downloadedBytes, totalBytes, progress))
                }
            }
        }
    }
}