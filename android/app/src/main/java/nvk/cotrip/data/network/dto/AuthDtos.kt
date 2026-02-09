package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthDevRequest(
    val googleId: String,
    val name: String,
)

@Serializable
data class AuthGoogleRequest(
    val idToken: String,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val photoUrl: String? = null,
    val initials: String,
)

@Serializable
data class UpdateUserRequest(
    val name: String,
    val photoUrl: String? = null,
)
