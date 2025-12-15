package app.oneroll.oneroll.storage

import android.content.Context
import java.io.File
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

    fun clearPhotos() {
        photoDir.listFiles()?.forEach { it.delete() }
    }
}
