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
import javax.inject.Singleton

private val Context.cacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "cotrip_cache")

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {
    @Provides
    @Singleton
    fun provideCacheDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.cacheDataStore
}
