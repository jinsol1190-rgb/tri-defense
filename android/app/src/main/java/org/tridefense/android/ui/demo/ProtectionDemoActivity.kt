package org.tridefense.android.ui.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Separate user-B concept screen. Never calls screening, audio, AI, Room, HTTP or contracts. */
class ProtectionDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        // Presenter-only selector; not an incoming call or a verified lookup result.
        val changedNumber = intent.getStringExtra("scenario") == "changed"
        setContent { ProtectionDemo(changedNumber) }
    }
}

@Composable internal fun ProtectionDemo(changedNumber: Boolean) = DemoTheme {
    var ended by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    Scaffold(containerColor = Paper, topBar = {
        Row(Modifier.statusBarsPadding().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tri-Defense", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Surface(color = Ink, shape = RoundedCornerShape(8.dp)) {
                Text("MOCK DEMO", Modifier.padding(8.dp), color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }, bottomBar = {
        Column(Modifier.navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (changedNumber && !ended) Action("통화 종료", { ended = true }, danger = true)
            Text("사용자 B 보호 시연 · 실제 차단·음성 대조 없음", color = Muted, fontSize = 11.sp)
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (changedNumber) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (ended) "통화 종료" else "통화 중 · SAMPLE", color = Muted)
                    Text("알 수 없는 번호", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("+1 202-555-0147", color = Muted)
                }
                DemoCard(color = if (ended) Ink else Color(0xFF3A1D1B)) {
                    IconDisc(if (ended) Icons.Outlined.CallEnd else Icons.Outlined.WarningAmber, if (ended) Lime else Rust, 56)
                    Text(if (ended) "기존 연락처로 확인하세요" else "등록된 위험 음성과\n유사해요", fontSize = 27.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
                    Text(if (ended) "확인 전에는 송금하거나 개인정보를 알려주지 마세요." else "통화를 멈추고\n기존 연락처로 확인하세요.", fontSize = 19.sp, lineHeight = 28.sp)
                    Text("MOCK 결과 · 실제 음성 대조 아님", color = if (ended) Muted else Rust, fontSize = 12.sp)
                }
                DemoCard {
                    Text("전화번호만으로 차단하지 않아요", fontWeight = FontWeight.Bold)
                    Note("음성 확보 후 경고하는 예시이며, 동일인으로 확정하지 않습니다.")
                    Note("이 번호는 차단 목록에 등록하지 않았습니다.")
                }
            } else {
                Text("보호 알림", color = Muted)
                DemoCard {
                    IconDisc(Icons.Outlined.VerifiedUser, Lime, 48)
                    Text("위험 전화를\n차단했어요", fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                    Text("등록된 악성 번호와 일치합니다.", color = Muted, lineHeight = 25.sp)
                    Text("수신 전 차단 · MOCK", color = Lime, fontWeight = FontWeight.Bold)
                    Note("실제로 전화를 차단한 결과가 아닙니다.")
                }
                Text("최근 보호 기록", style = MaterialTheme.typography.titleLarge)
                DemoCard {
                    Text("등록된 악성 번호", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SAMPLE · 09:41", color = Muted)
                        Text("수신 전 차단", color = Lime)
                    }
                    TextButton({ details = !details }) { Text(if (details) "기록 접기" else "보호 기록 보기") }
                    if (details) Note("가상 번호: +1 202-555-0123\n별도 검증을 거쳐 이미 차단 목록에 등록된 번호를 가정한 MOCK 기록입니다. 실제 조회·저장·차단은 없습니다.")
                }
            }
        }
    }
}
