package com.daasuu.llmsample.data.model_manager

import android.content.Context
import com.daasuu.llmsample.data.model.ModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
        onProgress: (DownloadProgress) -> Unit,
        model: ModelInfo,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // ディレクトリを作成
            destinationFile.parentFile?.mkdirs()

            // まずHEADリクエストでContent-Lengthを取得試行
            val totalBytes = try {
                val headConnection = URL(url).openConnection() as HttpURLConnection
                headConnection.requestMethod = "HEAD"
                headConnection.connectTimeout = 10000
                headConnection.readTimeout = 10000
                val contentLength = headConnection.contentLength.toLong()
                headConnection.disconnect()
                if (contentLength > 0) contentLength else -1L
            } catch (e: Exception) {
                android.util.Log.w(
                    "ModelDownloader",
                    "Failed to get content length via HEAD request: ${e.message}"
                )
                -1L
            }

            // 実際のダウンロード用接続
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            // HEADで取得できなかった場合はGETのContent-Lengthを試行
            val finalTotalBytes = if (totalBytes > 0) {
                totalBytes
            } else {
                val contentLength = connection.contentLength.toLong()
                if (contentLength > 0) contentLength else model.fileSize
            }

            android.util.Log.d(
                "ModelDownloader",
                "Downloading: $url, Total bytes: $finalTotalBytes"
            )

            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytes: Int

                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloadedBytes += bytes

                        val progress = if (finalTotalBytes > 0) {
                            downloadedBytes.toFloat() / finalTotalBytes
                        } else {
                            // Content-Lengthが取得できない場合は、ダウンロード中であることを示す
                            // 0.01f（1%）を最小値として、実際のプログレスは表示しない
                            0.01f
                        }

                        onProgress(DownloadProgress(downloadedBytes, finalTotalBytes, progress))
                    }
                }
            }

            android.util.Log.d("ModelDownloader", "Download completed: $downloadedBytes bytes")
            Result.success(destinationFile)
        } catch (e: Exception) {
            // エラー時はファイルを削除
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            Result.failure(e)
        }
    }
}