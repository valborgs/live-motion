package org.comon.ui.snackbar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 공통 에러 상세 다이얼로그
 *
 * 스낵바에서 "상세" 버튼 클릭 시 표시되는 에러 상세 정보 다이얼로그입니다.
 *
 * @param title 다이얼로그 제목
 * @param errorMessage 상세 에러 메시지
 * @param confirmButtonText 확인 버튼 텍스트
 * @param onDismiss 다이얼로그 닫기 콜백
 */
@Composable
fun ErrorDetailDialog(
    title: String,
    errorMessage: String,
    confirmButtonText: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(confirmButtonText, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/**
 * 스낵바와 에러 다이얼로그를 통합 관리하는 상태 홀더
 * 
 * 일반 스낵바와 에러 상세보기 기능이 있는 스낵바를 모두 지원합니다.
 */
class SnackbarStateHolder(
    val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope
) {
    var showErrorDialog by mutableStateOf(false)
        private set
    
    var currentErrorDetail by mutableStateOf<String?>(null)
        private set

    /**
     * 일반 스낵바를 표시합니다.
     */
    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Short
    ) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = duration
            )
        }
    }

    /**
     * 에러 스낵바를 표시합니다.
     * 사용자가 액션 버튼을 클릭하면 에러 상세 다이얼로그가 표시됩니다.
     */
    fun showErrorWithDetail(
        displayMessage: String,
        detailMessage: String,
        actionLabel: String,
        duration: SnackbarDuration = SnackbarDuration.Long
    ) {
        currentErrorDetail = detailMessage
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = displayMessage,
                actionLabel = actionLabel,
                duration = duration
            )
            if (result == SnackbarResult.ActionPerformed) {
                showErrorDialog = true
            }
        }
    }

    /**
     * 에러 상세 다이얼로그를 닫습니다.
     */
    fun dismissErrorDialog() {
        showErrorDialog = false
    }
}

/**
 * SnackbarStateHolder를 remember하여 생성합니다.
 */
@Composable
fun rememberSnackbarStateHolder(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
): SnackbarStateHolder {
    val scope = rememberCoroutineScope()
    return remember(snackbarHostState, scope) {
        SnackbarStateHolder(snackbarHostState, scope)
    }
}
