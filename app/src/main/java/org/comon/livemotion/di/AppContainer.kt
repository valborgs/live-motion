package org.comon.livemotion.di

import android.app.Application
import org.comon.common.asset.ModelAssetReader
import org.comon.common.di.AppContainer
import org.comon.tracking.FaceTrackerFactory

/**
 * 앱 전역 의존성 컨테이너 구현체
 * 싱글톤 의존성들을 lazy하게 생성하고 제공합니다.
 */
class AppContainerImpl(application: Application) : AppContainer {

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
}
