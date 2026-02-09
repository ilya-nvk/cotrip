package nvk.cotrip.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nvk.cotrip.data.cache.TripsCacheStore
import nvk.cotrip.data.cache.TripsCacheStoreImpl
import nvk.cotrip.data.cache.UserCacheStore
import nvk.cotrip.data.cache.UserCacheStoreImpl
import nvk.cotrip.data.repository.AuthRepository
import nvk.cotrip.data.repository.AuthRepositoryImpl
import nvk.cotrip.data.repository.ExpenseRepository
import nvk.cotrip.data.repository.ExpenseRepositoryImpl
import nvk.cotrip.data.repository.IdeaRepository
import nvk.cotrip.data.repository.IdeaRepositoryImpl
import nvk.cotrip.data.repository.InviteRepository
import nvk.cotrip.data.repository.InviteRepositoryImpl
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.ItineraryRepositoryImpl
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.TripRepositoryImpl
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.data.repository.UserRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTripsCacheStore(
        impl: TripsCacheStoreImpl
    ): TripsCacheStore

    @Binds
    @Singleton
    abstract fun bindUserCacheStore(
        impl: UserCacheStoreImpl
    ): UserCacheStore

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindTripRepository(
        impl: TripRepositoryImpl
    ): TripRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindInviteRepository(
        impl: InviteRepositoryImpl
    ): InviteRepository

    @Binds
    @Singleton
    abstract fun bindIdeaRepository(
        impl: IdeaRepositoryImpl
    ): IdeaRepository

    @Binds
    @Singleton
    abstract fun bindItineraryRepository(
        impl: ItineraryRepositoryImpl
    ): ItineraryRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(
        impl: ExpenseRepositoryImpl
    ): ExpenseRepository
}
