package com.maizon.maizosnes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF090D18)
private val Panel = Color(0xFF151C2D)
private val Violet = Color(0xFF8B7CFF)
private val Mint = Color(0xFF6EF0C2)
private val Muted = Color(0xFF9AA6BE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 13, 24)
        window.navigationBarColor = android.graphics.Color.rgb(9, 13, 24)
        val nativeInitResult = runCatching { NativeBridge.nativeInit() }.getOrElse { -999 }
        setContent { MaizoTheme { MaizoApp(nativeInitResult) } }
    }
}

@Composable
private fun MaizoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet,
            secondary = Mint,
            background = Ink,
            surface = Panel,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
private fun MaizoApp(nativeInitResult: Int) {
    var tab by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf(if (nativeInitResult == 0) "JNI retornou 0" else "Falha na inicialização JNI: $nativeInitResult") }
    Scaffold(
        containerColor = Ink,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101625), modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Gamepad, null) }, label = { Text("Início") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Groups, null) }, label = { Text("Multiplayer") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Tune, null) }, label = { Text("Ajustes") })
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(10.dp)); Header() }
            when (tab) {
                0 -> {
                    item { HeroCard(onOpen = { notice = "O carregador de ROM será conectado na próxima etapa." }) }
                    item { SectionTitle("CENTRAL DE JOGO", "Seu SNES, do seu jeito") }
                    item { FeatureCard(Icons.Default.Bolt, "Desempenho primeiro", "Base nativa ARM64/ARMv7 e foco em frame pacing estável.", Mint) }
                    item { FeatureCard(Icons.Default.Groups, "Jogue junto", "A sala local e o netplay serão ativados após a integração do núcleo.", Violet) }
                    item { StatusBanner(notice) }
                }
                1 -> {
                    item { SectionTitle("JOGUE COM ALGUÉM", "Multiplayer") }
                    item { ActionCard("Criar sala local", "Prepare uma partida na mesma rede Wi-Fi", "CRIAR SALA") { notice = "Ainda não conectado: transporte de rede em desenvolvimento." } }
                    item { ActionCard("Entrar em uma sala", "Conecte-se ao aparelho que está hospedando", "PROCURAR") { notice = "A descoberta de salas ainda não foi implementada." } }
                    item { ActionCard("Jogar pela internet", "Netplay com sincronização e rollback", "EM BREVE") { notice = "Rollback depende de estado serializável e determinismo do núcleo." } }
                    item { StatusBanner(notice) }
                }
                else -> {
                    item { SectionTitle("PERSONALIZE", "Ajustes") }
                    item { FeatureCard(Icons.Default.Tune, "Vídeo e upscale", "Planejado: proporção, filtros GLSL, pixel-perfect e shaders CRT.", Violet) }
                    item { FeatureCard(Icons.Default.Gamepad, "Controles", "Planejado: layout móvel, tamanho/posição dos botões e controles Bluetooth.", Mint) }
                    item { FeatureCard(Icons.Default.Bolt, "Desempenho", "Planejado: FPS, frame time, latência de rede e diagnóstico de quedas.", Violet) }
                    item { StatusBanner("As opções serão habilitadas quando os componentes reais forem integrados.") }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(
            modifier = Modifier.size(46.dp).background(Brush.linearGradient(listOf(Violet, Color(0xFF5145B8))), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Gamepad, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("MAIZO", fontSize = 12.sp, letterSpacing = 3.sp, color = Mint, fontWeight = FontWeight.Bold)
            Text("SNES", fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        Surface(shape = CircleShape, color = Color(0xFF1D2B36)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(Mint, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text("DEV BUILD", fontSize = 10.sp, color = Mint, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroCard(onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Box(
            Modifier.background(Brush.linearGradient(listOf(Color(0xFF29264E), Color(0xFF171E31), Color(0xFF102A32))))
                .padding(22.dp)
        ) {
            Column {
                Surface(color = Color(0x338B7CFF), shape = RoundedCornerShape(9.dp)) {
                    Text("RETRO. REIMAGINADO.", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color(0xFFC7C0FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(18.dp))
                Text("Sua próxima
aventura começa aqui.", fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Text("Uma nova experiência SNES, feita para Android e para jogar junto.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onOpen,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Gamepad, null)
                    Spacer(Modifier.width(9.dp))
                    Text("ABRIR BIBLIOTECA", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(eyebrow: String, title: String) {
    Column {
        Text(eyebrow, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(5.dp))
        Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(description, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun ActionCard(title: String, description: String, button: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(description, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onClick, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29264E))) {
                Text(button, fontWeight = FontWeight.Bold, color = Color(0xFFC7C0FF))
            }
        }
    }
}

@Composable
private fun StatusBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF182333), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(Mint, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
