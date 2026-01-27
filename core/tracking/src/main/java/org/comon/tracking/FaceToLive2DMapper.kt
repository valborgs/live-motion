package org.comon.tracking

import org.comon.domain.model.FacePose

/**
 * 얼굴 트래킹 원시 데이터를 Live2D 파라미터로 매핑하고 스무딩(EMA)을 적용하는 클래스
 */
class FaceToLive2DMapper {
    private var lastPose = FacePose()
    
    /**
     * EMA (Exponential Moving Average) 스무딩 계수
     * 
     * 공식: smoothed = lastValue + alpha * (newValue - lastValue)
     * 
     * - 낮은 값 (0.1~0.3): 부드럽지만 반응이 느림 (떨림 억제에 효과적)
     * - 중간 값 (0.3~0.5): 부드러움과 반응성의 균형
     * - 높은 값 (0.5~0.8): 빠른 반응이지만 떨림이 생길 수 있음
     * 
     * 조정 팁:
     * - 캐릭터가 너무 느리게 따라오면 값을 높이세요 (예: 0.5)
     * - 캐릭터가 떨리면 값을 낮추세요 (예: 0.3)
     */
    private val alpha = 0.4f

    /**
     * 내부 상태를 초기화하여 얼굴이 나타날 때 이전 위치에서 튀는 현상을 방지
     */
    fun reset() {
        lastPose = FacePose()
    }

    /**
     * 새로운 얼굴 포즈를 받아 스무딩을 적용하고 Live2D 파라미터 맵으로 변환
     */
    fun map(newPose: FacePose): Map<String, Float> {
        // EMA 스무딩 (모든 필드에 적용)
        val smoothed = FacePose(
            yaw = smooth(lastPose.yaw, newPose.yaw),
            pitch = smooth(lastPose.pitch, newPose.pitch),
            roll = smooth(lastPose.roll, newPose.roll),
            mouthOpen = smooth(lastPose.mouthOpen, newPose.mouthOpen),
            mouthForm = smooth(lastPose.mouthForm, newPose.mouthForm),
            eyeLOpen = smooth(lastPose.eyeLOpen, newPose.eyeLOpen),
            eyeROpen = smooth(lastPose.eyeROpen, newPose.eyeROpen),
            eyeBallX = smooth(lastPose.eyeBallX, newPose.eyeBallX),
            eyeBallY = smooth(lastPose.eyeBallY, newPose.eyeBallY)
        )
        lastPose = smoothed

        val params = mutableMapOf<String, Float>()
        
        // ===========================================
        // 머리 회전 파라미터 (AngleX, AngleY, AngleZ)
        // ===========================================
        // Live2D 표준 범위: -30 ~ 30
        params["ParamAngleX"] = (smoothed.yaw * 30f).coerceIn(-30f, 30f)
        // ParamAngleY: Live2D는 양수=위, 음수=아래이므로 부호 반전
        params["ParamAngleY"] = (-smoothed.pitch * 40f).coerceIn(-30f, 30f)
        
        // AngleZ는 실측 고개 기울기(roll)와 드래그 로직 특유의 수식(X*Y)을 혼합
        val dragStyleZ = smoothed.yaw * smoothed.pitch * (-30f)
        val realRollZ = smoothed.roll * 30f
        params["ParamAngleZ"] = (realRollZ + dragStyleZ).coerceIn(-30f, 30f)
        
        // ===========================================
        // 눈 파라미터
        // ===========================================
        params["ParamEyeLOpen"] = smoothed.eyeLOpen.coerceIn(0f, 1f)
        params["ParamEyeROpen"] = smoothed.eyeROpen.coerceIn(0f, 1f)
        
        // ===========================================
        // 입 파라미터
        // ===========================================
        params["ParamMouthOpenY"] = smoothed.mouthOpen.coerceIn(0f, 1f)
        // 입 모양 (미소) - 모델이 지원하지 않으면 무시됨
        params["ParamMouthForm"] = smoothed.mouthForm.coerceIn(0f, 1f)

        // ===========================================
        // 몸 파라미터
        // ===========================================
        params["ParamBodyAngleX"] = (smoothed.yaw * 10f).coerceIn(-10f, 10f)
        
        // ===========================================
        // 시선 파라미터 (Iris Tracking 기반)
        // ===========================================
        // 이제 실제 눈동자 위치 데이터를 사용 (고개 방향과 독립)
        params["ParamEyeBallX"] = smoothed.eyeBallX.coerceIn(-1f, 1f)
        params["ParamEyeBallY"] = smoothed.eyeBallY.coerceIn(-1f, 1f)

        return params
    }

    private fun smooth(last: Float, current: Float): Float {
        return last + alpha * (current - last)
    }
}
