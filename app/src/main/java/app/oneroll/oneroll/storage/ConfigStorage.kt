package app.oneroll.oneroll.storage

import android.content.Context
import app.oneroll.oneroll.model.OneRollConfig
import app.oneroll.oneroll.model.WebDavConfig
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
        }
    }

    fun clearConfig() {
        prefs.edit().remove(KEY_CONFIG_JSON).apply()
    }

    private fun parseConfig(rawJson: String): Result<OneRollConfig> {
        return runCatching {
            val json = JSONObject(rawJson)
            val occasionName = json.getString("occasionName")
            val maxPhotos = json.getInt("maxPhotos")
            val webDavJson = json.getJSONObject("webdav")
            val webDav = WebDavConfig(
                baseURL = webDavJson.getString("baseURL"),
                path = webDavJson.getString("path"),
                username = webDavJson.getString("username"),
                password = webDavJson.getString("password")
            )
            OneRollConfig(
                occasionName = occasionName,
                maxPhotos = maxPhotos,
                webDav = webDav,
                rawJson = rawJson
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "oneroll_prefs"
        private const val KEY_CONFIG_JSON = "config_json"
    }
}
