package org.comon.common.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.comon.common.asset.ModelAssetReader
import org.comon.tracking.FaceTrackerFactory
import javax.inject.Singleton

/**
 * Hilt Module - 앱 전역 의존성을 제공합니다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideModelAssetReader(
        @ApplicationContext context: Context
    ): ModelAssetReader {
        return ModelAssetReader(context.assets)
    }

    @Provides
    @Singleton
    fun provideFaceTrackerFactory(
        @ApplicationContext context: Context
    ): FaceTrackerFactory {
        return FaceTrackerFactory(context)
    }
}
