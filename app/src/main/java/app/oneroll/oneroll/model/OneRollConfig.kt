package app.oneroll.oneroll.model

data class WebDavConfig(
    val baseURL: String,
    val path: String,
    val username: String,
    val password: String
)

data class OneRollConfig(
    val occasionName: String,
    val maxPhotos: Int,
    val webDav: WebDavConfig,
    val rawJson: String
)
