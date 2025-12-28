package org.comon.livemotion

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.comon.livemotion.demo.minimum.LAppMinimumDelegate
import org.comon.livemotion.ui.theme.LiveMotionTheme

class MainActivity : ComponentActivity() {

    private lateinit var glSurfaceView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveMotionTheme {
                Live2DScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 샘플 minimum delegate는 여기서 초기화/시작하는 패턴이 가장 안정적
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