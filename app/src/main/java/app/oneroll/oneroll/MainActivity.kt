package app.oneroll.oneroll

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.oneroll.oneroll.storage.ConfigStorage

class MainActivity : AppCompatActivity() {
    private val configStorage by lazy { ConfigStorage(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigateToNext()
    }

    private fun navigateToNext() {
        val next = if (configStorage.loadConfig() == null) {
            Intent(this, QrScanActivity::class.java)
        } else {
            Intent(this, CameraActivity::class.java)
        }
        startActivity(next)
        finish()
    }
}
