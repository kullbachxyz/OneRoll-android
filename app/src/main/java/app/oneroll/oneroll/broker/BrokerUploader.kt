package app.oneroll.oneroll.broker

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.oneroll.oneroll.model.OneRollConfig
import app.oneroll.oneroll.model.UploadAuth
import app.oneroll.oneroll.storage.ConfigStorage
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BrokerUploader(
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

    fun uploadPhoto(
        file: File,
        config: OneRollConfig,
        onComplete: (Result<Unit>) -> Unit
    ) {
        executor.execute {
            val result = runCatching { uploadBlocking(file, config) }
            callbackHandler.post { onComplete(result) }
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun uploadBlocking(file: File, config: OneRollConfig) {
        var auth = ensureAuth(config)
        try {
            client.uploadPhoto(config, auth, deviceId, file)
            return
        } catch (unauthorized: BrokerClient.UnauthorizedException) {
            auth = refreshAuth(config)
            client.uploadPhoto(config, auth, deviceId, file)
        }
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
}
