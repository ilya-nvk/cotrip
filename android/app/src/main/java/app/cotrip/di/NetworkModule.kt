package app.cotrip.di

import app.cotrip.data.remote.KotlinSerializationConverterFactory
import app.cotrip.data.remote.TripApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://example.com/api/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(
                KotlinSerializationConverterFactory.create(
                    Json { ignoreUnknownKeys = true },
                    contentType
                )
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideTripApi(retrofit: Retrofit): TripApi = retrofit.create(TripApi::class.java)
}
