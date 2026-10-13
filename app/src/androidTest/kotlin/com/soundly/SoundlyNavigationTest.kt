package com.soundly

import android.Manifest
import android.app.NotificationManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundly.feature.main.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundlyNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigationAndScreenStateSurviveActivityRecreation() {
        compose.onNode(matcher = hasSetTextAction()).performTextInput(text = "상태 복원 확인")
        compose.onNodeWithText(text = "새 녹음", useUnmergedTree = true).performClick()
        compose.onNodeWithText(text = "WAV").performClick()

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText(text = "WAV").assertIsSelected()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(matcher = hasSetTextAction()).assertTextContains(value = "상태 복원 확인")
    }

    @Test
    fun recordingContinuesAcrossNavigationAndSupportsPauseResumeStop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)

        compose.onNodeWithText(text = "새 녹음", useUnmergedTree = true).performClick()
        compose.onNodeWithText(text = "녹음 시작").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(matcher = androidx.compose.ui.test.hasText(text = "녹음 중"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text = "일시정지").performClick()
        compose.onNodeWithText(text = "이어서 녹음").assertIsDisplayed()
        compose.onNodeWithText(text = "이어서 녹음").performClick()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(text = "나의 녹음").assertIsDisplayed()
        compose.onNodeWithText(text = "새 녹음", useUnmergedTree = true).performClick()
        compose.onNodeWithText(text = "녹음 중").assertIsDisplayed()
        compose.onNodeWithText(text = "저장하고 종료").performClick()

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(matcher = androidx.compose.ui.test.hasText(text = "나의 녹음"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text = "나의 녹음").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(matcher = hasText(text = "녹음 ·", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun notificationActionsControlTheSameRecordingSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)

        compose.onNodeWithText(text = "새 녹음", useUnmergedTree = true).performClick()
        compose.onNodeWithText(text = "녹음 시작").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(matcher = hasText(text = "녹음 중"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        sendRecordingNotificationAction("일시정지")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(matcher = hasText(text = "이어서 녹음"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text = "이어서 녹음").assertIsDisplayed()

        sendRecordingNotificationAction("녹음 재개")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(matcher = hasText(text = "녹음 중"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text = "녹음 중").assertIsDisplayed()

        sendRecordingNotificationAction("저장하고 종료")
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(matcher = hasText(text = "녹음 ·", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun sendRecordingNotificationAction(title: String) {
        val manager = compose.activity.getSystemService(NotificationManager::class.java)
        var action: android.app.Notification.Action? = null
        compose.waitUntil(timeoutMillis = 10_000) {
            action = manager.activeNotifications
                .firstOrNull { it.id == 1001 }
                ?.notification
                ?.actions
                ?.firstOrNull { it.title.toString() == title }
            action != null
        }
        checkNotNull(action).actionIntent.send()
    }
}
