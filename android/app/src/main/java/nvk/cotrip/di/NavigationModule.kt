package nvk.cotrip.di

import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.AppNavigatorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {

    @Binds
    @Singleton
    abstract fun bindNavigator(
        appNavigatorImpl: AppNavigatorImpl
    ): AppNavigator
}