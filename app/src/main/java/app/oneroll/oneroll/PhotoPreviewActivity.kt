package app.oneroll.oneroll

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import app.oneroll.oneroll.databinding.ActivityPhotoPreviewBinding
import androidx.exifinterface.media.ExifInterface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.oneroll.oneroll.storage.PhotoSaver
import java.io.File

class PhotoPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoPreviewBinding
    private var paths: List<String> = emptyList()
    private var index: Int = 0
    private var pendingDownloadPath: String? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val targetPath = pendingDownloadPath ?: return@registerForActivityResult
            pendingDownloadPath = null
            if (granted) {
                savePhoto(File(targetPath))
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.storage_permission_required),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        paths = intent.getStringArrayListExtra(EXTRA_PATHS) ?: emptyList()
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, paths.lastIndex.coerceAtLeast(0))

        binding.prevButton.setOnClickListener { showIndex(index - 1) }
        binding.nextButton.setOnClickListener { showIndex(index + 1) }
        binding.downloadButton.setOnClickListener { downloadCurrent() }

        showIndex(index)
    }

    private fun showIndex(newIndex: Int) {
        if (paths.isEmpty()) {
            finish()
            return
        }
        index = newIndex.coerceIn(0, paths.lastIndex)
        val file = File(paths[index])
        val bitmap = decodeOriented(file)
        binding.previewImage.setImageBitmap(bitmap)
        binding.counter.text = getString(R.string.photos_count_label, index + 1, paths.size)

        binding.prevButton.isEnabled = index > 0
        binding.nextButton.isEnabled = index < paths.lastIndex
    }

    private fun downloadCurrent() {
        if (paths.isEmpty()) return
        val file = File(paths[index])
        if (needsStoragePermission()) {
            pendingDownloadPath = file.absolutePath
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            savePhoto(file)
        }
    }

    private fun savePhoto(file: File) {
        PhotoSaver.saveToGallery(this, file)
            .onSuccess {
                Toast.makeText(this, getString(R.string.download_success), Toast.LENGTH_SHORT)
                    .show()
            }
            .onFailure { error ->
                Toast.makeText(
                    this,
                    getString(R.string.download_failed, error.localizedMessage ?: error.toString()),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun needsStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return false
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED
    }

    private fun decodeOriented(file: File): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val exif = ExifInterface(file.absolutePath)
        val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        const val EXTRA_PATHS = "extra_paths"
        const val EXTRA_INDEX = "extra_index"
    }
}
