package co.monveri.register.data

import co.monveri.register.network.AuthHeaderProvider
import co.monveri.register.network.BaseUrlProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAuthHeaderProvider(
        impl: SecurePrefsAuthHeaderProvider,
    ): AuthHeaderProvider

    @Binds
    @Singleton
    abstract fun bindBaseUrlProvider(
        impl: SecurePrefsBaseUrlProvider,
    ): BaseUrlProvider
}
