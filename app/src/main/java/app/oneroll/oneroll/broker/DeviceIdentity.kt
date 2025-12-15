package app.oneroll.oneroll.broker

import android.content.Context
import android.provider.Settings

object DeviceIdentity {
    fun deviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
    }
}
