package nvk.cotrip.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
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
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.KotlinxSerializationConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cotrip_auth")

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
        return DataStoreSessionStore(context.dataStore)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        sessionStore: SessionStore,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionStore))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
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
    fun provideCoTripApi(retrofit: Retrofit): CoTripApi = retrofit.create(CoTripApi::class.java)
}
