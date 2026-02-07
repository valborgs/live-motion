package org.comon.domain.usecase

import org.comon.domain.model.FacePose
import org.comon.domain.model.FacePoseSmoothingState
import org.comon.domain.model.Live2DParams
import org.comon.domain.model.TrackingSensitivity

/**
 * FacePose를 Live2D 파라미터로 변환하는 UseCase.
 *
 * ## 순수 함수
 * 이 UseCase는 상태를 가지지 않는 순수 함수입니다.
 * EMA 스무딩을 위한 상태는 [FacePoseSmoothingState]로 분리되어
 * 외부(ViewModel)에서 관리됩니다.
 *
 * ## 사용 예시
 * ```kotlin
 * @HiltViewModel
 * class StudioViewModel @Inject constructor(
 *     private val mapFacePoseUseCase: MapFacePoseUseCase
 * ) : ViewModel() {
 *     private var smoothingState = FacePoseSmoothingState()
 *
 *     fun mapFaceParams(facePose: FacePose, hasLandmarks: Boolean): Map<String, Float> {
 *         val (params, newState) = mapFacePoseUseCase(facePose, smoothingState, hasLandmarks)
 *         smoothingState = newState
 *         return params.params
 *     }
 * }
 * ```
 */
class MapFacePoseUseCase {

    /**
     * EMA (Exponential Moving Average) 스무딩 계수
     *
     * 공식: smoothed = lastValue + alpha * (newValue - lastValue)
     *
     * - 낮은 값 (0.1~0.3): 부드럽지만 반응이 느림 (떨림 억제에 효과적)
     * - 중간 값 (0.3~0.5): 부드러움과 반응성의 균형
     * - 높은 값 (0.5~0.8): 빠른 반응이지만 떨림이 생길 수 있음
     *
     * 기본값 0.4f, Settings에서 TrackingSensitivity.smoothing으로 조절 가능
     */

    /**
     * FacePose를 Live2D 파라미터로 변환합니다.
     *
     * @param facePose 얼굴 포즈 데이터
     * @param state 이전 스무딩 상태
     * @param hasLandmarks 얼굴 랜드마크 감지 여부
     * @return Pair<Live2DParams, FacePoseSmoothingState> - 변환된 파라미터와 새로운 상태
     */
    operator fun invoke(
        facePose: FacePose,
        state: FacePoseSmoothingState,
        hasLandmarks: Boolean,
        sensitivity: TrackingSensitivity = TrackingSensitivity()
    ): Pair<Live2DParams, FacePoseSmoothingState> {
        if (!hasLandmarks) {
            // 얼굴이 감지되지 않으면 상태 초기화
            return Pair(Live2DParams.DEFAULT, FacePoseSmoothingState())
        }
        return map(facePose, state, sensitivity)
    }

    /**
     * 새로운 얼굴 포즈를 받아 스무딩을 적용하고 Live2D 파라미터 맵으로 변환
     */
    private fun map(
        newPose: FacePose,
        state: FacePoseSmoothingState,
        sensitivity: TrackingSensitivity
    ): Pair<Live2DParams, FacePoseSmoothingState> {
        val lastPose = state.lastPose

        // EMA 스무딩 (모든 필드에 적용)
        val alpha = sensitivity.smoothing
        val smoothed = FacePose(
            yaw = smooth(lastPose.yaw, newPose.yaw, alpha),
            pitch = smooth(lastPose.pitch, newPose.pitch, alpha),
            roll = smooth(lastPose.roll, newPose.roll, alpha),
            mouthOpen = smooth(lastPose.mouthOpen, newPose.mouthOpen, alpha),
            mouthForm = smooth(lastPose.mouthForm, newPose.mouthForm, alpha),
            eyeLOpen = smooth(lastPose.eyeLOpen, newPose.eyeLOpen, alpha),
            eyeROpen = smooth(lastPose.eyeROpen, newPose.eyeROpen, alpha),
            eyeBallX = smooth(lastPose.eyeBallX, newPose.eyeBallX, alpha),
            eyeBallY = smooth(lastPose.eyeBallY, newPose.eyeBallY, alpha)
        )

        val newState = FacePoseSmoothingState(lastPose = smoothed)
        val params = buildParams(smoothed, sensitivity)

        return Pair(Live2DParams(params), newState)
    }

    private fun buildParams(smoothed: FacePose, sensitivity: TrackingSensitivity): Map<String, Float> {
        val params = mutableMapOf<String, Float>()

        // ===========================================
        // 머리 회전 파라미터 (AngleX, AngleY, AngleZ)
        // ===========================================
        // Live2D 표준 범위: -30 ~ 30
        params["ParamAngleX"] = (smoothed.yaw * 30f * sensitivity.yaw).coerceIn(-30f, 30f)
        // ParamAngleY: Live2D는 양수=위, 음수=아래이므로 부호 반전
        params["ParamAngleY"] = (-smoothed.pitch * 40f * sensitivity.pitch).coerceIn(-30f, 30f)

        // AngleZ는 실측 고개 기울기(roll)와 드래그 로직 특유의 수식(X*Y)을 혼합
        val dragStyleZ = smoothed.yaw * smoothed.pitch * (-30f) * sensitivity.yaw * sensitivity.pitch
        val realRollZ = smoothed.roll * 30f * sensitivity.roll
        params["ParamAngleZ"] = (realRollZ + dragStyleZ).coerceIn(-30f, 30f)

        // ===========================================
        // 눈 파라미터
        // eyeWide blendshape 적용으로 1.0 이상 값이 들어올 수 있음 -> 최대 2.0까지 허용
        params["ParamEyeLOpen"] = smoothed.eyeLOpen.coerceIn(0f, 2f)
        params["ParamEyeROpen"] = smoothed.eyeROpen.coerceIn(0f, 2f)

        // 미소 시 눈웃음 연동 (VTube Studio 설정 참고)
        params["ParamEyeLSmile"] = smoothed.mouthForm.coerceIn(0f, 1f)
        params["ParamEyeRSmile"] = smoothed.mouthForm.coerceIn(0f, 1f)

        // ===========================================
        // 입 파라미터
        // ===========================================
        // VTube Studio 참고: 출력 범위 확장 (0~2.1)으로 더 역동적인 입 표현
        params["ParamMouthOpenY"] = (smoothed.mouthOpen * 2.1f).coerceIn(0f, 2.1f)
        // 입 모양 (미소) - 모델이 지원하지 않으면 무시됨
        params["ParamMouthForm"] = smoothed.mouthForm.coerceIn(0f, 1f)

        // ===========================================
        // 몸 파라미터
        // ===========================================
        params["ParamBodyAngleX"] = (smoothed.yaw * 10f * sensitivity.yaw).coerceIn(-10f, 10f)

        // ===========================================
        // 시선 파라미터 (Iris Tracking 기반)
        // ===========================================
        // 이제 실제 눈동자 위치 데이터를 사용 (고개 방향과 독립)
        params["ParamEyeBallX"] = smoothed.eyeBallX.coerceIn(-1f, 1f)
        params["ParamEyeBallY"] = smoothed.eyeBallY.coerceIn(-1f, 1f)

        return params
    }

    private fun smooth(last: Float, current: Float, alpha: Float): Float {
        return last + alpha * (current - last)
    }
}
