package nvk.cotrip.data.network

import nvk.cotrip.data.auth.SessionStore
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale

class AuthInterceptor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = sessionStore.getAccessToken()
        val languageTag = Locale.getDefault().toLanguageTag()
        val withLanguage = request.newBuilder()
            .header("Accept-Language", languageTag)
            .build()
        val authenticated = if (token.isNullOrBlank()) {
            withLanguage
        } else {
            withLanguage.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(authenticated)
    }
}
