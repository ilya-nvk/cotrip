package nvk.cotrip.data.network

import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

class OfflineCacheInterceptor(
    private val networkStateProvider: NetworkStateProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.method == "GET" && !networkStateProvider.isOnline()) {
            val cacheControl = CacheControl.Builder()
                .onlyIfCached()
                .maxStale(7, TimeUnit.DAYS)
                .build()
            request = request.newBuilder()
                .cacheControl(cacheControl)
                .build()
        }
        return chain.proceed(request)
    }
}

class CacheControlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.method != "GET") return response

        val isAuthorizedRequest = !request.header("Authorization").isNullOrBlank()
        return if (isAuthorizedRequest) {
            response.newBuilder()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .build()
        } else {
            response.newBuilder()
                .header("Cache-Control", "public, max-age=120")
                .build()
        }
    }
}
