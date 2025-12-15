package app.oneroll.oneroll.upload

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.oneroll.oneroll.model.OneRollConfig
import app.oneroll.oneroll.storage.OccasionPhotoRepository
import app.oneroll.oneroll.storage.PhotoRepository
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource

class WebDavDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val deviceId: String = WebDavPaths.deviceId(appContext)

    fun syncExistingPhotos(
        config: OneRollConfig,
        repository: PhotoRepository,
        onComplete: (Result<Int>) -> Unit
    ) {
        executor.execute {
            val result = runCatching { syncBlocking(config, repository) }
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

    private fun syncBlocking(config: OneRollConfig, repository: PhotoRepository): Int {
        val folderUrl = WebDavPaths.buildDeviceFolderUrl(config.webDav, deviceId)
            ?: throw IllegalArgumentException("Invalid WebDAV base URL: ${config.webDav.baseURL}")
        val credential = Credentials.basic(config.webDav.username, config.webDav.password)

        ensureDeviceFolder(folderUrl, credential)
        val remotePhotos = listRemotePhotos(folderUrl, credential)
        var downloaded = 0
        remotePhotos.forEach { name ->
            if (repository.hasPhoto(name)) return@forEach
            if (downloadPhoto(folderUrl, name, credential, repository)) {
                downloaded++
            }
        }
        return downloaded
    }

    private fun syncOccasionBlocking(
        config: OneRollConfig,
        repository: OccasionPhotoRepository,
        onProgress: (() -> Unit)?
    ): Int {
        val rootUrl = WebDavPaths.buildOccasionFolderUrl(config.webDav)
            ?: throw IllegalArgumentException("Invalid WebDAV base URL: ${config.webDav.baseURL}")
        val credential = Credentials.basic(config.webDav.username, config.webDav.password)
        val deviceFolders = listCollections(rootUrl, credential)
        var downloaded = 0
        deviceFolders.forEach { folderName ->
            val folderUrl = rootUrl.newBuilder().addPathSegment(folderName).build()
            val remotePhotos = listRemotePhotos(folderUrl, credential)
            remotePhotos.forEach { name ->
                if (repository.hasPhoto(folderName, name)) return@forEach
                if (downloadPhoto(folderUrl, name, credential, repository, folderName)) {
                    downloaded++
                    onProgress?.let { callbackHandler.post(it) }
                }
            }
        }
        return downloaded
    }

    private fun listRemotePhotos(folderUrl: HttpUrl, credential: String): List<String> {
        return listEntries(folderUrl, credential)
            .filter { !it.isCollection && it.name.endsWith(".jpg", true) }
            .map { it.name }
    }

    private fun listCollections(folderUrl: HttpUrl, credential: String): List<String> {
        return listEntries(folderUrl, credential)
            .filter { it.isCollection }
            .map { it.name }
    }

    private fun listEntries(folderUrl: HttpUrl, credential: String): List<PropfindEntry> {
        val targetUrl = folderUrl.withTrailingSlash()
        val requests = listOf(
            buildPropfindRequest(targetUrl, credential, includeBody = false),
            buildPropfindRequest(targetUrl, credential, includeBody = true)
        )
        var lastError: IOException? = null
        requests.forEach { request ->
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 404) return emptyList()
                    if (!response.isSuccessful) {
                        lastError = IOException("PROPFIND failed with HTTP ${response.code}")
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    return parsePropfind(body)
                }
            } catch (ioe: IOException) {
                lastError = ioe
            }
        }
        lastError?.let { throw it }
        return emptyList()
    }

    private fun parsePropfind(xml: String): List<PropfindEntry> {
        if (xml.isBlank()) return emptyList()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Exception) {
                // Feature not available on all Android builds; best-effort hardening.
            }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val responses = document.getElementsByTagNameNS("*", "response")
        val entries = mutableListOf<PropfindEntry>()
        for (i in 0 until responses.length) {
            val element = responses.item(i) as? Element ?: continue
            val href = element.getElementsByTagNameNS("*", "href").item(0)?.textContent?.trim()
                ?: continue
            val name = href.trimEnd('/').substringAfterLast('/')
            if (name.isBlank()) continue
            val isCollection = element.getElementsByTagNameNS("*", "collection").length > 0
            entries += PropfindEntry(name = name, isCollection = isCollection)
        }
        return entries
    }

    private fun downloadPhoto(
        folderUrl: HttpUrl,
        name: String,
        credential: String,
        repository: PhotoRepository
    ): Boolean {
        val request = Request.Builder()
            .url(folderUrl.newBuilder().addPathSegment(name).build())
            .header(AUTHORIZATION_HEADER, credential)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Skipping download for $name, HTTP ${response.code}")
                return false
            }
            val body = response.body ?: return false
            repository.saveDownloadedPhoto(name, body.byteStream())
            return true
        }
    }

    private fun downloadPhoto(
        folderUrl: HttpUrl,
        name: String,
        credential: String,
        repository: OccasionPhotoRepository,
        deviceFolder: String
    ): Boolean {
        val request = Request.Builder()
            .url(folderUrl.newBuilder().addPathSegment(name).build())
            .header(AUTHORIZATION_HEADER, credential)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Skipping download for $deviceFolder/$name, HTTP ${response.code}")
                return false
            }
            val body = response.body ?: return false
            repository.saveDownloadedPhoto(deviceFolder, name, body.byteStream())
            return true
        }
    }

    private fun ensureDeviceFolder(folderUrl: HttpUrl, credential: String) {
        val request = Request.Builder()
            .url(folderUrl.withTrailingSlash())
            .header(AUTHORIZATION_HEADER, credential)
            .method("MKCOL", null)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) return
            if (response.code == 405) return // Already exists.
            Log.w(TAG, "MKCOL failed with HTTP ${response.code}")
        }
    }

    private fun buildPropfindRequest(
        folderUrl: HttpUrl,
        credential: String,
        includeBody: Boolean
    ): Request {
        val body: RequestBody? =
            if (includeBody) PROPFIND_BODY.toRequestBody(CONTENT_TYPE_XML) else EMPTY_BODY
        return Request.Builder()
            .url(folderUrl)
            .header(AUTHORIZATION_HEADER, credential)
            .header("Depth", "1") // Avoid Depth: infinity for compatibility.
            .method("PROPFIND", body)
            .build()
    }

    private fun HttpUrl.withTrailingSlash(): HttpUrl {
        return if (encodedPath.endsWith("/")) this else newBuilder().addPathSegment("").build()
    }

    companion object {
        private const val TAG = "WebDavDownloader"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private val CONTENT_TYPE_XML = "text/xml; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        private const val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:resourcetype/>
                    <d:getcontentlength/>
                </d:prop>
            </d:propfind>
        """
    }
}

private data class PropfindEntry(
    val name: String,
    val isCollection: Boolean
)
