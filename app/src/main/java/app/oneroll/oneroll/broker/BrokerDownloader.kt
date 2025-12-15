package app.oneroll.oneroll.broker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.oneroll.oneroll.model.OneRollConfig
import app.oneroll.oneroll.model.UploadAuth
import app.oneroll.oneroll.storage.ConfigStorage
import app.oneroll.oneroll.storage.OccasionPhotoRepository
import app.oneroll.oneroll.storage.PhotoRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BrokerDownloader(
    context: Context,
    private val configStorage: ConfigStorage
) {
    private val appContext = context.applicationContext
    private val client = BrokerClient()
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val deviceId: String = DeviceIdentity.deviceId(appContext)
    @Volatile
    private var cachedAuth: UploadAuth? = configStorage.loadConfig()?.uploadAuth

    fun syncExistingPhotos(
        config: OneRollConfig,
        repository: PhotoRepository,
        onComplete: (Result<Int>) -> Unit
    ) {
        executor.execute {
            val result = runCatching { syncOwnBlocking(config, repository) }
            callbackHandler.post { onComplete(result) }
        }
    }

    fun syncOccasionPhotos(
        config: OneRollConfig,
        repository: OccasionPhotoRepository,
        onProgress: (() -> Unit)? = null,
        onComplete: (Result<Int>) -> Unit
    ) {
        executor.execute {
            val result = runCatching { syncOccasionBlocking(config, repository, onProgress) }
            callbackHandler.post { onComplete(result) }
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun syncOwnBlocking(config: OneRollConfig, repository: PhotoRepository): Int {
        var auth = ensureAuth(config)
        val gallery = try {
            client.listGallery(config, auth)
        } catch (unauthorized: BrokerClient.UnauthorizedException) {
            auth = refreshAuth(config)
            client.listGallery(config, auth)
        }
        var downloaded = 0
        gallery.filter { it.deviceId == deviceId }.forEach { item ->
            val fileName = item.fileName.ifBlank { "${item.id}.jpg" }
            if (repository.hasPhoto(fileName)) return@forEach
            try {
                client.downloadTo(config, auth, item) { stream ->
                    repository.saveDownloadedPhoto(fileName, stream)
                }
                downloaded++
            } catch (ex: BrokerClient.UnauthorizedException) {
                auth = refreshAuth(config)
                client.downloadTo(config, auth, item) { stream ->
                    repository.saveDownloadedPhoto(fileName, stream)
                }
                downloaded++
            } catch (ex: Exception) {
                Log.w(TAG, "Skipping download for ${item.id}", ex)
            }
        }
        return downloaded
    }

    private fun syncOccasionBlocking(
        config: OneRollConfig,
        repository: OccasionPhotoRepository,
        onProgress: (() -> Unit)?
    ): Int {
        var auth = ensureAuth(config)
        val gallery = try {
            client.listGallery(config, auth)
        } catch (unauthorized: BrokerClient.UnauthorizedException) {
            auth = refreshAuth(config)
            client.listGallery(config, auth)
        }
        var downloaded = 0
        gallery.forEach { item ->
            val fileName = item.fileName.ifBlank { "${item.id}.jpg" }
            if (repository.hasPhoto(item.deviceId, fileName)) return@forEach
            try {
                client.downloadTo(config, auth, item) { stream ->
                    repository.saveDownloadedPhoto(item.deviceId, fileName, stream)
                }
                downloaded++
                onProgress?.let { callbackHandler.post(it) }
            } catch (unauthorized: BrokerClient.UnauthorizedException) {
                auth = refreshAuth(config)
                client.downloadTo(config, auth, item) { stream ->
                    repository.saveDownloadedPhoto(item.deviceId, fileName, stream)
                }
                downloaded++
                onProgress?.let { callbackHandler.post(it) }
            } catch (ex: Exception) {
                Log.w(TAG, "Skipping download for ${item.id}", ex)
            }
        }
        return downloaded
    }

    private fun ensureAuth(config: OneRollConfig): UploadAuth {
        val now = System.currentTimeMillis()
        val existing = cachedAuth ?: config.uploadAuth
        if (existing != null && !existing.isExpired(now)) {
            cachedAuth = existing
            return existing
        }
        return refreshAuth(config)
    }

    private fun refreshAuth(config: OneRollConfig): UploadAuth {
        val newAuth = client.enroll(config, deviceId)
        cachedAuth = newAuth
        configStorage.saveUploadAuth(newAuth)
        return newAuth
    }

    companion object {
        private const val TAG = "BrokerDownloader"
    }
}
