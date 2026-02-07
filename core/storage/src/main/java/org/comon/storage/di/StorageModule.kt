package org.comon.storage.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.comon.storage.ConsentLocalDataSource
import org.comon.storage.ExternalModelMetadataStore
import org.comon.storage.ModelCacheManager
import org.comon.storage.SAFPermissionManager
import org.comon.storage.TrackingSettingsLocalDataSource
import javax.inject.Singleton

/**
 * Hilt Module - Storage 관련 의존성을 제공합니다.
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideModelCacheManager(
        @ApplicationContext context: Context
    ): ModelCacheManager {
        return ModelCacheManager(context)
    }

    @Provides
    @Singleton
    fun provideExternalModelMetadataStore(
        @ApplicationContext context: Context
    ): ExternalModelMetadataStore {
        return ExternalModelMetadataStore(context)
    }

    @Provides
    @Singleton
    fun provideSAFPermissionManager(
        @ApplicationContext context: Context
    ): SAFPermissionManager {
        return SAFPermissionManager(context)
    }

    @Provides
    @Singleton
    fun provideConsentLocalDataSource(
        @ApplicationContext context: Context
    ): ConsentLocalDataSource {
        return ConsentLocalDataSource(context)
    }

    @Provides
    @Singleton
    fun provideTrackingSettingsLocalDataSource(
        @ApplicationContext context: Context
    ): TrackingSettingsLocalDataSource {
        return TrackingSettingsLocalDataSource(context)
    }
}
