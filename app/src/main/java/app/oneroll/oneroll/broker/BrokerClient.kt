package app.oneroll.oneroll.broker

import android.os.Build
import app.oneroll.oneroll.model.OneRollConfig
import app.oneroll.oneroll.model.UploadAuth
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class BrokerClient(
    private val client: OkHttpClient = OkHttpClient()
) {

    data class GalleryItem(
        val id: String,
        val fileName: String,
        val deviceId: String,
        val downloadUrl: String
    )

    fun enroll(config: OneRollConfig, deviceId: String): UploadAuth {
        val payload = JSONObject().apply {
            put("inviteToken", config.inviteToken)
            put("occasionId", config.occasionId)
            put("deviceId", deviceId)
            put("platform", "android")
            put("deviceModel", Build.MODEL ?: "unknown")
        }
        val request = Request.Builder()
            .url(endpoint(config, "enroll"))
            .post(payload.toString().toRequestBody(CONTENT_TYPE_JSON))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw response.asException("Enroll")
            }
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val token = json.optString("uploadToken")
                .ifBlank { json.optString("token") }
            if (token.isBlank()) {
                throw BrokerException("Enroll response missing upload token")
            }
            val expiresAt = parseExpiryMillis(json)
            return UploadAuth(token = token, expiresAtMillis = expiresAt)
        }
    }

    fun uploadPhoto(config: OneRollConfig, auth: UploadAuth, deviceId: String, file: File) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(CONTENT_TYPE_JPEG))
            .addFormDataPart("occasionId", config.occasionId)
            .addFormDataPart("deviceId", deviceId)
            .build()
        val request = Request.Builder()
            .url(endpoint(config, "upload"))
            .header(AUTHORIZATION_HEADER, bearer(auth))
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw response.asException("Upload")
            }
        }
    }

    fun listGallery(config: OneRollConfig, auth: UploadAuth): List<GalleryItem> {
        val request = Request.Builder()
            .url(endpoint(config, "gallery"))
            .header(AUTHORIZATION_HEADER, bearer(auth))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw response.asException("List gallery")
            }
            val body = response.body?.string().orEmpty()
            return parseGallery(body, config)
        }
    }

    fun downloadTo(
        config: OneRollConfig,
        auth: UploadAuth,
        item: GalleryItem,
        save: (java.io.InputStream) -> Unit
    ) {
        val request = Request.Builder()
            .url(resolveUrl(config, item.downloadUrl.ifBlank { "gallery/${item.id}" }))
            .header(AUTHORIZATION_HEADER, bearer(auth))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw response.asException("Download ${item.id}")
            }
            val body = response.body ?: throw BrokerException("Empty body for ${item.id}")
            body.byteStream().use(save)
        }
    }

    private fun parseGallery(body: String, config: OneRollConfig): List<GalleryItem> {
        if (body.isBlank()) return emptyList()
        val root = runCatching { JSONObject(body) }.getOrNull()
        val array: JSONArray = when {
            root == null -> runCatching { JSONArray(body) }.getOrDefault(JSONArray())
            root.has("items") -> root.getJSONArray("items")
            root.has("files") -> root.getJSONArray("files")
            root.has("data") -> root.getJSONArray("data")
            else -> JSONArray()
        }
        val items = mutableListOf<GalleryItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id")
                .ifBlank { obj.optString("uploadId") }
                .ifBlank { obj.optString("fileId") }
            val name = obj.optString("name")
                .ifBlank { obj.optString("fileName") }
                .ifBlank { obj.optString("filename") }
                .ifBlank { id }
            val deviceId = obj.optString("deviceId")
                .ifBlank { obj.optString("device") }
                .ifBlank { "unknown" }
            val downloadUrl = obj.optString("downloadUrl")
                .ifBlank { obj.optString("url") }
                .ifBlank {
                    if (id.isNotBlank()) {
                        endpoint(config, "gallery").newBuilder().addPathSegment(id).build().toString()
                    } else {
                        ""
                    }
                }
            if (downloadUrl.isBlank()) continue
            items += GalleryItem(
                id = id.ifBlank { name },
                fileName = name,
                deviceId = deviceId,
                downloadUrl = downloadUrl
            )
        }
        return items
    }

    private fun endpoint(config: OneRollConfig, pathSegment: String): HttpUrl {
        val base = config.brokerUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid broker URL: ${config.brokerUrl}")
        return base.newBuilder()
            .addPathSegment(pathSegment.trim('/'))
            .build()
    }

    private fun resolveUrl(config: OneRollConfig, candidate: String): HttpUrl {
        val parsed = candidate.toHttpUrlOrNull()
        if (parsed != null) return parsed
        val cleaned = candidate.trim('/').split('/').filter { it.isNotBlank() }
        val builder = config.brokerUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?: throw IllegalArgumentException("Invalid broker URL: ${config.brokerUrl}")
        cleaned.forEach { segment -> builder.addPathSegment(segment) }
        return builder.build()
    }

    private fun parseExpiryMillis(json: JSONObject): Long? {
        val numericKeys = listOf("uploadTokenExpiresAt", "expiresAt", "expires", "uploadTokenExpiresInSeconds")
        numericKeys.forEach { key ->
            val value = json.opt(key)
            if (value is Number) {
                val raw = value.toLong()
                return when {
                    raw > 1_000_000_000_000L -> raw
                    raw > 0 -> raw * 1000
                    else -> null
                }
            }
        }
        val stringKeys = listOf("uploadTokenExpiresAt", "expiresAt")
        stringKeys.forEach { key ->
            val value = json.optString(key)
            if (value.isNullOrBlank()) return@forEach
            parseIsoInstant(value)?.let { return it }
        }
        return null
    }

    private fun parseIsoInstant(value: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = formatter.parse(value) ?: continue
                return parsed.time
            } catch (_: Exception) {
                // Try the next pattern.
            }
        }
        return null
    }

    private fun Response.asException(action: String): IOException {
        val message = body?.string().orEmpty().takeIf { it.isNotBlank() }
        val prefix = "$action failed with HTTP $code"
        if (code == 401 || code == 403) {
            return UnauthorizedException("$prefix (unauthorized)")
        }
        return BrokerException(
            if (message.isNullOrBlank()) prefix else "$prefix: $message"
        )
    }

    class BrokerException(message: String) : IOException(message)
    class UnauthorizedException(message: String) : IOException(message)

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private val CONTENT_TYPE_JSON = "application/json".toMediaType()
        private val CONTENT_TYPE_JPEG = "image/jpeg".toMediaType()
        private fun bearer(auth: UploadAuth) = "Bearer ${auth.token}"
    }
}
