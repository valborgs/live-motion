package org.comon.storage

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * SAF (Storage Access Framework) 권한을 관리하는 클래스
 */
class SAFPermissionManager(private val context: Context) {

    /**
     * URI 권한을 영구적으로 저장합니다 (앱 재시작 후에도 유지).
     */
    fun persistPermission(uri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
    }

    /**
     * 저장된 URI 권한을 해제합니다.
     */
    fun releasePermission(uri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.releasePersistableUriPermission(uri, takeFlags)
        } catch (_: SecurityException) {
            // 권한이 없는 경우 무시
        }
    }
}
