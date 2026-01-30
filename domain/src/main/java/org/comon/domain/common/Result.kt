package org.comon.domain.common

/**
 * 성공 또는 실패를 나타내는 래퍼 클래스
 * Clean Architecture에서 UseCase의 반환 타입으로 사용합니다.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: DomainException) : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun exceptionOrNull(): DomainException? = when (this) {
        is Success -> null
        is Error -> exception
    }

    inline fun <R> fold(
        onSuccess: (T) -> R,
        onError: (DomainException) -> R
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Error -> onError(exception)
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (DomainException) -> Unit): Result<T> {
        if (this is Error) action(exception)
        return this
    }

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(exception: DomainException): Result<Nothing> = Error(exception)
    }
}

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
