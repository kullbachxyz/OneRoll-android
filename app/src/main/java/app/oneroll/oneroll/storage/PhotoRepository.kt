package app.oneroll.oneroll.storage

import android.content.Context
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoRepository(context: Context) {
    private val photoDir = File(context.filesDir, "photos").apply { mkdirs() }

    fun createOutputFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return File(photoDir, "IMG_$timestamp.jpg")
    }

    fun listPhotos(): List<File> {
        return photoDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun hasPhoto(fileName: String): Boolean {
        return File(photoDir, fileName).exists()
    }

    fun saveDownloadedPhoto(fileName: String, inputStream: InputStream) {
        val target = File(photoDir, fileName)
        inputStream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun clearPhotos() {
        photoDir.listFiles()?.forEach { it.delete() }
    }
}
