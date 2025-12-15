package app.oneroll.oneroll

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.exifinterface.media.ExifInterface
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import app.oneroll.oneroll.databinding.ActivityCameraBinding
import app.oneroll.oneroll.model.OneRollConfig
import app.oneroll.oneroll.storage.ConfigStorage
import app.oneroll.oneroll.storage.OccasionPhotoRepository
import app.oneroll.oneroll.storage.PhotoRepository
import app.oneroll.oneroll.upload.WebDavDownloader
import app.oneroll.oneroll.upload.WebDavUploader
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var preview: Preview
    private lateinit var cameraExecutor: ExecutorService
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var camera: Camera? = null
    private val scaleGestureDetector by lazy { ScaleGestureDetector(this, ZoomGestureListener()) }
    private val configStorage by lazy { ConfigStorage(this) }
    private val occasionPhotoRepository by lazy { OccasionPhotoRepository(this) }
    private val photoRepository by lazy { PhotoRepository(this) }
    private val webDavDownloader by lazy { WebDavDownloader(this) }
    private val webDavUploader by lazy { WebDavUploader(this) }
    private var config: OneRollConfig? = null
    private var orientationListener: OrientationEventListener? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.camera_permission_rationale),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()
        applyWindowInsets()

        config = configStorage.loadConfig()
        if (config == null) {
            startActivity(Intent(this, QrScanActivity::class.java))
            finish()
            return
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                updateRotation()
            }
        }
        initUi()
        syncExistingPhotosFromServer()
        checkPermissionAndStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        webDavUploader.shutdown()
        webDavDownloader.shutdown()
    }

    override fun onResume() {
        super.onResume()
        orientationListener?.enable()
        hideSystemBars()
    }

    override fun onPause() {
        orientationListener?.disable()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun initUi() {
        val cfg = config ?: return
        binding.occasionName.text = cfg.occasionName
        binding.occasionName.setOnClickListener { openOccasionGallery() }
        binding.switchCamera.setOnClickListener {
            cameraSelector =
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            startCamera()
        }
        binding.flashToggle.setOnClickListener { toggleFlash() }
        binding.captureButton.setOnClickListener { capturePhoto() }
        binding.settingsButton.setOnClickListener { showSettings() }
        binding.galleryButton.setOnClickListener { openGallery() }
        binding.previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.pointerCount > 1) {
                return@setOnTouchListener true
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> showFocusIndicator(event.x, event.y)
                MotionEvent.ACTION_UP -> focusOnPoint(event.x, event.y)
            }
            true
        }
        updateFlashIcon(false)

        refreshGallery()
    }

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> startCamera()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(this)
                    .setMessage(R.string.camera_permission_rationale)
                    .setPositiveButton(R.string.request_permission) { _, _ ->
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun applyWindowInsets() {
        val leftBaseMargin =
            (binding.topLeftContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
        val rightBaseMargin =
            (binding.topRightContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val topInset = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            binding.topLeftContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = leftBaseMargin + topInset
            }
            binding.topRightContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = rightBaseMargin + topInset
            }
            insets
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val rotation = binding.previewView.display?.rotation ?: Surface.ROTATION_0
            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
                it.targetRotation = rotation
            }
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(rotation)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                updateFlashIcon(false)
            } catch (exc: Exception) {
                Log.e("CameraActivity", "Camera binding failed", exc)
                Toast.makeText(this, "Camera failed: ${exc.localizedMessage}", Toast.LENGTH_LONG)
                    .show()
            }
            updateRotation()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val cfg = config ?: return
        val currentCount = photoRepository.listPhotos().size
        if (currentCount >= cfg.maxPhotos) {
            Toast.makeText(this, R.string.max_reached, Toast.LENGTH_SHORT).show()
            return
        }
        updateRotation()
        val outputFile = photoRepository.createOutputFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraActivity", "Photo capture failed", exception)
                    runOnUiThread {
                        Toast.makeText(
                            this@CameraActivity,
                            "Capture failed: ${exception.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    uploadPhoto(outputFile)
                    runOnUiThread {
                        refreshGallery()
                        showCaptureOverlay(outputFile)
                        flashScreen()
                    }
                }
            }
        )
    }

    private fun refreshGallery() {
        val photos = photoRepository.listPhotos()
        config?.let {
            binding.photoCount.text = getString(
                R.string.photos_count_label,
                photos.size,
                it.maxPhotos
            )
            binding.captureButton.isEnabled = photos.size < it.maxPhotos
        }
    }

    private fun syncExistingPhotosFromServer() {
        val cfg = config ?: return
        webDavDownloader.syncExistingPhotos(cfg, photoRepository) { result ->
            result.onSuccess { downloaded ->
                if (downloaded > 0) {
                    refreshGallery()
                }
            }
            result.onFailure { error ->
                Log.w("CameraActivity", "Failed to sync remote photos", error)
            }
        }
    }

    private fun toggleFlash() {
        if (!::imageCapture.isInitialized) return
        val newMode = if (imageCapture.flashMode == ImageCapture.FLASH_MODE_ON) {
            ImageCapture.FLASH_MODE_OFF
        } else {
            ImageCapture.FLASH_MODE_ON
        }
        imageCapture.flashMode = newMode
        updateFlashIcon(newMode == ImageCapture.FLASH_MODE_ON)
    }

    private fun showSettings() {
        val cfg = config ?: return
        val redactedRaw = cfg.rawJson.replace(Regex("(\"password\"\\s*:\\s*\")[^\"]+(\")"), "$1******$2")
        val message = buildString {
            appendLine(cfg.occasionName)
            appendLine(getString(R.string.photos_count_label, photoRepository.listPhotos().size, cfg.maxPhotos))
            appendLine()
            appendLine(getString(R.string.qr_raw_json, redactedRaw))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setMessage(message.trim())
            .setPositiveButton(R.string.wipe_photos) { _, _ ->
                photoRepository.clearPhotos()
                occasionPhotoRepository.clear()
                Toast.makeText(this, R.string.photos_cleared, Toast.LENGTH_SHORT).show()
                refreshGallery()
            }
            .setNegativeButton(R.string.clear_config) { _, _ ->
                configStorage.clearConfig()
                photoRepository.clearPhotos()
                occasionPhotoRepository.clear()
                Toast.makeText(this, R.string.config_cleared, Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, QrScanActivity::class.java))
                finish()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun openGallery() {
        startActivity(Intent(this, PhotoGalleryActivity::class.java))
    }

    private fun openOccasionGallery() {
        startActivity(Intent(this, OccasionGalleryActivity::class.java))
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun updateRotation() {
        if (!::imageCapture.isInitialized || !::preview.isInitialized) return
        val rotation = binding.previewView.display?.rotation ?: Surface.ROTATION_0
        imageCapture.targetRotation = rotation
        preview.targetRotation = rotation
    }

    private fun updateFlashIcon(isFlashOn: Boolean) {
        val icon = if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        val desc = if (isFlashOn) R.string.flash_on else R.string.flash_off
        binding.flashToggle.setIconResource(icon)
        binding.flashToggle.contentDescription = getString(desc)
    }

    private fun showFocusIndicator(x: Float, y: Float) {
        val indicator = binding.focusIndicator
        val indicatorSize = resources.getDimensionPixelSize(R.dimen.focus_indicator_size)
        val offsetX = binding.previewView.x + x - indicatorSize / 2f
        val offsetY = binding.previewView.y + y - indicatorSize / 2f

        indicator.animate().cancel()
        indicator.translationX = offsetX
        indicator.translationY = offsetY
        indicator.scaleX = 0.8f
        indicator.scaleY = 0.8f
        indicator.alpha = 0.2f
        indicator.visibility = View.VISIBLE
        indicator.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(90)
            .withEndAction {
                indicator.animate()
                    .alpha(0f)
                    .setStartDelay(180)
                    .setDuration(130)
                    .withEndAction { indicator.visibility = View.GONE }
                    .start()
            }
            .start()
    }

    private fun focusOnPoint(x: Float, y: Float) {
        val currentCamera = camera ?: return
        val factory = binding.previewView.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        currentCamera.cameraControl.startFocusAndMetering(action)
    }

    private fun showCaptureOverlay(file: File) {
        val overlay = binding.captureOverlay
        val targetWidth = overlay.width.takeIf { it > 0 } ?: binding.previewView.width
        val targetHeight = overlay.height.takeIf { it > 0 } ?: binding.previewView.height
        val bitmap = decodeScaledBitmap(file, targetWidth, targetHeight) ?: return

        overlay.setImageBitmap(bitmap)
        overlay.alpha = 1f
        overlay.visibility = View.VISIBLE
        overlay.animate()
            .alpha(0f)
            .setStartDelay(300)
            .setDuration(250)
            .withEndAction {
                overlay.setImageDrawable(null)
                overlay.visibility = View.GONE
                overlay.alpha = 1f
            }
            .start()
    }

    private fun decodeScaledBitmap(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (targetWidth == 0 || targetHeight == 0) return decodeOrientedBitmap(file)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val rotation = readRotationDegrees(file)
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
                halfHeight /= 2
                halfWidth /= 2
            }
        }
        return inSampleSize
    }

    private fun decodeOrientedBitmap(file: File): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val rotation = readRotationDegrees(file)
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun readRotationDegrees(file: File): Float {
        val exif = ExifInterface(file.absolutePath)
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }

    private fun flashScreen() {
        val flashView = binding.flashOverlay
        flashView.clearAnimation()
        flashView.alpha = 0f
        flashView.visibility = View.VISIBLE
        flashView.animate()
            .alpha(0.5f)
            .setDuration(60)
            .withEndAction {
                flashView.animate()
                    .alpha(0f)
                    .setDuration(140)
                    .withEndAction {
                        flashView.visibility = View.GONE
                    }
                    .start()
            }
            .start()
    }

    private fun uploadPhoto(file: File) {
        val cfg = config ?: return
        webDavUploader.uploadPhoto(file, cfg) { result ->
            result.onFailure { error ->
                Log.e("CameraActivity", "WebDAV upload failed", error)
                Toast.makeText(
                    this,
                    getString(R.string.upload_failed, error.localizedMessage ?: error.toString()),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private inner class ZoomGestureListener :
        ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentCamera = camera ?: return false
            val zoomState = currentCamera.cameraInfo.zoomState.value ?: return false
            val newZoom = (zoomState.zoomRatio * detector.scaleFactor)
                .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
            currentCamera.cameraControl.setZoomRatio(newZoom)
            return true
        }
    }
}
