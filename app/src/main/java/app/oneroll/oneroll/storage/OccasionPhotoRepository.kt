package app.oneroll.oneroll.storage

import android.content.Context
import java.io.File
import java.io.InputStream

class OccasionPhotoRepository(context: Context) {
    private val baseDir = File(context.filesDir, "occasion_photos").apply { mkdirs() }

    fun listPhotos(): List<File> {
        return baseDir.listFiles()?.flatMap { deviceDir ->
            if (deviceDir.isDirectory) {
                deviceDir.listFiles()?.toList().orEmpty()
            } else {
                emptyList()
            }
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun hasPhoto(deviceFolder: String, fileName: String): Boolean {
        return File(deviceDirectory(deviceFolder), fileName).exists()
    }

    fun saveDownloadedPhoto(deviceFolder: String, fileName: String, inputStream: InputStream) {
        val target = File(deviceDirectory(deviceFolder), fileName)
        inputStream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun clear() {
        baseDir.deleteRecursively()
        baseDir.mkdirs()
    }

    private fun deviceDirectory(deviceFolder: String): File {
        return File(baseDir, deviceFolder).apply { mkdirs() }
    }
}
