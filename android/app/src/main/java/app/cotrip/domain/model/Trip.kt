package app.cotrip.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Trip(
    @SerialName("id") val id: String,
    @SerialName("destination") val destination: String,
    @SerialName("startDate") val startDate: String,
    @SerialName("endDate") val endDate: String
)
