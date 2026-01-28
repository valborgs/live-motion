package org.comon.common.di

import org.comon.common.asset.ModelAssetReader
import org.comon.tracking.FaceTrackerFactory

/**
 * 앱 전역 의존성 컨테이너 인터페이스
 * feature 모듈에서 app 모듈을 직접 참조하지 않도록 추상화합니다.
 */
interface AppContainer {
    val modelAssetReader: ModelAssetReader
    val faceTrackerFactory: FaceTrackerFactory
}
