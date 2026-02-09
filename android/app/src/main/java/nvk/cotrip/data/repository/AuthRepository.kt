package nvk.cotrip.data.repository

import nvk.cotrip.data.network.dto.AuthDevRequest
import nvk.cotrip.data.network.dto.AuthResponse

interface AuthRepository {
    fun hasSession(): Boolean
    suspend fun signInWithGoogle(idToken: String): AuthResponse
    suspend fun signInWithDev(request: AuthDevRequest): AuthResponse
    fun clearSession()
}
