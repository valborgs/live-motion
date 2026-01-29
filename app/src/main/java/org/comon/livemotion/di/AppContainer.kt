package org.comon.livemotion.di

import android.app.Application
import org.comon.common.asset.ModelAssetReader
import org.comon.common.di.AppContainer
import org.comon.data.repository.ModelRepositoryImpl
import org.comon.domain.repository.IModelRepository
import org.comon.domain.usecase.GetLive2DModelsUseCase
import org.comon.domain.usecase.GetModelMetadataUseCase
import org.comon.domain.usecase.MapFacePoseUseCase
import org.comon.tracking.FaceTrackerFactory

/**
 * 앱 전역 의존성 컨테이너 구현체
 * 싱글톤 의존성들을 lazy하게 생성하고 제공합니다.
 *
 * 의존성 그래프:
 * ```
 * modelAssetReader
 *     └─ ModelRepositoryImpl
 *         ├─ GetLive2DModelsUseCase
 *         └─ GetModelMetadataUseCase
 *
 * MapFacePoseUseCase (매번 새 인스턴스 - EMA 상태 때문)
 * ```
 */
class AppContainerImpl(application: Application) : AppContainer {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 기존 의존성 (하위 호환성)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * FaceTracker 생성을 위한 Factory
     */
    override val faceTrackerFactory: FaceTrackerFactory by lazy {
        FaceTrackerFactory(application)
    }

    /**
     * Asset 읽기를 위한 Reader
     */
    override val modelAssetReader: ModelAssetReader by lazy {
        ModelAssetReader(application.assets)
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Repository
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    override val modelRepository: IModelRepository by lazy {
        ModelRepositoryImpl(modelAssetReader)
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // UseCases
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    override val getLive2DModelsUseCase: GetLive2DModelsUseCase by lazy {
        GetLive2DModelsUseCase(modelRepository)
    }

    override val getModelMetadataUseCase: GetModelMetadataUseCase by lazy {
        GetModelMetadataUseCase(modelRepository)
    }

    /**
     * MapFacePoseUseCase는 상태를 가지므로 매번 새 인스턴스를 생성합니다.
     */
    override fun createMapFacePoseUseCase(): MapFacePoseUseCase {
        return MapFacePoseUseCase()
    }
}
