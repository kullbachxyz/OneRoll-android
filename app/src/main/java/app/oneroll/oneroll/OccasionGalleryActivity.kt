package app.oneroll.oneroll

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import app.oneroll.oneroll.databinding.ActivityGalleryBinding
import app.oneroll.oneroll.model.OneRollConfig
import app.oneroll.oneroll.storage.ConfigStorage
import app.oneroll.oneroll.storage.OccasionPhotoRepository
import app.oneroll.oneroll.ui.gallery.GridSpacingDecoration
import app.oneroll.oneroll.ui.gallery.PhotoThumbnailAdapter
import app.oneroll.oneroll.upload.WebDavDownloader
import java.io.File

class OccasionGalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val configStorage by lazy { ConfigStorage(this) }
    private val occasionRepository by lazy { OccasionPhotoRepository(this) }
    private val webDavDownloader by lazy { WebDavDownloader(this) }
    private val adapter by lazy { PhotoThumbnailAdapter { _, index -> openPreview(index) } }
    private var photos: List<File> = emptyList()
    private var config: OneRollConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyWindowInsets()

        config = configStorage.loadConfig()
        val cfg = config
        if (cfg == null) {
            startActivity(Intent(this, QrScanActivity::class.java))
            finish()
            return
        }

        binding.galleryTitle.text = getString(R.string.occasion_gallery_title, cfg.occasionName)
        binding.galleryList.layoutManager = GridLayoutManager(this, 3)
        binding.galleryList.adapter = adapter
        if (binding.galleryList.itemDecorationCount == 0) {
            val spacing = resources.getDimensionPixelSize(R.dimen.gallery_spacing)
            binding.galleryList.addItemDecoration(GridSpacingDecoration(spacing))
        }

        binding.closeButton.setOnClickListener { finish() }
        refreshPhotos()
        fetchOccasionPhotos(cfg)
    }

    override fun onDestroy() {
        super.onDestroy()
        webDavDownloader.shutdown()
    }

    private fun fetchOccasionPhotos(cfg: OneRollConfig) {
        setLoading(true)
        webDavDownloader.syncOccasionPhotos(
            config = cfg,
            repository = occasionRepository,
            onProgress = { refreshPhotos() }
        ) { result ->
            setLoading(false)
            result.onFailure { error ->
                Toast.makeText(
                    this,
                    getString(
                        R.string.occasion_gallery_error,
                        error.localizedMessage ?: error.toString()
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
            refreshPhotos()
        }
    }

    private fun refreshPhotos() {
        photos = occasionRepository.listPhotos()
        adapter.submitList(photos)
        binding.emptyMessage.isVisible = photos.isEmpty() && !binding.loadingIndicator.isVisible
    }

    private fun openPreview(index: Int) {
        val paths = ArrayList(photos.map { it.absolutePath })
        val intent = Intent(this, PhotoPreviewActivity::class.java).apply {
            putStringArrayListExtra(PhotoPreviewActivity.EXTRA_PATHS, paths)
            putExtra(PhotoPreviewActivity.EXTRA_INDEX, index)
        }
        startActivity(intent)
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingIndicator.isVisible = isLoading
        binding.galleryList.alpha = if (isLoading) 0.4f else 1f
        binding.emptyMessage.isVisible = photos.isEmpty() && !isLoading
    }

    private fun applyWindowInsets() {
        val baseTop = binding.galleryList.paddingTop
        val baseBottom = binding.galleryList.paddingBottom
        val baseCloseBottom =
            (binding.closeButton.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        val baseTitleTop =
            (binding.galleryTitle.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.galleryTitle.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = baseTitleTop + systemInsets.top
            }
            binding.galleryList.setPadding(
                binding.galleryList.paddingLeft,
                baseTop + systemInsets.top,
                binding.galleryList.paddingRight,
                baseBottom + systemInsets.bottom
            )
            binding.closeButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseCloseBottom + systemInsets.bottom
            }
            binding.loadingIndicator.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemInsets.bottom
            }
            binding.emptyMessage.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemInsets.bottom
            }
            insets
        }
    }
}
