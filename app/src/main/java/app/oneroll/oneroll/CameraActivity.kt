package app.oneroll.oneroll

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.OrientationEventListener
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import app.oneroll.oneroll.databinding.ActivityCameraBinding
import app.oneroll.oneroll.model.OneRollConfig
import app.oneroll.oneroll.storage.ConfigStorage
import app.oneroll.oneroll.storage.PhotoRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var preview: Preview
    private lateinit var cameraExecutor: ExecutorService
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var camera: Camera? = null
    private val configStorage by lazy { ConfigStorage(this) }
    private val photoRepository by lazy { PhotoRepository(this) }
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
        checkPermissionAndStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
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
        binding.switchCamera.setOnClickListener {
            cameraSelector =
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            startCamera()
        }
        binding.flashToggle.setOnClickListener { toggleFlash() }
        binding.captureButton.setOnClickListener { capturePhoto() }
        binding.settingsButton.setOnClickListener { showSettings() }
        binding.galleryButton.setOnClickListener { openGallery() }
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
                    runOnUiThread { refreshGallery() }
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
                Toast.makeText(this, R.string.photos_cleared, Toast.LENGTH_SHORT).show()
                refreshGallery()
            }
            .setNegativeButton(R.string.clear_config) { _, _ ->
                configStorage.clearConfig()
                photoRepository.clearPhotos()
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
}
