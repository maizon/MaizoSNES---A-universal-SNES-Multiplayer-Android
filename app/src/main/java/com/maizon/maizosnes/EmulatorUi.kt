package com.maizon.maizosnes

import android.graphics.Bitmap
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val opacity: Float = 0.62f,
    val preset: TouchPreset = TouchPreset.MAIZO,
    val buttonShifts: Map<Int, ButtonShift> = emptyMap()
)

internal data class AppSettings(
    val aspect: AspectMode = AspectMode.CRT,
    val touch: TouchLayout = TouchLayout(),
    val audioEnabled: Boolean = true,
    val volume: Float = 1f,
    val filter: VideoFilter = VideoFilter.ORIGINAL,
    val performanceHud: Boolean = false,
    val automaticCovers: Boolean = true,
    val automaticBackup: Boolean = true
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
    coverBitmaps: Map<String, Bitmap>,
    screen: AppScreen,
    statusMessage: String,
    currentRomName: String?,
    hasLoadedRom: Boolean,
    paused: Boolean,
    settings: AppSettings,
    coreOptions: Map<String, String>,
    performanceText: String,
    onSelectTouchPreset: (TouchPreset) -> Unit,
    connectionMode: NetworkMode,
    onConnectionModeChanged: (NetworkMode) -> Unit,
    savedConnectionHost: String,
    savedConnectionPort: Int,
    onSaveConnectionTarget: (String, Int) -> Unit,
    devUnlocked: Boolean,
    devShareEnabled: Boolean,
    onDevUnlock: (String) -> Boolean,
    onDevShareChanged: (Boolean) -> Unit,
    cheats: List<CheatEntry>,
    enabledCheatIds: Set<String>,
    cheatStatus: String,
    onToggleCheat: (String, Boolean) -> Unit,
    onRefreshCheats: () -> Unit,
    multiplayerStatus: String,
    lanRooms: List<LanRoom>,
    scanningLan: Boolean,
    onScanLan: () -> Unit,
    shareRomAllowed: Boolean,
    receiveRomAllowed: Boolean,
    onShareRomAllowedChanged: (Boolean) -> Unit,
    onReceiveRomAllowedChanged: (Boolean) -> Unit,
    localIpAddress: String,
    multiplayerActive: Boolean,
    multiplayerReady: Boolean,
    localMultiplayer: Boolean,
    onHostMultiplayer: (Int) -> Unit,
    onJoinMultiplayer: (String, Int) -> Unit,
    onStopMultiplayer: () -> Unit,
    onLocalMultiplayerChanged: (Boolean) -> Unit,
    onChooseAddRom: () -> Unit,
    onOpenRom: (String) -> Unit,
    onContinueGame: () -> Unit,
    onRenameRom: (String, String) -> Unit,
    onSetScreen: (AppScreen) -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onCoreOptionChanged: (String, String) -> Unit,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    onRestoreMultiplayerBackup: (String) -> Unit,
    onExportState: () -> Unit,
    onImportState: () -> Unit,
    onResetGame: () -> Unit,
    onTogglePause: () -> Unit,
    onExitGame: () -> Unit,
    onInputChanged: (Int, Boolean) -> Unit,
    onSurfaceChanged: (Surface?) -> Unit
) {
    var showGameMenu by remember { mutableStateOf(false) }
    var editingControls by remember { mutableStateOf(false) }
    BackHandler(enabled = screen == AppScreen.GAME) { onExitGame() }

    if (screen == AppScreen.GAME) {
        GameScreen(
            romName = currentRomName ?: "SNES",
            paused = paused,
            settings = settings,
            performanceText = performanceText,
            editingControls = editingControls,
            onEditControls = { editingControls = !editingControls },
            onSettingsChanged = onSettingsChanged,
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
                        EmulatorSettings(settings, coreOptions, true, onSettingsChanged, onCoreOptionChanged, onSaveSlot, onLoadSlot, onRestoreMultiplayerBackup, onExportState, onImportState, onResetGame, cheats, enabledCheatIds, cheatStatus, onToggleCheat, onRefreshCheats, multiplayerActive,
                            onSelectTouchPreset, connectionMode, onConnectionModeChanged,
                            devUnlocked, devShareEnabled, onDevUnlock, onDevShareChanged)
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
                Modifier.fillMaxSize().padding(padding), romNames, coverBitmaps,
                statusMessage, currentRomName, hasLoadedRom, onChooseAddRom,
                onOpenRom, onContinueGame, onRenameRom
            )
            AppScreen.MULTIPLAYER -> MultiplayerScreen(
                Modifier.fillMaxSize().padding(padding), currentRomName, multiplayerReady,
                multiplayerStatus, localIpAddress, multiplayerActive, localMultiplayer,
                connectionMode, onConnectionModeChanged,
                savedConnectionHost, savedConnectionPort, onSaveConnectionTarget,
                lanRooms, scanningLan, onScanLan,
                shareRomAllowed, receiveRomAllowed,
                onShareRomAllowedChanged, onReceiveRomAllowedChanged,
                onHostMultiplayer, onJoinMultiplayer, onStopMultiplayer,
                onLocalMultiplayerChanged
            )
            AppScreen.SETTINGS -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("AJUSTES", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Configure o emulador", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 14.dp))
                EmulatorSettings(settings, coreOptions, false, onSettingsChanged, onCoreOptionChanged, onSaveSlot, onLoadSlot, onRestoreMultiplayerBackup, onExportState, onImportState, onResetGame, cheats, enabledCheatIds, cheatStatus, onToggleCheat, onRefreshCheats, multiplayerActive,
                    onSelectTouchPreset, connectionMode, onConnectionModeChanged,
                    devUnlocked, devShareEnabled, onDevUnlock, onDevShareChanged)
            }
            AppScreen.GAME -> Unit
        }
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    romNames: List<String>,
    coverBitmaps: Map<String, Bitmap>,
    statusMessage: String,
    currentRomName: String?,
    hasLoadedRom: Boolean,
    onChooseAddRom: () -> Unit,
    onOpenRom: (String) -> Unit,
    onContinueGame: () -> Unit,
    onRenameRom: (String, String) -> Unit
) {
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var libraryQuery by remember { mutableStateOf("") }
    val visibleRoms = remember(romNames, libraryQuery) {
        romNames.filter { it.contains(libraryQuery.trim(), ignoreCase = true) }
    }
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
        if (romNames.isNotEmpty()) {
            TextField(
                value = libraryQuery,
                onValueChange = { libraryQuery = it.take(80) },
                label = { Text("Pesquisar na biblioteca") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )
        }
        if (visibleRoms.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Gamepad, null, tint = Accent, modifier = Modifier.size(50.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(if (romNames.isEmpty()) "Sua biblioteca está vazia"
                        else "Nenhum jogo corresponde à pesquisa",
                        fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Adicione .sfc, .smc ou ZIP com sua ROM.",
                        color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 165.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gridItems(visibleRoms, key = { it }) { name ->
                    RomCard(name, currentRomName == name, coverBitmaps[name],
                        { onOpenRom(name) }, { renameTarget = name })
                }
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
private fun RomCard(
    fileName: String,
    current: Boolean,
    cover: Bitmap?,
    onOpen: () -> Unit,
    onRename: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(150.dp)
                    .background(Brush.linearGradient(
                        listOf(Color(0xFF35305F), Color(0xFF141C30), Color(0xFF0A2D36))
                    )),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    Image(
                        bitmap = cover.asImageBitmap(),
                        contentDescription = "Capa de " + File(fileName).nameWithoutExtension,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(Icons.Default.Gamepad, null, tint = Accent,
                        modifier = Modifier.size(48.dp))
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xC5101523)
                ) {
                    Text(if (current) "EM MEMÓRIA" else "SNES",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        color = if (current) Mint else Color.White,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(File(fileName).nameWithoutExtension,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, lineHeight = 16.sp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRename, contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.weight(1f)) { Text("EDITAR", fontSize = 10.sp) }
                Button(onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 5.dp)) {
                    Text("JOGAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MultiplayerScreen(
    modifier: Modifier,
    romName: String?,
    ready: Boolean,
    status: String,
    localIpAddress: String,
    active: Boolean,
    localMultiplayer: Boolean,
    connectionMode: NetworkMode,
    onConnectionModeChanged: (NetworkMode) -> Unit,
    savedConnectionHost: String,
    savedConnectionPort: Int,
    onSaveConnectionTarget: (String, Int) -> Unit,
    lanRooms: List<LanRoom>,
    scanningLan: Boolean,
    onScanLan: () -> Unit,
    shareRomAllowed: Boolean,
    receiveRomAllowed: Boolean,
    onShareRomAllowedChanged: (Boolean) -> Unit,
    onReceiveRomAllowedChanged: (Boolean) -> Unit,
    onHost: (Int) -> Unit,
    onJoin: (String, Int) -> Unit,
    onStop: () -> Unit,
    onLocalMode: (Boolean) -> Unit
) {
    var remoteHost by remember(connectionMode) { mutableStateOf(savedConnectionHost) }
    var portText by remember(connectionMode) { mutableStateOf(savedConnectionPort.toString()) }
    val port = portText.toIntOrNull()
    LaunchedEffect(connectionMode) {
        if (connectionMode == NetworkMode.LAN_AUTO) onScanLan()
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MULTIPLAYER", color = Mint, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Jogue junto, onde estiver.", fontSize = 25.sp,
            lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold)
        Text("LAN sem internet • Descoberta automática • IP manual • 2 controles locais",
            color = Muted, fontSize = 12.sp, lineHeight = 18.sp)

        Card(colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("MODO DE CONEXÃO", color = Mint, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold)
                Text("Troque sem apagar saves, ROMs, configurações ou seus perfis de rede.",
                    color = Muted, fontSize = 11.sp)
                NetworkMode.values().forEach { choice ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !active) {
                            onConnectionModeChanged(choice)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (choice == connectionMode)
                                Color(0xFF2B2851) else PanelRaised)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(choice.label, fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp)
                                Text(choice.hint, color = Muted, fontSize = 10.sp)
                            }
                            if (choice == connectionMode) Text("✓", color = Mint)
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("2 jogadores no mesmo aparelho", fontWeight = FontWeight.Bold)
                Text("Um controle: touch P1 e gamepad P2. Dois controles: primeiro P1 e segundo P2. Depende do suporte do jogo.",
                    color = Muted, fontSize = 12.sp)
                ToggleSettingRow("Ativar porta 2", "Funciona sem Wi-Fi e sem internet.", localMultiplayer, onLocalMode)
            }
        }

        if (connectionMode == NetworkMode.LAN_AUTO) Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Salas na rede Wi-Fi", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (scanningLan) "Procurando aparelhos na rede…" else
                            "${lanRooms.size} sala(s) encontrada(s)", color = Muted, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onScanLan, enabled = !active && !scanningLan) {
                        Text("BUSCAR", fontSize = 11.sp)
                    }
                }
                if (lanRooms.isEmpty()) {
                    Text("A busca começa automaticamente. Se o Wi-Fi bloquear descoberta, use IP/porta abaixo.",
                        color = Muted, fontSize = 11.sp)
                } else lanRooms.forEach { room ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PanelRaised),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !active) {
                            remoteHost = room.host
                            portText = room.port.toString()
                            if (ready || receiveRomAllowed) onJoin(room.host, room.port)
                        }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(room.game, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${room.host}:${room.port}", color = Muted, fontSize = 11.sp)
                            }
                            Text("ENTRAR ›", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (connectionMode != NetworkMode.LOCAL) Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Sala por IP/porta — experimental", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Seu endereço IPv4 local: $localIpAddress", color = Mint, fontSize = 12.sp)
                if (connectionMode == NetworkMode.INTERNET) {
                    Text("MODO INTERNET: use VPN privada ou conexão confiável. " +
                        "Não existe NAT traversal nem criptografia nativa; não exponha " +
                        "a porta publicamente.", color = Color(0xFFFFBD85), fontSize = 11.sp)
                }
                Text(if (ready) "Jogo aberto: ${romName ?: "SNES"}"
                    else "Nenhum jogo aberto. Para criar sala, abra uma ROM. Para entrar sem a ROM, autorize o recebimento abaixo.",
                    color = if (ready) Mint else Muted, fontSize = 12.sp)
                TextField(value = portText,
                    onValueChange = {
                        portText = it.filter(Char::isDigit).take(5)
                        portText.toIntOrNull()?.let { p -> onSaveConnectionTarget(remoteHost, p) }
                    },
                    label = { Text("Porta TCP (1024–65535)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !active)
                TextField(value = remoteHost,
                    onValueChange = {
                        remoteHost = it.take(253)
                        portText.toIntOrNull()?.let { p -> onSaveConnectionTarget(remoteHost, p) }
                    },
                    label = { Text("IP ou domínio do anfitrião") },
                    placeholder = { Text("192.168.1.20") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !active)
                ToggleSettingRow(
                    "Compartilhar minha ROM autorizada",
                    "Host: confirme apenas para homebrew, domínio público ou ROM com permissão de redistribuição. Desligado por padrão.",
                    shareRomAllowed,
                    { if (!active) onShareRomAllowedChanged(it) }
                )
                ToggleSettingRow(
                    "Aceitar ROM autorizada do host",
                    "Cliente: permite salvar automaticamente a ROM na biblioteca interna, somente em rede privada. Desligado por padrão.",
                    receiveRomAllowed,
                    { if (!active) onReceiveRomAllowedChanged(it) }
                )
                if (active) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Text("ENCERRAR SESSÃO")
                    }
                } else {
                    Button(onClick = { port?.let(onHost) },
                        enabled = ready && port != null && port in 1024..65535,
                        modifier = Modifier.fillMaxWidth()) { Text("CRIAR SALA • JOGADOR 1") }
                    OutlinedButton(onClick = { if (port != null) onJoin(remoteHost.trim(), port) },
                        enabled = (ready || receiveRomAllowed) &&
                            port != null && port in 1024..65535 && remoteHost.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()) {
                            Text("CONECTAR VIA IP • JOGADOR 2")
                        }
                }
                Surface(color = Color(0xFF242C3B), shape = RoundedCornerShape(12.dp)) {
                    Text(status, modifier = Modifier.padding(11.dp), color = Mint, fontSize = 12.sp)
                }
            }
        }
        Text("LAN funciona sem internet, mas pode haver isolamento Wi-Fi. Compartilhamento de ROM depende de permissão legal e consentimento mútuo, não envia jogos comerciais por padrão. TCP lockstep tem atraso de rede, sem rollback nem criptografia; para acesso remoto prefira VPN privada confiável.",
            color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun GameScreen(
    romName: String,
    paused: Boolean,
    settings: AppSettings,
    performanceText: String,
    editingControls: Boolean,
    onEditControls: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onExit: () -> Unit,
    onPause: () -> Unit,
    onOpenMenu: () -> Unit,
    onSurfaceChanged: (Surface?) -> Unit,
    onInputChanged: (Int, Boolean) -> Unit
) {
    var toolbarExpanded by remember(romName) { mutableStateOf(false) }
    var editIndividual by remember(romName) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(settings.aspect, onSurfaceChanged)
        if (settings.touch.enabled) TouchOverlay(
            settings.touch, onInputChanged, editingControls, editIndividual,
            onLayoutChanged = { onSettingsChanged(settings.copy(touch = it)) }
        )
        if (!toolbarExpanded && !editingControls) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x503B4563))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable { toolbarExpanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Settings,
                    contentDescription = "Expandir menu do emulador",
                    tint = Color.White.copy(alpha = 0.60f), modifier = Modifier.size(20.dp))
            }
        } else {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xB51A2030))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onExit) { Text("‹ Biblioteca", fontSize = 12.sp) }
                Column(Modifier.weight(1f, fill = false)) {
                    Text(File(romName).nameWithoutExtension,
                        fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                    if (paused) Text("PAUSADO", color = Mint, fontSize = 9.sp)
                }
                if (editingControls) TextButton(onClick = {
                    editIndividual = !editIndividual
                }) {
                    Text(if (editIndividual) "BOTÕES" else "GRUPOS",
                        fontSize = 10.sp, color = Mint)
                }
                if (settings.touch.enabled) TextButton(onClick = onEditControls) {
                    Text(if (editingControls) "SALVAR" else "EDITAR",
                        fontSize = 11.sp, color = Mint)
                }
                TextButton(onClick = onPause) {
                    Text(if (paused) "▶" else "Ⅱ", fontSize = 17.sp)
                }
                TextButton(onClick = onOpenMenu) {
                    Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color.White)
                }
                TextButton(onClick = {
                    if (editingControls) onEditControls()
                    toolbarExpanded = false
                }) { Text("−", color = Mint, fontSize = 24.sp) }
            }
        }
        if (editingControls) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(top = 45.dp),
                color = Color(0xC91A2030), shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (editIndividual)
                    "ARRASTE OS BOTÕES INDIVIDUALMENTE • SALVAR"
                    else "ARRASTE OS GRUPOS DE CONTROLE • SALVAR",
                    modifier = Modifier.padding(12.dp), color = Mint,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (settings.performanceHud && !editingControls) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                color = Color(0xA4131B28),
                shape = RoundedCornerShape(9.dp)
            ) {
                Text(performanceText, modifier = Modifier.padding(8.dp),
                    color = Mint, fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
        if (paused && !editingControls) {
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
private fun TouchOverlay(
    layout: TouchLayout,
    onInputChanged: (Int, Boolean) -> Unit,
    editing: Boolean,
    editIndividual: Boolean,
    onLayoutChanged: (TouchLayout) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val liveLayout by rememberUpdatedState(layout)
        val liveUpdate by rememberUpdatedState(onLayoutChanged)
        val dpadSize = 51f * layout.scale
        val actionSize = 49f * layout.scale
        val yDpad = -(maxHeight * layout.dpadY.coerceIn(0f, 0.65f))
        val xDpad = maxWidth * layout.dpadX.coerceIn(0f, 0.65f)
        val yButtons = -(maxHeight * layout.buttonsY.coerceIn(0f, 0.65f))
        val xButtons = -(maxWidth * layout.buttonsX.coerceIn(0f, 0.65f))

        fun positionFor(bit: Int): Modifier {
            val shift = layout.buttonShifts[bit] ?: ButtonShift()
            val placement = Modifier.offset(
                x = maxWidth * shift.x.coerceIn(-0.30f, 0.30f),
                y = maxHeight * shift.y.coerceIn(-0.30f, 0.30f)
            )
            val edit = if (editing && editIndividual) Modifier.pointerInput(bit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    val current = liveLayout.buttonShifts[bit] ?: ButtonShift()
                    val updated = ButtonShift(current.x + amount.x / widthPx,
                        current.y + amount.y / heightPx).sanitized()
                    liveUpdate(liveLayout.copy(
                        buttonShifts = liveLayout.buttonShifts + (bit to updated)))
                }
            } else Modifier
            return placement.then(edit)
        }
        val editDpad = if (editing) Modifier.pointerInput(editing) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                liveUpdate(liveLayout.copy(
                    dpadX = (liveLayout.dpadX + dragAmount.x / widthPx).coerceIn(0f, 0.65f),
                    dpadY = (liveLayout.dpadY - dragAmount.y / heightPx).coerceIn(0f, 0.65f)
                ))
            }
        } else Modifier
        val editButtons = if (editing && !editIndividual) Modifier.pointerInput(editing) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                liveUpdate(liveLayout.copy(
                    buttonsX = (liveLayout.buttonsX - dragAmount.x / widthPx).coerceIn(0f, 0.65f),
                    buttonsY = (liveLayout.buttonsY - dragAmount.y / heightPx).coerceIn(0f, 0.65f)
                ))
            }
        } else Modifier

        Box(
            modifier = Modifier.align(Alignment.BottomStart).offset(x = xDpad, y = yDpad)
                .then(editDpad).padding(3.dp)
        ) {
            GestureDpad(dpadSize, layout.opacity, onInputChanged, enabled = !editing, skin = layout.preset)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = xButtons, y = yButtons)
                .then(editButtons).padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TouchButton("Y", 1, actionSize, layout.opacity, onInputChanged, enabled = !editing, skin = layout.preset, positionModifier = positionFor(1))
                TouchButton("X", 9, actionSize, layout.opacity, onInputChanged, enabled = !editing, skin = layout.preset, positionModifier = positionFor(9))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TouchButton("B", 0, actionSize, layout.opacity, onInputChanged, enabled = !editing, skin = layout.preset, positionModifier = positionFor(0))
                TouchButton("A", 8, actionSize, layout.opacity, onInputChanged, enabled = !editing, skin = layout.preset, positionModifier = positionFor(8))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TouchButton("L", 10, actionSize * 0.82f, layout.opacity, onInputChanged, enabled = !editing, skin = layout.preset, positionModifier = positionFor(10))
                TouchButton("R", 11, actionSize * 0.82f, layout.opacity, onInputChanged, enabled = !editing, skin = layout.preset, positionModifier = positionFor(11))
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            TouchButton("SELECT", 2, 34f * layout.scale, layout.opacity, onInputChanged, true, !editing, layout.preset, positionFor(2))
            TouchButton("START", 3, 34f * layout.scale, layout.opacity, onInputChanged, true, !editing, layout.preset, positionFor(3))
        }
    }
}

/**
 * One continuous touch surface: moving the thumb changes the direction without
 * lifting it; diagonals press two real libretro joypad bits simultaneously.
 * The control does not intercept touches when the layout editor is active.
 */
@Composable
private fun GestureDpad(
    cellSize: Float,
    opacity: Float,
    onInputChanged: (Int, Boolean) -> Unit,
    enabled: Boolean,
    skin: TouchPreset = TouchPreset.MAIZO
) {
    val currentInput by rememberUpdatedState(onInputChanged)
    val gap = 2.dp
    val total = (cellSize * 3).dp + gap * 2
    Box(
        modifier = Modifier.size(total)
            .then(if (enabled) Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    var mask = 0
                    fun update(px: Float, py: Float) {
                        val nx = px / size.width.toFloat() * 2f - 1f
                        val ny = py / size.height.toFloat() * 2f - 1f
                        val dead = 0.27f
                        var next = 0
                        if (nx < -dead) next = next or (1 shl 6)
                        if (nx > dead) next = next or (1 shl 7)
                        if (ny < -dead) next = next or (1 shl 4)
                        if (ny > dead) next = next or (1 shl 5)
                        for (bit in listOf(4, 5, 6, 7)) {
                            val pressed = next and (1 shl bit) != 0
                            val wasPressed = mask and (1 shl bit) != 0
                            if (pressed != wasPressed) currentInput(bit, pressed)
                        }
                        mask = next
                    }
                    try {
                        update(first.position.x, first.position.y)
                        do {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.pressed }
                            if (pointer != null) update(pointer.position.x, pointer.position.y)
                        } while (pointer != null)
                    } finally {
                        for (bit in listOf(4, 5, 6, 7)) {
                            if (mask and (1 shl bit) != 0) currentInput(bit, false)
                        }
                    }
                }
            } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            TouchButton("↑", 4, cellSize, opacity, onInputChanged, enabled = false, skin = skin)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                TouchButton("←", 6, cellSize, opacity, onInputChanged, enabled = false, skin = skin)
                Box(Modifier.size(cellSize.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = opacity * 0.22f)))
                TouchButton("→", 7, cellSize, opacity, onInputChanged, enabled = false, skin = skin)
            }
            TouchButton("↓", 5, cellSize, opacity, onInputChanged, enabled = false, skin = skin)
        }
    }
}

@Composable
private fun TouchButton(
    label: String, bit: Int, size: Float, opacity: Float,
    onInputChanged: (Int, Boolean) -> Unit,
    isPill: Boolean = false,
    enabled: Boolean = true,
    skin: TouchPreset = TouchPreset.MAIZO,
    positionModifier: Modifier = Modifier
) {
    val shape = if (isPill) RoundedCornerShape(18.dp)
        else if (skin == TouchPreset.CTR) RoundedCornerShape(13.dp) else CircleShape
    val baseColor = when (skin) {
        TouchPreset.MAIZO -> Color(0xFFE4E8F2)
        TouchPreset.CTR -> Color(0xFFFFD75E)
        TouchPreset.ARMS -> Color(0xFF161C2B)
        TouchPreset.PPSSPP -> Color(0xFF3F64A3)
    }
    val glyphColor = when (skin) {
        TouchPreset.MAIZO, TouchPreset.CTR -> Color(0xFF0B1020)
        TouchPreset.ARMS -> Color(0xFF7FFFE3)
        TouchPreset.PPSSPP -> Color.White
    }
    Box(
        modifier = (if (isPill) Modifier.width((size * 2f).dp).height(size.dp) else Modifier.size(size.dp))
            .then(positionModifier)
            .clip(shape)
            .background(baseColor.copy(alpha = opacity.coerceIn(0.18f, 0.9f) * 0.68f))
            .border(1.dp, Color.White.copy(alpha = opacity.coerceIn(0.18f, 0.9f) * 0.65f), shape)
            .then(if (enabled) Modifier.pointerInput(bit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onInputChanged(bit, true)
                    try { waitForUpOrCancellation() } finally { onInputChanged(bit, false) }
                }
            } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = glyphColor, fontWeight = FontWeight.Black,
            fontSize = if (label.length > 3) 8.sp else 15.sp)
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
    onRestoreMultiplayerBackup: (String) -> Unit,
    onExportState: () -> Unit,
    onImportState: () -> Unit,
    onResetGame: () -> Unit,
    cheats: List<CheatEntry>,
    enabledCheatIds: Set<String>,
    cheatStatus: String,
    onToggleCheat: (String, Boolean) -> Unit,
    onRefreshCheats: () -> Unit,
    cheatsLocked: Boolean,
    onSelectTouchPreset: (TouchPreset) -> Unit,
    connectionMode: NetworkMode,
    onConnectionModeChanged: (NetworkMode) -> Unit,
    devUnlocked: Boolean,
    devShareEnabled: Boolean,
    onDevUnlock: (String) -> Boolean,
    onDevShareChanged: (Boolean) -> Unit
) {
    var category by remember { mutableStateOf("Vídeo") }
    var devCode by remember { mutableStateOf("") }
    var devError by remember { mutableStateOf(false) }
    var confirmBackup by remember { mutableStateOf<String?>(null) }
    var cheatFilter by remember { mutableStateOf("") }
    val categories = listOf("Vídeo", "Áudio", "Controles", "Cheats", "Rede", "Núcleo", "Estados", "DEV")
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
                    SettingSectionTitle("Filtros leves", "Sem Vulkan/OpenGL adicional. Desative para desempenho máximo.")
                    VideoFilter.values().forEach { filter ->
                        ChoiceRow(filter.label, filter.description,
                            settings.filter == filter) {
                            onSettingsChanged(settings.copy(filter = filter))
                        }
                    }
                    ToggleSettingRow("HUD de FPS e latência",
                        "Mede quadros realmente executados, tempo de emulação e custo de apresentação.",
                        settings.performanceHud) {
                        onSettingsChanged(settings.copy(performanceHud = it))
                    }
                    ToggleSettingRow("Capas automáticas",
                        "Baixa miniaturas opcionais via libretro para o cache do aplicativo.",
                        settings.automaticCovers) {
                        onSettingsChanged(settings.copy(automaticCovers = it))
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
                    SettingSectionTitle("Layouts de controle",
                        "Presets originais inspirados na ergonomia de CTR, ARMSX1 e PPSSPP; " +
                            "totalmente ajustáveis, sem copiar imagens ou código dos projetos.")
                    TouchPreset.values().forEach { preset ->
                        ChoiceRow(preset.label, preset.hint,
                            settings.touch.preset == preset) { onSelectTouchPreset(preset) }
                    }
                    SettingSectionTitle("Editor de layout",
                        "No jogo: EDITAR → GRUPOS/BOTÕES. " +
                            "Arraste D-pad/grupos ou ABXY, L/R, Start/Select individualmente.")
                    SliderSetting("D-pad: horizontal", settings.touch.dpadX, 0f..0.65f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(dpadX = it))) }, (settings.touch.dpadX * 100).toInt().toString() + "%")
                    SliderSetting("D-pad: subir", settings.touch.dpadY, 0f..0.65f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(dpadY = it))) }, (settings.touch.dpadY * 100).toInt().toString() + "%")
                    SliderSetting("Botões: horizontal", settings.touch.buttonsX, 0f..0.65f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(buttonsX = it))) }, (settings.touch.buttonsX * 100).toInt().toString() + "%")
                    SliderSetting("Botões: subir", settings.touch.buttonsY, 0f..0.65f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(buttonsY = it))) }, (settings.touch.buttonsY * 100).toInt().toString() + "%")
                    SliderSetting("Tamanho", settings.touch.scale, 0.70f..1.45f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(scale = it))) }, (settings.touch.scale * 100).toInt().toString() + "%")
                    SliderSetting("Opacidade", settings.touch.opacity, 0.20f..0.90f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(opacity = it))) }, (settings.touch.opacity * 100).toInt().toString() + "%")
                    OutlinedButton(onClick = {
                        onSettingsChanged(settings.copy(
                            touch = settings.touch.preset.defaultLayout()))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("RESTAURAR O PRESET SELECIONADO")
                    }
                }
                "Cheats" -> {
                    SettingSectionTitle("Cheats para o jogo aberto",
                        "Busca automática na libretro-database. Marque para ativar; mudanças aplicadas sem reiniciar.")
                    Text(cheatStatus, color = Mint, fontSize = 11.sp, lineHeight = 16.sp)
                    if (cheatsLocked) {
                        Text("CHEATS DESATIVADOS DURANTE LAN/ONLINE para evitar dessincronização.",
                            color = Color(0xFFFFBA72), fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = onRefreshCheats, enabled = !cheatsLocked,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("ATUALIZAR PESQUISA")
                    }
                    if (cheats.isNotEmpty()) {
                        TextField(
                            value = cheatFilter,
                            onValueChange = { cheatFilter = it.take(70) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Filtrar por nome do cheat") },
                            placeholder = { Text("Ex.: vidas infinitas") }
                        )
                        val visible = remember(cheats, cheatFilter) {
                            cheats.filter { cheatFilter.isBlank() ||
                                it.title.contains(cheatFilter, ignoreCase = true) ||
                                it.source.contains(cheatFilter, ignoreCase = true) }
                        }
                        Text("${visible.size} resultados • ${enabledCheatIds.size} ativados",
                            color = Muted, fontSize = 11.sp)
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(420.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(visible, key = { it.id }) { cheat ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = PanelRaised),
                                    shape = RoundedCornerShape(13.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(cheat.title, fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp)
                                            Text(cheat.source + " • " + cheat.code,
                                                fontSize = 10.sp, color = Muted,
                                                maxLines = 2)
                                        }
                                        Switch(
                                            checked = cheat.id in enabledCheatIds,
                                            onCheckedChange = { onToggleCheat(cheat.id, it) },
                                            enabled = !cheatsLocked
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text("Fonte: libretro/libretro-database, cheats CC BY-SA 4.0. " +
                        "Correspondência por nome (não por hash exato). Códigos de outra região " +
                        "podem falhar e afetar saves; use backup.",
                        color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                }
                "Rede" -> {
                    SettingSectionTitle("Perfil de conexão",
                        "Alternar modos não apaga ROMs, save states nem preferências; " +
                            "as salas usam o protocolo MZNP v2 existente.")
                    NetworkMode.values().forEach { mode ->
                        ChoiceRow(mode.label, mode.hint, connectionMode == mode) {
                            if (!cheatsLocked) onConnectionModeChanged(mode)
                        }
                    }
                    if (cheatsLocked) Text("Para trocar modo, encerre a sessão ativa.",
                        color = Color(0xFFFFC47E), fontSize = 11.sp)
                    Text("LAN usa descoberta automática ou endereço manual. Internet " +
                        "continua em lockstep, por isso o RTT real limita o FPS e a resposta. " +
                        "O perfil Internet aumenta tolerância a jitter, sem desativar os " +
                        "testes de hash ou corromper a sincronização.",
                        color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
                "Núcleo" -> {
                    SettingSectionTitle("Snes9x 1.63", "Opções reais do núcleo. Alterações são consultadas no caminho de atualização do core.")
                    CoreOptionDefinitions.all.filter { it.category == "Núcleo" }.forEach { option ->
                        CoreOptionRow(option, coreOptions[option.key] ?: option.defaultValue, onCoreOptionChanged)
                    }
                    Text("Overclock e hacks podem causar incompatibilidades. Os valores padrão são recomendados.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                }
                "DEV" -> {
                    SettingSectionTitle("Desenvolvedor",
                        "Opções avançadas. O PIN de conveniência não concede permissões legais.")
                    if (!devUnlocked) {
                        TextField(
                            value = devCode,
                            onValueChange = { devCode = it.filter(Char::isDigit).take(8) },
                            label = { Text("Código DEV") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = {
                            devError = !onDevUnlock(devCode)
                            if (!devError) devCode = ""
                        }) { Text("DESBLOQUEAR") }
                        if (devError) Text("Código incorreto.",
                            color = Color(0xFFFFAA88), fontSize = 11.sp)
                    } else {
                        Text("DEV DESBLOQUEADO", color = Mint, fontWeight = FontWeight.Bold)
                        ToggleSettingRow(
                            "Compartilhamento geral (homebrew e comercial autorizado)",
                            "Libera a opção avançada de enviar qualquer arquivo ROM " +
                                "cuja redistribuição seja autorizada pelo titular. " +
                                "O código 9090 não autoriza copiar jogos comerciais " +
                                "protegidos; ambas as pessoas precisam concordar.",
                            devShareEnabled,
                            onDevShareChanged
                        )
                        Text("A transferência continua limitada a LAN privada, " +
                            "com consentimento bilateral e verificação SHA-256. " +
                            "Sem autenticação ou criptografia nativa: não exponha " +
                            "portas à internet pública.",
                            color = Color(0xFFFFC47E), fontSize = 11.sp)
                    }
                }
                "Estados" -> {
                    SettingSectionTitle("Save states", "Slots privados do app e arquivos externos. O núcleo rejeita estados de outra ROM.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { confirmBackup = "before" },
                            modifier = Modifier.weight(1f), enabled = !cheatsLocked) {
                            Text("ANTES", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { confirmBackup = "after" },
                            modifier = Modifier.weight(1f), enabled = !cheatsLocked) {
                            Text("DEPOIS", fontSize = 11.sp)
                        }
                    }
                    Text("Restaurar cópia da última sessão multiplayer. " +
                        "Isso substitui o estado em memória, mas não apaga slots manuais.",
                        fontSize = 10.sp, color = Muted)
                    ToggleSettingRow("Backup automático no multiplayer",
                        "Cria snapshots de segurança antes e depois da sessão; " +
                            "não sobrescreve os slots manuais.",
                        settings.automaticBackup) {
                        onSettingsChanged(settings.copy(automaticBackup = it))
                    }
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
    confirmBackup?.let { stage ->
        AlertDialog(
            onDismissRequest = { confirmBackup = null },
            title = { Text("Restaurar backup multiplayer?") },
            text = { Text("O estado atual em memória será substituído pela " +
                "cópia de " + (if (stage == "before") "ANTES da partida." else "DEPOIS da partida.")) },
            confirmButton = {
                TextButton(onClick = {
                    onRestoreMultiplayerBackup(stage)
                    confirmBackup = null
                }) { Text("RESTAURAR") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBackup = null }) { Text("CANCELAR") }
            }
        )
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
