package org.comon.livemotion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.comon.live2d.LAppMinimumDelegate
import org.comon.navigation.NavKey
import org.comon.studio.ModelSelectScreen
import org.comon.studio.StudioScreen
import org.comon.ui.theme.LiveMotionTheme
import org.comon.common.di.LocalAppContainer

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as LiveMotionApp).container
            CompositionLocalProvider(LocalAppContainer provides container) {
                LiveMotionTheme {
                    MainContent()
                }
            }
        }
    }

    @Composable
    fun MainContent() {
        val context = LocalContext.current
        val activity = this

        var hasCameraPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        // 권한이 영구 거부되었는지 확인하는 상태
        var permissionPermanentlyDenied by remember { mutableStateOf(false) }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                hasCameraPermission = granted
                if (!granted) {
                    // 권한 거부 후 shouldShowRequestPermissionRationale이 false면 영구 거부
                    permissionPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.CAMERA
                    )
                }
            }
        )

        // 앱이 다시 포커스를 받았을 때 권한 상태 재확인 (설정에서 돌아온 경우)
        var lifecycleResumed by remember { mutableStateOf(false) }
        DisposableEffect(Unit) {
            lifecycleResumed = true
            onDispose { lifecycleResumed = false }
        }

        LaunchedEffect(lifecycleResumed) {
            if (lifecycleResumed) {
                val currentPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (currentPermission != hasCameraPermission) {
                    hasCameraPermission = currentPermission
                }
            }
        }

        LaunchedEffect(Unit) {
            if (!hasCameraPermission) {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }

        if (hasCameraPermission) {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = NavKey.ModelSelect
            ) {
                composable<NavKey.ModelSelect> { backStackEntry ->
                    // savedStateHandle에서 에러 메시지 읽기
                    val errorMessage by backStackEntry.savedStateHandle
                        .getStateFlow<String?>("model_load_error", null)
                        .collectAsState()

                    ModelSelectScreen(
                        onModelSelected = { modelId ->
                            navController.navigate(NavKey.Studio(modelId))
                        },
                        errorMessage = errorMessage,
                        onErrorConsumed = {
                            // 에러 메시지 소비 후 제거
                            backStackEntry.savedStateHandle.remove<String>("model_load_error")
                        }
                    )
                }
                composable<NavKey.Studio> { backStackEntry ->
                    val studio = backStackEntry.toRoute<NavKey.Studio>()
                    StudioScreen(
                        modelId = studio.modelId,
                        onBack = {
                            navController.popBackStack()
                        },
                        onError = { errorMessage ->
                            // 이전 화면(ModelSelect)의 savedStateHandle에 에러 메시지 저장
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("model_load_error", errorMessage)
                        }
                    )
                }
            }
        } else {
            CameraPermissionScreen(
                isPermanentlyDenied = permissionPermanentlyDenied,
                onRequestPermission = {
                    launcher.launch(Manifest.permission.CAMERA)
                },
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        LAppMinimumDelegate.getInstance().onStart(this)
    }

    override fun onStop() {
        super.onStop()
        LAppMinimumDelegate.getInstance().onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        LAppMinimumDelegate.getInstance().onDestroy()
    }
}

@Composable
private fun CameraPermissionScreen(
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 카메라 아이콘
                Text(
                    text = "📷",
                    fontSize = 48.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "카메라 권한 필요",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isPermanentlyDenied) {
                        "카메라 권한이 거부되었습니다.\n설정에서 권한을 허용해주세요.\n\n얼굴 추적 기능을 사용하려면\n카메라 접근이 필요합니다."
                    } else {
                        "이 앱은 얼굴 추적을 위해\n카메라 접근이 필요합니다.\n\n카메라 권한을 허용해주세요."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isPermanentlyDenied) {
                    // 설정으로 이동 버튼
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("설정으로 이동")
                    }
                } else {
                    // 권한 요청 버튼
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("권한 허용하기")
                    }
                }
            }
        }
    }
}
