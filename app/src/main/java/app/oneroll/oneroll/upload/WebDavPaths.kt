package app.oneroll.oneroll.upload

import android.content.Context
import android.provider.Settings
import app.oneroll.oneroll.model.WebDavConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object WebDavPaths {
    fun deviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
    }

    fun buildDeviceFolderUrl(webDav: WebDavConfig, deviceId: String): HttpUrl? {
        val base = webDav.baseURL.toHttpUrlOrNull() ?: return null
        val cleanedSegments = webDav.path.trim('/').split('/').filter { it.isNotBlank() }
        val builder = base.newBuilder().encodedPath("/")
        cleanedSegments.forEach { segment -> builder.addPathSegment(segment) }
        builder.addPathSegment(deviceId)
        return builder.build()
    }
}
