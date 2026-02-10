package nvk.cotrip.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import nvk.cotrip.data.network.CoTripApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ImageUploadRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    @ApplicationContext private val context: Context,
) : ImageUploadRepository {

    override suspend fun uploadImage(uriString: String): String {
        val uri = Uri.parse(uriString)
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)?.takeIf { it.startsWith("image/") }
            ?: "image/jpeg"
        val fileName = contentResolver.resolveDisplayName(uri)
            ?: "image-${System.currentTimeMillis()}.jpg"
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read image")

        val requestBody = bytes.toRequestBody(mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
        return api.uploadImage(part).url
    }
}

private fun ContentResolver.resolveDisplayName(uri: Uri): String? {
    return query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (column == -1) return@use null
        if (!cursor.moveToFirst()) return@use null
        cursor.getString(column)
    }
}
