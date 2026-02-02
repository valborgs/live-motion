package org.comon.domain.common

/**
 * 도메인 예외 계층
 * 모든 비즈니스 로직 예외는 이 클래스를 상속합니다.
 */
sealed class DomainException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    /** 얼굴 트래킹 초기화 실패 */
    class FaceTrackingInitError(message: String) : DomainException(message)

    /** 카메라 관련 에러 */
    class CameraError(message: String) : DomainException(message)

    /** MediaPipe 런타임 에러 */
    class MediaPipeRuntimeError(message: String) : DomainException(message)

    /** 모델을 찾을 수 없음 */
    class ModelNotFoundError(modelId: String) : DomainException("Model not found: $modelId")

    /** Asset 읽기 실패 */
    class AssetReadError(path: String, cause: Throwable? = null) :
        DomainException("Failed to read: $path", cause)

    /** 외부 모델 관련 에러 */
    sealed class ExternalModelException(
        message: String,
        cause: Throwable? = null
    ) : DomainException(message, cause) {
        /** SAF 권한 거부 */
        class PermissionDenied(message: String) : ExternalModelException(message)

        /** 잘못된 모델 형식 (model3.json 없음 등) */
        class InvalidModelFormat(message: String) : ExternalModelException(message)

        /** 모델 유효성 검사 실패 */
        class ModelValidationFailed(message: String) : ExternalModelException(message)

        /** 캐시 작업 실패 */
        class CacheOperationFailed(message: String, cause: Throwable? = null) :
            ExternalModelException(message, cause)

        /** 메모리 부족 */
        class OutOfMemory(message: String) : ExternalModelException(message)

        /** 지원되지 않는 Cubism 버전 */
        class UnsupportedVersion(version: String) :
            ExternalModelException("Unsupported Cubism version: $version")
    }
}
