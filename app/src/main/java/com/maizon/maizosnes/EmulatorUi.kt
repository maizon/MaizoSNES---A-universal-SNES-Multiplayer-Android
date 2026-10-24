package com.maizon.maizosnes

import android.graphics.Bitmap
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val buttonShifts: Map<Int, ButtonShift> = emptyMap(),
    val buttonStyles: Map<Int, ButtonStyle> = emptyMap()
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

// NeoStation-inspired presentation, independently implemented in Compose.
// No Flutter code or GPLv3 assets are copied into the Snes9x-based application.
// Original Jetpack Compose artwork. NeoStation is a GPLv3 design reference,
// not a source of copied Dart code, proprietary artwork or branding.
private val Bg = Color(0xFF15191E)
private val Panel = Color(0xFF1D232A)
private val PanelRaised = Color(0xFF29323D)
private val Accent = Color(0xFF83ADFA)
private val Mint = Color(0xFF29DAAE)
private val Muted = Color(0xFFB2C2CE)
private val NeoPink = Color(0xFFF43098)
private val NeoOutline = Color(0xFF454C73)
private val NeoGlass = Color(0xE51D2035)

@Composable
private fun ConsoleRoomAvatar(ownerName: String) {
    Box(Modifier.size(40.dp).clip(CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFF31518B), Color(0xFF22564A))))
        .border(1.dp, Mint.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center) {
        Text(ConsoleExperienceRules.avatarInitials(ownerName),
            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    }
}

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

/** Shared page chrome for Library, Multiplayer, Settings and Credits.
 * Rebuilt in Compose so our native emulation, controllers and network callbacks remain untouched.
 */
@Composable
private fun NeoPageHeader(eyebrow: String, title: String, caption: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(eyebrow.uppercase(), color = Mint, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        Text(title, color = Color.White, fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp)
        Text(caption, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun NeoBadge(label: String, highlighted: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(40.dp),
        color = if (highlighted) Color(0xFF17463F) else PanelRaised,
        modifier = Modifier.border(
            1.dp, if (highlighted) Mint.copy(alpha = 0.24f) else NeoOutline,
            RoundedCornerShape(40.dp))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = if (highlighted) Mint else Muted)
    }
}

/** Floating NeoStation-inspired glass chrome, independently implemented in Compose. */
@Composable
private fun NeoFloatingNavigation(screen: AppScreen, onSelect: (AppScreen) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(33.dp).clip(RoundedCornerShape(11.dp))
                .background(Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Gamepad, null, tint = Bg,
                    modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(7.dp))
            Text("MAIZO", fontWeight = FontWeight.Black,
                fontSize = 12.sp, color = Color.White, letterSpacing = 1.sp)
        }
        Surface(color = NeoGlass, shape = RoundedCornerShape(40.dp),
            modifier = Modifier.border(1.5.dp, Accent.copy(alpha = 0.43f),
                RoundedCornerShape(40.dp))) {
            Row(Modifier.padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(
                    Triple(AppScreen.LIBRARY, Icons.Default.Gamepad, "Biblioteca"),
                    Triple(AppScreen.MULTIPLAYER, Icons.Default.Groups, "Multiplayer"),
                    Triple(AppScreen.SETTINGS, Icons.Default.Tune, "Configurações"),
                    Triple(AppScreen.CREDITS, Icons.Default.Info, "Créditos")
                ).forEach { (target, icon, label) ->
                    val selected = screen == target
                    Box(Modifier.size(45.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (selected) Accent else Color.Transparent)
                        .padFocusRing().clickable { onSelect(target) },
                        contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = label,
                            tint = if (selected) Bg else Color(0xFFD3D8FC),
                            modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MaizoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, secondary = NeoPink, tertiary = Mint,
            background = Bg, surface = Panel, surfaceVariant = PanelRaised,
            outline = NeoOutline, onSurfaceVariant = Muted,
            onPrimary = Color.White, onBackground = Color.White, onSurface = Color.White
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
    playerProfile: PlayerProfile,
    playerAvatar: Bitmap?,
    onEditPlayerProfile: () -> Unit,
    recentRooms: List<RecentRoom>,
    onRejoinRecentRoom: (String, String) -> Unit,
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
    onJoinRemoteLan: (LanRoom) -> Unit,
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
    remoteHostView: RemoteHostView,
    remoteClientView: RemoteClientView?,
    onStartRemoteHost: () -> Unit,
    onStopRemoteHost: () -> Unit,
    onDecideRemote: (Long, Boolean) -> Unit,
    onKickRemote: (Int) -> Unit,
    onMoveRemote: (Int, Int) -> Unit,
    onExitRemote: () -> Unit,
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
    onReloadCovers: () -> Unit,
    onCoverNeeded: (String) -> Unit,
    onPickCustomCover: (String) -> Unit,
    onResetCustomCover: (String) -> Unit,
    hasQuickResume: (String) -> Boolean,
    onQuickResume: (String) -> Unit,
    quickResumeEnabled: Boolean,
    onQuickResumeEnabledChanged: (Boolean) -> Unit,
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
    var remotePanel by remember { mutableStateOf(false) }
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
    remoteHostView.pending.firstOrNull()?.takeIf { screen == AppScreen.GAME }?.let { request ->
        AlertDialog(
            onDismissRequest = {}, // ignoring the dialog is the requested autoaccept policy
            title = { Text("🎮 Novo controle LAN") },
            text = { Text(request.name + " quer entrar como Jogador " + request.slot +
                ".\nAceitação automática em " + request.secondsToAutoAccept + " s.") },
            confirmButton = { TextButton(onClick = { onDecideRemote(request.nonce, true) }) {
                Text("ACEITAR")
            } },
            dismissButton = { TextButton(onClick = { onDecideRemote(request.nonce, false) }) {
                Text("REJEITAR")
            } }
        )
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

    if (screen == AppScreen.GAME && remoteClientView != null) {
        RemoteControllerScreen(
            state = remoteClientView, settings = settings,
            onInputChanged = onInputChanged,
            onSettingsChanged = onSettingsChanged,
            onSelectTouchPreset = onSelectTouchPreset,
            onExit = onExitRemote,
            gameMenuOpen = gameMenuOpen,
            onGameMenuChanged = onGameMenuChanged
        )
        return
    }
    if (screen == AppScreen.GAME) {
        GameScreen(
            romName = currentRomName ?: "SNES",
            paused = paused,
            settings = settings,
            performanceText = performanceText,
            networkDebug = networkDebug,
            multiplayerActive = multiplayerActive,
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
            Dialog(
                onDismissRequest = { onGameMenuChanged(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val wideDialog = maxWidth > maxHeight
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(if (wideDialog) 0.84f else 0.95f)
                            .fillMaxHeight(if (wideDialog) 0.92f else 0.94f)
                            .dialogGamepadKeys { onGameMenuChanged(false) },
                        shape = RoundedCornerShape(23.dp),
                        color = Panel,
                        tonalElevation = 12.dp
                    ) {
                        // Height-bounded flex layout: switching Vídeo/Áudio/Controles
                        // always presents an independently scrollable settings pane.
                        Column(Modifier.fillMaxSize().padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("MAIZOSNES / PAINEL DO JOGO",
                                        color = Mint, fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                                    Text(
                                        File(currentRomName ?: "Jogo").nameWithoutExtension,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = Color.White, fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(onClick = { onGameMenuChanged(false) },
                                    modifier = Modifier.focusRequester(menuFocus)) {
                                    Text("✕ FECHAR", fontSize = 11.sp)
                                }
                            }
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { connectionOverlay = true },
                                    modifier = Modifier.weight(1f).padFocusRing(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) { Text("◉ STATUS / DEBUG", fontSize = 10.sp, maxLines = 1) }
                                OutlinedButton(
                                    onClick = {
                                        editingControls = !editingControls
                                        onGameMenuChanged(false)
                                    },
                                    modifier = Modifier.weight(1f).padFocusRing(),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(if (editingControls) "✓ SALVAR CONTROLES"
                                        else "🎮 EDITAR CONTROLES", fontSize = 10.sp, maxLines = 1)
                                }
                            }
                            // Fast presentation controls; never touch frame timing or netcode.
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { onGameMenuChanged(false) },
                                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Mint)) {
                                    Text("▶ CONTINUAR", fontSize = 10.sp, color = Bg,
                                        fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = {
                                    onSettingsChanged(settings.copy(aspect = AspectMode.CRT))
                                }, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp)) {
                                    Text("▣ PORTÁTIL 4:3", fontSize = 10.sp,
                                        color = if (settings.aspect == AspectMode.CRT) Mint else Muted)
                                }
                                OutlinedButton(onClick = {
                                    onSettingsChanged(settings.copy(aspect = AspectMode.PIXEL))
                                }, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp)) {
                                    Text("▦ PIXEL PERFECT", fontSize = 10.sp,
                                        color = if (settings.aspect == AspectMode.PIXEL) Mint else Muted)
                                }
                                OutlinedButton(onClick = {
                                    onSettingsChanged(settings.copy(
                                        touch = settings.touch.copy(enabled = !settings.touch.enabled)))
                                }, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp)) {
                                    Text(if (settings.touch.enabled) "◉ TOUCH LIGADO" else "○ TOUCH OCULTO",
                                        fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            OutlinedButton(
                                onClick = { remotePanel = !remotePanel },
                                modifier = Modifier.fillMaxWidth().padFocusRing(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(if (remotePanel) "‹ VOLTAR AOS AJUSTES"
                                    else "🎮 MULTIPLAYER • CONTROLES CONECTADOS",
                                    fontSize = 11.sp)
                            }
                            if (remotePanel) {
                                RemoteHostSettings(
                                    view = remoteHostView,
                                    multiplayerActive = multiplayerActive,
                                    onStart = onStartRemoteHost,
                                    onStop = onStopRemoteHost,
                                    onKick = onKickRemote,
                                    onMove = onMoveRemote,
                                    modifier = Modifier.weight(1f)
                                )
                            } else EmulatorSettings(
                                settings, coreOptions, true, onSettingsChanged,
                                onCoreOptionChanged, onSaveSlot, onLoadSlot,
                                onRestoreMultiplayerBackup, onExportState,
                                onImportState, onResetGame, cheats,
                                enabledCheatIds, cheatStatus, onToggleCheat,
                                onRefreshCheats, multiplayerActive,
                                onSelectTouchPreset, connectionMode,
                                onConnectionModeChanged, devUnlocked,
                                devShareEnabled, onDevUnlock, onDevShareChanged,
                                iceServerOverrides, onIceOverridesChanged,
                                iceTestStatus, onTestIce,
                                quickResumeEnabled = quickResumeEnabled,
                                onQuickResumeEnabledChanged = onQuickResumeEnabledChanged,
                                containerModifier = Modifier.weight(1f)
                            )
                        }
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

    // Avoid the status bar overlapping titles, as reported in the device screenshots.
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)
        .windowInsetsPadding(WindowInsets.statusBars)) {
    val compactConsole = maxWidth > maxHeight && maxHeight < 480.dp
    val wideLayout = maxWidth >= 720.dp || (maxWidth > maxHeight && maxWidth >= 560.dp)
    val screenStateHolder = rememberSaveableStateHolder()
    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0),
        topBar = { if (!wideLayout) NeoFloatingNavigation(screen, onSetScreen) }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)
            .windowInsetsPadding(WindowInsets.navigationBars)) {
            if (wideLayout) {
                Column(
                    Modifier.width(if (compactConsole) 176.dp else 232.dp).fillMaxHeight()
                        .background(Brush.verticalGradient(listOf(Panel, Bg)))
                        .border(1.dp, NeoOutline.copy(alpha = 0.30f))
                        .padding(if (compactConsole) 10.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactConsole) 6.dp else 12.dp)
                ) {
                    Box(Modifier.size(47.dp).clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(Accent, NeoPink))),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Gamepad, "MaizoSNES", tint = Color.White,
                            modifier = Modifier.size(26.dp))
                    }
                    Text("MAIZOSNES", color = Color.White, fontSize = 21.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                    if (!compactConsole)
                        Text("YOUR SUPER NINTENDO UNIVERSE", color = Muted, fontSize = 10.sp)
                    Spacer(Modifier.height(if (compactConsole) 2.dp else 13.dp))
                    Text("NAVEGAR", color = Mint, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, letterSpacing = 1.8.sp)
                    listOf(
                        Triple(AppScreen.LIBRARY, "▦", "Biblioteca"),
                        Triple(AppScreen.MULTIPLAYER, "◉", "Multiplayer"),
                        Triple(AppScreen.SETTINGS, "⚙", "Configurações"),
                        Triple(AppScreen.CREDITS, "ⓘ", "Créditos")
                    ).forEach { (destination, symbol, label) ->
                        val selected = screen == destination
                        Surface(
                            modifier = Modifier.fillMaxWidth().padFocusRing()
                                .clickable { onSetScreen(destination) },
                            color = if (selected) Accent else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(Modifier.padding(if (compactConsole) 8.dp else 14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(symbol, color = if (selected) Bg else Muted,
                                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.width(12.dp))
                                Text(label, color = if (selected) Bg else Muted,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!compactConsole) NeoBadge("SNES  •  Snes9x 1.63", highlighted = false)
                    Spacer(Modifier.height(5.dp))
                    Text("BY MAICON NASCIMENTO", color = Muted, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()
                .background(Brush.verticalGradient(
                    listOf(Bg, Color(0xFF191F2A), Bg)))) {
        when (screen) {
            AppScreen.LIBRARY -> screenStateHolder.SaveableStateProvider("library") {
                LibraryScreen(
                Modifier.fillMaxSize(), romNames, coverBitmaps,
                romHeaders, favoriteRoms, recentRoms, onToggleFavorite,
                onRemoveRom, onApplyPatchToRom,
                statusMessage, currentRomName, hasLoadedRom, onChooseAddRom,
                onScanRomFolder, onApplyRomPatch, onScanAllDevice,
                onOpenRom, onContinueGame, onRenameRom, onSetScreen,
                onReloadCovers, onCoverNeeded,
                onPickCustomCover, onResetCustomCover,
                hasQuickResume, onQuickResume
                )
            }
            AppScreen.MULTIPLAYER -> MultiplayerScreen(
                Modifier.fillMaxSize(), currentRomName, localRomHashPrefix, multiplayerReady,
                multiplayerStatus, localIpAddress, multiplayerActive, localMultiplayer,
                connectionMode, onConnectionModeChanged,
                netplayLatencyMode, onNetplayLatencyModeChanged,
                netcodeMode, onNetcodeModeChanged,
                backgroundNetplayEnabled, onBackgroundNetplayChanged,
                iceServerOverrides, onIceOverridesChanged, iceTestStatus, onTestIce,
                savedConnectionHost, savedConnectionPort, onSaveConnectionTarget,
                lanRooms, scanningLan, onScanLan, onJoinRemoteLan,
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
                playerProfile, playerAvatar, onEditPlayerProfile,
                recentRooms, onRejoinRecentRoom,
                onDebug = { connectionOverlay = true }
            )
            AppScreen.SETTINGS -> Column(Modifier.fillMaxSize().padding(horizontal = 19.dp, vertical = 16.dp)) {
                NeoPageHeader("SISTEMA  /  MAIZOSNES",
                    "Configurações", "Controle seu jogo do seu jeito.")
                OutlinedButton(onClick = onEditPlayerProfile,
                    modifier = Modifier.fillMaxWidth().padFocusRing()) {
                    PlayerAvatar(playerProfile.name, playerProfile.localId, playerAvatar, size = 32)
                    Spacer(Modifier.size(10.dp))
                    Text("EDITAR PERFIL • " + playerProfile.name, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { connectionOverlay = true },
                        modifier = Modifier.padFocusRing()) {
                        Text("◉  DIAGNÓSTICO DE REDE  ›",
                            fontSize = 11.sp, color = Accent,
                            fontWeight = FontWeight.Bold)
                    }
                }
                EmulatorSettings(settings, coreOptions, false, onSettingsChanged, onCoreOptionChanged, onSaveSlot, onLoadSlot, onRestoreMultiplayerBackup, onExportState, onImportState, onResetGame, cheats, enabledCheatIds, cheatStatus, onToggleCheat, onRefreshCheats, multiplayerActive,
                    onSelectTouchPreset, connectionMode, onConnectionModeChanged,
                    devUnlocked, devShareEnabled, onDevUnlock, onDevShareChanged,
                    iceServerOverrides, onIceOverridesChanged, iceTestStatus, onTestIce,
                    quickResumeEnabled = quickResumeEnabled,
                    onQuickResumeEnabledChanged = onQuickResumeEnabledChanged)
            }
            AppScreen.CREDITS -> CreditsScreen(Modifier.fillMaxSize(), onOpenInstagram)
            AppScreen.GAME -> Unit
        }
            }
        }
    }
    }
}

@Composable
private fun CreditsScreen(modifier: Modifier, onOpenInstagram: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        NeoPageHeader("SOBRE  /  CRÉDITOS", "Feito para jogar juntos",
            "Uma experiência SNES para Android, criada por entusiastas e construída sobre software aberto.")
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(
                        listOf(Color(0xFF302A58), Color(0xFF202F37), Panel)))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                NeoBadge("CRIADOR DO PROJETO")
                Text("Maicon Nascimento", fontSize = 27.sp,
                    fontWeight = FontWeight.Black, color = Color.White)
                Text("MaizoSNES • Desenvolvimento, integração Android e experiência multiplayer.",
                    color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                Button(onClick = onOpenInstagram,
                    modifier = Modifier.focusRequester(firstFocus).padFocusRing(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("INSTAGRAM  /  @maicon_nasciment0")
                }
            }
        }
        SettingSectionTitle("O que torna o MaizoSNES especial",
            "Emulação nativa, biblioteca integrada e sessões multiplayer LAN/online.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { NeoBadge("SNES 1.63") }
            Box(Modifier.weight(1f)) { NeoBadge("ANDROID") }
            Box(Modifier.weight(1f)) { NeoBadge("NETPLAY") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingSectionTitle("Tecnologias e agradecimentos",
                    "Cada projeto mantém seus próprios autores e respectivas licenças.")
                Text("Snes9x 1.63 • núcleo de emulação SNES e licença não comercial.\n" +
                    "Libretro • interfaces e bancos de dados de apoio.\n" +
                    "GekkoNet • rollback multiplayer.\n" +
                    "Jetpack Compose / Android Open Source Project • interface e sistema.\n" +
                    "NeoStation • referência de UX e organização de biblioteca, sem cópia de código Flutter ou recursos GPLv3.\n" +
                    "PPSSPP, CTR Native Android, VirtualGamePad-Mobile, ARMSX1 e Daijishou • referências históricas de UX.",
                    color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
            }
        }
        Text("Este aplicativo não inclui jogos, BIOS ou conteúdo proprietário. " +
            "Créditos e termos completos: THIRD_PARTY_NOTICES.md e LICENSES/ no GitHub. " +
            "Os projetos mencionados não endossam o MaizoSNES.",
            color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
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
    onSetScreen: (AppScreen) -> Unit,
    onReloadCovers: () -> Unit,
    onCoverNeeded: (String) -> Unit,
    onPickCustomCover: (String) -> Unit,
    onResetCustomCover: (String) -> Unit,
    hasQuickResume: (String) -> Boolean,
    onQuickResume: (String) -> Unit
) {
    var selectedRom by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var columns by rememberSaveable { mutableStateOf(0) }
    var viewMode by rememberSaveable { mutableStateOf("GRADE") }
    var sortMode by rememberSaveable { mutableStateOf("Título") }
    var sortExpanded by remember { mutableStateOf(false) }
    var collectionMode by rememberSaveable { mutableStateOf("Todos") }
    val featuredRom = ConsoleExperienceRules.featuredGame(currentRomName, recentRoms, romNames)
    val featuredTitle = featuredRom?.let { romHeaders[it]?.title ?: File(it).nameWithoutExtension }
        ?: "Sua coleção começa aqui"
    val homeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { homeFocus.requestFocus() }
    val visibleRoms = remember(romNames, romHeaders, libraryQuery, collectionMode, favoriteRoms, recentRoms, sortMode) {
        val filtered = romNames.filter { name ->
            val gameTitle = romHeaders[name]?.title ?: name
            (name.contains(libraryQuery.trim(), ignoreCase = true) ||
                gameTitle.contains(libraryQuery.trim(), ignoreCase = true)) &&
                (collectionMode != "Favoritos" || name in favoriteRoms) &&
                (collectionMode != "Recentes" || name in recentRoms)
        }
        LibraryUiRules.order(filtered, romHeaders.mapValues { it.value.title },
            recentRoms, sortMode)
    }
    BackHandler(enabled = selectedRom != null || renameTarget != null) {
        selectedRom = null
        renameTarget = null
    }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var viewMenuExpanded by remember { mutableStateOf(false) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var orderMenuExpanded by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier.background(Brush.verticalGradient(
        listOf(Color(0xFF101520), Color(0xFF191D31), Bg)))) {
        val effectiveColumns = LibraryUiRules.columns(maxWidth.value, columns)
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MAIZOSNES  /  BIBLIOTECA", color = Mint,
                        fontSize = 10.sp, letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold)
                    Text("Minha coleção", fontSize = 23.sp,
                        color = Color.White, fontWeight = FontWeight.Black)
                }
                NeoBadge("${visibleRoms.size} JOGOS", highlighted = visibleRoms.isNotEmpty())
            }

            // Fixed top toolbar. No oversized banner or filter rows in the collection.
            // All options remain accessible by touch, TalkBack and gamepad focus.
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box {
                    Surface(
                        color = Mint, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(47.dp).focusRequester(homeFocus)
                            .padFocusRing().clickable { addMenuExpanded = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, "Expandir opções de jogos",
                                tint = Bg, modifier = Modifier.size(27.dp))
                        }
                    }
                    DropdownMenu(expanded = addMenuExpanded,
                        onDismissRequest = { addMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Importar ROM / ZIP") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { addMenuExpanded = false; onChooseAddRom() })
                        DropdownMenuItem(text = { Text("Escolher pasta") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                            onClick = { addMenuExpanded = false; onScanRomFolder() })
                        DropdownMenuItem(text = { Text("Procurar ROMs no aparelho") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            onClick = { addMenuExpanded = false; onScanAllDevice() })
                        DropdownMenuItem(text = { Text("Buscar capas por título") },
                            leadingIcon = { Icon(Icons.Default.Image, null) },
                            onClick = { addMenuExpanded = false; onReloadCovers() })
                        if (hasLoadedRom) {
                            DropdownMenuItem(text = { Text("Continuar jogo atual") },
                                onClick = { addMenuExpanded = false; onContinueGame() })
                            DropdownMenuItem(text = { Text("Aplicar patch IPS/BPS") },
                                onClick = { addMenuExpanded = false; onApplyRomPatch() })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { searchExpanded = !searchExpanded },
                    modifier = Modifier.padFocusRing()) {
                    Icon(Icons.Default.Search, "Pesquisar jogos",
                        tint = if (searchExpanded) Mint else Color.White)
                }
                Box {
                    IconButton(onClick = { filterMenuExpanded = true },
                        modifier = Modifier.padFocusRing()) {
                        Icon(Icons.Default.FilterList, "Filtrar coleção", tint = Color.White)
                    }
                    DropdownMenu(expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false }) {
                        listOf("Todos", "Favoritos", "Recentes").forEach { option ->
                            DropdownMenuItem(
                                text = { Text((if (option == collectionMode) "✓ " else "") + option) },
                                onClick = {
                                    collectionMode = option
                                    filterMenuExpanded = false
                                })
                        }
                    }
                }
                Box {
                    IconButton(onClick = { viewMenuExpanded = true },
                        modifier = Modifier.padFocusRing()) {
                        Icon(Icons.Default.ViewModule, "Visualização da coleção",
                            tint = Color.White)
                    }
                    DropdownMenu(expanded = viewMenuExpanded,
                        onDismissRequest = { viewMenuExpanded = false }) {
                        listOf("GRADE" to "▦ Grade", "LISTA" to "☷ Lista",
                            "FILEIRA" to "↔ Fileira", "COLEÇÃO" to "◫ Coleção").forEach { opt ->
                            DropdownMenuItem(
                                text = { Text((if (viewMode == opt.first) "✓ " else "") + opt.second) },
                                onClick = { viewMode = opt.first; viewMenuExpanded = false })
                        }
                        if (viewMode == "GRADE") {
                            androidx.compose.material3.HorizontalDivider()
                            listOf(0, 2, 3, 4, 5).forEach { count ->
                                DropdownMenuItem(
                                    text = { Text((if (columns == count) "✓ " else "") +
                                        (if (count == 0) "Colunas automáticas" else "${count} colunas")) },
                                    onClick = { columns = count; viewMenuExpanded = false })
                            }
                        }
                    }
                }
                Box {
                    IconButton(onClick = { orderMenuExpanded = true },
                        modifier = Modifier.padFocusRing()) {
                        Icon(Icons.Default.Sort, "Ordenar jogos", tint = Color.White)
                    }
                    DropdownMenu(expanded = orderMenuExpanded,
                        onDismissRequest = { orderMenuExpanded = false }) {
                        listOf("Título", "Recentes", "Arquivo").forEach { option ->
                            DropdownMenuItem(
                                text = { Text((if (sortMode == option) "✓ " else "") + option) },
                                onClick = { sortMode = option; orderMenuExpanded = false })
                        }
                    }
                }
            }
            if (searchExpanded) {
                TextField(
                    value = libraryQuery, onValueChange = { libraryQuery = it.take(80) },
                    singleLine = true, label = { Text("Buscar na coleção") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = NeoGlass,
                        unfocusedContainerColor = NeoGlass,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent))
            }
            // One-tap local collection shelves (preserves the existing grid/list modes).
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Todos", "Favoritos", "Recentes").forEach { shelf ->
                    val selected = collectionMode == shelf && viewMode != "COLEÇÃO"
                    Surface(
                        color = if (selected) Accent else PanelRaised,
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier.padFocusRing().clickable {
                            collectionMode = shelf
                            if (viewMode == "COLEÇÃO") viewMode = "GRADE"
                        }
                    ) {
                        Text(shelf.uppercase(), color = if (selected) Bg else Muted,
                            fontWeight = FontWeight.Bold, fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                }
                Surface(
                    color = if (viewMode == "COLEÇÃO") Accent else PanelRaised,
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier.padFocusRing().clickable {
                        collectionMode = "Todos"
                        viewMode = "COLEÇÃO"
                    }
                ) {
                    Text("COLEÇÕES", color = if (viewMode == "COLEÇÃO") Bg else Muted,
                        fontWeight = FontWeight.Bold, fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
            if (collectionMode != "Todos" || libraryQuery.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Exibindo: " + collectionMode +
                        (if (libraryQuery.isNotBlank()) " • \"$libraryQuery\"" else ""),
                        color = Mint, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        collectionMode = "Todos"
                        libraryQuery = ""
                        searchExpanded = false
                    }) { Text("LIMPAR FILTROS", fontSize = 10.sp) }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (viewMode == "GRADE") effectiveColumns else 1),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 28.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
            // A responsive, low-cost hero reusing the already cached cover and ROM header.
            if (featuredRom != null && libraryQuery.isBlank() && collectionMode == "Todos") {
                item(key = "console_featured", span = { GridItemSpan(maxLineSpan) }) {
                    LaunchedEffect(featuredRom) {
                        if (coverBitmaps[featuredRom] == null) onCoverNeeded(featuredRom)
                    }
                    Surface(color = Panel, shape = RoundedCornerShape(19.dp),
                        modifier = Modifier.fillMaxWidth()
                            .border(1.dp, Accent.copy(alpha = 0.28f),
                                RoundedCornerShape(19.dp))) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            Box(Modifier.width(if (maxWidth > maxHeight) 112.dp else 86.dp)
                                .height(113.dp).clip(RoundedCornerShape(13.dp))
                                .background(Brush.linearGradient(listOf(Accent, NeoPink))),
                                contentAlignment = Alignment.Center) {
                                val cover = coverBitmaps[featuredRom]
                                if (cover != null) Image(cover.asImageBitmap(),
                                    "Capa em destaque", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                else Icon(Icons.Default.Gamepad, "Jogo em destaque",
                                    tint = Color.White, modifier = Modifier.size(45.dp))
                            }
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("★  EM DESTAQUE  •  SUPER NINTENDO", color = Mint,
                                    fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                Text(featuredTitle, color = Color.White, fontSize = 17.sp,
                                    lineHeight = 20.sp, fontWeight = FontWeight.Black,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text((romHeaders[featuredRom]?.region ?: "SNES") +
                                    (if (featuredRom in favoriteRoms) "  •  ★ Favorito"
                                     else "  •  Seu próximo jogo"),
                                    color = Muted, fontSize = 10.sp, maxLines = 1)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = {
                                        if (currentRomName == featuredRom && hasLoadedRom)
                                            onContinueGame()
                                        else onOpenRom(featuredRom)
                                    }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Mint)) {
                                        Text("▶ JOGAR", fontSize = 10.sp,
                                            color = Bg, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(onClick = { selectedRom = featuredRom },
                                        contentPadding = PaddingValues(horizontal = 4.dp)) {
                                        Text("DETALHES ›", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (visibleRoms.isEmpty()) {
                item(key = "empty_library", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Gamepad, null, tint = Accent,
                            modifier = Modifier.size(48.dp))
                        Text(if (romNames.isEmpty()) "Importe seu primeiro jogo SNES"
                            else "Nenhum jogo encontrado", color = Muted)
                        if (romNames.isEmpty()) Button(onClick = onChooseAddRom) {
                            Text("+ ADICIONAR JOGO")
                        }
                    }
                }
            } else when (viewMode) {
                "LISTA" -> gridItems(visibleRoms,
                    span = { GridItemSpan(maxLineSpan) }, key = { "list_" + it }) { name ->
                    RomCard(name, romHeaders[name]?.title ?: File(name).nameWithoutExtension,
                        currentRomName == name, coverBitmaps[name],
                        { selectedRom = name }, { renameTarget = name },
                        compact = true, listStyle = true, onCoverNeeded = onCoverNeeded)
                }
                "FILEIRA" -> item(key = "game_row",
                    span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(visibleRoms, key = { "row_" + it }) { name ->
                            Box(Modifier.width(175.dp)) {
                                RomCard(name, romHeaders[name]?.title ?: File(name).nameWithoutExtension,
                                    currentRomName == name, coverBitmaps[name],
                                    { selectedRom = name }, { renameTarget = name }, onCoverNeeded = onCoverNeeded)
                            }
                        }
                    }
                }
                "COLEÇÃO" -> {
                    val sections = listOf(
                        "★ FAVORITOS" to visibleRoms.filter { it in favoriteRoms },
                        "↺ RECENTES" to recentRoms.filter { it in visibleRoms },
                        "TODOS OS JOGOS" to visibleRoms
                    )
                    sections.forEachIndexed { index, entry ->
                        if (entry.second.isNotEmpty()) {
                            item(key = "section_$index",
                                span = { GridItemSpan(maxLineSpan) }) {
                                Text(entry.first, color = Mint, fontSize = 12.sp,
                                    fontWeight = FontWeight.Black)
                            }
                            item(key = "section_row_$index",
                                span = { GridItemSpan(maxLineSpan) }) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    items(entry.second.distinct(), key = { "${index}_" + it }) { name ->
                                        Box(Modifier.width(165.dp)) {
                                            RomCard(name, romHeaders[name]?.title
                                                    ?: File(name).nameWithoutExtension,
                                                currentRomName == name, coverBitmaps[name],
                                                { selectedRom = name }, { renameTarget = name }, onCoverNeeded = onCoverNeeded)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> gridItems(visibleRoms, key = { "grid_" + it }) { name ->
                    RomCard(name, romHeaders[name]?.title ?: File(name).nameWithoutExtension,
                        currentRomName == name, coverBitmaps[name],
                        { selectedRom = name }, { renameTarget = name },
                        compact = columns >= 3, onCoverNeeded = onCoverNeeded)
                }
            }
            item(key = "library_message", span = { GridItemSpan(maxLineSpan) }) {
                Text(statusMessage, color = Muted, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }

            }
        }
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
                    // Hero art only opens AFTER the user selects a game.
                    // The collection itself is immediately browsable, without a giant card.
                    Box(
                        Modifier.fillMaxWidth().height(230.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(
                                Color(0xFF27386A), Color(0xFF18453F), Bg)))
                    ) {
                        val cover = coverBitmaps[name]
                        if (cover != null) Image(
                            cover.asImageBitmap(), "Arte de " + name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = .65f)
                        else Icon(Icons.Default.Gamepad, "Sem capa",
                            modifier = Modifier.align(Alignment.Center).size(86.dp),
                            tint = Accent)
                        Box(Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent,
                                Color(0xDD0E1423), Color(0xFA0E1423)))))
                        Column(Modifier.align(Alignment.BottomStart).padding(17.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("SUPER NINTENDO", color = Mint,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(info?.title ?: File(name).nameWithoutExtension,
                                color = Color.White, fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                lineHeight = 25.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        NeoBadge("SUPER NINTENDO")
                        NeoBadge("REGIÃO: " + (info?.region ?: "DESCONHECIDA"),
                            highlighted = false)
                    }
                    info?.warning?.let { Text(it, color = Color(0xFFFFBD85), fontSize = 12.sp) }
                    Button(onClick = { selectedRom = null; onOpenRom(name) },
                        modifier = Modifier.fillMaxWidth().focusRequester(detailFocus)) { Text("▶ JOGAR") }
                    if (hasQuickResume(name)) {
                        OutlinedButton(
                            onClick = { selectedRom = null; onQuickResume(name) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = Mint)
                        ) { Text("↺ RETOMAR ÚLTIMA PARTIDA") }
                    }
                    OutlinedButton(
                        onClick = { selectedRom = null; onPickCustomCover(name) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("◈ ESCOLHER CAPA / FUNDO DA GALERIA") }
                    TextButton(
                        onClick = { selectedRom = null; onResetCustomCover(name) }
                    ) { Text("↺ RESTAURAR CAPA AUTOMÁTICA", fontSize = 11.sp) }
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
    compact: Boolean = false,
    listStyle: Boolean = false,
    onCoverNeeded: (String) -> Unit = {}
) {
    LaunchedEffect(fileName) { if (cover == null) onCoverNeeded(fileName) }
    Card(
        modifier = Modifier.fillMaxWidth().padFocusRing()
            .border(1.dp, Accent.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NeoGlass)
    ) {
        if (listStyle) {
            Row(
                Modifier.fillMaxWidth().padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Box(Modifier.width(66.dp).height(78.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(
                        listOf(Color(0xFF40365F), Color(0xFF194238)))),
                    contentAlignment = Alignment.Center) {
                    if (cover != null) Image(cover.asImageBitmap(),
                        "Capa de $displayTitle", modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit)
                    else Icon(Icons.Default.Gamepad, "Jogo SNES", tint = Accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(displayTitle, color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        maxLines = 2, lineHeight = 18.sp)
                    Text(if (current) "● EM MEMÓRIA" else "SUPER NINTENDO",
                        color = if (current) Mint else Muted, fontSize = 10.sp)
                    Text("A  •  DETALHES", color = Muted, fontSize = 9.sp)
                }
                TextButton(onClick = onRename) { Text("EDITAR", fontSize = 10.sp) }
                Text("›", color = Mint, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }
        } else {
            Column {
                Box(
                    Modifier.fillMaxWidth().height(if (compact) 116.dp else 174.dp)
                        .background(Brush.linearGradient(
                            listOf(Color(0xFF40365F), Color(0xFF1E3444),
                                Color(0xFF144339)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (cover != null) {
                        Image(cover.asImageBitmap(), "Capa de $displayTitle",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Gamepad, "SNES", tint = Accent,
                                modifier = Modifier.size(if (compact) 40.dp else 55.dp))
                            Text("SUPER NINTENDO", color = Muted, fontSize = 8.sp,
                                letterSpacing = 1.1.sp)
                        }
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xE51D232A)
                    ) {
                        Text(if (current) "● ATIVO" else "SNES",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            color = if (current) Mint else Color.White,
                            fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(displayTitle, fontSize = if (compact) 11.sp else 14.sp,
                        color = Color.White, fontWeight = FontWeight.Bold,
                        maxLines = 2, lineHeight = 17.sp)
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("A  •  ABRIR", color = Mint, fontWeight = FontWeight.Bold,
                            fontSize = 9.sp)
                        if (!compact) TextButton(onClick = onRename,
                            contentPadding = PaddingValues(0.dp)) {
                            Text("EDITAR", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
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
    onJoinRemoteLan: (LanRoom) -> Unit,
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
    playerProfile: PlayerProfile,
    playerAvatar: Bitmap?,
    onEditPlayerProfile: () -> Unit,
    recentRooms: List<RecentRoom>,
    onRejoinRecentRoom: (String, String) -> Unit,
    onDebug: () -> Unit
) {
    var advancedNetwork by remember { mutableStateOf(false) }
    val compactLobby = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    var roomSearch by rememberSaveable { mutableStateOf("") }
    var compatibleOnly by rememberSaveable { mutableStateOf(false) }
    var fastestFirst by rememberSaveable { mutableStateOf(true) }
    val visibleAutoRooms = autoRooms.filter { room ->
        val searchable = room.game + " " + room.ownerName + " " + room.region
        searchable.contains(roomSearch.trim(), ignoreCase = true) &&
            (!compatibleOnly || localRomHashPrefix.isNotBlank() &&
                room.romHash == localRomHashPrefix)
    }.let { list -> if (fastestFirst) list.sortedBy { it.latencyMs } else list.sortedBy { it.game.lowercase() } }
    val visibleRelayRooms = relayRooms.filter { room ->
        (room.game + " " + room.ownerName).contains(roomSearch.trim(), ignoreCase = true) &&
            (!compatibleOnly || localRomHashPrefix.isNotBlank() && room.romHash == localRomHashPrefix)
    }
    val visibleLanRooms = lanRooms.filter { room ->
        (room.game + " " + room.ownerName).contains(roomSearch.trim(), ignoreCase = true) &&
            (!compatibleOnly || room.acceptsRemotePad ||
                localRomHashPrefix.isNotBlank() && room.romHash == localRomHashPrefix)
    }
    var showNetcodeDetails by remember { mutableStateOf(false) }
    var selectedLanRoom by remember { mutableStateOf<LanRoom?>(null) }
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
    selectedLanRoom?.let { room ->
        val canPlay = room.acceptsGameClient
        val canControl = room.acceptsRemotePad
        val sameRom = ready && room.romHash.isNotBlank() &&
            room.romHash == localRomHashPrefix
        AlertDialog(
            onDismissRequest = { selectedLanRoom = null },
            title = { Text("Como deseja participar?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(room.game, color = Mint, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            // Choosing game emulation explicitly permits asking the
                            // host for an authorized ROM, still subject to the host
                            // consent dialog and client accept/hash verification.
                            if (!sameRom && !receiveRomAllowed)
                                onReceiveRomAllowedChanged(true)
                            selectedLanRoom = null
                            onJoin(room.host, room.port)
                        },
                        enabled = canPlay && !active,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🖥️ Jogar no dispositivo") }
                    OutlinedButton(
                        onClick = {
                            selectedLanRoom = null
                            onJoinRemoteLan(room)
                        },
                        enabled = canControl && !active,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🎮 Usar como controle virtual") }
                    Text(
                        when {
                            canPlay && canControl ->
                                "As duas opções estão disponíveis: jogar localmente com " +
                                    "ROM autorizada ou controlar o jogo no anfitrião."
                            canPlay -> "Multiplayer GekkoNet/Lockstep; ROM compatível ou oferta autorizada."
                            else -> "Sala antiga exclusiva de controle: anfitrião precisa atualizar o APK."
                        }, color = Muted, fontSize = 11.sp
                    )
                    if (canPlay && !sameRom) Text(
                        "Ao escolher Jogar, o cliente pode solicitar a ROM. " +
                            "O anfitrião e o cliente precisam autorizar o envio; " +
                            "nunca é automático sem consentimento.",
                        color = Color(0xFFFFC06A), fontSize = 11.sp
                    )
                }
            },
            confirmButton = { TextButton(onClick = { selectedLanRoom = null }) {
                Text("CANCELAR")
            } }
        )
    }
    Column(modifier.verticalScroll(rememberScrollState())
        .padding(if (compactLobby) 11.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactLobby) 8.dp else 12.dp)) {
        if (compactLobby) Row(Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("◉  CONSOLE MULTIPLAYER", color = Mint,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Jogue com seus amigos", color = Color.White,
                    fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
            NeoBadge(if (active) "● SESSÃO ATIVA" else "LAN + ONLINE", active)
        }
        else NeoPageHeader("SOCIAL  /  MULTIPLAYER",
            "Multiplayer", "Seus amigos e jogos, na mesma tela.")
        Surface(color = Panel, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().clickable { onEditPlayerProfile() }) {
            Row(Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerAvatar(playerProfile.name, playerProfile.localId, playerAvatar, size = 48)
                Column(Modifier.weight(1f)) {
                    Text(playerProfile.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Seu perfil • visível nas salas criadas", color = Muted, fontSize = 11.sp)
                }
                Text("EDITAR ›", color = Mint, fontSize = 11.sp)
            }
        }
        if (!active && recentRooms.isNotEmpty() &&
            (connectionMode == NetworkMode.AUTO || connectionMode == NetworkMode.RELAY)) {
            Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("SALAS VISITADAS", color = Mint, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                    recentRooms.take(3).forEach { room ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ConsoleRoomAvatar(room.ownerName)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(room.game, fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp, maxLines = 1)
                                Text(room.ownerName.ifBlank { "Anfitrião desconhecido" },
                                    color = Muted, fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                onRejoinRecentRoom(room.id, room.endpoint)
                            }) { Text("VERIFICAR / ENTRAR", fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
        if (connectionMode != NetworkMode.LOCAL) {
            TextField(value = roomSearch, onValueChange = { roomSearch = it.take(48) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Pesquisar salas por jogo, anfitrião ou região") })
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { compatibleOnly = !compatibleOnly }) {
                    Text(if (compatibleOnly) "✓ ROM compatível" else "Todas as ROMs",
                        fontSize = 11.sp, color = Mint)
                }
                TextButton(onClick = { fastestFirst = !fastestFirst }) {
                    Text(if (fastestFirst) "Menor resposta" else "Por jogo",
                        fontSize = 11.sp, color = Mint)
                }
            }
        }
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDebug, modifier = Modifier.padFocusRing()) {
                Text("◉  STATUS / LOGS  ›", color = Accent,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Accent, NeoPink))),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Groups, "Multiplayer", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (active) "Sessão em andamento" else "Pronto para conectar",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(romName ?: "Selecione um jogo na Biblioteca",
                        color = Muted, fontSize = 11.sp, maxLines = 2)
                }
                NeoBadge(if (active) "● CONECTADO" else "○ AGUARDANDO",
                    highlighted = active)
            }
        }
        if (!ready) Surface(color = NeoGlass, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.border(1.dp, NeoOutline.copy(alpha = 0.65f),
                RoundedCornerShape(14.dp))) {
            Text("SEM ROM: para criar uma sala, importe e abra um jogo. " +
                "Para entrar como convidado, escolha uma sala TLS e aceite a " +
                "oferta autorizada da ROM (ative Receber ROM se necessário).",
                modifier = Modifier.fillMaxWidth().padding(13.dp),
                color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("1 • ESCOLHA ONDE JOGAR", color = Mint, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold)
                Text("Toque em uma opção. Não altera sua biblioteca nem os saves.",
                    color = Muted, fontSize = 11.sp)
                val displayModes = NetworkMode.values().filter { choice ->
                    advancedNetwork || choice == NetworkMode.AUTO ||
                        choice == NetworkMode.LAN_AUTO ||
                        choice == NetworkMode.LOCAL || choice == connectionMode
                }
                if (compactLobby) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        displayModes.forEach { choice ->
                            Surface(modifier = Modifier.padFocusRing()
                                .clickable(enabled = !active) { onConnectionModeChanged(choice) },
                                shape = RoundedCornerShape(13.dp),
                                color = if (choice == connectionMode)
                                    Accent.copy(alpha = 0.32f) else PanelRaised) {
                                Text((if (choice == connectionMode) "✓ " else "") +
                                    choice.label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp))
                            }
                        }
                    }
                } else displayModes.forEachIndexed { index, choice ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstModeFocus) else Modifier)
                            .padFocusRing().clickable(enabled = !active) {
                            onConnectionModeChanged(choice)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (choice == connectionMode)
                                Accent.copy(alpha = 0.28f) else PanelRaised)
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
        if (connectionMode != NetworkMode.LOCAL) {
            var showPermissions by remember { mutableStateOf(false) }
            Surface(color = NeoGlass, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padFocusRing()
                    .clickable { showPermissions = !showPermissions }) {
                Row(Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ROMs entre amigos", fontWeight = FontWeight.Bold,
                            fontSize = 14.sp)
                        Text("Compartilhar/receber arquivos com autorização",
                            color = Muted, fontSize = 11.sp)
                    }
                    Text(if (showPermissions) "▲" else "▼",
                        color = Accent, fontSize = 16.sp)
                }
            }
            if (showPermissions) {
                ToggleSettingRow("Aceitar ROM do anfitrião",
                    "Você confirma cada oferta antes do download; verificação SHA-256.",
                    receiveRomAllowed) {
                    if (!active) onReceiveRomAllowedChanged(it)
                }
                ToggleSettingRow("Oferecer ROM autorizada",
                    "O anfitrião declara os direitos de redistribuição a cada sala.",
                    shareRomAllowed) {
                    if (!active) onShareRomAllowedChanged(it)
                }
            }
        }
        if (connectionMode == NetworkMode.AUTO) Card(
            colors = CardDefaults.cardColors(containerColor = NeoGlass),
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
                Text("Encontre salas sem digitar IP. Para criar uma sala, abra " +
                    "uma ROM; para entrar sem ROM, use uma sala TLS com oferta autorizada.",
                    color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                Button(onClick = onQuickMatch,
                    // quickMatch() already supports joining an existing TLS/WSS
                    // room without a local ROM. Do not disable that path in UI.
                    enabled = !active && (ready || (receiveRomAllowed &&
                        autoRooms.any { room ->
                            (room.tls || room.wss) &&
                                (!room.cheats || onlineCheatsOptIn)
                        })),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint)) {
                    Text("JOGO RÁPIDO • ENTRAR OU CRIAR", color = Color(0xFF111827),
                        fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
                Text("Jogo rápido busca pessoas e salas disponíveis. " +
                    "Se não encontrar, tenta criar uma sala.",
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
                if (visibleAutoRooms.isEmpty()) {
                    Text("Nenhuma sala online encontrada com os filtros atuais.",
                        color = Muted, fontSize = 12.sp)
                } else {
                    visibleAutoRooms.forEach { room ->
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
                                ConsoleRoomAvatar(room.ownerName)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(room.game, fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold)
                                    Text("👤 " + room.ownerName.ifBlank { "Anfitrião não informado" } +
                                        " • 1/2 • Aguardando", color = Mint, fontSize = 11.sp)
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

        if (connectionMode == NetworkMode.LOCAL || advancedNetwork) Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DOIS JOGADORES • MESMO CELULAR", fontWeight = FontWeight.Bold)
                Text("Use touch e controle físico ou conecte dois controles. " +
                    "O jogo precisa oferecer suporte ao segundo jogador.",
                    color = Muted, fontSize = 12.sp)
                ToggleSettingRow("Ativar porta 2", "Funciona sem Wi-Fi e sem internet.", localMultiplayer, onLocalMode)
                ToggleSettingRow(
                    "Autorizar cheats sincronizados no multiplayer",
                    "Opcional para partidas em que os dois jogadores concordam. " +
                        "Os códigos são iguais nos dois aparelhos e não podem mudar " +
                        "durante a partida.",
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
                if ((connectionMode == NetworkMode.AUTO ||
                        connectionMode == NetworkMode.LAN_AUTO) && !active) {
                    OutlinedButton(onClick = { onHost(55435) },
                        enabled = ready, modifier = Modifier.fillMaxWidth()) {
                        Text("CRIAR SALA LAN SEM INTERNET")
                    }
                }
                if (visibleLanRooms.isEmpty()) {
                    Text("A busca começa automaticamente. Se o Wi-Fi bloquear descoberta, use IP/porta abaixo.",
                        color = Muted, fontSize = 11.sp)
                } else visibleLanRooms.forEach { room ->
                    val hashCompatible = ready && (room.romHash.isBlank() ||
                        room.romHash == localRomHashPrefix)
                    val cheatsAllowed = !room.cheats || onlineCheatsOptIn
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PanelRaised),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padFocusRing().clickable(
                            enabled = !active && (room.acceptsRemotePad ||
                                room.acceptsGameClient && cheatsAllowed)
                        ) {
                            remoteHost = room.host
                            portText = room.port.toString()
                            selectedLanRoom = room
                        }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ConsoleRoomAvatar(room.ownerName)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(room.game, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("👤 " + room.ownerName.ifBlank { "Anfitrião não informado" } +
                                    " • " + room.netcode.ifBlank { "Modo ao conectar" },
                                    color = Mint, fontSize = 10.sp)
                                if (connectionMode == NetworkMode.AUTO)
                                    Text("Conexão local • Wi-Fi / LAN", color = Muted, fontSize = 11.sp)
                                else Text("${room.host}:${room.port}", color = Muted, fontSize = 11.sp)
                                Text(when {
                                    room.netcode == "Jogar ou controle" ->
                                        "JOGAR NO DISPOSITIVO OU CONTROLE REMOTO • P2–P4"
                                    room.netcode == "Controle remoto" ->
                                        "CONTROLE SEM FIO • SEM ROM E SEM INTERNET"
                                    !hashCompatible && receiveRomAllowed ->
                                        "ROM DIFERENTE — entre para solicitar transferência autorizada"
                                    !hashCompatible -> "ROM/MOD DIFERENTE — habilite receber ROM"
                                    !cheatsAllowed -> "CHEATS — autorize nas configurações"
                                    room.cheats -> "CHEATS SINCRONIZADOS"
                                    room.romHash.isNotBlank() -> "ROM COMPATÍVEL POR SHA-256"
                                    else -> "Compatibilidade conferida ao entrar"
                                }, color = if (room.acceptsRemotePad ||
                                    ((hashCompatible || receiveRomAllowed) && cheatsAllowed)) Mint
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
                Text("${visibleRelayRooms.size} sala(s) encontrada(s)", color = Mint, fontSize = 12.sp)
                if (visibleRelayRooms.isEmpty()) {
                    Text("Sem salas listadas. Abra uma ROM e crie a primeira sala.",
                        color = Muted, fontSize = 12.sp)
                }
                visibleRelayRooms.forEach { room ->
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
                            ConsoleRoomAvatar(room.ownerName)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(room.game, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("👤 " + room.ownerName.ifBlank { "Anfitrião não informado" } +
                                    " • 1/2 • Aguardando", color = Mint, fontSize = 10.sp)
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
        if (connectionMode != NetworkMode.LOCAL) {
            Surface(color = NeoGlass, shape = RoundedCornerShape(17.dp),
                modifier = Modifier.fillMaxWidth().padFocusRing()
                    .border(1.dp, NeoOutline.copy(alpha = 0.75f),
                        RoundedCornerShape(17.dp))
                    .clickable { showNetcodeDetails = !showNetcodeDetails }) {
                Row(Modifier.fillMaxWidth().padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MOTOR  •  " + netcodeMode.label,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("GekkoNet, latência, ICE/TURN e segundo plano",
                            color = Muted, fontSize = 10.sp)
                    }
                    Text(if (showNetcodeDetails) "▲ FECHAR" else "▼ AJUSTES",
                        color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (connectionMode != NetworkMode.LOCAL && showNetcodeDetails) Card(
            colors = CardDefaults.cardColors(containerColor = NeoGlass),
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
                Text("AJUSTE DE ATRASO (AVANÇADO)",
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

        Text("MZNP v7: LAN e IP manual continuam disponíveis. " +
            "Transferência entre redes exige TLS autenticado, confirmação do host " +
            "e aceite do cliente. O relay não fornece criptografia ponta a ponta.",
            color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
}


/** The client displays the existing full touch layout without any SNES video or ROM. */
@Composable
private fun RemoteControllerScreen(
    state: RemoteClientView,
    settings: AppSettings,
    onInputChanged: (Int, Boolean) -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onSelectTouchPreset: (TouchPreset) -> Unit,
    onExit: () -> Unit,
    gameMenuOpen: Boolean,
    onGameMenuChanged: (Boolean) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var individual by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF111228), Color(0xFF231C44), Bg)))) {
        Column(Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Gamepad, contentDescription = null,
                tint = Accent.copy(alpha = 0.6f), modifier = Modifier.size(60.dp))
            Text(state.title, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                color = Color.White.copy(alpha = .62f), maxLines = 1)
            Text(state.name + " • Jogador " + state.slot, fontSize = 12.sp, color = Muted)
            Text(state.status + (state.rttMs?.let { " • RTT ${it} ms" } ?: ""),
                fontSize = 10.sp, color = if (state.connected) Mint else Color(0xFFFFC06A))
        }
        if (settings.touch.enabled) {
            TouchOverlay(settings.touch, onInputChanged, editing, individual) { updated ->
                onSettingsChanged(settings.copy(touch = updated))
            }
        }
        if (editing) Surface(Modifier.align(Alignment.BottomCenter).padding(9.dp),
            color = Panel.copy(alpha = .92f), shape = RoundedCornerShape(14.dp)) {
            Text("ARRASTE OS CONTROLES • SALVAR", Modifier.padding(10.dp), fontSize = 11.sp)
        }
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp)
                .background(Panel.copy(alpha = .84f), RoundedCornerShape(18.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("‹ SAIR", fontSize = 11.sp) }
            TextButton(onClick = { editing = !editing }) {
                Text(if (editing) "✓ SALVAR" else "EDITAR", fontSize = 11.sp)
            }
            if (editing) TextButton(onClick = { individual = !individual }) {
                Text(if (individual) "BOTÕES" else "GRUPOS", fontSize = 10.sp)
            }
            IconButton(onClick = { optionsOpen = true }) {
                Icon(Icons.Default.Settings, "Configurar controles")
            }
        }
    }
    if (optionsOpen || gameMenuOpen) {
        AlertDialog(onDismissRequest = {
            optionsOpen = false
            onGameMenuChanged(false)
        }, title = { Text("🎮 Configurações do controle") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Layouts do MaizoSNES", color = Mint)
                TouchPreset.values().forEach { preset ->
                    TextButton(onClick = {
                        onSelectTouchPreset(preset)
                        optionsOpen = false
                        onGameMenuChanged(false)
                    }) { Text(preset.name.replace('_', ' '),
                        color = if (preset == settings.touch.preset) Mint else Color.White) }
                }
                Text("Tamanho dos botões")
                Slider(value = settings.touch.scale.coerceIn(.65f, 1.45f),
                    onValueChange = {
                        onSettingsChanged(settings.copy(touch = settings.touch.copy(scale = it)))
                    }, valueRange = .65f..1.45f)
                Text("Transparência")
                Slider(value = settings.touch.opacity.coerceIn(.15f, 1f),
                    onValueChange = {
                        onSettingsChanged(settings.copy(touch = settings.touch.copy(opacity = it)))
                    }, valueRange = .15f..1f)
                ToggleSettingRow("Controles na tela", "Gamepad USB/Bluetooth também funciona",
                    settings.touch.enabled, { onSettingsChanged(settings.copy(
                        touch = settings.touch.copy(enabled = it))) })
            }
        }, confirmButton = { TextButton(onClick = {
            optionsOpen = false
            onGameMenuChanged(false)
        }) { Text("CONCLUÍDO") } })
    }
}

@Composable
private fun RemoteHostSettings(
    view: RemoteHostView,
    multiplayerActive: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onKick: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("MULTIPLAYER / CONTROLES CONECTADOS",
            fontWeight = FontWeight.ExtraBold, color = Mint)
        Text("A sala local aceita as duas opções: Jogar no dispositivo com " +
            "transferência de ROM autorizada ou usar controle sem fio." +
            " A opção de controle vem ATIVADA por padrão.",
            color = Muted, fontSize = 12.sp)
        if (!view.enabled) {
            Button(onClick = onStart, enabled = !multiplayerActive,
                modifier = Modifier.fillMaxWidth()) {
                Text("ATIVAR CONTROLE REMOTO LAN")
            }
            if (multiplayerActive) Text("Finalize a sessão GekkoNet antes " +
                "de ativar o controle remoto.", color = Muted, fontSize = 11.sp)
        } else {
            Text("● Sala LAN pronta • TCP 55435 + UDP 55437", color = Mint)
            Text("P1 • Anfitrião (controle local)", color = Color.White)
            for (slot in 2..4) {
                val p = view.peers.firstOrNull { it.slot == slot }
                Surface(color = PanelRaised, shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(9.dp)) {
                        Text("P$slot • " + (p?.name ?: "Disponível"),
                            color = if (p != null) Mint else Muted)
                        if (p != null) {
                            Text(p.address + " • controle UDP",
                                fontSize = 10.sp, color = Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                OutlinedButton(onClick = { onKick(slot) }) {
                                    Text("DESCONECTAR", fontSize = 9.sp)
                                }
                                for (dest in 2..4) if (dest != slot) {
                                    TextButton(onClick = { onMove(slot, dest) }) {
                                        Text("→ P$dest", fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            view.pending.forEach { p ->
                Text("⏳ " + p.name + " → P" + p.slot +
                    " • aceitação automática em " + p.secondsToAutoAccept + " s",
                    color = Color(0xFFFFC06A), fontSize = 11.sp)
            }
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("DESATIVAR CONTROLE REMOTO LAN")
            }
        }
        Text("O multitap do Snes9x envia P2, P3 e P4 aos jogos que aceitam " +
            "mais de dois jogadores. Para entrar como jogador com imagem, " +
            "o GekkoNet assume a sessão e desconecta os controles UDP ativos.",
            color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun GameScreen(
    romName: String,
    paused: Boolean,
    settings: AppSettings,
    performanceText: String,
    networkDebug: NetworkDebugState,
    multiplayerActive: Boolean,
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
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val portableLandscape = maxWidth > maxHeight && settings.touch.enabled &&
            (settings.aspect == AspectMode.CRT || settings.aspect == AspectMode.NATIVE ||
                settings.aspect == AspectMode.PIXEL || settings.aspect == AspectMode.CLASSIC)
        VideoSurface(settings, onSurfaceChanged, onGpuReady)
        // Decorative handheld side rails: no touch handlers or video surface changes.
        if (portableLandscape) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight()
                .fillMaxWidth(0.095f).background(Color(0xB2161C2D)))
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                .fillMaxWidth(0.095f).background(Color(0xB2161C2D)))
        }
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
                    .background(Color(0xC21D232A))
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
                    .background(Color(0xE51D232A))
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
                color = Panel.copy(alpha = 0.93f), shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (editIndividual)
                    "ARRASTE OS BOTÕES INDIVIDUALMENTE • SALVAR"
                    else "ARRASTE OS GRUPOS DE CONTROLE • SALVAR",
                    modifier = Modifier.padding(12.dp), color = Mint,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (multiplayerActive && !editingControls) {
            val latency = networkDebug.rttAvg
            val unstable = (latency != null && latency > 180) ||
                (networkDebug.jitterMs ?: 0.0) > 35
            val fair = (latency != null && latency > 90) ||
                (networkDebug.jitterMs ?: 0.0) > 18
            val signalColor = if (unstable) Color(0xFFFF776D)
                else if (fair) Color(0xFFFFCA6E) else Mint
            Surface(modifier = Modifier.align(Alignment.TopCenter).padding(6.dp),
                color = Color(0xD91B2333), shape = RoundedCornerShape(14.dp)) {
                Text("●  " + (latency?.let {
                    String.format(java.util.Locale.US, "%.0f ms", it)
                } ?: "Conectando") + (if (unstable) " • REDE INSTÁVEL" else ""),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    color = signalColor, fontSize = 10.sp)
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
                color = Panel.copy(alpha = 0.93f), shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(horizontal = 25.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ⅱ  PAUSADO", color = Mint,
                        fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
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

/**
 * Unified responsive touch layout for EVERY existing preset (including VGP).
 * SNES mappings are unchanged. All face, shoulder and menu keys can be
 * dragged individually; D-pad and face cluster can also be dragged as groups.
 * The old VGP top-right oversized cluster is replaced by bottom-corner pads.
 */
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
        val current by rememberUpdatedState(layout)
        val emit by rememberUpdatedState(onLayoutChanged)
        val landscape = maxWidth > maxHeight
        val base = minOf(maxWidth.value, maxHeight.value).coerceAtLeast(240f)
        fun bitScale(bit: Int): Float = layout.buttonStyles[bit]?.sanitized()?.scale ?: 1f
        fun bitOpacity(bit: Int): Float =
            (layout.opacity * (layout.buttonStyles[bit]?.sanitized()?.opacity ?: 1f))
                .coerceIn(0.15f, 1f)
        val faceSize = (base * 0.130f * layout.scale).coerceIn(37f, 66f)
        val dpadSize = (base * 0.128f * layout.scale * bitScale(4)).coerceIn(30f, 94f)
        val padWidth = (dpadSize * if (layout.preset == TouchPreset.VIRTUAL_GAMEPAD)
            2.5f else 3.0f).dp
        val faceWidth = (faceSize * 2.95f *
            listOf(0, 1, 8, 9).maxOf(::bitScale)).dp
        val dpadLeft = (maxWidth * layout.dpadX).coerceIn(
            0.dp, (maxWidth - padWidth - 24.dp).coerceAtLeast(0.dp))
        val dpadRise = (maxHeight * layout.dpadY).coerceIn(
            0.dp, (maxHeight - padWidth - 20.dp).coerceAtLeast(0.dp))
        val faceRight = (maxWidth * layout.buttonsX).coerceIn(
            0.dp, (maxWidth - faceWidth - 24.dp).coerceAtLeast(0.dp))
        val faceRise = (maxHeight * layout.buttonsY).coerceIn(
            0.dp, (maxHeight - faceWidth - 20.dp).coerceAtLeast(0.dp))

        fun bitModifier(bit: Int): Modifier {
            val shift = layout.buttonShifts[bit] ?: ButtonShift()
            val placement = Modifier.offset(
                x = maxWidth * shift.x.coerceIn(-0.30f, 0.30f),
                y = maxHeight * shift.y.coerceIn(-0.30f, 0.30f))
            val drag = if (editing && editIndividual) Modifier.pointerInput(bit) {
                detectDragGestures { change, delta ->
                    change.consume()
                    val before = current.buttonShifts[bit] ?: ButtonShift()
                    val updated = ButtonShift(before.x + delta.x / widthPx,
                        before.y + delta.y / heightPx).sanitized()
                    emit(current.copy(buttonShifts =
                        current.buttonShifts + (bit to updated)))
                }
            } else Modifier
            return placement.then(drag)
        }
        val moveDpad = if (editing) Modifier.pointerInput(editing) {
            detectDragGestures { change, delta ->
                change.consume()
                emit(current.copy(
                    dpadX = (current.dpadX + delta.x / widthPx).coerceIn(0f, 0.65f),
                    dpadY = (current.dpadY - delta.y / heightPx).coerceIn(0f, 0.65f)))
            }
        } else Modifier
        val moveFace = if (editing && !editIndividual) Modifier.pointerInput(editing) {
            detectDragGestures { change, delta ->
                change.consume()
                emit(current.copy(
                    buttonsX = (current.buttonsX - delta.x / widthPx).coerceIn(0f, 0.65f),
                    buttonsY = (current.buttonsY - delta.y / heightPx).coerceIn(0f, 0.65f)))
            }
        } else Modifier

        Box(Modifier.align(Alignment.BottomStart)
            .padding(start = 13.dp, bottom = 14.dp)
            .offset(x = dpadLeft, y = -dpadRise)
            .then(moveDpad)
            .then(if (editing) Modifier.border(1.dp,
                Mint.copy(alpha = 0.75f), RoundedCornerShape(18.dp)) else Modifier)) {
            GestureDpad(dpadSize, bitOpacity(4), onInputChanged,
                enabled = !editing, skin = layout.preset)
        }

        Box(Modifier.align(Alignment.BottomEnd)
            .padding(end = 13.dp, bottom = 14.dp)
            .offset(x = -faceRight, y = -faceRise)
            .size(faceWidth).then(moveFace)
            .then(if (editing && !editIndividual) Modifier.border(1.dp,
                Mint.copy(alpha = 0.75f), RoundedCornerShape(18.dp)) else Modifier)) {
            if (layout.preset.diamond) {
                // SNES: X north, Y west, A east, B south.
                listOf(
                    Triple(9, Alignment.TopCenter, layout.preset.faceLegend(9)),
                    Triple(1, Alignment.CenterStart, layout.preset.faceLegend(1)),
                    Triple(8, Alignment.CenterEnd, layout.preset.faceLegend(8)),
                    Triple(0, Alignment.BottomCenter, layout.preset.faceLegend(0))
                ).forEach { (bit, alignment, label) ->
                    TouchButton(label, bit, faceSize * bitScale(bit), bitOpacity(bit),
                        onInputChanged, enabled = !editing, skin = layout.preset,
                        positionModifier = Modifier.align(alignment).then(bitModifier(bit)))
                }
            } else {
                Column(Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(1, 9).forEach { bit ->
                            TouchButton(layout.preset.faceLegend(bit), bit,
                                faceSize * bitScale(bit), bitOpacity(bit),
                                onInputChanged, enabled = !editing,
                                skin = layout.preset, positionModifier = bitModifier(bit))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(0, 8).forEach { bit ->
                            TouchButton(layout.preset.faceLegend(bit), bit,
                                faceSize * bitScale(bit), bitOpacity(bit),
                                onInputChanged, enabled = !editing,
                                skin = layout.preset, positionModifier = bitModifier(bit))
                        }
                    }
                }
            }
        }

        // Shoulder buttons stay at the upper corners, away from game art.
        val shoulderSize = (base * 0.11f * layout.scale).coerceIn(33f, 53f)
        listOf(10, 11).forEach { bit ->
            Box(Modifier.align(if (bit == 10) Alignment.TopStart
                else Alignment.TopEnd).padding(
                start = if (bit == 10) 14.dp else 0.dp,
                end = if (bit == 11) 14.dp else 0.dp, top = 65.dp
            ).then(bitModifier(bit))) {
                TouchButton(layout.preset.shoulderLegend(bit), bit,
                    shoulderSize * bitScale(bit), bitOpacity(bit), onInputChanged,
                    isPill = true, enabled = !editing, skin = layout.preset)
            }
        }
        // SELECT/START stay mapped to the game, never to the emulator menu.
        val menuSize = (base * 0.105f * layout.scale).coerceIn(32f, 48f)
        Row(Modifier.align(Alignment.BottomCenter)
            .padding(bottom = if (landscape) 17.dp else (faceWidth + 16.dp)),
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf(2, 3).forEach { bit ->
                Box(bitModifier(bit)) {
                    TouchButton(layout.preset.menuLegend(bit), bit,
                        menuSize * bitScale(bit), bitOpacity(bit),
                        onInputChanged, isPill = true,
                        enabled = !editing, skin = layout.preset)
                }
            }
        }
        if (editing) {
            Surface(Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
                shape = RoundedCornerShape(14.dp), color = NeoGlass) {
                Text(if (editIndividual) "ARRASTE CADA BOTÃO • SALVE NO MENU"
                    else "ARRASTE O DIRECIONAL E O GRUPO ABXY • SALVE NO MENU",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
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
            // Single modern cross surface. Gesture mask and diagonal movement
            // remain entirely in the unchanged parent pointer handler above.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                    val cell = size.minDimension / 3f
                    val rounding = CornerRadius(cell * .25f, cell * .25f)
                    drawRoundRect(color = Color(0xFF10182D)
                        .copy(alpha = opacity.coerceIn(.2f, 1f)),
                        topLeft = Offset(cell, 0f),
                        size = androidx.compose.ui.geometry.Size(cell, cell * 3f),
                        cornerRadius = rounding)
                    drawRoundRect(color = Color(0xFF10182D)
                        .copy(alpha = opacity.coerceIn(.2f, 1f)),
                        topLeft = Offset(0f, cell),
                        size = androidx.compose.ui.geometry.Size(cell * 3f, cell),
                        cornerRadius = rounding)
                    drawRoundRect(color = Accent.copy(alpha = opacity * .88f),
                        topLeft = Offset(cell, 0f),
                        size = androidx.compose.ui.geometry.Size(cell, cell * 3f),
                        cornerRadius = rounding,
                        style = Stroke(width = 1.4.dp.toPx()))
                    drawRoundRect(color = Accent.copy(alpha = opacity * .88f),
                        topLeft = Offset(0f, cell),
                        size = androidx.compose.ui.geometry.Size(cell * 3f, cell),
                        cornerRadius = rounding,
                        style = Stroke(width = 1.4.dp.toPx()))
                }
                Text("▲", modifier = Modifier.align(Alignment.TopCenter),
                    color = Color.White.copy(alpha = opacity), fontSize = 15.sp)
                Text("▼", modifier = Modifier.align(Alignment.BottomCenter),
                    color = Color.White.copy(alpha = opacity), fontSize = 15.sp)
                Text("◀", modifier = Modifier.align(Alignment.CenterStart),
                    color = Color.White.copy(alpha = opacity), fontSize = 15.sp)
                Text("▶", modifier = Modifier.align(Alignment.CenterEnd),
                    color = Color.White.copy(alpha = opacity), fontSize = 15.sp)
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
            .semantics { contentDescription = "Botão $label do SNES" }
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
                        drawRoundRect(color = Color(0xFF151E36).copy(alpha = alpha * .92f),
                            cornerRadius = CornerRadius(17.dp.toPx(), 17.dp.toPx()))
                        drawRoundRect(color = faceAccent.copy(alpha = alpha * .75f),
                            cornerRadius = CornerRadius(17.dp.toPx(), 17.dp.toPx()),
                            style = Stroke(width = 1.6.dp.toPx()))
                    } else {
                        drawCircle(color = Color(0xFF10182F).copy(alpha = alpha * .90f),
                            center = c, radius = radius)
                        drawCircle(color = faceAccent.copy(alpha = alpha * .25f),
                            center = c, radius = radius * .82f)
                        drawCircle(color = faceAccent.copy(alpha = alpha * .96f),
                            center = c, radius = radius * .93f,
                            style = Stroke(width = 2.5.dp.toPx()))
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
    onTestIce: () -> Unit,
    quickResumeEnabled: Boolean = true,
    onQuickResumeEnabledChanged: (Boolean) -> Unit = {},
    containerModifier: Modifier = Modifier
) {
    var category by remember { mutableStateOf("Vídeo") }
    var controlTarget by rememberSaveable { mutableStateOf(4) }
    val firstCategoryFocus = remember { FocusRequester() }
    LaunchedEffect(inGame) { if (!inGame) firstCategoryFocus.requestFocus() }
    var devCode by remember { mutableStateOf("") }
    var devError by remember { mutableStateOf(false) }
    var confirmBackup by remember { mutableStateOf<String?>(null) }
    var cheatFilter by remember { mutableStateOf("") }
    val categories = listOf("Vídeo", "Áudio", "Controles", "Cheats", "Rede", "Núcleo", "Estados", "DEV")
    Column(containerModifier.fillMaxWidth()) {
        if (inGame) {
            Row(Modifier.fillMaxWidth().padding(bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Ajustes durante o jogo", fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                NeoBadge("EM JOGO")
            }
        }
        // In-game landscape gets a compact horizontal category bar so
        // the actual settings pane has enough vertical room. The full
        // Settings screen keeps its accessible two-row overview.
        if (inGame) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEachIndexed { index, item ->
                    androidx.compose.material3.FilterChip(
                        selected = item == category,
                        onClick = { category = item },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstCategoryFocus)
                                else Modifier).padFocusRing(),
                        label = { Text(item, fontSize = 11.sp, maxLines = 1) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            containerColor = PanelRaised, labelColor = Muted,
                            selectedContainerColor = Accent, selectedLabelColor = Bg)
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                categories.chunked(4).forEachIndexed { rowIndex, group ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        group.forEachIndexed { position, item ->
                            androidx.compose.material3.FilterChip(
                                selected = item == category,
                                onClick = { category = item },
                                modifier = Modifier.weight(1f)
                                    .then(if (rowIndex == 0 && position == 0)
                                        Modifier.focusRequester(firstCategoryFocus) else Modifier)
                                    .padFocusRing(),
                                label = { Text(item, maxLines = 1, fontSize = 10.sp) },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    containerColor = Panel, labelColor = Muted,
                                    selectedContainerColor = Accent, selectedLabelColor = Bg))
                        }
                    }
                }
            }
        }
        Text(when (category) {
            "Vídeo" -> "Qualidade da imagem e desempenho."
            "Áudio" -> "Volume e atraso do som."
            "Controles" -> "Escolha o estilo, arraste e ajuste cada botão."
            "Cheats" -> "Truques opcionais para o jogo atual."
            "Rede" -> "Multiplayer, LAN, internet e conexão."
            "Núcleo" -> "Detalhes avançados da emulação."
            "Estados" -> "Salve e retome suas partidas."
            else -> "Ferramentas extras para quem conhece o emulador."
        }, color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 6.dp))
        val categoryScroll = rememberScrollState()
        LaunchedEffect(category) { categoryScroll.scrollTo(0) }
        Column(
            modifier = Modifier.fillMaxWidth()
                .then(if (inGame) Modifier.weight(1f)
                    else Modifier.heightIn(min = 140.dp, max = 720.dp))
                .verticalScroll(categoryScroll).padding(top = 5.dp),
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
                    SettingSectionTitle("Escolha o visual do controle",
                        "Todos os estilos têm botões adaptados à tela. " +
                            "SNES START sempre funciona dentro do jogo.")
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
                    SettingSectionTitle("Personalizar cada botão",
                        "1. Abra um jogo. 2. Toque na engrenagem e em EDITAR. " +
                            "3. Arraste o direcional/grupo ABXY. Alterne BOTÕES para " +
                            "mover cada botão e depois SALVAR. " +
                            "Use as barras abaixo para tamanho, transparência e posição.")
                    SliderSetting("D-pad: horizontal", settings.touch.dpadX, 0f..0.65f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(dpadX = it))) }, (settings.touch.dpadX * 100).toInt().toString() + "%")
                    SliderSetting("D-pad: subir", settings.touch.dpadY, 0f..0.65f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(dpadY = it))) }, (settings.touch.dpadY * 100).toInt().toString() + "%")
                    SliderSetting("Botões: horizontal", settings.touch.buttonsX, 0f..0.65f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(buttonsX = it))) }, (settings.touch.buttonsX * 100).toInt().toString() + "%")
                    SliderSetting("Botões: subir", settings.touch.buttonsY, 0f..0.65f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(buttonsY = it))) }, (settings.touch.buttonsY * 100).toInt().toString() + "%")
                    SliderSetting("Tamanho", settings.touch.scale, 0.70f..1.45f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(scale = it))) }, (settings.touch.scale * 100).toInt().toString() + "%")
                    SliderSetting("Opacidade", settings.touch.opacity, 0.20f..0.90f, { onSettingsChanged(settings.copy(touch = settings.touch.copy(opacity = it))) }, (settings.touch.opacity * 100).toInt().toString() + "%")
                    SettingSectionTitle("Ajustar botões separadamente",
                        "Personalize tamanho e transparência de cada botão. " +
                        "Para mover um botão, use EDITAR durante o jogo.")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(
                            4 to "D-PAD", 1 to "Y", 9 to "X", 0 to "B",
                            8 to "A", 10 to "L", 11 to "R",
                            2 to "SELECT", 3 to "START"
                        ).forEach { (bit, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = controlTarget == bit,
                                onClick = { controlTarget = bit },
                                label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.padFocusRing())
                        }
                    }
                    val style = settings.touch.buttonStyles[controlTarget]
                        ?.sanitized() ?: ButtonStyle()
                    SliderSetting("Tamanho deste botão", style.scale,
                        0.60f..1.55f, { value ->
                            onSettingsChanged(settings.copy(touch = settings.touch.copy(
                                buttonStyles = settings.touch.buttonStyles +
                                    (controlTarget to style.copy(scale = value)))))
                        }, (style.scale * 100).toInt().toString() + "%")
                    SliderSetting("Opacidade deste botão", style.opacity,
                        0.25f..1f, { value ->
                            onSettingsChanged(settings.copy(touch = settings.touch.copy(
                                buttonStyles = settings.touch.buttonStyles +
                                    (controlTarget to style.copy(opacity = value)))))
                        }, (style.opacity * 100).toInt().toString() + "%")
                    OutlinedButton(onClick = {
                        onSettingsChanged(settings.copy(touch = settings.touch.copy(
                            buttonStyles = settings.touch.buttonStyles - controlTarget)))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("RESTAURAR ESTE BOTÃO")
                    }
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
                    SettingSectionTitle("Retomada rápida",
                        "Ao sair de um jogo offline, salva uma cópia separada dos slots manuais.")
                    ToggleSettingRow(
                        "Salvar automaticamente ao sair (Quick Resume)",
                        "Disponível apenas para partidas offline. Não altera seus saves.",
                        quickResumeEnabled, onQuickResumeEnabledChanged
                    )
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
    val activeBg = if (selected) Accent else NeoGlass
    val labelColor = if (selected) Color(0xFF101727) else Color.White
    Surface(
        modifier = Modifier.fillMaxWidth().padFocusRing()
            .border(if (selected) 0.dp else 1.dp,
                if (selected) Color.Transparent else NeoOutline.copy(alpha = 0.56f),
                RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        color = activeBg, shape = RoundedCornerShape(15.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = labelColor)
                Text(description, color = if (selected) Color(0xFF28334A) else Muted,
                    fontSize = 10.sp, lineHeight = 13.sp)
            }
            if (selected) Text("✓", color = Color(0xFF101727),
                fontSize = 19.sp, fontWeight = FontWeight.Black)
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
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
            color = Color.White)
        Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
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
