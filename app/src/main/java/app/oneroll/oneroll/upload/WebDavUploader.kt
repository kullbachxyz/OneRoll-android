package app.oneroll.oneroll.upload

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.oneroll.oneroll.model.OneRollConfig
import app.oneroll.oneroll.model.WebDavConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

class WebDavUploader(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val deviceId: String = WebDavPaths.deviceId(appContext)

    fun uploadPhoto(
        file: File,
        config: OneRollConfig,
        onComplete: (Result<Unit>) -> Unit
    ) {
        executor.execute {
            val result = runCatching { uploadBlocking(file, config.webDav) }
            callbackHandler.post { onComplete(result) }
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun uploadBlocking(file: File, webDav: WebDavConfig) {
        val folderUrl = WebDavPaths.buildDeviceFolderUrl(webDav, deviceId)
            ?: throw IllegalArgumentException("Invalid WebDAV base URL: ${webDav.baseURL}")
        val credential = Credentials.basic(webDav.username, webDav.password)

        ensureDeviceFolder(folderUrl, credential)
        val uploadUrl = folderUrl.newBuilder()
            .addPathSegment(file.name)
            .build()

        val request = Request.Builder()
            .url(uploadUrl)
            .header(AUTHORIZATION_HEADER, credential)
            .put(file.asRequestBody(CONTENT_TYPE_JPEG))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Upload failed with HTTP ${response.code}")
            }
        }
    }

    private fun ensureDeviceFolder(folderUrl: HttpUrl, credential: String) {
        val request = Request.Builder()
            .url(folderUrl)
            .header(AUTHORIZATION_HEADER, credential)
            .method("MKCOL", null)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) return
            // 405 indicates the collection already exists, which is fine for our use case.
            if (response.code == 405) {
                Log.d(TAG, "WebDAV folder already exists at $folderUrl")
                return
            }
            throw IOException("MKCOL failed with HTTP ${response.code}")
        }
    }

    companion object {
        private const val TAG = "WebDavUploader"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private val CONTENT_TYPE_JPEG = "image/jpeg".toMediaType()
    }
}
