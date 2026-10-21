package com.maizon.maizosnes

import android.graphics.Bitmap
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
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

internal enum class AppScreen { LIBRARY, MULTIPLAYER, SETTINGS, CREDITS, GAME }
internal enum class AspectMode(val label: String) {
    PIXEL("Pixel perfect"),
    CRT("4:3 (CRT)"),
    NATIVE("8:7 (pixels SNES)"),
    CLASSIC("3:2"),
    WIDE("16:9"),
    ULTRAWIDE("21:9 (barras)"),
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
    val preset: TouchPreset = TouchPreset.VIRTUAL_GAMEPAD,
    val buttonShifts: Map<Int, ButtonShift> = emptyMap()
)

internal data class AppSettings(
    val aspect: AspectMode = AspectMode.CRT,
    val touch: TouchLayout = TouchLayout(),
    val audioEnabled: Boolean = true,
    val volume: Float = 1f,
    val filter: VideoFilter = VideoFilter.ORIGINAL,
    val outputScale: Int = 1,
    val renderBackend: RenderBackend = RenderBackend.CPU,
    val gpuShader: GpuShader = GpuShader.NEAREST,
    val performanceHud: Boolean = false,
    val automaticCovers: Boolean = true,
    val automaticBackup: Boolean = true,
    val audioBufferMs: Int = 40
)

private val Bg = Color(0xFF090D19)
private val Panel = Color(0xFF171E2E)
private val PanelRaised = Color(0xFF242D40)
private val Accent = Color(0xFFAA96FF)
private val Mint = Color(0xFF72E8C7)
private val Muted = Color(0xFFBAC5D7)

/** Visible directional-controller focus, without affecting touch/click actions. */
private fun Modifier.padFocusRing(): Modifier = composed {
    var hasFocus by remember { mutableStateOf(false) }
    this.onFocusChanged { hasFocus = it.isFocused }
        .border(if (hasFocus) 2.dp else 0.dp,
            if (hasFocus) Mint else Color.Transparent, RoundedCornerShape(14.dp))
}

/** Compose Dialog is a separate Android window (not dispatched via Activity).
 * Convert pad A to ENTER on that window and pad B to Back.
 */
private fun Modifier.dialogGamepadKeys(onBack: () -> Unit): Modifier = composed {
    val view = LocalView.current
    this.onPreviewKeyEvent { key ->
        val raw = key.nativeKeyEvent
        when (raw.keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_MODE, GamepadKeyRouting.OPTIONAL_GUIDE_BUTTON,
            android.view.KeyEvent.KEYCODE_BUTTON_B -> {
                if (raw.action == android.view.KeyEvent.ACTION_DOWN && raw.repeatCount == 0)
                    onBack()
                true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                val enter = android.view.KeyEvent(raw.downTime, raw.eventTime,
                    raw.action, android.view.KeyEvent.KEYCODE_ENTER,
                    raw.repeatCount, raw.metaState, raw.deviceId,
                    raw.scanCode, raw.flags, raw.source)
                view.dispatchKeyEvent(enter)
                true
            }
            else -> false
        }
    }
}

@Composable
internal fun MaizoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, secondary = Mint, background = Bg, surface = Panel,
            surfaceVariant = PanelRaised, onSurfaceVariant = Muted,
            onPrimary = Bg, onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

@Composable
internal fun MaizoEmulatorApp(
    romNames: List<String>,
    coverBitmaps: Map<String, Bitmap>,
    romHeaders: Map<String, SnesRomHeader.Details>,
    favoriteRoms: Set<String>,
    recentRoms: List<String>,
    onToggleFavorite: (String) -> Unit,
    onRemoveRom: (String) -> Unit,
    onApplyPatchToRom: (String) -> Unit,
    screen: AppScreen,
    statusMessage: String,
    currentRomName: String?,
    localRomHashPrefix: String,
    hasLoadedRom: Boolean,
    paused: Boolean,
    gameMenuOpen: Boolean,
    onGameMenuChanged: (Boolean) -> Unit,
    settings: AppSettings,
    coreOptions: Map<String, String>,
    performanceText: String,
    networkDebug: NetworkDebugState,
    onSelectTouchPreset: (TouchPreset) -> Unit,
    connectionMode: NetworkMode,
    onConnectionModeChanged: (NetworkMode) -> Unit,
    netplayLatencyMode: NetplayLatencyMode,
    onNetplayLatencyModeChanged: (NetplayLatencyMode) -> Unit,
    netcodeMode: NetcodeMode,
    onNetcodeModeChanged: (NetcodeMode) -> Unit,
    backgroundNetplayEnabled: Boolean,
    onBackgroundNetplayChanged: (Boolean) -> Unit,
    iceServerOverrides: String,
    onIceOverridesChanged: (String) -> Unit,
    iceTestStatus: String,
    onTestIce: () -> Unit,
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
    relayAddress: String,
    relayRooms: List<InternetRoom>,
    scanningRelay: Boolean,
    onRelayAddressChanged: (String) -> Unit,
    onRefreshRelayRooms: () -> Unit,
    onHostRelay: () -> Unit,
    onJoinRelay: (String) -> Unit,
    autoRooms: List<AutoRoom>,
    autoServers: List<AutoServerStatus>,
    autoStatus: String,
    autoScanActive: Boolean,
    onRefreshAutoRooms: () -> Unit,
    onHostAutomatic: () -> Unit,
    onQuickMatch: () -> Unit,
    onJoinAutomatic: (AutoRoom) -> Unit,
    onlineCheatsOptIn: Boolean,
    onOnlineCheatsChanged: (Boolean) -> Unit,
    shareRomAllowed: Boolean,
    shareConsentVisible: Boolean,
    onShareConsent: (Boolean) -> Unit,
    incomingRomName: String?,
    incomingRomHash: String,
    incomingRomBytes: Int,
    onIncomingRomDecision: (Boolean) -> Unit,
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
    onScanRomFolder: () -> Unit,
    onApplyRomPatch: () -> Unit,
    onScanAllDevice: () -> Unit,
    onOpenInstagram: () -> Unit,
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
    onSurfaceChanged: (Surface?) -> Unit,
    onGpuReady: (Boolean) -> Unit,
    onExportDebug: () -> Unit
) {
    var editingControls by remember { mutableStateOf(false) }
    var connectionOverlay by remember { mutableStateOf(false) }
    incomingRomName?.let { name ->
        AlertDialog(
            onDismissRequest = { onIncomingRomDecision(false) },
            modifier = Modifier.dialogGamepadKeys { onIncomingRomDecision(false) },
            title = { Text("Receber ROM do anfitrião?") },
            text = { Text("Arquivo: " + name +
                "\nTamanho: " + (incomingRomBytes / 1024) + " KiB" +
                "\nSHA-256: " + incomingRomHash.take(20) + "…" +
                "\nAceite apenas arquivos autorizados. Os bytes serão verificados antes do uso.") },
            confirmButton = { TextButton(onClick = { onIncomingRomDecision(true) }) {
                Text("ACEITAR")
            } },
            dismissButton = { TextButton(onClick = { onIncomingRomDecision(false) }) {
                Text("REJEITAR")
            } })
    }
    if (shareConsentVisible) {
        AlertDialog(onDismissRequest = { onShareConsent(false) },
            modifier = Modifier.dialogGamepadKeys { onShareConsent(false) },
            title = { Text("Permissão de compartilhar ROM") },
            text = { Text("Compartilhar esta ROM nesta sessão? Confirme apenas se for homebrew, domínio público ou houver autorização de redistribuição. Transferências remotas só devem usar conexões protegidas.") },
            confirmButton = { TextButton(onClick = { onShareConsent(true) }) { Text("TENHO PERMISSÃO") } },
            dismissButton = { TextButton(onClick = { onShareConsent(false) }) { Text("NÃO COMPARTILHAR") } })
    }
    BackHandler(enabled = screen != AppScreen.GAME && screen != AppScreen.LIBRARY) {
        onSetScreen(AppScreen.LIBRARY)
    }
    BackHandler(enabled = screen == AppScreen.GAME) {
        if (gameMenuOpen) onGameMenuChanged(false) else onExitGame()
    }

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
            onOpenMenu = { onGameMenuChanged(true) },
            onSurfaceChanged = onSurfaceChanged,
            onGpuReady = onGpuReady,
            onInputChanged = onInputChanged
        )
        if (gameMenuOpen) {
            val menuFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(gameMenuOpen) { menuFocus.requestFocus() }
            Dialog(onDismissRequest = { onGameMenuChanged(false) }) {
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 690.dp)
                        .dialogGamepadKeys { onGameMenuChanged(false) },
                    shape = RoundedCornerShape(26.dp), color = Bg, tonalElevation = 12.dp
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("MENU DO EMULADOR", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                Text(currentRomName ?: "Jogo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { onGameMenuChanged(false) },
                                modifier = Modifier.focusRequester(menuFocus)) { Text("FECHAR") }
                        }
                        OutlinedButton(onClick = { connectionOverlay = true },
                            modifier = Modifier.fillMaxWidth().padFocusRing()) {
                            Text("STATUS DE CONEXÃO / DEBUG")
                        }
                        TextButton(onClick = {
                            editingControls = !editingControls
                            onGameMenuChanged(false)
                        }) { Text(if (editingControls) "SALVAR CONTROLES" else "EDITAR CONTROLES") }
                        EmulatorSettings(settings, coreOptions, true, onSettingsChanged, onCoreOptionChanged, onSaveSlot, onLoadSlot, onRestoreMultiplayerBackup, onExportState, onImportState, onResetGame, cheats, enabledCheatIds, cheatStatus, onToggleCheat, onRefreshCheats, multiplayerActive,
                            onSelectTouchPreset, connectionMode, onConnectionModeChanged,
                            devUnlocked, devShareEnabled, onDevUnlock, onDevShareChanged,
                    iceServerOverrides, onIceOverridesChanged, iceTestStatus, onTestIce)
                    }
                }
            }
        }
        if (connectionOverlay) NetplayDebugDialog(
            networkDebug, { connectionOverlay = false }, onExportDebug)
        return
    }

    if (connectionOverlay) NetplayDebugDialog(
        networkDebug, { connectionOverlay = false }, onExportDebug)

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101624), modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                NavigationBarItem(screen == AppScreen.LIBRARY, { onSetScreen(AppScreen.LIBRARY) }, icon = { Icon(Icons.Default.Gamepad, null) }, label = { Text("Biblioteca") }, modifier = Modifier.padFocusRing())
                NavigationBarItem(screen == AppScreen.MULTIPLAYER, { onSetScreen(AppScreen.MULTIPLAYER) }, icon = { Icon(Icons.Default.Groups, null) }, label = { Text("Online") }, modifier = Modifier.padFocusRing())
                NavigationBarItem(screen == AppScreen.SETTINGS, { onSetScreen(AppScreen.SETTINGS) }, icon = { Icon(Icons.Default.Tune, null) }, label = { Text("Ajustes") }, modifier = Modifier.padFocusRing())
                NavigationBarItem(screen == AppScreen.CREDITS, { onSetScreen(AppScreen.CREDITS) }, icon = { Icon(Icons.Default.Info, null) }, label = { Text("Créditos") }, modifier = Modifier.padFocusRing())
            }
        }
    ) { padding ->
        when (screen) {
            AppScreen.LIBRARY -> LibraryScreen(
                Modifier.fillMaxSize().padding(padding), romNames, coverBitmaps,
                romHeaders, favoriteRoms, recentRoms, onToggleFavorite,
                onRemoveRom, onApplyPatchToRom,
                statusMessage, currentRomName, hasLoadedRom, onChooseAddRom,
                onScanRomFolder, onApplyRomPatch, onScanAllDevice,
                onOpenRom, onContinueGame, onRenameRom, onSetScreen
            )
            AppScreen.MULTIPLAYER -> MultiplayerScreen(
                Modifier.fillMaxSize().padding(padding), currentRomName, localRomHashPrefix, multiplayerReady,
                multiplayerStatus, localIpAddress, multiplayerActive, localMultiplayer,
                connectionMode, onConnectionModeChanged,
                netplayLatencyMode, onNetplayLatencyModeChanged,
                netcodeMode, onNetcodeModeChanged,
                backgroundNetplayEnabled, onBackgroundNetplayChanged,
                iceServerOverrides, onIceOverridesChanged, iceTestStatus, onTestIce,
                savedConnectionHost, savedConnectionPort, onSaveConnectionTarget,
                lanRooms, scanningLan, onScanLan,
                relayAddress, relayRooms, scanningRelay,
                onRelayAddressChanged, onRefreshRelayRooms,
                onHostRelay, onJoinRelay,
                autoRooms, autoServers, autoStatus, autoScanActive,
                onRefreshAutoRooms, onHostAutomatic, onQuickMatch, onJoinAutomatic,
                onlineCheatsOptIn, onOnlineCheatsChanged,
                shareRomAllowed, receiveRomAllowed,
                onShareRomAllowedChanged, onReceiveRomAllowedChanged,
                onHostMultiplayer, onJoinMultiplayer, onStopMultiplayer,
                onLocalMultiplayerChanged,
                onDebug = { connectionOverlay = true }
            )
            AppScreen.SETTINGS -> Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("AJUSTES", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Configure o emulador", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 8.dp))
                OutlinedButton(onClick = { connectionOverlay = true },
                    modifier = Modifier.fillMaxWidth().padFocusRing()) {
                    Text("STATUS DE CONEXÃO / DEBUG")
                }
                EmulatorSettings(settings, coreOptions, false, onSettingsChanged, onCoreOptionChanged, onSaveSlot, onLoadSlot, onRestoreMultiplayerBackup, onExportState, onImportState, onResetGame, cheats, enabledCheatIds, cheatStatus, onToggleCheat, onRefreshCheats, multiplayerActive,
                    onSelectTouchPreset, connectionMode, onConnectionModeChanged,
                    devUnlocked, devShareEnabled, onDevUnlock, onDevShareChanged,
                    iceServerOverrides, onIceOverridesChanged, iceTestStatus, onTestIce)
            }
            AppScreen.CREDITS -> CreditsScreen(Modifier.fillMaxSize().padding(padding), onOpenInstagram)
            AppScreen.GAME -> Unit
        }
    }
}

@Composable
private fun CreditsScreen(modifier: Modifier, onOpenInstagram: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("CRÉDITOS", color = Mint, fontSize = 12.sp, letterSpacing = 2.sp)
        Text("MaizoSNES", fontSize = 32.sp, fontWeight = FontWeight.Black)
        Card(colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("MAICON NASCIMENTO", fontWeight = FontWeight.Black,
                    fontSize = 21.sp, color = Mint)
                Text("Criador e responsável pelo projeto MaizoSNES.",
                    color = Color.White, fontSize = 13.sp)
                Button(onClick = onOpenInstagram,
                    modifier = Modifier.focusRequester(firstFocus),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("INSTAGRAM • @maicon_nasciment0")
                }
            }
        }
        SettingSectionTitle("Tecnologias e agradecimentos",
            "Reconhecimento aos projetos de código aberto e referências de interface.")
        Text("Snes9x 1.63 — núcleo de emulação SNES (licença não comercial).\n" +
            "Libretro — interfaces, cheats, identificação e thumbnails.\n" +
            "PPSSPP (hrydgard) — referência de ergonomia dos controles.\n" +
            "CTR Native Android (igawa6) — referência de controles.\n" +
            "VirtualGamePad-Mobile (kitswas) — referência de proporção e disposição; arte original.\n" +
            "ARMSX1 (ARMSX2) — referência de interface.\n" +
            "Daijishou (TapiocaFox) — referência de navegação da biblioteca.\n" +
            "Android Open Source Project e Jetpack Compose — plataforma e interface.",
            color = Muted, fontSize = 13.sp, lineHeight = 22.sp)
        Text("Créditos e licenças de terceiros: THIRD_PARTY_NOTICES.md e LICENSES/ " +
            "no repositório. Os projetos citados não endossam o MaizoSNES.",
            color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    romNames: List<String>,
    coverBitmaps: Map<String, Bitmap>,
    romHeaders: Map<String, SnesRomHeader.Details>,
    favoriteRoms: Set<String>,
    recentRoms: List<String>,
    onToggleFavorite: (String) -> Unit,
    onRemoveRom: (String) -> Unit,
    onApplyPatchToRom: (String) -> Unit,
    statusMessage: String,
    currentRomName: String?,
    hasLoadedRom: Boolean,
    onChooseAddRom: () -> Unit,
    onScanRomFolder: () -> Unit,
    onApplyRomPatch: () -> Unit,
    onScanAllDevice: () -> Unit,
    onOpenRom: (String) -> Unit,
    onContinueGame: () -> Unit,
    onRenameRom: (String, String) -> Unit,
    onSetScreen: (AppScreen) -> Unit
) {
    var selectedRom by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    var libraryQuery by remember { mutableStateOf("") }
    var columns by remember { mutableStateOf(2) }
    var listMode by remember { mutableStateOf(false) }
    var collectionMode by remember { mutableStateOf("Todos") }
    val homeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { homeFocus.requestFocus() }
    val visibleRoms = remember(romNames, romHeaders, libraryQuery, collectionMode, favoriteRoms, recentRoms) {
        val filtered = romNames.filter { name ->
            val gameTitle = romHeaders[name]?.title ?: name
            (name.contains(libraryQuery.trim(), ignoreCase = true) ||
                gameTitle.contains(libraryQuery.trim(), ignoreCase = true)) &&
                (collectionMode != "Favoritos" || name in favoriteRoms) &&
                (collectionMode != "Recentes" || name in recentRoms)
        }
        if (collectionMode == "Recentes") filtered.sortedBy { recentRoms.indexOf(it) }
        else filtered.sortedBy { (romHeaders[it]?.title ?: it).lowercase() }
    }
    BackHandler(enabled = selectedRom != null || renameTarget != null) {
        selectedRom = null
        renameTarget = null
    }
    Column(modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Accent, Color(0xFF4540A1)))),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Gamepad, null, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("MAIZO", color = Mint, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("SNES", fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            Text("Snes9x 1.63", color = Mint, fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Button(onClick = onChooseAddRom,
                modifier = Modifier.weight(1f).focusRequester(homeFocus),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("+ IMPORTAR ROM / ZIP", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onScanRomFolder,
                contentPadding = PaddingValues(horizontal = 8.dp)) { Text("PASTA", fontSize = 11.sp) }
            OutlinedButton(onClick = onScanAllDevice,
                contentPadding = PaddingValues(horizontal = 8.dp)) { Text("BUSCAR", fontSize = 11.sp) }
        }
        if (hasLoadedRom) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onContinueGame) { Text("▶ CONTINUAR") }
                OutlinedButton(onClick = onApplyRomPatch) { Text("PATCH ROM ATUAL") }
            }
        }
        if (romNames.isNotEmpty()) {
            TextField(value = libraryQuery, onValueChange = { libraryQuery = it.take(80) },
                singleLine = true, label = { Text("Buscar nome ou título SNES") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Todos", "Favoritos", "Recentes").forEach { mode ->
                OutlinedButton(onClick = { collectionMode = mode },
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(if (mode == collectionMode) "● $mode" else mode, fontSize = 11.sp)
                }
            }
            listOf(2, 3, 4).forEach { count ->
                OutlinedButton(onClick = { columns = count; listMode = false },
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(if (!listMode && columns == count) "● $count" else "$count", fontSize = 11.sp)
                }
            }
            OutlinedButton(onClick = { listMode = true },
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (listMode) "● Lista" else "Lista", fontSize = 11.sp)
            }
        }
        Text(visibleRoms.size.toString() + " jogo(s) • A: abrir • B: voltar • L1/R1: abas",
            color = Muted, fontSize = 11.sp)
        if (visibleRoms.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(if (romNames.isEmpty()) "Importe seus jogos .sfc, .smc ou .zip"
                    else "Nenhum jogo encontrado.", color = Muted)
            }
        } else if (listMode) {
            LazyColumn(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleRoms, key = { it }) { name ->
                    RomCard(name, romHeaders[name]?.title ?: File(name).nameWithoutExtension,
                        currentRomName == name, coverBitmaps[name],
                        { selectedRom = name }, { renameTarget = name }, compact = true)
                }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                gridItems(visibleRoms, key = { it }) { name ->
                    RomCard(name, romHeaders[name]?.title ?: File(name).nameWithoutExtension,
                        currentRomName == name, coverBitmaps[name],
                        { selectedRom = name }, { renameTarget = name }, compact = columns >= 3)
                }
            }
        }
        Text(statusMessage, color = Muted, fontSize = 11.sp, lineHeight = 15.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
    }
    selectedRom?.let { name ->
        val info = romHeaders[name]
        val detailFocus = remember(name) { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(name) { detailFocus.requestFocus() }
        Dialog(onDismissRequest = { selectedRom = null }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Panel,
                modifier = Modifier.fillMaxWidth().heightIn(max = 670.dp)
                    .dialogGamepadKeys { selectedRom = null }) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                        val cover = coverBitmaps[name]
                        if (cover != null) Image(cover.asImageBitmap(), "Capa",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Icon(Icons.Default.Gamepad, "Sem capa", modifier = Modifier.size(74.dp))
                    }
                    Text(info?.title ?: File(name).nameWithoutExtension,
                        fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text("Região: " + (info?.region ?: "desconhecida"), color = Muted)
                    info?.warning?.let { Text(it, color = Color(0xFFFFBD85), fontSize = 12.sp) }
                    Button(onClick = { selectedRom = null; onOpenRom(name) },
                        modifier = Modifier.fillMaxWidth().focusRequester(detailFocus)) { Text("▶ JOGAR") }
                    OutlinedButton(onClick = { selectedRom = null; onApplyPatchToRom(name) },
                        modifier = Modifier.fillMaxWidth()) { Text("APLICAR PATCH IPS / BPS") }
                    if (currentRomName == name && hasLoadedRom) {
                        OutlinedButton(onClick = {
                            selectedRom = null
                            onSetScreen(AppScreen.SETTINGS)
                        }, modifier = Modifier.fillMaxWidth()) { Text("SAVE STATES E AJUSTES") }
                        OutlinedButton(onClick = {
                            selectedRom = null
                            onSetScreen(AppScreen.MULTIPLAYER)
                        }, modifier = Modifier.fillMaxWidth()) { Text("MULTIPLAYER LAN / ONLINE") }
                    } else Text("Para saves e multiplayer, abra o jogo primeiro.",
                        color = Muted, fontSize = 11.sp)
                    OutlinedButton(onClick = { onToggleFavorite(name) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (name in favoriteRoms) "★ REMOVER FAVORITO" else "☆ FAVORITAR")
                    }
                    OutlinedButton(onClick = { selectedRom = null; renameTarget = name },
                        modifier = Modifier.fillMaxWidth()) { Text("RENOMEAR") }
                    TextButton(onClick = { confirmRemove = name; selectedRom = null }) {
                        Text("REMOVER DA BIBLIOTECA", color = Color(0xFFFFBD85))
                    }
                    TextButton(onClick = { selectedRom = null }) { Text("VOLTAR") }
                }
            }
        }
    }
    confirmRemove?.let { name ->
        AlertDialog(onDismissRequest = { confirmRemove = null },
            modifier = Modifier.dialogGamepadKeys { confirmRemove = null },
            title = { Text("Remover ROM?") },
            text = { Text("O arquivo será apagado da biblioteca local.") },
            confirmButton = { TextButton(onClick = {
                onRemoveRom(name); confirmRemove = null
            }) { Text("REMOVER") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("CANCELAR") } })
    }
    renameTarget?.let { name ->
        var editedName by remember(name) { mutableStateOf(File(name).nameWithoutExtension) }
        AlertDialog(onDismissRequest = { renameTarget = null },
            modifier = Modifier.dialogGamepadKeys { renameTarget = null },
            title = { Text("Renomear ROM") },
            text = { TextField(editedName, { editedName = it }, label = { Text("Nome") }) },
            confirmButton = { TextButton(onClick = {
                onRenameRom(name, editedName); renameTarget = null
            }) { Text("SALVAR") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("CANCELAR") } })
    }
}

@Composable
private fun RomCard(
    fileName: String,
    displayTitle: String,
    current: Boolean,
    cover: Bitmap?,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    compact: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth().padFocusRing().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(if (compact) 102.dp else 150.dp)
                    .background(Brush.linearGradient(
                        listOf(Color(0xFF35305F), Color(0xFF141C30), Color(0xFF0A2D36))
                    )),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    Image(
                        bitmap = cover.asImageBitmap(),
                        contentDescription = "Capa de " + displayTitle,
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
            Text(displayTitle,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp),
                fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, lineHeight = 16.sp)
            // In 3/4-column launcher mode the entire card is a gamepad/touch
            // focus target. Avoid oversized buttons overflowing narrow cards.
            if (!compact) Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRename, contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.weight(1f)) { Text("EDITAR", fontSize = 10.sp) }
                Button(onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 5.dp)) {
                    Text("ABRIR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else Text("A • DETALHES",
                color = Mint, fontSize = 9.sp, maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, bottom = 7.dp))
        }
    }
}

@Composable
private fun MultiplayerScreen(
    modifier: Modifier,
    romName: String?,
    localRomHashPrefix: String,
    ready: Boolean,
    status: String,
    localIpAddress: String,
    active: Boolean,
    localMultiplayer: Boolean,
    connectionMode: NetworkMode,
    onConnectionModeChanged: (NetworkMode) -> Unit,
    netplayLatencyMode: NetplayLatencyMode,
    onNetplayLatencyModeChanged: (NetplayLatencyMode) -> Unit,
    netcodeMode: NetcodeMode,
    onNetcodeModeChanged: (NetcodeMode) -> Unit,
    backgroundNetplayEnabled: Boolean,
    onBackgroundNetplayChanged: (Boolean) -> Unit,
    iceServerOverrides: String,
    onIceOverridesChanged: (String) -> Unit,
    iceTestStatus: String,
    onTestIce: () -> Unit,
    savedConnectionHost: String,
    savedConnectionPort: Int,
    onSaveConnectionTarget: (String, Int) -> Unit,
    lanRooms: List<LanRoom>,
    scanningLan: Boolean,
    onScanLan: () -> Unit,
    relayAddress: String,
    relayRooms: List<InternetRoom>,
    scanningRelay: Boolean,
    onRelayAddressChanged: (String) -> Unit,
    onRefreshRelayRooms: () -> Unit,
    onHostRelay: () -> Unit,
    onJoinRelay: (String) -> Unit,
    autoRooms: List<AutoRoom>,
    autoServers: List<AutoServerStatus>,
    autoStatus: String,
    autoScanActive: Boolean,
    onRefreshAutoRooms: () -> Unit,
    onHostAutomatic: () -> Unit,
    onQuickMatch: () -> Unit,
    onJoinAutomatic: (AutoRoom) -> Unit,
    onlineCheatsOptIn: Boolean,
    onOnlineCheatsChanged: (Boolean) -> Unit,
    shareRomAllowed: Boolean,
    receiveRomAllowed: Boolean,
    onShareRomAllowedChanged: (Boolean) -> Unit,
    onReceiveRomAllowedChanged: (Boolean) -> Unit,
    onHost: (Int) -> Unit,
    onJoin: (String, Int) -> Unit,
    onStop: () -> Unit,
    onLocalMode: (Boolean) -> Unit,
    onDebug: () -> Unit
) {
    var advancedNetwork by remember { mutableStateOf(false) }
    var remoteHost by remember(connectionMode) { mutableStateOf(savedConnectionHost) }
    var portText by remember(connectionMode) { mutableStateOf(savedConnectionPort.toString()) }
    val port = portText.toIntOrNull()
    val firstModeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstModeFocus.requestFocus() }
    LaunchedEffect(connectionMode) {
        if (connectionMode == NetworkMode.LAN_AUTO ||
            connectionMode == NetworkMode.AUTO) onScanLan()
        if (connectionMode == NetworkMode.AUTO) onRefreshAutoRooms()
        if (connectionMode == NetworkMode.RELAY && relayAddress.isNotBlank()) onRefreshRelayRooms()
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onDebug,
            modifier = Modifier.fillMaxWidth().padFocusRing()) {
            Text("STATUS DE CONEXÃO / DEBUG")
        }
        Text("MULTIPLAYER • MAIZOSNES", color = Mint, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        Text("Jogue junto, de qualquer lugar.", fontSize = 24.sp,
            lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("Crie uma sala, convide alguém e joguem a mesma ROM. " +
            "Online, Wi-Fi local ou dois controles no mesmo aparelho.",
            color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        if (!ready) Surface(color = Color(0xFF31283D), shape = RoundedCornerShape(14.dp)) {
            Text("① Antes de conectar, escolha e abra um jogo na Biblioteca. " +
                "② Volte aqui para criar ou entrar em uma sala.",
                modifier = Modifier.fillMaxWidth().padding(13.dp),
                color = Color(0xFFFFDEAB), fontSize = 12.sp, lineHeight = 18.sp)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("MODO DE CONEXÃO", color = Mint, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold)
                Text("Troque sem apagar saves, ROMs, configurações ou seus perfis de rede.",
                    color = Muted, fontSize = 11.sp)
                NetworkMode.values().filter { choice ->
                    advancedNetwork || choice == NetworkMode.AUTO ||
                        choice == NetworkMode.LOCAL || choice == connectionMode
                }.forEachIndexed { index, choice ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstModeFocus) else Modifier)
                            .padFocusRing().clickable(enabled = !active) {
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
                TextButton(onClick = { advancedNetwork = !advancedNetwork },
                    enabled = !active, modifier = Modifier.fillMaxWidth()) {
                    Text(if (advancedNetwork) "OCULTAR OPÇÕES AVANÇADAS"
                        else "CONFIGURAÇÕES AVANÇADAS DE REDE ›", fontSize = 12.sp)
                }
            }
        }
        if (connectionMode != NetworkMode.LOCAL) Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MOTOR DE MULTIPLAYER",
                    color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                NetcodeMode.values().forEach { choice ->
                    ChoiceRow(choice.label, choice.info, choice == netcodeMode) {
                        if (!active) onNetcodeModeChanged(choice)
                    }
                }
                if (netcodeMode == NetcodeMode.ROLLBACK) {
                    Text("Rollback GekkoNet usa UDP em LAN/IP ou " +
                        "WebRTC DataChannel quando o ICE/STUN/TURN conecta online. " +
                        "Caso contrário, Lockstep TCP via Railway é preservado.",
                        fontSize = 11.sp, color = Muted)
                    Text("RTT, jitter e correções aparecem nas estatísticas; " +
                        "perda de pacotes não é publicada pela API atual do GekkoNet.",
                        fontSize = 11.sp, color = Muted)
                }
                Row(
                    Modifier.fillMaxWidth().padFocusRing(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Multiplayer em segundo plano",
                            color = Mint, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                        Text("Ativado por padrão. Mantém CPU/Wi-Fi conectados " +
                            "ao trocar de app; aumenta o consumo de bateria. " +
                            "Desligue aqui se preferir pausar a proteção.",
                            color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = backgroundNetplayEnabled,
                        onCheckedChange = onBackgroundNetplayChanged)
                }
                Text("PERFIL DE LATÊNCIA • LOCKSTEP MZNP v6",
                    color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("O Lockstep TCP envia comandos para quadros futuros, evitando um RTT " +
                    "a cada frame. Buffer menor reage mais rápido, " +
                    "mas pode engasgar; o host aplica o mesmo buffer aos dois.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                NetplayLatencyMode.values().forEach { choice ->
                    ChoiceRow(choice.label, choice.hint,
                        netplayLatencyMode == choice) {
                        if (!active) onNetplayLatencyModeChanged(choice)
                    }
                }
                if (advancedNetwork) IceServerSettings(
                    iceServerOverrides, onIceOverridesChanged,
                    iceTestStatus, onTestIce, !active)
                if (active) Text("O buffer não pode ser alterado no meio da sessão.",
                    color = Color(0xFFFFBD85), fontSize = 10.sp)
            }
        }

        if (connectionMode == NetworkMode.AUTO) Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SALAS ONLINE", modifier = Modifier.weight(1f),
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Surface(color = if (autoServers.isNotEmpty()) Color(0xFF1C473D)
                        else PanelRaised, shape = RoundedCornerShape(100.dp)) {
                        Text(if (autoServers.isNotEmpty()) "● ONLINE" else "○ OFFLINE",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            color = if (autoServers.isNotEmpty()) Mint else Muted,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Crie ou entre sem digitar IP. Use a mesma versão do jogo " +
                    "nos dois celulares.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                Button(onClick = onQuickMatch,
                    enabled = ready && !active,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint)) {
                    Text("JOGO RÁPIDO • ENTRAR OU CRIAR", color = Color(0xFF111827),
                        fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
                Text("Jogo rápido procura amigos na LAN e salas online; se não encontrar, " +
                    "cria automaticamente uma sala para você.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        onRefreshAutoRooms()
                        onScanLan()
                    }, enabled = !autoScanActive && !active,
                        modifier = Modifier.weight(1f)) {
                        Text(if (autoScanActive) "BUSCANDO…" else "ATUALIZAR SALAS",
                            fontSize = 11.sp)
                    }
                    Button(onClick = onHostAutomatic,
                        enabled = ready && !active && autoServers.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("CRIAR SALA", fontSize = 11.sp)
                    }
                }
                Surface(color = PanelRaised, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (active) "STATUS DA SUA SESSÃO" else "STATUS DA REDE",
                            color = if (active) Mint else Muted,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(autoStatus, color = if (active || autoServers.isNotEmpty()) Mint else Muted,
                            fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                if (active) {
                    Surface(color = Color(0xFF173A39), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SESSÃO ATIVA • AGUARDANDO OU JOGANDO",
                                color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("O botão Criar sala fica desativado enquanto uma " +
                                "sessão estiver aberta. O convidado abre a mesma ROM, " +
                                "toca em Atualizar salas e escolhe sua sala. " +
                                "Cancele a sessão para criar outra.",
                                color = Color.White, fontSize = 11.sp, lineHeight = 17.sp)
                            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                                Text("CANCELAR SALA / DESCONECTAR", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (autoServers.isNotEmpty()) {
                    Text("SERVIDORES ONLINE", color = Mint,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    autoServers.forEach { server ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(server.relay.name + " • " + server.relay.region,
                                color = Color.White, fontSize = 12.sp)
                            Text("Resposta HTTP " + server.latencyMs + " ms",
                                color = Muted, fontSize = 10.sp)
                        }
                    }
                }
                if (autoRooms.isEmpty()) {
                    Text("Nenhuma sala online disponível neste momento.",
                        color = Muted, fontSize = 12.sp)
                } else {
                    autoRooms.forEach { room ->
                        val hashCompatible = ready && (room.romHash.isBlank() ||
                            room.romHash == localRomHashPrefix)
                        val cheatsAllowed = !room.cheats || onlineCheatsOptIn
                        Card(
                            modifier = Modifier.fillMaxWidth().padFocusRing().clickable(
                                enabled = !active && cheatsAllowed &&
                                    ((ready && hashCompatible) || (receiveRomAllowed && (room.tls || room.wss)))
                            ) {
                                onJoinAutomatic(room)
                            },
                            colors = CardDefaults.cardColors(containerColor = PanelRaised),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(room.game, fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold)
                                    Text(room.serverName + " • " + room.region +
                                        " • resposta HTTP " + room.latencyMs + " ms",
                                        color = Muted, fontSize = 10.sp)
                                    Text(
                                        when {
                                            !cheatsAllowed -> "CHEATS — autorize nas configurações"
                                            !hashCompatible && (room.tls || room.wss) && receiveRomAllowed ->
                                                "ROM DIFERENTE — host pode oferecer via TLS"
                                            !hashCompatible -> "ROM/MOD DIFERENTE — mesma versão necessária"
                                            room.cheats -> "CHEATS SINCRONIZADOS • ROM compatível"
                                            room.romHash.isNotBlank() -> "ROM compatível por SHA-256"
                                            else -> "Versão da ROM será validada ao conectar"
                                        }, color = if (cheatsAllowed &&
                                            (hashCompatible || ((room.tls || room.wss) && receiveRomAllowed))) Mint
                                            else Color(0xFFFFBD85), fontSize = 10.sp
                                    )
                                }
                                Text("ENTRAR ›", color = Mint,
                                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Text("Sem ROM? Selecione sala TLS para receber uma oferta autorizada. " +
                    "Salas sem TLS exigem ROM idêntica em ambos os aparelhos.",
                    color = Muted, fontSize = 10.sp)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DOIS JOGADORES • MESMO CELULAR", fontWeight = FontWeight.Bold)
                Text("Um controle: touch P1 e gamepad P2. Dois controles: primeiro P1 e segundo P2. Depende do suporte do jogo.",
                    color = Muted, fontSize = 12.sp)
                ToggleSettingRow("Ativar porta 2", "Funciona sem Wi-Fi e sem internet.", localMultiplayer, onLocalMode)
                ToggleSettingRow(
                    "Autorizar cheats sincronizados no multiplayer",
                    "Experimental: marque antes de entrar ou criar sala. O anfitrião " +
                        "seleciona os cheats da biblioteca e os dois aparelhos instalam " +
                        "exatamente os mesmos códigos antes do primeiro quadro. " +
                        "Sem autorização, a partida com cheats é recusada. " +
                        "Não é permitido mudar os códigos durante o jogo.",
                    onlineCheatsOptIn,
                    { if (!active) onOnlineCheatsChanged(it) }
                )
            }
        }

        if (connectionMode == NetworkMode.LAN_AUTO ||
            connectionMode == NetworkMode.AUTO) Card(
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
                if (connectionMode == NetworkMode.AUTO && !active) {
                    OutlinedButton(onClick = { onHost(55435) },
                        enabled = ready, modifier = Modifier.fillMaxWidth()) {
                        Text("CRIAR SALA LAN SEM INTERNET")
                    }
                }
                if (lanRooms.isEmpty()) {
                    Text("A busca começa automaticamente. Se o Wi-Fi bloquear descoberta, use IP/porta abaixo.",
                        color = Muted, fontSize = 11.sp)
                } else lanRooms.forEach { room ->
                    val hashCompatible = ready && (room.romHash.isBlank() ||
                        room.romHash == localRomHashPrefix)
                    val cheatsAllowed = !room.cheats || onlineCheatsOptIn
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PanelRaised),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padFocusRing().clickable(
                            enabled = !active && cheatsAllowed &&
                                (hashCompatible || receiveRomAllowed)
                        ) {
                            remoteHost = room.host
                            portText = room.port.toString()
                            onJoin(room.host, room.port)
                        }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(room.game, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (connectionMode == NetworkMode.AUTO)
                                    Text("Conexão local • Wi-Fi / LAN", color = Muted, fontSize = 11.sp)
                                else Text("${room.host}:${room.port}", color = Muted, fontSize = 11.sp)
                                Text(when {
                                    !hashCompatible && receiveRomAllowed ->
                                        "ROM DIFERENTE — entre para solicitar transferência autorizada"
                                    !hashCompatible -> "ROM/MOD DIFERENTE — habilite receber ROM"
                                    !cheatsAllowed -> "CHEATS — autorize nas configurações"
                                    room.cheats -> "CHEATS SINCRONIZADOS"
                                    room.romHash.isNotBlank() -> "ROM COMPATÍVEL POR SHA-256"
                                    else -> "Compatibilidade conferida ao entrar"
                                }, color = if ((hashCompatible || receiveRomAllowed) && cheatsAllowed) Mint
                                    else Color(0xFFFFBD85), fontSize = 10.sp)
                            }
                            Text("ENTRAR ›", color = Mint, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (connectionMode == NetworkMode.RELAY) Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SALAS PELA INTERNET", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("O anfitrião e o convidado conectam ao servidor pela saída TCP. " +
                    "Funciona atrás de NAT/CGNAT, sem abrir porta no roteador. " +
                    "O servidor precisa estar online com as portas HTTP e TCP liberadas.",
                    fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
                TextField(
                    value = relayAddress,
                    onValueChange = onRelayAddressChanged,
                    label = { Text("IP ou URL do servidor") },
                    placeholder = { Text("http://meu-servidor:8787") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !active
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefreshRelayRooms,
                        enabled = !scanningRelay && !active && relayAddress.isNotBlank(),
                        modifier = Modifier.weight(1f)) {
                        Text(if (scanningRelay) "BUSCANDO…" else "ATUALIZAR SALAS", fontSize = 11.sp)
                    }
                    Button(onClick = onHostRelay,
                        enabled = ready && !active && relayAddress.isNotBlank(),
                        modifier = Modifier.weight(1f)) {
                        Text("CRIAR SALA", fontSize = 11.sp)
                    }
                }
                Text("${relayRooms.size} sala(s) encontrada(s)", color = Mint, fontSize = 12.sp)
                if (relayRooms.isEmpty()) {
                    Text("Sem salas listadas. Abra uma ROM e crie a primeira sala.",
                        color = Muted, fontSize = 12.sp)
                }
                relayRooms.forEach { room ->
                    val hashCompatible = room.romHash.isBlank() ||
                        localRomHashPrefix.isBlank() || room.romHash == localRomHashPrefix
                    val cheatsAllowed = !room.cheats || onlineCheatsOptIn
                    Card(
                        modifier = Modifier.fillMaxWidth().padFocusRing().clickable(
                            enabled = !active && cheatsAllowed &&
                                ((ready && hashCompatible) || (receiveRomAllowed && (room.tls || room.wss)))
                        ) {
                            onJoinRelay(room.id)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = PanelRaised)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(room.game, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Sala " + room.id, color = Muted, fontSize = 10.sp)
                                Text(
                                    when {
                                        !cheatsAllowed -> "CHEATS — autorize"
                                        !hashCompatible && (room.tls || room.wss) && receiveRomAllowed ->
                                            "ROM DIFERENTE — oferta autorizada via TLS"
                                        !hashCompatible -> "ROM/MOD DIFERENTE"
                                        room.cheats -> "CHEATS SINCRONIZADOS"
                                        else -> "ROM verificada ao entrar"
                                    }, color = if (cheatsAllowed &&
                                        (hashCompatible || ((room.tls || room.wss) && receiveRomAllowed))) Mint
                                        else Color(0xFFFFBD85), fontSize = 10.sp
                                )
                            }
                            Text("ENTRAR ›", color = Mint, fontWeight = FontWeight.Bold,
                                fontSize = 11.sp)
                        }
                    }
                }
                if (active) OutlinedButton(onClick = onStop,
                    modifier = Modifier.fillMaxWidth()) { Text("ENCERRAR SESSÃO") }
                Surface(color = Color(0xFF242C3B), shape = RoundedCornerShape(12.dp)) {
                    Text(status, modifier = Modifier.padding(12.dp), color = Mint, fontSize = 12.sp)
                }
                Text("Relay sem TLS: requer a mesma ROM e não compartilha arquivos. " +
                    "Relay TLS certificado e diretório HTTPS permitem oferecer ROM " +
                    "autorizada com confirmação do host e do cliente. Não é E2E.",
                    fontSize = 11.sp, color = Color(0xFFFFBD85), lineHeight = 16.sp)
            }
        }

        if (connectionMode != NetworkMode.LOCAL &&
            connectionMode != NetworkMode.RELAY &&
            connectionMode != NetworkMode.AUTO) Card(
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
                    "ON por padrão; o host confirma autorização por sessão. " +
                        "Na Internet requer relay HTTPS/WSS autenticado.",
                    shareRomAllowed,
                    { if (!active) onShareRomAllowedChanged(it) }
                )
                ToggleSettingRow(
                    "Aceitar ROM autorizada do host",
                    "ON por padrão; cada oferta exige aceite e SHA-256. " +
                        "Na Internet requer relay TLS confiável.",
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
        Text("MZNP v6: LAN e IP manual continuam disponíveis. " +
            "Transferência entre redes exige TLS autenticado, confirmação do host " +
            "e aceite do cliente. O relay não fornece criptografia ponta a ponta.",
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
    onGpuReady: (Boolean) -> Unit,
    onInputChanged: (Int, Boolean) -> Unit
) {
    var toolbarExpanded by remember(romName) { mutableStateOf(false) }
    var editIndividual by remember(romName) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(settings, onSurfaceChanged, onGpuReady)
        if (settings.touch.enabled) TouchOverlay(
            settings.touch, onInputChanged, editingControls, editIndividual,
            onLayoutChanged = { onSettingsChanged(settings.copy(touch = it)) }
        )
        if (!toolbarExpanded && !editingControls) {
            Box(
                modifier = (if (settings.touch.enabled &&
                        settings.touch.preset == TouchPreset.VIRTUAL_GAMEPAD)
                        Modifier.align(Alignment.TopStart)
                            .padding(top = 9.dp, start = 12.dp)
                    else Modifier.align(Alignment.TopEnd)
                            .padding(top = 9.dp, end = 12.dp))
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
private fun VideoSurface(
    settings: AppSettings,
    onSurfaceChanged: (Surface?) -> Unit,
    onGpuReady: (Boolean) -> Unit
) {
    val aspect = settings.aspect
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
            AspectMode.NATIVE -> containedSize(maxWidth, maxHeight, 8f / 7f)
            AspectMode.CLASSIC -> containedSize(maxWidth, maxHeight, 3f / 2f)
            AspectMode.WIDE -> containedSize(maxWidth, maxHeight, 16f / 9f)
            AspectMode.ULTRAWIDE -> containedSize(maxWidth, maxHeight, 21f / 9f)
        }
        Box(Modifier.align(Alignment.Center).requiredWidth(size.first).requiredHeight(size.second)) {
            if (settings.renderBackend == RenderBackend.GLES) {
                GpuVideoSurface(settings.gpuShader, Modifier.fillMaxSize(), onGpuReady)
            } else {
                AndroidViewSurface(onSurfaceChanged)
            }
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
    if (layout.preset == TouchPreset.VIRTUAL_GAMEPAD) {
        VirtualGamepadSnesOverlay(layout, onInputChanged, editing, editIndividual,
            onLayoutChanged)
        return
    }
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
            if (layout.preset.diamond) {
                // Modern gamepads arrange SNES X at north, A at east,
                // B at south, Y at west. Labels are presentation only;
                // input mapping remains SNES/libretro native.
                val faceDiameter = (actionSize * 2.95f).dp
                Box(Modifier.size(faceDiameter)) {
                    TouchButton(layout.preset.faceLegend(9), 9, actionSize,
                        layout.opacity, onInputChanged, enabled = !editing,
                        skin = layout.preset,
                        positionModifier = Modifier.align(Alignment.TopCenter).then(positionFor(9)))
                    TouchButton(layout.preset.faceLegend(1), 1, actionSize,
                        layout.opacity, onInputChanged, enabled = !editing,
                        skin = layout.preset,
                        positionModifier = Modifier.align(Alignment.CenterStart).then(positionFor(1)))
                    TouchButton(layout.preset.faceLegend(8), 8, actionSize,
                        layout.opacity, onInputChanged, enabled = !editing,
                        skin = layout.preset,
                        positionModifier = Modifier.align(Alignment.CenterEnd).then(positionFor(8)))
                    TouchButton(layout.preset.faceLegend(0), 0, actionSize,
                        layout.opacity, onInputChanged, enabled = !editing,
                        skin = layout.preset,
                        positionModifier = Modifier.align(Alignment.BottomCenter).then(positionFor(0)))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    TouchButton(layout.preset.faceLegend(1), 1, actionSize,
                        layout.opacity, onInputChanged, enabled = !editing,
                        skin = layout.preset, positionModifier = positionFor(1))
                    TouchButton(layout.preset.faceLegend(9), 9, actionSize,
                        layout.opacity, onInputChanged, enabled = !editing,
                        skin = layout.preset, positionModifier = positionFor(9))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    TouchButton(layout.preset.faceLegend(0), 0, actionSize,
                        layout.opacity, onInputChanged, enabled = !editing,
                        skin = layout.preset, positionModifier = positionFor(0))
                    TouchButton(layout.preset.faceLegend(8), 8, actionSize,
                        layout.opacity, onInputChanged, enabled = !editing,
                        skin = layout.preset, positionModifier = positionFor(8))
                }
            }
            if (!layout.preset.diamond) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    TouchButton(layout.preset.shoulderLegend(10), 10,
                        actionSize * 0.82f, layout.opacity, onInputChanged,
                        enabled = !editing, skin = layout.preset,
                        positionModifier = positionFor(10))
                    TouchButton(layout.preset.shoulderLegend(11), 11,
                        actionSize * 0.82f, layout.opacity, onInputChanged,
                        enabled = !editing, skin = layout.preset,
                        positionModifier = positionFor(11))
                }
            }
        }
        if (layout.preset.diamond) {
            // Modern pad shoulder triggers are at the top of the device,
            // well clear of the collapsible menu (44dp upper right).
            Box(Modifier.align(Alignment.TopStart).padding(start = 17.dp, top = 58.dp)
                .then(positionFor(10))) {
                TouchButton(layout.preset.shoulderLegend(10), 10,
                    34f * layout.scale, layout.opacity, onInputChanged,
                    isPill = true, enabled = !editing, skin = layout.preset)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(end = 17.dp, top = 58.dp)
                .then(positionFor(11))) {
                TouchButton(layout.preset.shoulderLegend(11), 11,
                    34f * layout.scale, layout.opacity, onInputChanged,
                    isPill = true, enabled = !editing, skin = layout.preset)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            TouchButton(layout.preset.menuLegend(2), 2, 34f * layout.scale,
                layout.opacity, onInputChanged, true, !editing, layout.preset, positionFor(2))
            TouchButton(layout.preset.menuLegend(3), 3, 34f * layout.scale,
                layout.opacity, onInputChanged, true, !editing, layout.preset, positionFor(3))
        }
    }
}

/**
 * Maizo original SNES input overlay in the *visual arrangement* of
 * VirtualGamePad-Mobile. The upstream project has no separate button-image
 * pack: its controls are Material Compose components licensed GPL-3.0.
 * This is independent original code/rendering, NOT copied upstream source.
 * Common physical control dimensions and placements are adapted:
 * height-relative 0.45 screen-height face/D-pad cluster, 1/18 edge padding,
 * shoulder and center buttons placed relative to 1/4 screen-height.
 *
 * SNES face mapping stays native: Y=bit1, X=bit9, B=bit0, A=bit8.
 * Separate buttons (and diagonal D-pad gesture) allow multitouch.
 */
@Composable
private fun VirtualGamepadSnesOverlay(
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
        val edge = maxHeight / 18f
        val cluster = maxHeight * (0.45f * layout.scale.coerceIn(0.65f, 1.45f))
        val buttonSize = cluster.value * 0.4f
        val miniButtonSize = (maxHeight.value * layout.scale / 7f).coerceIn(30f, 58f)

        fun offsetBit(bit: Int): Modifier {
            val shift = layout.buttonShifts[bit] ?: ButtonShift()
            val placement = Modifier.offset(
                x = maxWidth * shift.x.coerceIn(-0.30f, 0.30f),
                y = maxHeight * shift.y.coerceIn(-0.30f, 0.30f)
            )
            val edit = if (editing && editIndividual) Modifier.pointerInput(bit) {
                detectDragGestures { change, delta ->
                    change.consume()
                    val old = liveLayout.buttonShifts[bit] ?: ButtonShift()
                    val updated = ButtonShift(old.x + delta.x / widthPx,
                        old.y + delta.y / heightPx).sanitized()
                    liveUpdate(liveLayout.copy(
                        buttonShifts = liveLayout.buttonShifts + (bit to updated)))
                }
            } else Modifier
            return placement.then(edit)
        }
        val dragDpad = if (editing) Modifier.pointerInput(editing) {
            detectDragGestures { change, delta ->
                change.consume()
                liveUpdate(liveLayout.copy(
                    dpadX = (liveLayout.dpadX + delta.x / widthPx).coerceIn(0f, 0.65f),
                    dpadY = (liveLayout.dpadY - delta.y / heightPx).coerceIn(0f, 0.65f)))
            }
        } else Modifier
        val dragFace = if (editing && !editIndividual) Modifier.pointerInput(editing) {
            detectDragGestures { change, delta ->
                change.consume()
                liveUpdate(liveLayout.copy(
                    buttonsX = (liveLayout.buttonsX - delta.x / widthPx).coerceIn(0f, 0.65f),
                    buttonsY = (liveLayout.buttonsY + delta.y / heightPx).coerceIn(0f, 0.65f)))
            }
        } else Modifier

        // D-pad bottom left, shifted +1/3 screen height from the left edge.
        Box(Modifier.align(Alignment.BottomStart)
            .offset(x = edge + maxHeight / 3f + maxWidth * layout.dpadX,
                y = -edge - maxHeight * layout.dpadY)
            .then(dragDpad)) {
            GestureDpad(buttonSize, layout.opacity, onInputChanged,
                enabled = !editing, skin = TouchPreset.VIRTUAL_GAMEPAD)
        }

        // Face controls top right, A south / B east / X west / Y north.
        // Same screen-space diamond as the upstream reference, SNES bit mapping.
        Box(Modifier.align(Alignment.TopEnd)
            .offset(x = -edge - maxWidth * layout.buttonsX,
                y = edge + maxHeight * layout.buttonsY)
            .size(cluster)
            .then(dragFace)) {
            TouchButton("Y", 1, buttonSize, layout.opacity, onInputChanged,
                enabled = !editing, skin = TouchPreset.VIRTUAL_GAMEPAD,
                positionModifier = Modifier.align(Alignment.TopCenter).then(offsetBit(1)))
            TouchButton("X", 9, buttonSize, layout.opacity, onInputChanged,
                enabled = !editing, skin = TouchPreset.VIRTUAL_GAMEPAD,
                positionModifier = Modifier.align(Alignment.CenterStart).then(offsetBit(9)))
            TouchButton("B", 0, buttonSize, layout.opacity, onInputChanged,
                enabled = !editing, skin = TouchPreset.VIRTUAL_GAMEPAD,
                positionModifier = Modifier.align(Alignment.CenterEnd).then(offsetBit(0)))
            TouchButton("A", 8, buttonSize, layout.opacity, onInputChanged,
                enabled = !editing, skin = TouchPreset.VIRTUAL_GAMEPAD,
                positionModifier = Modifier.align(Alignment.BottomCenter).then(offsetBit(8)))
        }

        // L/R at upper center +/-1/4 screen height. No phantom LT/RT:
        // SNES only exposes L/R. Center controls use the same anchor system.
        Box(Modifier.align(Alignment.TopCenter)
            .offset(x = -(maxHeight / 4f), y = edge)
            .then(offsetBit(10))) {
            TouchButton("L", 10, miniButtonSize, layout.opacity,
                onInputChanged, isPill = true, enabled = !editing,
                skin = TouchPreset.VIRTUAL_GAMEPAD)
        }
        Box(Modifier.align(Alignment.TopCenter)
            .offset(x = maxHeight / 4f, y = edge)
            .then(offsetBit(11))) {
            TouchButton("R", 11, miniButtonSize, layout.opacity,
                onInputChanged, isPill = true, enabled = !editing,
                skin = TouchPreset.VIRTUAL_GAMEPAD)
        }
        Box(Modifier.align(Alignment.TopCenter)
            .offset(x = -(maxHeight / 4f), y = edge + maxHeight / 4f)
            .then(offsetBit(2))) {
            TouchButton("SELECT", 2, miniButtonSize * 0.7f, layout.opacity,
                onInputChanged, isPill = true, enabled = !editing,
                skin = TouchPreset.VIRTUAL_GAMEPAD)
        }
        Box(Modifier.align(Alignment.TopCenter)
            .offset(x = maxHeight / 4f, y = edge + maxHeight / 4f)
            .then(offsetBit(3))) {
            TouchButton("START", 3, miniButtonSize * 0.7f, layout.opacity,
                onInputChanged, isPill = true, enabled = !editing,
                skin = TouchPreset.VIRTUAL_GAMEPAD)
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
    val total = if (skin == TouchPreset.VIRTUAL_GAMEPAD)
        (cellSize * 2.5f).dp else (cellSize * 3).dp + gap * 2
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
        if (skin == TouchPreset.VIRTUAL_GAMEPAD) {
            // Compact D-pad with 4 separated direction discs. The outer
            // gesture handler still supports continuous diagonals.
            Box(Modifier.fillMaxSize()) {
                TouchButton("▲", 4, cellSize, opacity, onInputChanged,
                    enabled = false, skin = skin,
                    positionModifier = Modifier.align(Alignment.TopCenter))
                TouchButton("▼", 5, cellSize, opacity, onInputChanged,
                    enabled = false, skin = skin,
                    positionModifier = Modifier.align(Alignment.BottomCenter))
                TouchButton("◀", 6, cellSize, opacity, onInputChanged,
                    enabled = false, skin = skin,
                    positionModifier = Modifier.align(Alignment.CenterStart))
                TouchButton("▶", 7, cellSize, opacity, onInputChanged,
                    enabled = false, skin = skin,
                    positionModifier = Modifier.align(Alignment.CenterEnd))
            }
        } else Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            TouchButton("↑", 4, cellSize, opacity, onInputChanged, enabled = false, skin = skin)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                TouchButton("←", 6, cellSize, opacity, onInputChanged, enabled = false, skin = skin)
                Box(Modifier.size(cellSize.dp).clip(CircleShape)
                    .background(if (skin == TouchPreset.STEAM) Color(0xFF121E32).copy(alpha = opacity)
                        else Color.White.copy(alpha = opacity * 0.22f)), contentAlignment = Alignment.Center) {
                    if (skin == TouchPreset.STEAM || skin == TouchPreset.XBOX) {
                        Canvas(Modifier.size((cellSize * 0.72f).dp)) {
                            drawCircle(color = Color(0xFF3A4E6E).copy(alpha = opacity),
                                radius = size.minDimension * 0.45f)
                            drawCircle(color = Color.White.copy(alpha = opacity * 0.54f),
                                radius = size.minDimension * 0.42f,
                                style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }
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
        else if (skin == TouchPreset.CTR || skin == TouchPreset.ARMS ||
            skin == TouchPreset.SWITCH) RoundedCornerShape(13.dp)
        else CircleShape
    val faceAccent = when (skin) {
        TouchPreset.VIRTUAL_GAMEPAD -> when (bit) {
            8 -> Color(0xFF38EF58)   // SNES A / VGP green
            0 -> Color(0xFFFF5965)   // SNES B / VGP red
            9 -> Color(0xFF4B9EFF)   // SNES X / VGP blue
            1 -> Color(0xFFFFFF4D)   // SNES Y / VGP yellow
            else -> Color(0xFFDBE5FF)
        }
        TouchPreset.PLAYSTATION -> when (bit) {
            9 -> Color(0xFF50E6A5)  // triangle
            8 -> Color(0xFFFF646B)  // circle
            0 -> Color(0xFF65B9FF)  // cross
            1 -> Color(0xFFFF9ED5)  // square
            else -> Color(0xFFE4E6F0)
        }
        TouchPreset.XBOX -> when (bit) {
            0 -> Color(0xFF34C758)  // A
            8 -> Color(0xFFF24C45)  // B
            1 -> Color(0xFF2388EA)  // X
            9 -> Color(0xFFFFCC30)  // Y
            else -> Color(0xFFD0D9E4)
        }
        TouchPreset.ARCADE -> when (bit) {
            0 -> Color(0xFFFF5B51)
            1 -> Color(0xFF45E9F6)
            8 -> Color(0xFFFFCF47)
            9 -> Color(0xFF82F96D)
            else -> Color(0xFF97E8FE)
        }
        else -> Color.White
    }
    val glyph = when (skin) {
        TouchPreset.MAIZO, TouchPreset.CTR -> Color(0xFF121522)
        TouchPreset.ARMS -> Color(0xFF7FFFE3)
        TouchPreset.PLAYSTATION, TouchPreset.VIRTUAL_GAMEPAD -> faceAccent
        TouchPreset.PPSSPP, TouchPreset.XBOX, TouchPreset.SWITCH,
        TouchPreset.ARCADE, TouchPreset.STEAM -> Color.White
    }
    val alpha = opacity.coerceIn(0.18f, 1f)
    Box(
        modifier = (if (isPill) Modifier.width((size * 2f).dp).height(size.dp)
            else Modifier.size(size.dp))
            .then(positionModifier)
            .clip(shape)
            .then(if (enabled) Modifier.pointerInput(bit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onInputChanged(bit, true)
                    try { waitForUpOrCancellation() }
                    finally { onInputChanged(bit, false) }
                }
            } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // Distinct original vector artwork drawn at any DPI. Each preset
        // switches BOTH positions and rendered geometry; not just tint.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = minOf(this.size.width, this.size.height) * 0.47f
            val corner = CornerRadius(radius * 0.32f, radius * 0.32f)
            when (skin) {
                TouchPreset.VIRTUAL_GAMEPAD -> {
                    // Deep graphite backing and colored outline like a
                    // mobile virtual pad, independently drawn as SNES vector art.
                    if (isPill) {
                        drawRoundRect(color = Color(0xFF111C2D).copy(alpha = alpha * .82f),
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()))
                        drawRoundRect(color = faceAccent.copy(alpha = alpha * .97f),
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                            style = Stroke(width = 2.2.dp.toPx()))
                    } else {
                        drawCircle(color = Color(0xFF121A2B).copy(alpha = alpha * .83f),
                            center = c, radius = radius)
                        drawCircle(color = faceAccent.copy(alpha = alpha * .98f),
                            center = c, radius = radius * .94f,
                            style = Stroke(width = 2.2.dp.toPx()))
                    }
                }
                TouchPreset.MAIZO -> {
                    drawRoundRect(brush = Brush.linearGradient(listOf(
                        Color(0xFFFFFFFF).copy(alpha = alpha * .80f),
                        Color(0xFF98BBEA).copy(alpha = alpha * .68f))),
                        cornerRadius = CornerRadius(radius * .68f, radius * .68f))
                    drawRoundRect(color = Color.White.copy(alpha = alpha * .86f),
                        cornerRadius = CornerRadius(radius * .68f, radius * .68f),
                        style = Stroke(width = 2.dp.toPx()))
                }
                TouchPreset.CTR -> {
                    drawRoundRect(color = Color(0xFF16181C).copy(alpha = alpha * .90f),
                        cornerRadius = corner)
                    drawRoundRect(brush = Brush.linearGradient(listOf(
                        Color(0xFFFFF1A8).copy(alpha = alpha),
                        Color(0xFFFFAE32).copy(alpha = alpha * .91f),
                        Color(0xFF88481A).copy(alpha = alpha * .88f))),
                        topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(
                            (this.size.width - 6.dp.toPx()).coerceAtLeast(1f),
                            (this.size.height - 6.dp.toPx()).coerceAtLeast(1f)),
                        cornerRadius = corner)
                    drawRoundRect(color = Color(0xFFFFF7CD).copy(alpha = alpha),
                        topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(
                            (this.size.width - 6.dp.toPx()).coerceAtLeast(1f),
                            (this.size.height - 6.dp.toPx()).coerceAtLeast(1f)),
                        cornerRadius = corner, style = Stroke(width = 1.dp.toPx()))
                    drawLine(Color.White.copy(alpha = alpha * .38f),
                        Offset(radius * .27f, radius * .32f),
                        Offset(this.size.width - radius * .27f, radius * .32f),
                        strokeWidth = 1.5.dp.toPx())
                }
                TouchPreset.ARMS -> {
                    drawRoundRect(color = Color(0xFF030A14).copy(alpha = alpha * .82f),
                        cornerRadius = corner)
                    drawRoundRect(color = Color(0xFF54FFE7).copy(alpha = alpha * .88f),
                        cornerRadius = corner, style = Stroke(width = 2.dp.toPx()))
                    drawLine(Color(0xFF2AE3E2).copy(alpha = alpha * .44f),
                        Offset(4.dp.toPx(), this.size.height - 6.dp.toPx()),
                        Offset(this.size.width - 4.dp.toPx(), this.size.height - 6.dp.toPx()),
                        strokeWidth = 3.dp.toPx())
                }
                TouchPreset.PLAYSTATION -> {
                    drawCircle(color = Color(0xFF151D2F).copy(alpha = alpha * 0.86f),
                        center = c, radius = radius)
                    drawCircle(color = faceAccent.copy(alpha = alpha * 0.96f),
                        center = c, radius = radius * 0.91f,
                        style = Stroke(width = 2.1.dp.toPx()))
                    drawCircle(color = Color.White.copy(alpha = alpha * 0.18f),
                        center = Offset(c.x - radius * 0.13f, c.y - radius * 0.17f),
                        radius = radius * 0.56f, style = Stroke(width = 1.dp.toPx()))
                }
                TouchPreset.XBOX -> {
                    drawCircle(color = Color(0xFF151A23).copy(alpha = alpha * 0.95f),
                        center = c, radius = radius)
                    drawCircle(brush = Brush.linearGradient(listOf(
                        faceAccent.copy(alpha = alpha * 0.93f),
                        faceAccent.copy(alpha = alpha * 0.67f))),
                        center = c, radius = radius * 0.87f)
                    drawCircle(color = Color.White.copy(alpha = alpha * 0.33f),
                        center = c, radius = radius * 0.89f,
                        style = Stroke(width = 1.4.dp.toPx()))
                }
                TouchPreset.SWITCH -> {
                    drawRoundRect(color = Color(0xFF0E1520).copy(alpha = alpha * 0.82f),
                        cornerRadius = corner)
                    drawRoundRect(color = Color(0xFFE8EBF1).copy(alpha = alpha * 0.86f),
                        cornerRadius = corner, style = Stroke(width = 1.6.dp.toPx()))
                    drawCircle(color = Color(0xFF1B222E).copy(alpha = alpha * 0.62f),
                        center = c, radius = radius * 0.45f)
                }
                TouchPreset.ARCADE -> {
                    drawCircle(color = Color(0xFF0F0D1F).copy(alpha = alpha * 0.97f),
                        center = c, radius = radius)
                    drawCircle(color = faceAccent.copy(alpha = alpha * 0.53f),
                        center = c, radius = radius * 0.88f)
                    drawCircle(color = faceAccent.copy(alpha = alpha),
                        center = c, radius = radius * 0.94f,
                        style = Stroke(width = 3.2.dp.toPx()))
                    drawCircle(color = Color.White.copy(alpha = alpha * 0.35f),
                        center = Offset(c.x - radius * 0.3f, c.y - radius * 0.30f),
                        radius = radius * 0.22f)
                }
                TouchPreset.STEAM -> {
                    drawCircle(color = Color(0xFF101A2B).copy(alpha = alpha * 0.94f),
                        center = c, radius = radius)
                    drawCircle(color = Color(0xFF8AA8C9).copy(alpha = alpha * 0.8f),
                        center = c, radius = radius * 0.93f,
                        style = Stroke(width = 1.8.dp.toPx()))
                    drawCircle(color = Color(0xFF3D6083).copy(alpha = alpha * 0.55f),
                        center = c, radius = radius * 0.73f,
                        style = Stroke(width = 1.2.dp.toPx()))
                }
                TouchPreset.PPSSPP -> {
                    if (isPill) {
                        drawRoundRect(color = Color(0xFF142B4A).copy(alpha = alpha * .42f),
                            cornerRadius = CornerRadius(radius, radius))
                        drawRoundRect(color = Color.White.copy(alpha = alpha * .85f),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = 2.dp.toPx()))
                    } else {
                        drawCircle(color = Color(0xFF416EA9).copy(alpha = alpha * .32f),
                            center = c, radius = radius)
                        drawCircle(color = Color.White.copy(alpha = alpha * .88f),
                            center = c, radius = radius, style = Stroke(2.3.dp.toPx()))
                        drawCircle(color = Color.White.copy(alpha = alpha * .20f),
                            center = c, radius = radius * .77f, style = Stroke(.8.dp.toPx()))
                    }
                }
            }
        }
        Text(label, color = glyph, fontWeight = FontWeight.Black,
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
    onDevShareChanged: (Boolean) -> Unit,
    iceServerOverrides: String,
    onIceOverridesChanged: (String) -> Unit,
    iceTestStatus: String,
    onTestIce: () -> Unit
) {
    var category by remember { mutableStateOf("Vídeo") }
    val firstCategoryFocus = remember { FocusRequester() }
    LaunchedEffect(inGame) { if (!inGame) firstCategoryFocus.requestFocus() }
    var devCode by remember { mutableStateOf("") }
    var devError by remember { mutableStateOf(false) }
    var confirmBackup by remember { mutableStateOf<String?>(null) }
    var cheatFilter by remember { mutableStateOf("") }
    val categories = listOf("Vídeo", "Áudio", "Controles", "Cheats", "Rede", "Núcleo", "Estados", "DEV")
    Column(Modifier.fillMaxWidth()) {
        Text("CENTRAL DE AJUSTES",
            color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEachIndexed { index, item ->
                androidx.compose.material3.FilterChip(
                    selected = item == category,
                    onClick = { category = item },
                    modifier = (if (index == 0) Modifier.focusRequester(firstCategoryFocus)
                        else Modifier).padFocusRing(),
                    label = { Text(item, fontSize = 12.sp) }
                )
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
                            AspectMode.NATIVE -> "Pixels 8:7 para jogos 256×224 sem correção CRT."
                            AspectMode.CLASSIC -> "Enquadra a imagem em 3:2, sem criar conteúdo lateral."
                            AspectMode.WIDE -> "Enquadra o jogo em uma área 16:9, sem widescreen hack." 
                            AspectMode.ULTRAWIDE -> "Enquadra em 21:9; não expande o campo de visão."
                            AspectMode.STRETCH -> "Estica para ocupar toda a área, podendo distorcer a imagem."
                            AspectMode.FILL -> "Preenche usando 4:3 e corta parte da imagem se necessário."
                        }
                        ChoiceRow(mode.label, description, settings.aspect == mode) { onSettingsChanged(settings.copy(aspect = mode)) }
                    }
                    SettingSectionTitle("Perfis de imagem em um toque",
                        "Ajuste vários controles juntos, sem perder as opções manuais abaixo.")
                    OutlinedButton(onClick = {
                        onSettingsChanged(settings.copy(
                            aspect = AspectMode.CRT,
                            renderBackend = RenderBackend.CPU,
                            filter = VideoFilter.ORIGINAL,
                            outputScale = 1))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("MODO LEVE • COMPATIBILIDADE E FPS")
                    }
                    OutlinedButton(onClick = {
                        onSettingsChanged(settings.copy(
                            aspect = AspectMode.CRT,
                            renderBackend = RenderBackend.GLES,
                            gpuShader = GpuShader.NEAREST,
                            outputScale = 1))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("GPU OPENGL • PIXELS NÍTIDOS (EXPERIMENTAL)")
                    }
                    SettingSectionTitle("Resolução e escala",
                        "O núcleo SNES renderiza na resolução nativa (normalmente 256×224 ou 512×448). " +
                            "A saída é ampliada pela superfície Android; mudar proporção NÃO cria " +
                            "detalhes nem widescreen real. Upscale por shader exigirá backend GPU.")
                    SettingSectionTitle("Renderizador de vídeo",
                        "Alternância entre CPU RGB565 comprovada e GPU experimental OpenGL ES 2.")
                    RenderBackend.values().forEach { backend ->
                        ChoiceRow(backend.label, backend.hint,
                            settings.renderBackend == backend) {
                            onSettingsChanged(settings.copy(renderBackend = backend))
                        }
                    }
                    if (settings.renderBackend == RenderBackend.GLES) {
                        SettingSectionTitle("Shaders OpenGL ES 2",
                            "Escala na GPU do tamanho nativo da ROM para a superfície Android. " +
                                "Não adiciona detalhes ao jogo nem altera o campo de visão.")
                        GpuShader.values().forEach { shader ->
                            ChoiceRow(shader.label, shader.hint,
                                settings.gpuShader == shader) {
                                onSettingsChanged(settings.copy(gpuShader = shader))
                            }
                        }
                    }
                    SettingSectionTitle("Framebuffer ampliado (CPU RGB565)",
                        "Escala de SAÍDA por duplicação nítida dos pixels, sem criar detalhes " +
                            "novos nem alterar a resolução interna do SNES. " +
                            "Em aparelhos fracos mantenha 1x.")
                    for (factor in 1..4) {
                        ChoiceRow("Saída " + factor + "×",
                            if (factor == 1) "Resolução original, máxima velocidade."
                            else "Framebuffer RGB565 " + factor + "×, sem shader GPU. " +
                                "Pode consumir mais CPU/memória; Hi-Res reduz escala automaticamente.",
                            settings.outputScale == factor) {
                            onSettingsChanged(settings.copy(outputScale = factor))
                        }
                    }
                    SettingSectionTitle("Filtros RGB565", "Filtros por CPU sobre a imagem " +
                        "original antes de ampliar. Original + 1x oferece menor consumo.")
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
                    SettingSectionTitle("Buffer de áudio (25–60 ms)",
                        "25 ms: resposta rápida, exige aparelho estável. " +
                        "40 ms: equilíbrio recomendado. 60 ms: reduz cortes quando há oscilação.")
                    listOf(25, 40, 60).forEach { millis ->
                        ChoiceRow("$millis ms", when (millis) {
                            25 -> "Baixa latência • sensível a engasgos"
                            40 -> "Balanceado • recomendado"
                            else -> "Estabilidade • buffer maior"
                        }, settings.audioBufferMs == millis) {
                            onSettingsChanged(settings.copy(audioBufferMs = millis))
                        }
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
                        "Dez estilos. Padrão VirtualGamePad Mobile: botões superiores coloridos, " +
                            "D-pad inferior, L/R e Start/Select no topo; também inclui PlayStation, Xbox, Switch, Arcade " +
                            "e Steam Deck. Botões ABXY mantêm mapeamento do SNES. " +
                            "A arte do preset VirtualGamePad é recriada, não são arquivos GPL copiados.")
                    TouchPreset.values().forEach { preset ->
                        ChoiceRow(preset.label, preset.hint,
                            settings.touch.preset == preset) { onSelectTouchPreset(preset) }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)) {
                            for (bit in listOf(1, 9, 0, 8)) {
                                TouchButton(preset.faceLegend(bit), bit, 33f, 0.78f,
                                    { _, _ -> }, enabled = false, skin = preset)
                            }
                        }
                    }
                    Text(
                        if (settings.touch.preset == TouchPreset.PLAYSTATION)
                            "Mapeamento: □ = Y • △ = X • ✕ = B • ○ = A do SNES."
                        else if (settings.touch.preset == TouchPreset.XBOX)
                            "Mapeamento: X = Y • Y = X • A = B • B = A do SNES."
                        else "Todos os estilos preservam os bits dos botões originais do SNES.",
                        color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                    SettingSectionTitle("Editor de layout",
                        "No jogo: EDITAR → GRUPOS/BOTÕES. " +
                            "Arraste o D-pad e os botões individuais. " +
                            "Perfis modernos usam L/R nos cantos superiores.")
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
                            "as salas usam MZNP v5 (entradas antecipadas) e opções temporárias do host.")
                    NetworkMode.values().forEach { mode ->
                        ChoiceRow(mode.label, mode.hint, connectionMode == mode) {
                            if (!cheatsLocked) onConnectionModeChanged(mode)
                        }
                    }
                    IceServerSettings(
                        iceServerOverrides, onIceOverridesChanged,
                        iceTestStatus, onTestIce, !cheatsLocked)
                    if (cheatsLocked) Text("Para trocar modo, encerre a sessão ativa.",
                        color = Color(0xFFFFC47E), fontSize = 11.sp)
                    Text("LAN usa descoberta automática ou IP manual. Internet " +
                        "usa fila de inputs antecipados em vez de um RTT por quadro. " +
                        "O convidado copia temporariamente as opções compatíveis do host; " +
                        "ao sair, os ajustes pessoais são restaurados.",
                        color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
                "Núcleo" -> {
                    SettingSectionTitle("Snes9x 1.63 • opções avançadas",
                        "Controle nativo do emulador. No multiplayer, as opções do anfitrião " +
                            "são sincronizadas temporariamente com o convidado.")
                    OutlinedButton(onClick = {
                        CoreOptionDefinitions.all.forEach { option ->
                            if (coreOptions[option.key] != option.defaultValue)
                                onCoreOptionChanged(option.key, option.defaultValue)
                        }
                    }, enabled = !cheatsLocked, modifier = Modifier.fillMaxWidth()) {
                        Text("RESTAURAR CONFIGURAÇÕES DO NÚCLEO")
                    }
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
                                "O código DEV não autoriza copiar jogos comerciais " +
                                "protegidos; ambas as pessoas precisam concordar.",
                            devShareEnabled,
                            onDevShareChanged
                        )
                        Text("Compartilhamento autorizado funciona sem código 9090; " +
                            "mas pode ser desativado. A Internet exige relay TLS " +
                            "e consentimento por sessão. O relay pode ler o tráfego.",
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
        modifier = Modifier.fillMaxWidth().padFocusRing().clickable(onClick = onClick),
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

@Composable
private fun NetplayDebugDialog(
    debug: NetworkDebugState, dismiss: () -> Unit, export: () -> Unit
) {
    fun number(v: Double?): String = if (v == null) "N/D" else
        String.format(java.util.Locale.US, "%.1f", v)
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = Modifier.dialogGamepadKeys(dismiss),
        title = { Text("STATUS DE CONEXÃO • DEBUG", color = Mint) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Sessão: " + if (debug.active) "ATIVA" else "INATIVA")
                Text("Motor: " + debug.mode)
                Text("Transporte: " + debug.transport)
                Text("Peer: " + debug.peer)
                Text("RTT (mín/média/máx): " +
                    number(debug.rttMin) + " / " + number(debug.rttAvg) +
                    " / " + number(debug.rttMax) + " ms")
                Text("Jitter: " + number(debug.jitterMs) + " ms")
                Text("Perda de pacotes: " + debug.packetLoss)
                Text("ICE state: " + debug.iceState)
                Text("NAT: " + debug.natType)
                Text("TURN: " + debug.turnUsed)
                Text("Rollbacks/s: " + debug.rollbacksPerSec)
                Text("Correções totais: " + debug.totalRollbacks)
                Text("Input delay: " + (debug.delay?.toString() ?: "N/D") + " quadros")
                Text("Snes9x save+load: " + number(debug.saveLoadMs) + " ms")
                Text("Maior parada Gekko: " +
                    String.format(java.util.Locale.US, "%.0f", debug.peakFrameStallMs) + " ms")
                Text("Render exibido: " +
                    if (debug.presentationStride > 1) "1 em 2 quadros" else "todos")
                Text("Limited Saving: " + if (debug.limitedSaving) "ON" else "OFF")
                Text("Desyncs detectados: " + debug.desyncs)
                Text("Quadros executados: " + debug.frame)
                Text(debug.state, color = Mint, fontSize = 11.sp)
                Text("O transporte mostra o modo REAL: WSS/TCP não é WebRTC. " +
                    "ICE stats não fornecem perda de pacotes SCTP confiável.", color = Muted, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = dismiss, modifier = Modifier.padFocusRing()) {
                Text("FECHAR")
            }
        },
        dismissButton = {
            TextButton(onClick = export, modifier = Modifier.padFocusRing()) {
                Text("COMPARTILHAR DIAGNÓSTICO")
            }
        }
    )
}
 
@Composable
private fun IceServerSettings(
    overrides: String, onChange: (String) -> Unit,
    status: String, onTest: () -> Unit, enabled: Boolean
) {
    SettingSectionTitle("ICE • STUN e TURN",
        "Google/Cloudflare automáticos. TURN do Railway/Metered é buscado " +
        "sem incluir chave de API no APK. Redes com NAT restritivo podem exigir TURN.")
    TextField(
        value = overrides, onValueChange = { if (enabled) onChange(it) },
        label = { Text("Servidores próprios (opcional)") },
        placeholder = { Text("stun:stun.example.com:3478\nturn:relay.example.com:3478|usuario|senha") },
        maxLines = 4, enabled = enabled,
        modifier = Modifier.fillMaxWidth().padFocusRing())
    Text("Uma URI por linha; TURN privado: URI|usuário|senha. " +
        "Credenciais locais ficam nas preferências do aparelho; " +
        "não inclua chaves API de conta e não compartilhe sua configuração.",
        fontSize = 10.sp, color = Muted)
    OutlinedButton(onClick = onTest, enabled = enabled,
        modifier = Modifier.fillMaxWidth().padFocusRing()) {
        Text("TESTAR COLETA STUN/TURN (ICE)")
    }
    Text(status, fontSize = 11.sp, color = Mint)
    Text("O teste coleta candidatos, mas só dois aparelhos comprovam " +
        "P2P conectado. Em falha de ICE, o jogo usa Lockstep por WSS.",
        fontSize = 10.sp, color = Muted)
}
