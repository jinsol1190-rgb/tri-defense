package org.tridefense.android.ui.demo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class ProtectionDemoTest {
    @get:Rule val ui = createComposeRule()
    @Test fun registeredNumberShowsOnlyMockProtectionRecord() {
        ui.setContent { ProtectionDemo(false) }
        ui.onNodeWithText("MOCK DEMO").assertIsDisplayed()
        ui.onNodeWithText("수신 전 차단 · MOCK").assertIsDisplayed()
        ui.onNodeWithText("통화 종료").assertDoesNotExist()
        ui.onNodeWithText("보호 기록 보기").performScrollTo().performClick()
        ui.onNodeWithText("가상 번호:", substring = true).assertExists()
    }
    @Test fun changedNumberWarnsDuringCallWithoutClaimingBlocking() {
        ui.setContent { ProtectionDemo(true) }
        ui.onNodeWithText("통화 중 · SAMPLE").assertIsDisplayed()
        ui.onNodeWithText("등록된 위험 음성과\n유사해요").assertIsDisplayed()
        ui.onNodeWithText("수신 전 차단 · MOCK").assertDoesNotExist()
        ui.onNodeWithText("통화 종료").performClick()
        ui.onNodeWithText("기존 연락처로 확인하세요").assertIsDisplayed()
        ui.onNodeWithText("이 번호는 차단 목록에 등록하지 않았습니다.", substring = true).assertExists()
    }
}
