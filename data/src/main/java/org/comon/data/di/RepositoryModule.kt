package org.comon.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.comon.common.asset.ModelAssetReader
import org.comon.data.repository.ExternalModelRepositoryImpl
import org.comon.data.repository.ModelRepositoryImpl
import org.comon.domain.repository.IExternalModelRepository
import org.comon.domain.repository.IModelRepository
import org.comon.storage.ExternalModelMetadataStore
import org.comon.storage.ModelCacheManager
import javax.inject.Singleton

/**
 * Hilt Module - Repository 의존성을 제공합니다.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideModelRepository(
        modelAssetReader: ModelAssetReader
    ): IModelRepository {
        return ModelRepositoryImpl(modelAssetReader)
    }

    @Provides
    @Singleton
    fun provideExternalModelRepository(
        modelCacheManager: ModelCacheManager,
        metadataStore: ExternalModelMetadataStore
    ): IExternalModelRepository {
        return ExternalModelRepositoryImpl(modelCacheManager, metadataStore)
    }
}
