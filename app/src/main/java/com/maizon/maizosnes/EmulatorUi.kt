package com.maizon.maizosnes

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import java.io.File
import kotlin.math.floor
import kotlin.math.min

internal enum class AppScreen { LIBRARY, MULTIPLAYER, SETTINGS, GAME }
internal enum class AspectMode(val label: String) {
    PIXEL("Pixel perfect"),
    CRT("4:3 (CRT)"),
    WIDE("16:9"),
    STRETCH("Esticar tela"),
    FILL("Preencher")
}

internal data class TouchLayout(
    val enabled: Boolean = true,
    val dpadX: Float = 0.02f,
    val dpadY: Float = 0.025f,
    val buttonsX: Float = 0.02f,
    val buttonsY: Float = 0.025f,
    val scale: Float = 1f,
    val opacity: Float = 0.62f
)

internal data class AppSettings(
    val aspect: AspectMode = AspectMode.CRT,
    val touch: TouchLayout = TouchLayout(),
    val audioEnabled: Boolean = true,
    val volume: Float = 1f
)

private val Bg = Color(0xFF080B13)
private val Panel = Color(0xFF151C2A)
private val PanelRaised = Color(0xFF20293A)
private val Accent = Color(0xFF8B7CFF)
private val Mint = Color(0xFF69E8BE)
private val Muted = Color(0xFFA6B0C3)

@Composable
internal fun MaizoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, secondary = Mint, background = Bg, surface = Panel,
            onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

@Composable
internal fun MaizoEmulatorApp(
    romNames: List<String>,
    screen: AppScreen,
    statusMessage: String,
    currentRomName: String?,
    hasLoadedRom: Boolean,
    paused: Boolean,
    settings: AppSettings,
    coreOptions: Map<String, String>,
    onChooseAddRom: () -> Unit,
    onOpenRom: (String) -> Unit,
    onContinueGame: () -> Unit,
    onRenameRom: (String, String) -> Unit,
    onSetScreen: (AppScreen) -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onCoreOptionChanged: (String, String) -> Unit,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    onExportState: () -> Unit,
    onImportState: () -> Unit,
    onResetGame: () -> Unit,
    onTogglePause: () -> Unit,
    onExitGame: () -> Unit,
    onInputChanged: (Int, Boolean) -> Unit,
    onSurfaceChanged: (Surface?) -> Unit
) {
    var showGameMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = screen == AppScreen.GAME) { onExitGame() }

    if (screen == AppScreen.GAME) {
        GameScreen(
            romName = currentRomName ?: "SNES",
            paused = paused,
            settings = settings,
            onExit = onExitGame,
            onPause = onTogglePause,
            onOpenMenu = { showGameMenu = true },
            onSurfaceChanged = onSurfaceChanged,
            onInputChanged = onInputChanged
        )
        if (showGameMenu) {
            Dialog(onDismissRequest = { showGameMenu = false }) {
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 690.dp),
                    shape = RoundedCornerShape(26.dp), color = Bg, tonalElevation = 12.dp
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("MENU DO EMULADOR", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                Text(currentRomName ?: "Jogo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { showGameMenu = false }) { Text("FECHAR") }
                        }
                        EmulatorSettings(settings, coreOptions, true, onSettingsChanged, onCoreOptionChanged, onSaveSlot, onLoadSlot, onExportState, onImportState, onResetGame)
                    }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101624), modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                NavigationBarItem(screen == AppScreen.LIBRARY, { onSetScreen(AppScreen.LIBRARY) }, icon = { Icon(Icons.Default.Gamepad, null) }, label = { Text("Biblioteca") })
                NavigationBarItem(screen == AppScreen.MULTIPLAYER, { onSetScreen(AppScreen.MULTIPLAYER) }, icon = { Icon(Icons.Default.Groups, null) }, label = { Text("LAN") })
                NavigationBarItem(screen == AppScreen.SETTINGS, { onSetScreen(AppScreen.SETTINGS) }, icon = { Icon(Icons.Default.Tune, null) }, label = { Text("Ajustes") })
            }
        }
    ) { padding ->
        when (screen) {
            AppScreen.LIBRARY -> LibraryScreen(
                Modifier.fillMaxSize().padding(padding), romNames, statusMessage, currentRomName,
                hasLoadedRom, onChooseAddRom, onOpenRom, onContinueGame, onRenameRom
            )
            AppScreen.MULTIPLAYER -> MultiplayerScreen(Modifier.fillMaxSize().padding(padding))
            AppScreen.SETTINGS -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("AJUSTES", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Configure o emulador", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 14.dp))
                EmulatorSettings(settings, coreOptions, false, onSettingsChanged, onCoreOptionChanged, onSaveSlot, onLoadSlot, onExportState, onImportState, onResetGame)
            }
            AppScreen.GAME -> Unit
        }
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    romNames: List<String>,
    statusMessage: String,
    currentRomName: String?,
    hasLoadedRom: Boolean,
    onChooseAddRom: () -> Unit,
    onOpenRom: (String) -> Unit,
    onContinueGame: () -> Unit,
    onRenameRom: (String, String) -> Unit
) {
    var renameTarget by remember { mutableStateOf<String?>(null) }
    Column(modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Accent, Color(0xFF4540A1)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Gamepad, null, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("MAIZO", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("SNES", fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            Surface(color = Color(0xFF1A2C2A), shape = CircleShape) {
                Text("Snes9x 1.63", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF29264E), Panel, Color(0xFF102B31)))).padding(20.dp)) {
                Text("SUA COLEÇÃO", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Spacer(Modifier.height(8.dp))
                Text("Seu próximo jogo começa aqui.", fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(7.dp))
                Text("Adicione suas ROMs uma vez. Depois, abra qualquer jogo direto desta biblioteca.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onChooseAddRom, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("+ ADICIONAR ROM", fontWeight = FontWeight.Bold)
                    }
                    if (hasLoadedRom) OutlinedButton(onClick = onContinueGame) { Text("CONTINUAR") }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Biblioteca", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text(romNames.size.toString() + " jogo(s) importado(s)", color = Muted, fontSize = 12.sp)
            }
            if (currentRomName != null) Text("Em memória: " + File(currentRomName).nameWithoutExtension, color = Mint, fontSize = 10.sp)
        }
        if (romNames.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Gamepad, null, tint = Accent, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Sua biblioteca está vazia", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Toque em Adicionar ROM para importar .sfc ou .smc.", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(romNames, key = { it }) { name ->
                    RomCard(name, currentRomName == name, { onOpenRom(name) }, { renameTarget = name })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
        Text(statusMessage, color = Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
    }
    renameTarget?.let { target ->
        var editedName by remember(target) { mutableStateOf(File(target).nameWithoutExtension) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renomear ROM") },
            text = {
                Column {
                    Text("A extensão ." + File(target).extension + " será mantida automaticamente.", color = Muted, fontSize = 12.sp)
                    TextField(value = editedName, onValueChange = { editedName = it }, singleLine = true, label = { Text("Nome do jogo") })
                }
            },
            confirmButton = { TextButton(onClick = { onRenameRom(target, editedName); renameTarget = null }) { Text("SALVAR") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("CANCELAR") } }
        )
    }
}

@Composable
private fun RomCard(fileName: String, current: Boolean, onOpen: () -> Unit, onRename: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF28244A)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Gamepad, null, tint = Accent)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(File(fileName).nameWithoutExtension, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text(File(fileName).extension.uppercase() + if (current) " • ABERTO" else " • ROM LOCAL", color = if (current) Mint else Muted, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Accent), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)) {
                    Text("JOGAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onRename, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Renomear", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun MultiplayerScreen(modifier: Modifier) {
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("MULTIPLAYER", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Jogue com alguém na mesma rede Wi-Fi", fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("A sala LAN ainda não está habilitada nesta versão. Esta tela não simula uma conexão nem mostra falsos estados de conectado.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("O que falta", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("• Sincronizar entradas por número de frame nos dois aparelhos.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                Text("• Negociar hash da ROM, versão do núcleo e opções determinísticas antes de iniciar.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                Text("• Mapear o segundo controle para a porta 2 do Snes9x e encerrar sessões sem travar o loop.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                Text("• Detectar dessincronização e recuperar ou abortar a sessão com segurança.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        Text("Detalhes técnicos em docs/NETPLAY.md no repositório.", color = Mint, fontSize = 12.sp)
    }
}

@Composable
private fun GameScreen(
    romName: String,
    paused: Boolean,
    settings: AppSettings,
    onExit: () -> Unit,
    onPause: () -> Unit,
    onOpenMenu: () -> Unit,
    onSurfaceChanged: (Surface?) -> Unit,
    onInputChanged: (Int, Boolean) -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(settings.aspect, onSurfaceChanged)
        if (settings.touch.enabled) TouchOverlay(settings.touch, onInputChanged)
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).clip(RoundedCornerShape(18.dp))
                .background(Color(0xB51A2030)).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onExit) { Text("‹ Biblioteca", fontSize = 12.sp) }
            Column(Modifier.weight(1f, fill = false)) {
                Text(File(romName).nameWithoutExtension, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                if (paused) Text("PAUSADO", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onPause) { Text(if (paused) "▶" else "Ⅱ", fontSize = 17.sp) }
            TextButton(onClick = onOpenMenu) { Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = Color.White) }
        }
        if (paused) {
            Surface(
                modifier = Modifier.align(Alignment.Center).clickable(onClick = onPause),
                color = Color(0xC91A2030), shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(horizontal = 25.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("JOGO PAUSADO", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("Toque para retomar", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun VideoSurface(aspect: AspectMode, onSurfaceChanged: (Surface?) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val size = when (aspect) {
            AspectMode.STRETCH -> maxWidth to maxHeight
            AspectMode.FILL -> maxWidth to (maxWidth / (4f / 3f))
            AspectMode.PIXEL -> {
                val integerScale = floor(min(widthPx / 256f, heightPx / 224f)).toInt().coerceAtLeast(1)
                with(density) { (256 * integerScale).toDp() } to with(density) { (224 * integerScale).toDp() }
            }
            AspectMode.CRT -> containedSize(maxWidth, maxHeight, 4f / 3f)
            AspectMode.WIDE -> containedSize(maxWidth, maxHeight, 16f / 9f)
        }
        Box(Modifier.align(Alignment.Center).requiredWidth(size.first).requiredHeight(size.second)) {
            AndroidViewSurface(onSurfaceChanged)
        }
    }
}

@Composable
private fun AndroidViewSurface(onSurfaceChanged: (Surface?) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) { onSurfaceChanged(holder.surface) }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { onSurfaceChanged(holder.surface) }
                    override fun surfaceDestroyed(holder: SurfaceHolder) { onSurfaceChanged(null) }
                })
            }
        }
    )
}

private fun containedSize(maxWidth: Dp, maxHeight: Dp, targetRatio: Float): Pair<Dp, Dp> {
    val availableRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
    return if (availableRatio > targetRatio) (maxHeight * targetRatio) to maxHeight
    else maxWidth to (maxWidth / targetRatio)
}

@Composable
private fun TouchOverlay(layout: TouchLayout, onInputChanged: (Int, Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dpadSize = 51f * layout.scale
        val actionSize = 49f * layout.scale
        val yDpad = -(maxHeight * layout.dpadY.coerceIn(0f, 0.27f))
        val xDpad = maxWidth * layout.dpadX.coerceIn(0f, 0.27f)
        val yButtons = -(maxHeight * layout.buttonsY.coerceIn(0f, 0.27f))
        val xButtons = -(maxWidth * layout.buttonsX.coerceIn(0f, 0.27f))
        Column(
            modifier = Modifier.align(Alignment.BottomStart).offset(x = xDpad, y = yDpad).padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TouchButton("↑", 4, dpadSize, layout.opacity, onInputChanged)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TouchButton("←", 6, dpadSize, layout.opacity, onInputChanged)
                Spacer(Modifier.size(dpadSize.dp).clip(CircleShape).background(Color.White.copy(alpha = layout.opacity * 0.22f)))
                TouchButton("→", 7, dpadSize, layout.opacity, onInputChanged)
            }
            TouchButton("↓", 5, dpadSize, layout.opacity, onInputChanged)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = xButtons, y = yButtons).padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TouchButton("Y", 1, actionSize, layout.opacity, onInputChanged)
                TouchButton("X", 9, actionSize, layout.opacity, onInputChanged)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TouchButton("B", 0, actionSize, layout.opacity, onInputChanged)
                TouchButton("A", 8, actionSize, layout.opacity, onInputChanged)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TouchButton("L", 10, actionSize * 0.82f, layout.opacity, onInputChanged)
                TouchButton("R", 11, actionSize * 0.82f, layout.opacity, onInputChanged)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            TouchButton("SELECT", 2, 34f * layout.scale, layout.opacity, onInputChanged, true)
            TouchButton("START", 3, 34f * layout.scale, layout.opacity, onInputChanged, true)
        }
    }
}

@Composable
private fun TouchButton(
    label: String, bit: Int, size: Float, opacity: Float,
    onInputChanged: (Int, Boolean) -> Unit, isPill: Boolean = false
) {
    val shape = if (isPill) RoundedCornerShape(18.dp) else CircleShape
    Box(
        modifier = (if (isPill) Modifier.width((size * 2f).dp).height(size.dp) else Modifier.size(size.dp))
            .clip(shape).background(Color(0xFFE4E8F2).copy(alpha = opacity.coerceIn(0.18f, 0.9f) * 0.68f))
            .border(1.dp, Color.White.copy(alpha = opacity.coerceIn(0.18f, 0.9f) * 0.65f), shape)
            .pointerInput(bit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onInputChanged(bit, true)
                    try { waitForUpOrCancellation() } finally { onInputChanged(bit, false) }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFF0B1020), fontWeight = FontWeight.Black, fontSize = if (label.length > 3) 8.sp else 15.sp)
    }
}

@Composable
private fun EmulatorSettings(
    settings: AppSettings,
    coreOptions: Map<String, String>,
    inGame: Boolean,
    onSettingsChanged: (AppSettings) -> Unit,
    onCoreOptionChanged: (String, String) -> Unit,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    onExportState: () -> Unit,
    onImportState: () -> Unit,
    onResetGame: () -> Unit
) {
    var category by remember { mutableStateOf("Vídeo") }
    val categories = listOf("Vídeo", "Áudio", "Controles", "Núcleo", "Estados")
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            categories.forEach { item ->
                val selected = item == category
                TextButton(onClick = { category = item }, colors = ButtonDefaults.textButtonColors(contentColor = if (selected) Mint else Muted)) {
                    Text(item, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = if (inGame) 550.dp else 720.dp)
                .verticalScroll(rememberScrollState()).padding(top = 3.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            when (category) {
                "Vídeo" -> {
                    SettingSectionTitle("Proporção do vídeo", if (inGame) "Aplicada imediatamente e salva no perfil do jogo." else "Aplicada ao abrir o jogo.")
                    AspectMode.values().forEach { mode ->
                        val description = when (mode) {
                            AspectMode.PIXEL -> "Escala física inteira baseada em 256×224; modos Hi-Res podem variar."
                            AspectMode.CRT -> "Preserva uma área 4:3 com barras laterais quando necessário."
                            AspectMode.WIDE -> "Enquadra o jogo em uma área 16:9."
                            AspectMode.STRETCH -> "Estica para ocupar toda a área, podendo distorcer a imagem."
                            AspectMode.FILL -> "Preenche usando 4:3 e corta parte da imagem se necessário."
                        }
                        ChoiceRow(mode.label, description, settings.aspect == mode) { onSettingsChanged(settings.copy(aspect = mode)) }
                    }
                    CoreOptionDefinitions.all.filter { it.category == "Vídeo" }.forEach { option ->
                        CoreOptionRow(option, coreOptions[option.key] ?: option.defaultValue, onCoreOptionChanged)
                    }
                }
                "Áudio" -> {
                    ToggleSettingRow("Áudio do jogo", "Silencia a saída sem parar a emulação.", settings.audioEnabled) {
                        onSettingsChanged(settings.copy(audioEnabled = it))
                    }
                    SettingSectionTitle("Volume de saída", (settings.volume * 100).toInt().toString() + "%")
                    Slider(value = settings.volume, onValueChange = { onSettingsChanged(settings.copy(volume = it)) }, valueRange = 0f..1f)
                    CoreOptionDefinitions.all.filter { it.category == "Áudio" }.forEach { option ->
                        CoreOptionRow(option, coreOptions[option.key] ?: option.defaultValue, onCoreOptionChanged)
                    }
                }
                "Controles" -> {
                    ToggleSettingRow("Controles na tela", "Exibe os comandos sobrepostos ao vídeo.", settings.touch.enabled) {
                        onSettingsChanged(settings.copy(touch = settings.touch.copy(enabled = it)))
                    }
                    SettingSectionTitle("Editor de layout", "Ajuste posição, tamanho e transparência; as mudanças são salvas no perfil ativo.")
                    SliderSetting("D-pad: horizontal", settings.touch.dpadX, 0f..0.27f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(dpadX = it))) }, (settings.touch.dpadX * 100).toInt().toString() + "%")
                    SliderSetting("D-pad: subir", settings.touch.dpadY, 0f..0.27f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(dpadY = it))) }, (settings.touch.dpadY * 100).toInt().toString() + "%")
                    SliderSetting("Botões: horizontal", settings.touch.buttonsX, 0f..0.27f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(buttonsX = it))) }, (settings.touch.buttonsX * 100).toInt().toString() + "%")
                    SliderSetting("Botões: subir", settings.touch.buttonsY, 0f..0.27f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(buttonsY = it))) }, (settings.touch.buttonsY * 100).toInt().toString() + "%")
                    SliderSetting("Tamanho", settings.touch.scale, 0.70f..1.45f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(scale = it))) }, (settings.touch.scale * 100).toInt().toString() + "%")
                    SliderSetting("Opacidade", settings.touch.opacity, 0.20f..0.90f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(opacity = it))) }, (settings.touch.opacity * 100).toInt().toString() + "%")
                    OutlinedButton(onClick = { onSettingsChanged(settings.copy(touch = TouchLayout())) }, modifier = Modifier.fillMaxWidth()) { Text("RESTAURAR LAYOUT PADRÃO") }
                }
                "Núcleo" -> {
                    SettingSectionTitle("Snes9x 1.63", "Opções reais do núcleo. Alterações são consultadas no caminho de atualização do core.")
                    CoreOptionDefinitions.all.filter { it.category == "Núcleo" }.forEach { option ->
                        CoreOptionRow(option, coreOptions[option.key] ?: option.defaultValue, onCoreOptionChanged)
                    }
                    Text("Overclock e hacks podem causar incompatibilidades. Os valores padrão são recomendados.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                }
                "Estados" -> {
                    SettingSectionTitle("Save states", "Slots privados do app e arquivos externos. O núcleo rejeita estados de outra ROM.")
                    (1..3).forEach { slot ->
                        Card(colors = CardDefaults.cardColors(containerColor = PanelRaised), shape = RoundedCornerShape(15.dp)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Slot " + slot, fontWeight = FontWeight.Bold)
                                    Text("Salvar ou restaurar o estado atual.", color = Muted, fontSize = 10.sp)
                                }
                                TextButton(onClick = { onSaveSlot(slot) }) { Text("SALVAR") }
                                TextButton(onClick = { onLoadSlot(slot) }) { Text("CARREGAR") }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportState, modifier = Modifier.weight(1f)) { Text("EXPORTAR") }
                        OutlinedButton(onClick = onImportState, modifier = Modifier.weight(1f)) { Text("IMPORTAR") }
                    }
                    if (inGame) OutlinedButton(onClick = onResetGame, modifier = Modifier.fillMaxWidth()) { Text("REINICIAR JOGO") }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF252447) else Panel),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
            if (selected) Text("✓", color = Mint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CoreOptionRow(option: CoreOptionDefinition, current: String, onOptionChanged: (String, String) -> Unit) {
    var expanded by remember(option.key) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(option.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(option.description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp, bottom = 5.dp))
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(displayCoreOptionValue(current))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    option.values.forEach { value ->
                        DropdownMenuItem(
                            text = { Text(displayCoreOptionValue(value)) },
                            onClick = { expanded = false; onOptionChanged(option.key, value) }
                        )
                    }
                }
            }
        }
    }
}

private fun displayCoreOptionValue(value: String): String = when (value) {
    "enabled" -> "Ativado"
    "disabled" -> "Desativado"
    "auto" -> "Automático"
    "4:3 scaled" -> "4:3 preservado"
    "uncorrected" -> "Sem correção"
    "gaussian" -> "Gaussiana"
    "cubic" -> "Cúbica"
    "sinc" -> "Sinc"
    "linear" -> "Linear"
    "none" -> "Nenhuma"
    "compatible" -> "Compatível"
    "light" -> "Leve"
    "max" -> "Máximo"
    "merge" -> "Mesclar"
    "blur" -> "Desfoque"
    else -> value
}

@Composable
private fun ToggleSettingRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit, trailing: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(trailing, color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
    }
}

@Composable
private fun SettingSectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 5.dp, bottom = 2.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}
