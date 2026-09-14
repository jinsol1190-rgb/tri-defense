package org.tridefense.android.ui.demo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class DemoUiTest {
    @get:Rule val ui = createAndroidComposeRule<DemoActivity>()
    private fun answer() { ui.onNodeWithContentDescription("받기").performClick(); ui.mainClock.autoAdvance = false; advance(100) }
    private fun advance(ms: Long) {
        if (InstrumentationRegistry.getArguments().getString("captureDemo") == "true") {
            var left = ms
            while (left > 0) {
                val step = minOf(left, 50L)
                Thread.sleep(step)
                ui.mainClock.advanceTimeBy(step, ignoreFrameDuration = true)
                ui.waitForIdle()
                left -= step
            }
        } else { ui.mainClock.advanceTimeBy(ms); ui.waitForIdle() }
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureDemo") != "true") return
        ui.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "demo-captures").apply { mkdirs() }
        val acknowledgement = File(directory, "$name.done")
        acknowledgement.delete()
        val request = File(directory, "capture-request.txt")
        request.writeText(name)
        val deadline = System.currentTimeMillis() + 15000
        while (!acknowledgement.exists() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        check(acknowledgement.exists()) { "Host screenshot acknowledgement timed out: $name" }
        request.delete()
        acknowledgement.delete()
    }


    @Test fun continuousCallDemo() {
        ui.onNodeWithContentDescription("받기").assertIsDisplayed()
        ui.onNodeWithContentDescription("거절").assertIsDisplayed()
        capture("01-incoming")
        answer(); advance(350)
        ui.onNodeWithText("피싱 위험도").assertIsDisplayed()
        advance(4500)
        ui.onNodeWithText("합성음성 의심").assertDoesNotExist()
        capture("02-in-call")
        advance(4000)
        ui.onNodeWithTag("call-surface").assertIsDisplayed()
        ui.onNodeWithText("합성음성 의심").assertIsDisplayed()
        ui.onNodeWithText("통화 종료").assertIsDisplayed()
        ui.onNodeWithText("등록").assertDoesNotExist()
        ui.onNodeWithText("기록").assertDoesNotExist()
        capture("03-warning")
        ui.onNodeWithText("통화 종료").performClick(); advance(100)
        ui.onNodeWithText("위협 정보 공유 중").assertIsDisplayed()
        advance(5000)
        ui.onNodeWithText("음성 지문 공유 완료 · MOCK").assertIsDisplayed()
        ui.onNodeWithText("전화번호는 등록하지 않았어요").assertIsDisplayed()
        capture("04-after-call")
    }
    @Test fun earlyHangUpCancelsWarning() {
        answer(); advance(1000)
        ui.onNodeWithContentDescription("통화 종료").performClick(); advance(15000)
        ui.onNodeWithText("합성음성 의심").assertDoesNotExist()
        ui.onNodeWithText("통화가 종료되었습니다").assertIsDisplayed()
        ui.onNodeWithText("음성 지문 공유 완료 · MOCK").assertDoesNotExist()
        ui.mainClock.autoAdvance = true
        ui.onNodeWithText("데모 다시 보기").performClick()
        ui.onNodeWithContentDescription("받기").assertIsDisplayed()
    }
    @Test fun rejectingDoesNotStartAnalysis() {
        ui.onNodeWithContentDescription("거절").performClick()
        advance(15000)
        ui.onNodeWithText("합성음성 의심").assertDoesNotExist()
        ui.onNodeWithText("통화가 종료되었습니다").assertIsDisplayed()
        ui.onNodeWithText("음성 지문 공유 완료 · MOCK").assertDoesNotExist()
    }
    @Test fun recreationPreservesActiveCall() {
        answer(); advance(9000)
        ui.activityRule.scenario.recreate()
        advance(100)
        ui.onNodeWithText("합성음성 의심").assertIsDisplayed()
        ui.onNodeWithText("통화 종료").assertIsDisplayed()
    }
}
