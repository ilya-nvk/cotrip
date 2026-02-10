package nvk.cotrip.data.repository

interface ImageUploadRepository {
    suspend fun uploadImage(uriString: String): String
}
