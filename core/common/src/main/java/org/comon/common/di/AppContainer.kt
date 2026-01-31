package org.comon.common.di

import org.comon.common.asset.ModelAssetReader
import org.comon.domain.repository.IExternalModelRepository
import org.comon.domain.repository.IModelRepository
import org.comon.domain.usecase.DeleteExternalModelsUseCase
import org.comon.domain.usecase.GetAllModelsUseCase
import org.comon.domain.usecase.GetLive2DModelsUseCase
import org.comon.domain.usecase.GetModelMetadataUseCase
import org.comon.domain.usecase.ImportExternalModelUseCase
import org.comon.domain.usecase.MapFacePoseUseCase
import org.comon.tracking.FaceTrackerFactory

/**
 * 앱 전역 의존성 컨테이너 인터페이스
 * feature 모듈에서 app 모듈을 직접 참조하지 않도록 추상화합니다.
 */
interface AppContainer {
    // 기존 의존성 (하위 호환성)
    val modelAssetReader: ModelAssetReader
    val faceTrackerFactory: FaceTrackerFactory

    // Repository
    val modelRepository: IModelRepository
    val externalModelRepository: IExternalModelRepository

    // UseCases (Singleton)
    val getLive2DModelsUseCase: GetLive2DModelsUseCase
    val getModelMetadataUseCase: GetModelMetadataUseCase
    val getAllModelsUseCase: GetAllModelsUseCase
    val importExternalModelUseCase: ImportExternalModelUseCase
    val deleteExternalModelsUseCase: DeleteExternalModelsUseCase

    /**
     * MapFacePoseUseCase는 상태를 가지므로 매번 새 인스턴스를 생성합니다.
     * (EMA 스무딩을 위한 이전 값 저장)
     */
    fun createMapFacePoseUseCase(): MapFacePoseUseCase
}
