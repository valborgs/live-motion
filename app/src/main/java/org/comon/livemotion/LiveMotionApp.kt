package org.comon.livemotion

import android.app.Application
import org.comon.common.di.AppContainer
import org.comon.livemotion.di.AppContainerImpl

/**
 * 커스텀 Application 클래스
 * 앱 시작 시 의존성 컨테이너를 초기화합니다.
 */
class LiveMotionApp : Application() {

    /**
     * 앱 전역 의존성 컨테이너
     */
    lateinit var container: AppContainerImpl
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainerImpl(this)
    }
}

/**
 * Application에서 AppContainer를 가져오는 Extension
 */
val Application.appContainer: AppContainer
    get() = (this as LiveMotionApp).container
