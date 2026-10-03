package com.soundly.feature.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.soundly.core.designsystem.theme.SoundlyTheme
import com.soundly.feature.recording.api.RecordingNavigation
import com.soundly.feature.recording.api.RecordingRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 앱의 진입과 시스템 창 설정만 담당하며, 화면 탐색은 Compose에 맡깁니다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var recordingNavigation: RecordingNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SoundlyTheme {
                SoundlyNavigation(navigation = recordingNavigation)
            }
        }
    }
}

@Composable
private fun SoundlyNavigation(navigation: RecordingNavigation) {
    // 스택과 화면별 저장 상태·ViewModel의 수명을 같은 탐색 범위에서 관리합니다.
    val backStack = rememberNavBackStack(RecordingRoute.Recordings)
    val onBackClick: () -> Unit = {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
    val entries = remember(navigation, backStack) {
        navigation.createEntryProvider(
            onRecordClick = {
                if (backStack.lastOrNull() != RecordingRoute.Recorder) {
                    backStack.add(RecordingRoute.Recorder)
                }
            },
            onBackClick = onBackClick,
        )
    }
    val background = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background,
        ),
    )

    NavDisplay(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = background),
        backStack = backStack,
        entryProvider = entries,
        onBack = onBackClick,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 240)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 160))
        },
        popTransitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 240)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 160))
        },
    )
}
