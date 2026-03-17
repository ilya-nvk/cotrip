package nvk.cotrip.ui.trip.form

import nvk.cotrip.data.repository.ImageUploadRepository

internal class FakeImageUploadRepository(
    private val uploadResult: String = "https://cover.test",
) : ImageUploadRepository {
    val uploadCalls = mutableListOf<String>()

    override suspend fun uploadImage(uriString: String): String {
        uploadCalls += uriString
        return uploadResult
    }
}
