package org.comon.live2d

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.comon.domain.model.ModelSource

@Composable
fun Live2DScreen(
    modifier: Modifier = Modifier,
    modelSource: ModelSource? = null,
    faceParams: Map<String, Float>? = null,
    isZoomEnabled: Boolean = false,
    isMoveEnabled: Boolean = false,
    onModelLoaded: (() -> Unit)? = null,
    onModelLoadError: ((String) -> Unit)? = null
) {
    val context: Context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val glView = remember {
        Live2DGLSurfaceView(context)
    }

    // 제스처 모드 업데이트
    LaunchedEffect(isZoomEnabled, isMoveEnabled) {
        glView.isZoomEnabled = isZoomEnabled
        glView.isMoveEnabled = isMoveEnabled
    }

    // 모델 소스 변경 시 GL Thread로 작업 큐잉
    LaunchedEffect(modelSource) {
        modelSource?.let { source ->
            glView.queueEvent {
                LAppMinimumLive2DManager.getInstance().setOnModelLoadListener(
                    object : LAppMinimumLive2DManager.OnModelLoadListener {
                        override fun onModelLoaded() {
                            onModelLoaded?.invoke()
                        }
                        override fun onModelLoadError(error: String) {
                            onModelLoadError?.invoke(error)
                        }
                    }
                )
                when (source) {
                    is ModelSource.Asset -> {
                        LAppMinimumLive2DManager.getInstance().loadModel(source.modelId)
                    }
                    is ModelSource.External -> {
                        LAppMinimumLive2DManager.getInstance().loadExternalModel(
                            source.model.cachePath,
                            source.model.modelJsonName
                        )
                    }
                }
            }
        }
    }

    // 얼굴 파라미터 업데이트가 있을 때 GL Thread로 전달
    LaunchedEffect(faceParams) {
        faceParams?.let { params ->
            // Log.d("Live2DScreen", "Applying Face Params: $params")
            glView.queueEvent {
                LAppMinimumLive2DManager.getInstance().applyFacePose(params)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { glView }
    )

    // GLSurfaceView resume/pause를 Lifecycle에 연결
    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 화면을 나갈 때 리소스 정리 (view, textureManager, model, CubismFramework.dispose)
            // 싱글톤 인스턴스는 유지하여 다음 초기화 시 cleanUp+startUp이 다시 호출되지 않도록 함
            LAppMinimumDelegate.getInstance().onStop()
        }
    }
}
