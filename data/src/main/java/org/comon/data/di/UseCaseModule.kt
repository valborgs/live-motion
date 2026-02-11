package org.comon.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.comon.domain.repository.IBackgroundRepository
import org.comon.domain.repository.IExternalModelRepository
import org.comon.domain.repository.IModelRepository
import org.comon.domain.usecase.DeleteBackgroundsUseCase
import org.comon.domain.usecase.DeleteExternalModelsUseCase
import org.comon.domain.usecase.GetAllBackgroundsUseCase
import org.comon.domain.usecase.GetAllModelsUseCase
import org.comon.domain.usecase.GetLive2DModelsUseCase
import org.comon.domain.usecase.GetModelMetadataUseCase
import org.comon.domain.usecase.ImportBackgroundUseCase
import org.comon.domain.usecase.ImportExternalModelUseCase
import org.comon.domain.usecase.MapFacePoseUseCase
import javax.inject.Singleton

/**
 * Hilt Module - UseCase 의존성을 제공합니다.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetLive2DModelsUseCase(
        modelRepository: IModelRepository
    ): GetLive2DModelsUseCase {
        return GetLive2DModelsUseCase(modelRepository)
    }

    @Provides
    @Singleton
    fun provideGetModelMetadataUseCase(
        modelRepository: IModelRepository,
        externalModelRepository: IExternalModelRepository
    ): GetModelMetadataUseCase {
        return GetModelMetadataUseCase(modelRepository, externalModelRepository)
    }

    @Provides
    @Singleton
    fun provideGetAllModelsUseCase(
        modelRepository: IModelRepository,
        externalModelRepository: IExternalModelRepository
    ): GetAllModelsUseCase {
        return GetAllModelsUseCase(modelRepository, externalModelRepository)
    }

    @Provides
    @Singleton
    fun provideImportExternalModelUseCase(
        externalModelRepository: IExternalModelRepository
    ): ImportExternalModelUseCase {
        return ImportExternalModelUseCase(externalModelRepository)
    }

    @Provides
    @Singleton
    fun provideDeleteExternalModelsUseCase(
        externalModelRepository: IExternalModelRepository
    ): DeleteExternalModelsUseCase {
        return DeleteExternalModelsUseCase(externalModelRepository)
    }

    @Provides
    @Singleton
    fun provideMapFacePoseUseCase(): MapFacePoseUseCase {
        return MapFacePoseUseCase()
    }

    @Provides
    @Singleton
    fun provideGetAllBackgroundsUseCase(
        backgroundRepository: IBackgroundRepository
    ): GetAllBackgroundsUseCase {
        return GetAllBackgroundsUseCase(backgroundRepository)
    }

    @Provides
    @Singleton
    fun provideImportBackgroundUseCase(
        backgroundRepository: IBackgroundRepository
    ): ImportBackgroundUseCase {
        return ImportBackgroundUseCase(backgroundRepository)
    }

    @Provides
    @Singleton
    fun provideDeleteBackgroundsUseCase(
        backgroundRepository: IBackgroundRepository
    ): DeleteBackgroundsUseCase {
        return DeleteBackgroundsUseCase(backgroundRepository)
    }
}
