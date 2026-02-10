package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class UploadImageResponseDto(
    val url: String,
)
