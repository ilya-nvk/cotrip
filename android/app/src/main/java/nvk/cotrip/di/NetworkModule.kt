package nvk.cotrip.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import nvk.cotrip.BuildConfig
import nvk.cotrip.data.auth.DataStoreSessionStore
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.AuthInterceptor
import nvk.cotrip.data.network.AuthRefreshApi
import nvk.cotrip.data.network.CacheControlInterceptor
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.KotlinxSerializationConverterFactory
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.OfflineCacheInterceptor
import nvk.cotrip.data.network.SessionAuthenticator
import nvk.cotrip.data.sync.CoTripDatabase
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideSessionStore(
        @ApplicationContext context: Context,
    ): SessionStore {
        return DataStoreSessionStore(context)
    }

    @Provides
    @Singleton
    fun provideNetworkStateProvider(
        @ApplicationContext context: Context,
    ): NetworkStateProvider {
        return NetworkStateProvider(context)
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CoTripDatabase {
        return Room.databaseBuilder(context, CoTripDatabase::class.java, "cotrip.db").build()
    }

    @Provides
    @Singleton
    fun provideHttpCache(
        @ApplicationContext context: Context,
    ): Cache {
        val cacheDir = File(context.cacheDir, "http_cache")
        return Cache(cacheDir, 10L * 1024L * 1024L)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        sessionStore: SessionStore,
        httpCache: Cache,
        networkStateProvider: NetworkStateProvider,
        sessionAuthenticator: SessionAuthenticator,
    ): OkHttpClient {
        val loggingInterceptor = createLoggingInterceptor()
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionStore))
            .addInterceptor(OfflineCacheInterceptor(networkStateProvider))
            .addNetworkInterceptor(CacheControlInterceptor())
            .addInterceptor(loggingInterceptor)
            .authenticator(sessionAuthenticator)
            .cache(httpCache)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("auth_refresh")
    fun provideAuthRefreshOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(createLoggingInterceptor())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        json: Json,
        okHttpClient: OkHttpClient,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(KotlinxSerializationConverterFactory.create(json, contentType))
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    @Named("auth_refresh")
    fun provideAuthRefreshRetrofit(
        json: Json,
        @Named("auth_refresh") authRefreshOkHttpClient: OkHttpClient,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(KotlinxSerializationConverterFactory.create(json, contentType))
            .client(authRefreshOkHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideCoTripApi(retrofit: Retrofit): CoTripApi = retrofit.create(CoTripApi::class.java)

    @Provides
    @Singleton
    fun provideAuthRefreshApi(@Named("auth_refresh") authRefreshRetrofit: Retrofit): AuthRefreshApi {
        return authRefreshRetrofit.create(AuthRefreshApi::class.java)
    }

    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }
    }
}
