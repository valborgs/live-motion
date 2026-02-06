package org.comon.data.di

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.comon.common.asset.ModelAssetReader
import org.comon.data.repository.ConsentRepositoryImpl
import org.comon.data.repository.ExternalModelRepositoryImpl
import org.comon.data.repository.ModelRepositoryImpl
import org.comon.domain.repository.IConsentRepository
import org.comon.domain.repository.IExternalModelRepository
import org.comon.domain.repository.IModelRepository
import org.comon.storage.ConsentLocalDataSource
import org.comon.storage.ExternalModelMetadataStore
import org.comon.storage.ModelCacheManager
import org.comon.storage.SAFPermissionManager
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
        metadataStore: ExternalModelMetadataStore,
        safPermissionManager: SAFPermissionManager
    ): IExternalModelRepository {
        return ExternalModelRepositoryImpl(modelCacheManager, metadataStore, safPermissionManager)
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideConsentRepository(
        localDataSource: ConsentLocalDataSource,
        firestore: FirebaseFirestore
    ): IConsentRepository {
        return ConsentRepositoryImpl(localDataSource, firestore)
    }
}
