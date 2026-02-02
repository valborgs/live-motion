package org.comon.livemotion

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 커스텀 Application 클래스
 *
 * Hilt를 통해 의존성 주입을 관리합니다.
 */
@HiltAndroidApp
class LiveMotionApp : Application()
