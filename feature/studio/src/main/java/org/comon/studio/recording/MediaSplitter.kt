package org.comon.studio.recording

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * 녹화된 MP4 파일에서 영상과 음성 트랙을 분리하는 유틸리티.
 *
 * [MediaExtractor]로 트랙을 탐색하고, 각 트랙을 별도의 [MediaMuxer]로 추출합니다.
 *
 * ## 사용 흐름
 * 1. [split] 호출 → video.mp4 + audio.m4a 생성
 * 2. 완료 후 [SplitResult] 반환
 * 3. 사용 완료 시 [cleanupTempFiles]로 임시 파일 정리
 *
 * @param cacheDir 임시 파일이 생성될 캐시 디렉토리
 */
class MediaSplitter(private val cacheDir: File) {

    private companion object {
        const val TAG = "MediaSplitter"
        const val BUFFER_SIZE = 1024 * 1024 // 1MB
    }

    data class SplitResult(
        val videoFile: File,
        val audioFile: File?,
    )

    private var videoTempFile: File? = null
    private var audioTempFile: File? = null

    /**
     * 소스 파일에서 영상/음성 트랙을 분리합니다.
     *
     * @param sourceFile 분리할 MP4 소스 파일
     * @param onProgress 진행률 콜백 (0.0 ~ 1.0). video는 0~0.5, audio는 0.5~1.0.
     * @return 분리 결과. 코루틴 취소 시 [kotlinx.coroutines.CancellationException] 발생.
     */
    suspend fun split(
        sourceFile: File,
        onProgress: (Float) -> Unit = {},
    ): SplitResult {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(sourceFile.absolutePath)

            val videoTrackIndex = findTrackIndex(extractor, "video/")
            val audioTrackIndex = findTrackIndex(extractor, "audio/")

            if (videoTrackIndex < 0) {
                throw IllegalStateException("Video track not found in source file")
            }

            // Video 추출 (진행률 0.0 ~ 0.5)
            val videoFile = File(cacheDir, "split_video_${System.currentTimeMillis()}.mp4")
            videoTempFile = videoFile
            extractTrack(
                sourceFile = sourceFile,
                trackIndex = videoTrackIndex,
                outputFile = videoFile,
                outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                progressOffset = 0f,
                progressScale = if (audioTrackIndex >= 0) 0.5f else 1.0f,
                onProgress = onProgress,
            )

            // Audio 추출 (진행률 0.5 ~ 1.0)
            var audioFile: File? = null
            if (audioTrackIndex >= 0) {
                audioFile = File(cacheDir, "split_audio_${System.currentTimeMillis()}.m4a")
                audioTempFile = audioFile
                extractTrack(
                    sourceFile = sourceFile,
                    trackIndex = audioTrackIndex,
                    outputFile = audioFile,
                    outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                    progressOffset = 0.5f,
                    progressScale = 0.5f,
                    onProgress = onProgress,
                )
            }

            onProgress(1.0f)
            Log.d(TAG, "Split complete: video=${videoFile.length()}, audio=${audioFile?.length()}")
            return SplitResult(videoFile, audioFile)

        } finally {
            extractor.release()
        }
    }

    /**
     * 분리 작업에서 생성된 임시 파일을 정리합니다.
     */
    fun cleanupTempFiles() {
        videoTempFile?.let { if (it.exists()) it.delete() }
        audioTempFile?.let { if (it.exists()) it.delete() }
        videoTempFile = null
        audioTempFile = null
    }

    private fun findTrackIndex(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    private suspend fun extractTrack(
        sourceFile: File,
        trackIndex: Int,
        outputFile: File,
        outputFormat: Int,
        progressOffset: Float,
        progressScale: Float,
        onProgress: (Float) -> Unit,
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(sourceFile.absolutePath)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, outputFormat)
            val muxerTrackIndex = muxer.addTrack(trackFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            // duration을 통한 진행률 계산
            val durationUs = trackFormat.getLong(MediaFormat.KEY_DURATION, 0L)

            while (true) {
                coroutineContext.ensureActive()

                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)

                // 진행률 보고
                if (durationUs > 0) {
                    val trackProgress = (extractor.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f)
                    onProgress(progressOffset + trackProgress * progressScale)
                }

                extractor.advance()
            }

        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping muxer", e)
            }
            extractor.release()
        }
    }
}
