package nvk.cotrip.data.network

import nvk.cotrip.data.network.dto.RefreshRequest
import nvk.cotrip.data.network.dto.RefreshResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthRefreshApi {
    @POST("v1/auth/refresh")
    fun refresh(@Body request: RefreshRequest): Call<RefreshResponse>
}
