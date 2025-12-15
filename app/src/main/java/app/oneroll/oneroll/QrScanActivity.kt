package app.oneroll.oneroll

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import app.oneroll.oneroll.databinding.ActivityQrScanBinding
import app.oneroll.oneroll.storage.ConfigStorage
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory

class QrScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScanBinding
    private val configStorage by lazy { ConfigStorage(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyWindowInsets()

        binding.scannerView.decoderFactory = DefaultDecoderFactory(
            listOf(BarcodeFormat.QR_CODE)
        )
        binding.scannerView.decodeContinuous(barcodeCallback)
    }

    override fun onResume() {
        super.onResume()
        binding.scannerView.resume()
    }

    override fun onPause() {
        binding.scannerView.pause()
        super.onPause()
    }

    private fun applyWindowInsets() {
        val instructionBaseTop =
            (binding.instructionText.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
        val statusView = binding.scannerView.statusView
        val statusBaseBottom =
            (statusView?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        val statusExtraBottom =
            resources.getDimensionPixelSize(R.dimen.qr_status_bottom_padding)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.instructionText.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = instructionBaseTop + systemInsets.top
            }
            statusView?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = statusBaseBottom + statusExtraBottom + systemInsets.bottom
            }
            insets
        }
    }

    private val barcodeCallback = BarcodeCallback { result: BarcodeResult ->
        val raw = result.text ?: return@BarcodeCallback
        handleScan(raw)
    }

    private fun handleScan(raw: String) {
        binding.scannerView.pause()
        configStorage.saveConfig(raw)
            .onSuccess {
                Toast.makeText(this, getString(R.string.config_saved), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, CameraActivity::class.java))
                finish()
            }
            .onFailure { error ->
                Toast.makeText(
                    this,
                    getString(R.string.config_error, error.localizedMessage),
                    Toast.LENGTH_LONG
                ).show()
                binding.scannerView.resume()
            }
    }
}
