package org.comon.livemotion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveMotionTheme {
                MainContent()
            }
        }
    }

    @Composable
    fun MainContent() {
        val context = LocalContext.current
        
        var hasCameraPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> hasCameraPermission = granted }
        )

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
                composable<NavKey.ModelSelect> {
                    ModelSelectScreen(
                        onModelSelected = { modelId ->
                            navController.navigate(NavKey.Studio(modelId))
                        }
                    )
                }
                composable<NavKey.Studio> { backStackEntry ->
                    val studio = backStackEntry.toRoute<NavKey.Studio>()
                    StudioScreen(
                        modelId = studio.modelId,
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("카메라 권한이 필요합니다.")
            }
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
