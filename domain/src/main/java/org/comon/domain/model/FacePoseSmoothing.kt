package org.comon.domain.model

/**
 * FacePose EMA 스무딩 상태를 저장하는 데이터 클래스.
 *
 * MapFacePoseUseCase가 순수 함수가 되도록 상태를 분리했습니다.
 *
 * @property lastPose 마지막으로 처리된 얼굴 포즈 (스무딩 계산에 사용)
 */
data class FacePoseSmoothingState(
    val lastPose: FacePose = FacePose()
)
