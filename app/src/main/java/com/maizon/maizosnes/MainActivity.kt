package com.maizon.maizosnes

import android.os.Bundle

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
    private val statusMessage = mutableStateOf("Preparando o núcleo Snes9x...")
    private val pressedInputs = AtomicInteger(0)
    private var audioOutput: AudioOutput? = null
    private var hasLoadedRom = false
    private var activityResumed = false
    private var surfaceAttached = false

    private val openRomLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadRom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 13, 24)
        window.navigationBarColor = android.graphics.Color.rgb(9, 13, 24)

        val initResult = runCatching { NativeBridge.nativeInit(filesDir.absolutePath) }.getOrElse {
            statusMessage.value = "Falha ao iniciar Snes9x: ${it.message ?: "erro JNI"}"
            -999
        }
        if (initResult == 0) {
            statusMessage.value = "Snes9x 1.63 inicializado. Abra uma ROM própria (.smc/.sfc)."
            audioOutput = AudioOutput(NativeBridge.nativeGetAudioSampleRate())
        } else if (statusMessage.value.startsWith("Preparando")) {
            statusMessage.value = "Falha na inicialização do núcleo: $initResult"
        }

        val licenseText = readBundledLicenses()
        setContent {
            MaizoTheme {
                MaizoApp(
                    statusMessage = statusMessage.value,
                    licenseText = licenseText,
                    onOpenRom = { openRomLauncher.launch(arrayOf("*/*")) },
                    onSurfaceChanged = ::updateEmulatorSurface,
                    onInputChanged = ::updateInput
                )
            }
        }
    }

    private fun updateInput(bit: Int, pressed: Boolean) {
        if (bit !in 0..15) return
        val mask = 1 shl bit
        var previous: Int
        var next: Int
        do {
            previous = pressedInputs.get()
            next = if (pressed) previous or mask else previous and mask.inv()
        } while (!pressedInputs.compareAndSet(previous, next))
        runCatching { NativeBridge.nativeSetInput(next) }
    }

    private fun loadRom(uri: Uri) {
        lifecycleScope.launch {
            statusMessage.value = "Lendo arquivo de ROM..."
            try {
                val (bytes, name) = withContext(Dispatchers.IO) { readRom(uri) }
                // Stop the consumer before the native core resets its audio ring during ROM changes.
                audioOutput?.stop()
                val result = withContext(Dispatchers.Default) {
                    NativeBridge.nativeLoadRom(bytes, name)
                }
                if (result == 0) {
                    hasLoadedRom = true
                    // Recreate the output using the rate reported after this ROM was loaded.
                    audioOutput?.stop()
                    audioOutput = AudioOutput(NativeBridge.nativeGetAudioSampleRate())
                    syncPlaybackState()
                    statusMessage.value = "ROM carregada: $name"
                } else {
                    hasLoadedRom = false
                    syncPlaybackState()
                    statusMessage.value = "O núcleo recusou a ROM ($result). Use uma ROM SNES válida, sem ZIP."
                }
            } catch (error: Exception) {
                statusMessage.value = "Não foi possível abrir a ROM: ${error.message ?: "erro de leitura"}"
            }
        }
    }

    private fun readRom(uri: Uri): Pair<ByteArray, String> {
        val name = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
        }?.substringAfterLast('/')?.take(128)?.takeIf { it.isNotBlank() } ?: "game.sfc"

        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 64 * 1024 * 1024) { "arquivo maior que 64 MiB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalStateException("o Android não conseguiu abrir o arquivo escolhido")

        require(bytes.isNotEmpty()) { "arquivo vazio" }
        return bytes to name
    }

    private fun updateEmulatorSurface(surface: Surface?) {
        surfaceAttached = surface != null
        runCatching { NativeBridge.nativeSetSurface(surface) }
        syncPlaybackState()
    }

    /**
     * A running emulation requires a loaded game, a resumed Activity and a live SurfaceView.
     * Stop the audio consumer before pausing the core so the native ring can be safely drained.
     */
    private fun syncPlaybackState() {
        val shouldRun = activityResumed && surfaceAttached && hasLoadedRom
        if (!shouldRun) audioOutput?.stop()
        runCatching { NativeBridge.nativeSetPaused(!shouldRun) }
        if (shouldRun) audioOutput?.start()
    }

    override fun onPause() {
        activityResumed = false
        syncPlaybackState()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        syncPlaybackState()
    }

    private fun readBundledLicenses(): String {
        fun read(path: String): String = runCatching {
            assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { "Arquivo de licença indisponível no pacote: $path" }

        return buildString {
            append("MaizoSNES inclui o núcleo Snes9x 1.63 e o filtro snes_ntsc. ")
            append("O Snes9x tem restrições de uso não comercial; consulte o texto integral abaixo.\n\n")
            append("===== LICENÇA SNES9X 1.63 =====\n\n")
            append(read("licenses/snes9x.txt"))
            append("\n\n===== LICENÇA DO FILTRO SNES_NTSC =====\n\n")
            append(read("licenses/snes_ntsc-license.txt"))
        }
    }

    override fun onDestroy() {
        activityResumed = false
        surfaceAttached = false
        syncPlaybackState()
        audioOutput = null
        runCatching { NativeBridge.nativeShutdown() }
        super.onDestroy()
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
private fun MaizoApp(
    statusMessage: String,
    licenseText: String,
    onOpenRom: () -> Unit,
    onSurfaceChanged: (Surface?) -> Unit,
    onInputChanged: (Int, Boolean) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var notice by remember(statusMessage) { mutableStateOf(statusMessage) }
    var showLicenses by remember { mutableStateOf(false) }
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
                    item { HeroCard(onOpen = { onOpenRom(); notice = "Selecione uma ROM SNES no armazenamento do aparelho." }) }
                    item { EmulatorSurface(onSurfaceChanged) }
                    item { VirtualController(onInputChanged) }
                    item { SectionTitle("CENTRAL DE JOGO", "Seu SNES, do seu jeito") }
                    item { FeatureCard(Icons.Default.Bolt, "Desempenho primeiro", "Núcleo nativo ARM64, com foco em frame pacing estável.", Mint) }
                    item { FeatureCard(Icons.Default.Groups, "Jogue junto", "A sala local e o netplay ainda estão em desenvolvimento.", Violet) }
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
                    item { ActionCard("Avisos e licenças", "Consulte os textos das licenças incluídas neste APK.", "VER LICENÇAS") { showLicenses = true } }
                    item { StatusBanner("As opções serão habilitadas quando os componentes reais forem integrados.") }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Avisos e licenças de terceiros") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(licenseText, fontSize = 11.sp, lineHeight = 15.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text("FECHAR") }
            }
        )
    }
}


@Composable
private fun EmulatorSurface(onSurfaceChanged: (Surface?) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        onSurfaceChanged(holder.surface)
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        onSurfaceChanged(holder.surface)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        onSurfaceChanged(null)
                    }
                })
            }
        }
    )
}

@Composable
private fun VirtualController(onInputChanged: (Int, Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ControllerButton("↑", 4, onInputChanged)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ControllerButton("←", 6, onInputChanged)
                ControllerButton("↓", 5, onInputChanged)
                ControllerButton("→", 7, onInputChanged)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ControllerButton("Y", 1, onInputChanged)
                ControllerButton("X", 9, onInputChanged)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ControllerButton("B", 0, onInputChanged)
                ControllerButton("A", 8, onInputChanged)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ControllerButton("SEL", 2, onInputChanged)
                ControllerButton("START", 3, onInputChanged, width = 66)
            }
        }
    }
}

@Composable
private fun ControllerButton(
    label: String,
    bit: Int,
    onInputChanged: (Int, Boolean) -> Unit,
    width: Int = 52
) {
    Box(
        modifier = Modifier
            .size(width = width.dp, height = 48.dp)
            .background(Color(0xFF252C43), RoundedCornerShape(14.dp))
            .pointerInput(bit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onInputChanged(bit, true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onInputChanged(bit, false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = if (label.length > 3) 9.sp else 14.sp, fontWeight = FontWeight.Bold)
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
                Text("Sua próxima aventura começa aqui.", fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
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
