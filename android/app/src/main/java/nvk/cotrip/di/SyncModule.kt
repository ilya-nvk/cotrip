package nvk.cotrip.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nvk.cotrip.data.sync.SyncStateStore
import nvk.cotrip.data.sync.SyncStateStoreImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindSyncStateStore(
        impl: SyncStateStoreImpl
    ): SyncStateStore
}
