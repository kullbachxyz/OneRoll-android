package app.oneroll.oneroll

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.oneroll.oneroll.databinding.ActivityPhotoPreviewBinding
import androidx.exifinterface.media.ExifInterface
import java.io.File

class PhotoPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoPreviewBinding
    private var paths: List<String> = emptyList()
    private var index: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        paths = intent.getStringArrayListExtra(EXTRA_PATHS) ?: emptyList()
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, paths.lastIndex.coerceAtLeast(0))

        binding.closeButton.setOnClickListener { finish() }
        binding.prevButton.setOnClickListener { showIndex(index - 1) }
        binding.nextButton.setOnClickListener { showIndex(index + 1) }

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
