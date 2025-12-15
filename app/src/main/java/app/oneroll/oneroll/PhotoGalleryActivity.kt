package app.oneroll.oneroll

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import app.oneroll.oneroll.databinding.ActivityGalleryBinding
import app.oneroll.oneroll.storage.PhotoRepository
import app.oneroll.oneroll.ui.gallery.PhotoThumbnailAdapter

class PhotoGalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val photoRepository by lazy { PhotoRepository(this) }
    private val adapter by lazy { PhotoThumbnailAdapter { _, index -> openPreview(index) } }
    private var photos: List<java.io.File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyWindowInsets()

        binding.galleryList.layoutManager = GridLayoutManager(this, 3)
        binding.galleryList.adapter = adapter
        photos = photoRepository.listPhotos()
        adapter.submitList(photos)

        binding.closeButton.setOnClickListener { finish() }
    }

    private fun openPreview(index: Int) {
        val paths = ArrayList(photos.map { it.absolutePath })
        val intent = Intent(this, PhotoPreviewActivity::class.java).apply {
            putStringArrayListExtra(PhotoPreviewActivity.EXTRA_PATHS, paths)
            putExtra(PhotoPreviewActivity.EXTRA_INDEX, index)
        }
        startActivity(intent)
    }

    private fun applyWindowInsets() {
        val baseTop = binding.galleryList.paddingTop
        val baseBottom = binding.galleryList.paddingBottom
        val baseCloseBottom =
            (binding.closeButton.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.galleryList.setPadding(
                binding.galleryList.paddingLeft,
                baseTop + systemInsets.top,
                binding.galleryList.paddingRight,
                baseBottom + systemInsets.bottom
            )
            binding.closeButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseCloseBottom + systemInsets.bottom
            }
            insets
        }
    }
}
