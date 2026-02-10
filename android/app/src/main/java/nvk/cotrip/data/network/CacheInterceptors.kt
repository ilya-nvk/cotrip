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
        if (request.method == "GET") {
            val cacheControl = if (networkStateProvider.isOnline()) {
                CacheControl.FORCE_NETWORK
            } else {
                CacheControl.Builder()
                    .onlyIfCached()
                    .maxStale(7, TimeUnit.DAYS)
                    .build()
            }
            request = request.newBuilder()
                .cacheControl(cacheControl)
                .build()
        }
        return chain.proceed(request)
    }
}

class CacheControlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return if (chain.request().method == "GET") {
            response.newBuilder()
                .header("Cache-Control", "public, max-age=120")
                .build()
        } else {
            response
        }
    }
}
