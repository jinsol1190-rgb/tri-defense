package org.tridefense.android.ui.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// MOCK pacing only; no audio, inference, proof, API, or telephony operation.
private const val FRAME_MS = 50L
private const val GAUGE_START_TICK = 4 // 200 ms after answering, inside the 0.5s presentation target.
private const val WARNING_TICK = 160 // 8s: chosen demo pacing, not a specified analysis latency.
private const val SHARED_TICK = 240 // 12s: MOCK result pacing, not actual chain finality.

/** Victim-facing submission wireframe, isolated from the application container. */
@Composable internal fun DemoApp() = DemoTheme {
    var started by rememberSaveable { mutableStateOf(false) }
    var ended by rememberSaveable { mutableStateOf(false) }
    var ticks by rememberSaveable { mutableIntStateOf(0) }
    var callTicks by rememberSaveable { mutableIntStateOf(0) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var speaker by rememberSaveable { mutableStateOf(false) }
    val warning = started && ticks >= WARNING_TICK
    val gaugeVisible = started && ticks >= GAUGE_START_TICK
    val seconds = callTicks / 20
    val score = ((ticks - GAUGE_START_TICK).coerceAtLeast(0) * 90f / (WARNING_TICK - GAUGE_START_TICK)).coerceAtMost(90f)
    val active = started && !ended
    fun reset() { started = false; ended = false; ticks = 0; callTicks = 0; muted = false; speaker = false }
    LaunchedEffect(started, ended) {
        while (started && (!ended || (warning && ticks < SHARED_TICK))) {
            delay(FRAME_MS)
            ticks++
            if (!ended) callTicks++
        }
    }
    BackHandler(active) { ended = true }
    Scaffold(containerColor = Paper, topBar = {
        Row(Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (gaugeVisible || ended) "Tri-Defense" else "전화", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Surface(color = Ink, shape = RoundedCornerShape(8.dp)) {
                Text("MOCK DEMO", Modifier.padding(8.dp), color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }, bottomBar = {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (!started && !ended) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    CallControl(Icons.Outlined.CallEnd, "거절", false, true) { ended = true }
                    CallControl(Icons.Outlined.Call, "받기", true) { started = true }
                }
            } else if (active) {
                if (warning) Action("통화 종료", { ended = true }, danger = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallControl(if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, "음소거", muted) { muted = !muted }
                    CallControl(Icons.AutoMirrored.Outlined.VolumeUp, "스피커", speaker) { speaker = !speaker }
                    if (!warning) CallControl(Icons.Outlined.CallEnd, "통화 종료", false, true) { ended = true }
                }
            } else TextButton({ reset() }) { Text("데모 다시 보기") }
            Text("가상 통화 시연 · 실제 연결·분석 없음", color = Muted, fontSize = 11.sp)
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().testTag("call-surface"), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (ended) "통화 종료" else if (started) "통화 중" else "전화가 왔습니다", color = Muted)
                Text("알 수 없는 번호", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("+1 202-555-0123", color = Muted, fontSize = 18.sp)
                if (started) Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Muted, modifier = Modifier.testTag("call-timer"))
            }
            Spacer(Modifier.height(4.dp))
            if (ended) {
                if (warning) {
                    DemoCard {
                        IconDisc(Icons.Outlined.VerifiedUser, Lime, 48)
                        Text(if (ticks < SHARED_TICK) "위협 정보 공유 중" else "의심 음성의 특징을\n공유했습니다", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text(if (ticks < SHARED_TICK) "검증·등록 결과를 기다리고 있어요." else "음성 지문 공유 완료 · MOCK", color = Lime)
                        HorizontalDivider(color = Muted.copy(alpha = .3f))
                        Text("전화번호는 등록하지 않았어요", fontWeight = FontWeight.Bold)
                        Text("미저장 번호만으로 차단하지 않아요.\n번호 등록에는 추가 검증이 필요해요.", color = Muted, lineHeight = 24.sp)
                        Text("MOCK 시연 · 실제 공유·등록 없음", color = Muted, fontSize = 12.sp)
                    }
                    Text("송금 전, 저장된 연락처로 직접 확인하세요.", color = Muted, lineHeight = 24.sp)
                } else DemoCard {
                    Text("통화가 종료되었습니다", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Note("전화 시연이 끝났습니다. 위협 정보는 공유하지 않았습니다.")
                }
            } else if (warning) {
                DemoCard(color = Color(0xFF3A1D1B)) {
                    IconDisc(Icons.Outlined.WarningAmber, Rust, 48)
                    Text("합성음성 의심", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("통화를 멈추고\n기존 연락처로 확인하세요", fontSize = 20.sp, lineHeight = 28.sp, color = Color(0xFFFFCEC5))
                    Text("송금·개인정보 요청에 응하지 마세요.", color = Color(0xFFFFCEC5), lineHeight = 24.sp)
                    Text("MOCK 경고 · 실제 AI 판정 아님", color = Rust, fontSize = 12.sp)
                }
            } else if (gaugeVisible) {
                DemoCard {
                    Text("피싱 위험도", fontWeight = FontWeight.Bold)
                    CallRiskGauge(score)
                    Text(if (ticks < 80) "음성의 위험 신호를 살펴보고 있어요" else "위험 신호 감지 · 정밀 확인 중", color = if (ticks < 80) Lime else Amber, fontSize = 14.sp)
                    Text("MOCK 점수 · 실제 피싱 확률 아님", color = Muted, fontSize = 12.sp)
                }
            } else {
                Spacer(Modifier.height(24.dp))
                IconDisc(Icons.Outlined.PersonOutline, Muted, 120)
            }
        }
    }
}

@Composable private fun CallControl(icon: ImageVector, label: String, selected: Boolean, danger: Boolean = false, click: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledIconToggleButton(checked = selected, onCheckedChange = { click() }, modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = if (danger) Rust else Ink,
                contentColor = if (danger) Paper else Color.White, checkedContainerColor = Lime, checkedContentColor = Paper)) {
            Icon(icon, label)
        }
        Text(label, color = Muted, fontSize = 12.sp)
    }
}
