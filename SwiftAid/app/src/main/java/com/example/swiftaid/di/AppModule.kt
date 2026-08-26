package com.example.swiftaid.di

import android.content.Context
import com.example.swiftaid.AndroidTokenStorage
import com.example.swiftaid.AuthApi
import com.example.swiftaid.SettingsRepository
import com.example.swiftaid.TokenStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAuthApi(): AuthApi = AuthApi()

    @Provides
    @Singleton
    fun provideTokenStorage(@ApplicationContext context: Context): TokenStorage =
        AndroidTokenStorage(context)

    @Provides
    @Singleton
    fun provideSettingsRepository(api: AuthApi): SettingsRepository =
        SettingsRepository(api)
}
