package app.oneroll.oneroll.storage

import android.content.Context
import app.oneroll.oneroll.model.OneRollConfig
import app.oneroll.oneroll.model.UploadAuth
import org.json.JSONObject

class ConfigStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): OneRollConfig? {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return null
        return parseConfig(raw).getOrNull()
    }

    fun saveConfig(rawJson: String): Result<OneRollConfig> {
        val parsed = parseConfig(rawJson)
        return parsed.onSuccess { config ->
            prefs.edit().putString(KEY_CONFIG_JSON, rawJson).apply()
            clearUploadAuth()
        }
    }

    fun clearConfig() {
        prefs.edit()
            .remove(KEY_CONFIG_JSON)
            .remove(KEY_UPLOAD_TOKEN)
            .remove(KEY_UPLOAD_TOKEN_EXPIRES_AT)
            .apply()
    }

    fun saveUploadAuth(auth: UploadAuth) {
        prefs.edit()
            .putString(KEY_UPLOAD_TOKEN, auth.token)
            .putLong(KEY_UPLOAD_TOKEN_EXPIRES_AT, auth.expiresAtMillis ?: 0L)
            .apply()
    }

    private fun clearUploadAuth() {
        prefs.edit()
            .remove(KEY_UPLOAD_TOKEN)
            .remove(KEY_UPLOAD_TOKEN_EXPIRES_AT)
            .apply()
    }

    private fun parseConfig(rawJson: String): Result<OneRollConfig> {
        return runCatching {
            val json = JSONObject(rawJson)
            val occasionId = json.getString("occasionId")
            val occasionName = json.getString("occasionName")
            val maxPhotos = json.getInt("maxPhotos")
            val brokerUrl = json.getString("brokerURL")
            val inviteToken = json.getString("inviteToken")
            val uploadAuth = loadUploadAuth()
            OneRollConfig(
                occasionId = occasionId,
                occasionName = occasionName,
                maxPhotos = maxPhotos,
                brokerUrl = brokerUrl,
                inviteToken = inviteToken,
                uploadAuth = uploadAuth,
                rawJson = rawJson
            )
        }
    }

    private fun loadUploadAuth(): UploadAuth? {
        val token = prefs.getString(KEY_UPLOAD_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_UPLOAD_TOKEN_EXPIRES_AT, 0L).takeIf { it > 0 }
        return UploadAuth(token = token, expiresAtMillis = expiresAt)
    }

    companion object {
        private const val PREFS_NAME = "oneroll_prefs"
        private const val KEY_CONFIG_JSON = "config_json"
        private const val KEY_UPLOAD_TOKEN = "upload_token"
        private const val KEY_UPLOAD_TOKEN_EXPIRES_AT = "upload_token_expires_at"
    }
}
