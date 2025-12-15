package app.oneroll.oneroll.model

data class UploadAuth(
    val token: String,
    val expiresAtMillis: Long? = null
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val expiry = expiresAtMillis ?: return false
        return nowMillis >= expiry
    }
}

data class OneRollConfig(
    val occasionId: String,
    val occasionName: String,
    val maxPhotos: Int,
    val brokerUrl: String,
    val inviteToken: String,
    val uploadAuth: UploadAuth?,
    val rawJson: String
) {
    fun withUploadAuth(auth: UploadAuth?): OneRollConfig = copy(uploadAuth = auth)
}
