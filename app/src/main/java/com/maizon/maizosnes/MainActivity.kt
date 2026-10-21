package com.maizon.maizosnes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import android.net.wifi.WifiManager
import android.content.Context
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("maizosnes", MODE_PRIVATE) }
    private val romDirectory by lazy { File(filesDir, "roms").apply { mkdirs() } }
    private val stateDirectory by lazy { File(filesDir, "states").apply { mkdirs() } }

    private val statusMessage = androidx.compose.runtime.mutableStateOf("Preparando Snes9x 1.63…")
    private val romFiles = androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
    private val romHeaders = androidx.compose.runtime.mutableStateMapOf<String, SnesRomHeader.Details>()
    private val favoriteRoms = androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet())
    private val recentRoms = androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
    private var patchTarget: File? = null
    private val activeScreen = androidx.compose.runtime.mutableStateOf(AppScreen.LIBRARY)
    private val currentRomFile = androidx.compose.runtime.mutableStateOf<File?>(null)
    private val currentRomName = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val currentRomHashPrefix = androidx.compose.runtime.mutableStateOf("")
    private val appSettings = androidx.compose.runtime.mutableStateOf(AppSettings())
    private val coreSettings = androidx.compose.runtime.mutableStateMapOf<String, String>()
    private val paused = androidx.compose.runtime.mutableStateOf(false)
    private val gameMenuOpen = androidx.compose.runtime.mutableStateOf(false)
    private var pausedBeforeGameMenu = false
    private val pressedInputs = AtomicInteger(0)
    private val coverBitmaps = androidx.compose.runtime.mutableStateMapOf<String, Bitmap>()
    private var coverJob: Job? = null
    private var perfJob: Job? = null
    private val performanceText = androidx.compose.runtime.mutableStateOf("Aguardando métricas…")
    private val connectionMode = androidx.compose.runtime.mutableStateOf(NetworkMode.AUTO)
    private val netplayLatencyMode = androidx.compose.runtime.mutableStateOf(NetplayLatencyMode.BALANCED)
    private val netcodeMode = androidx.compose.runtime.mutableStateOf(NetcodeMode.ROLLBACK)
    private val savedHost = androidx.compose.runtime.mutableStateOf("")
    private val savedPort = androidx.compose.runtime.mutableStateOf(55435)
    private val relayAddress = androidx.compose.runtime.mutableStateOf("")
    private val iceServerOverrides = androidx.compose.runtime.mutableStateOf("")
    private val iceTestStatus = androidx.compose.runtime.mutableStateOf(
        "STUN automático; TURN via Railway se configurado. Teste local não prova P2P.")
    private val relayRooms = androidx.compose.runtime.mutableStateOf<List<InternetRoom>>(emptyList())
    private val scanningRelay = androidx.compose.runtime.mutableStateOf(false)
    private val autoRooms = androidx.compose.runtime.mutableStateOf<List<AutoRoom>>(emptyList())
    private val autoServers = androidx.compose.runtime.mutableStateOf<List<AutoServerStatus>>(emptyList())
    private val onlineCheatsOptIn = androidx.compose.runtime.mutableStateOf(false)
    private val autoScanActive = androidx.compose.runtime.mutableStateOf(false)
    private val autoNetworkStatus = androidx.compose.runtime.mutableStateOf("Buscando servidores disponíveis…")
    private val devUnlocked = androidx.compose.runtime.mutableStateOf(false)
    private val devShareEnabled = androidx.compose.runtime.mutableStateOf(false)
    private val lastNetworkMetrics = androidx.compose.runtime.mutableStateOf<NetworkMetrics?>(null)
    private val networkDebug = androidx.compose.runtime.mutableStateOf(NetworkDebugState())
    private var lastRollbackCounter = 0L
    private val lanStatus = androidx.compose.runtime.mutableStateOf("Sem sessão ativa.")
    private val availableLanRooms = androidx.compose.runtime.mutableStateOf<List<LanRoom>>(emptyList())
    private val scanningLan = androidx.compose.runtime.mutableStateOf(false)
    private val shareRomAllowed = androidx.compose.runtime.mutableStateOf(true)
    private val receiveRomAllowed = androidx.compose.runtime.mutableStateOf(true)
    private val keepMultiplayerInBackground = androidx.compose.runtime.mutableStateOf(true)
    private val shareConsentVisible = androidx.compose.runtime.mutableStateOf(false)
    private data class PendingHost(val port: Int, val roomId: String?, val relayHost: String?,
        val relayTls: Boolean, val wssEndpoint: String?)
    private var pendingHost: PendingHost? = null
    private var hostApproved = false
    private var hostAnswered = false
    private data class RomOffer(val filename: String, val digest: String, val bytes: Int)
    private val incomingRomOffer = androidx.compose.runtime.mutableStateOf<RomOffer?>(null)
    private val pendingRomApproval =
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.ArrayBlockingQueue<Boolean>?>(null)
    private val cheatEntries = androidx.compose.runtime.mutableStateOf<List<CheatEntry>>(emptyList())
    private val activeCheatIds = androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet())
    private val cheatStatus = androidx.compose.runtime.mutableStateOf("Abra uma ROM para consultar cheats.")
    private var cheatRomKey: String? = null
    private val localPlayer2Enabled = androidx.compose.runtime.mutableStateOf(false)
    private val gamepadKeyMasks = ConcurrentHashMap<Int, Int>()
    private val gamepadAxisMasks = ConcurrentHashMap<Int, Int>()
    private var lastUiDirection = 0
    private var lastUiDirectionAt = 0L
    private var netplaySession: NetplaySession? = null
    private var hostWifiLock: WifiManager.MulticastLock? = null
    private var sessionKeepAliveStarted = false

    private fun changeBackgroundNetplay(enabled: Boolean) {
        keepMultiplayerInBackground.value = enabled
        prefs.edit().putBoolean("netplay_background_enabled", enabled).apply()
        if (netplaySession != null) {
            if (enabled) startMultiplayerKeepAlive() else stopMultiplayerKeepAlive()
        }
    }

    private fun startMultiplayerKeepAlive() {
        if (!keepMultiplayerInBackground.value || sessionKeepAliveStarted) return
        val action = Intent(this, MultiplayerKeepAliveService::class.java)
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(this, action)
            sessionKeepAliveStarted = true
        }.onFailure {
            // On Android variants that block FGS initiation, native TCP/UDP
            // still works while the Activity is in foreground.
            lanStatus.value = "Multiplayer ativo, sem proteção de segundo plano: " +
                (it.message ?: "permissão do Android")
        }
    }

    private fun stopMultiplayerKeepAlive() {
        if (!sessionKeepAliveStarted) return
        sessionKeepAliveStarted = false
        runCatching { stopService(Intent(this, MultiplayerKeepAliveService::class.java)) }
    }

    private var audioOutput: AudioOutput? = null
    private var hasLoadedRom = false
    private var activityResumed = false
    private var audioOutputWasAudible = false
    private var surfaceAttached = false
    private var userPaused = false

    private val openRomLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addRomToLibrary(uri)
    }
    private val scanRomTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scanRomTree(uri)
    }
    private val allFilesPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager())
                scanAllSharedStorage()
            else statusMessage.value = "Acesso a todos os arquivos não autorizado. " +
                "Use a busca por pasta, que não requer acesso especial."
        }
    private val legacyStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scanAllSharedStorage()
            else statusMessage.value = "Permissão negada. Use busca por pasta."
        }
    private val patchRomLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) applyRomPatch(uri)
    }
    private val exportStateLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) exportState(uri)
    }
    private val importStateLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importState(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 13, 24)
        window.navigationBarColor = android.graphics.Color.rgb(9, 13, 24)
        romDirectory.mkdirs()
        stateDirectory.mkdirs()
        favoriteRoms.value = prefs.getStringSet("library_favorites", emptySet())?.toSet() ?: emptySet()
        recentRoms.value = runCatching {
            val arr = org.json.JSONArray(prefs.getString("library_recent", "[]"))
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
        refreshLibrary()

        val initResult = runCatching { NativeBridge.nativeInit(filesDir.absolutePath) }.getOrElse {
            statusMessage.value = "Falha ao iniciar Snes9x: " + (it.message ?: "erro JNI")
            -999
        }
        if (initResult == 0) {
            statusMessage.value = "Snes9x 1.63 pronto. Adicione uma ROM própria à biblioteca."
            CoreOptionDefinitions.all.forEach { option ->
                val value = prefs.getString("core_" + option.key, option.defaultValue) ?: option.defaultValue
                coreSettings[option.key] = value
                runCatching { NativeBridge.nativeSetCoreOption(option.key, value) }
            }
        } else {
            statusMessage.value = "Falha na inicialização do núcleo: " + initResult
        }
        appSettings.value = readSettings("profile_global_")
        val restoredMode = prefs.getString("network_mode", NetworkMode.AUTO.name)
        connectionMode.value = runCatching {
            NetworkMode.valueOf(restoredMode ?: NetworkMode.AUTO.name)
        }.getOrDefault(NetworkMode.AUTO)
        loadConnectionTarget()
        netplayLatencyMode.value = runCatching {
            NetplayLatencyMode.valueOf(prefs.getString("netplay_latency_mode",
                NetplayLatencyMode.BALANCED.name) ?: NetplayLatencyMode.BALANCED.name)
        }.getOrDefault(NetplayLatencyMode.BALANCED)
        netcodeMode.value = runCatching {
            NetcodeMode.valueOf(prefs.getString("netcode_mode", NetcodeMode.ROLLBACK.name)!!)
        }.getOrDefault(NetcodeMode.ROLLBACK)
        keepMultiplayerInBackground.value =
            prefs.getBoolean("netplay_background_enabled", true)
        relayAddress.value = prefs.getString("relay_server_url", "") ?: ""
        iceServerOverrides.value = prefs.getString("ice_servers_override", "") ?: ""
        onlineCheatsOptIn.value = prefs.getBoolean("online_cheats_opt_in", false)
        devUnlocked.value = prefs.getBoolean("dev_unlocked", false)
        devShareEnabled.value = prefs.getBoolean("dev_share_advanced", true)
        shareRomAllowed.value = prefs.getBoolean("share_rom_enabled", true)
        receiveRomAllowed.value = prefs.getBoolean("receive_rom_enabled", true)
        runCatching {
            NativeBridge.nativeSetVideoFilter(appSettings.value.filter.nativeId)
            NativeBridge.nativeSetOutputScale(appSettings.value.outputScale)
            NativeBridge.nativeSetGpuEnabled(appSettings.value.renderBackend == RenderBackend.GLES)
        }
        refreshCovers()
        startPerformanceSampler()
        val localIp = getLocalIpv4()

        setContent {
            MaizoTheme {
                MaizoEmulatorApp(
                    romNames = romFiles.value,
                    coverBitmaps = coverBitmaps.toMap(),
                    romHeaders = romHeaders.toMap(),
                    favoriteRoms = favoriteRoms.value,
                    recentRoms = recentRoms.value,
                    onToggleFavorite = ::toggleFavorite,
                    onRemoveRom = ::removeRom,
                    onApplyPatchToRom = ::choosePatchForRom,
                    screen = activeScreen.value,
                    statusMessage = statusMessage.value,
                    currentRomName = currentRomName.value,
                    localRomHashPrefix = currentRomHashPrefix.value,
                    hasLoadedRom = hasLoadedRom,
                    paused = paused.value,
                    gameMenuOpen = gameMenuOpen.value,
                    onGameMenuChanged = ::setGameMenuOpen,
                    settings = appSettings.value,
                    coreOptions = coreSettings.toMap(),
                    performanceText = performanceText.value,
                    networkDebug = networkDebug.value,
                    onSelectTouchPreset = ::selectTouchPreset,
                    connectionMode = connectionMode.value,
                    onConnectionModeChanged = ::changeConnectionMode,
                    netplayLatencyMode = netplayLatencyMode.value,
                    onNetplayLatencyModeChanged = ::changeNetplayLatencyMode,
                    netcodeMode = netcodeMode.value,
                    onNetcodeModeChanged = ::changeNetcodeMode,
                    backgroundNetplayEnabled = keepMultiplayerInBackground.value,
                    onBackgroundNetplayChanged = ::changeBackgroundNetplay,
                    iceServerOverrides = iceServerOverrides.value,
                    onIceOverridesChanged = ::changeIceOverrides,
                    iceTestStatus = iceTestStatus.value,
                    onTestIce = ::testIceSettings,
                    savedConnectionHost = savedHost.value,
                    savedConnectionPort = savedPort.value,
                    onSaveConnectionTarget = ::saveConnectionTarget,
                    devUnlocked = devUnlocked.value,
                    devShareEnabled = devShareEnabled.value,
                    onDevUnlock = ::unlockDev,
                    onDevShareChanged = ::setAdvancedSharing,
                    multiplayerStatus = lanStatus.value,
                    lanRooms = availableLanRooms.value,
                    scanningLan = scanningLan.value,
                    onScanLan = ::scanLan,
                    relayAddress = relayAddress.value,
                    relayRooms = relayRooms.value,
                    scanningRelay = scanningRelay.value,
                    onRelayAddressChanged = ::changeRelayAddress,
                    onRefreshRelayRooms = ::refreshRelayRooms,
                    onHostRelay = ::hostRelay,
                    onJoinRelay = ::joinRelay,
                    autoRooms = autoRooms.value,
                    autoServers = autoServers.value,
                    autoStatus = autoNetworkStatus.value,
                    autoScanActive = autoScanActive.value,
                    onRefreshAutoRooms = ::refreshAutoRooms,
                    onHostAutomatic = ::hostAutomatic,
                    onQuickMatch = ::quickMatch,
                    onJoinAutomatic = ::joinAutomatic,
                    onlineCheatsOptIn = onlineCheatsOptIn.value,
                    onOnlineCheatsChanged = ::setOnlineCheatsOptIn,
                    shareRomAllowed = shareRomAllowed.value,
                    shareConsentVisible = shareConsentVisible.value,
                    onShareConsent = ::resolveShareConsent,
                    incomingRomName = incomingRomOffer.value?.filename,
                    incomingRomHash = incomingRomOffer.value?.digest ?: "",
                    incomingRomBytes = incomingRomOffer.value?.bytes ?: 0,
                    onIncomingRomDecision = ::resolveIncomingRomOffer,
                    receiveRomAllowed = receiveRomAllowed.value,
                    onShareRomAllowedChanged = {
                        shareRomAllowed.value = it
                        prefs.edit().putBoolean("share_rom_enabled", it).apply()
                    },
                    onReceiveRomAllowedChanged = {
                        receiveRomAllowed.value = it
                        prefs.edit().putBoolean("receive_rom_enabled", it).apply()
                    },
                    cheats = cheatEntries.value,
                    enabledCheatIds = activeCheatIds.value,
                    cheatStatus = cheatStatus.value,
                    onToggleCheat = ::toggleCheat,
                    onRefreshCheats = { searchCheats(refresh = true) },
                    localIpAddress = localIp,
                    multiplayerActive = netplaySession != null,
                    localMultiplayer = localPlayer2Enabled.value,
                    multiplayerReady = hasLoadedRom,
                    onHostMultiplayer = ::hostMultiplayer,
                    onJoinMultiplayer = ::joinMultiplayer,
                    onStopMultiplayer = ::stopMultiplayer,
                    onLocalMultiplayerChanged = ::setLocalMultiplayer,
                    onChooseAddRom = { openRomLauncher.launch(arrayOf("*/*")) },
                    onScanRomFolder = { scanRomTreeLauncher.launch(null) },
                    onApplyRomPatch = { patchRomLauncher.launch(arrayOf("*/*")) },
                    onScanAllDevice = ::requestGlobalRomScan,
                    onOpenInstagram = {
                        val uri = Uri.parse("https://www.instagram.com/maicon_nasciment0/")
                        runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                            .onFailure { statusMessage.value = "Não foi possível abrir o Instagram." }
                    },
                    onOpenRom = ::openRom,
                    onContinueGame = ::continueGame,
                    onRenameRom = ::renameRom,
                    onSetScreen = { requested -> if (requested != AppScreen.GAME) activeScreen.value = requested },
                    onSettingsChanged = ::persistSettings,
                    onCoreOptionChanged = ::changeCoreOption,
                    onSaveSlot = ::saveSlot,
                    onLoadSlot = ::loadSlot,
                    onRestoreMultiplayerBackup = ::restoreMultiplayerBackup,
                    onExportState = { exportStateLauncher.launch(defaultStateName()) },
                    onImportState = { importStateLauncher.launch(arrayOf("*/*")) },
                    onResetGame = ::resetGame,
                    onTogglePause = ::togglePause,
                    onExitGame = ::exitGameplay,
                    onInputChanged = ::updateInput,
                    onSurfaceChanged = ::updateEmulatorSurface,
                    onGpuReady = ::updateGpuReady,
                    onExportDebug = ::shareNetworkDiagnostics
                )
            }
        }
    }

    private fun refreshCovers() {
        if (!appSettings.value.automaticCovers) {
            coverJob?.cancel()
            coverBitmaps.clear()
            return
        }
        coverJob?.cancel()
        val names = romFiles.value.take(48)
        coverJob = lifecycleScope.launch {
            for (name in names) {
                if (!isActive || !appSettings.value.automaticCovers) break
                if (coverBitmaps.containsKey(name)) continue
                val file = File(romDirectory, name)
                if (!file.isFile) continue
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching { CoverArt.load(this@MainActivity, file) }.getOrNull()
                }
                if (bitmap != null && name in romFiles.value) {
                    coverBitmaps[name] = bitmap
                }
            }
        }
    }

    private fun startPerformanceSampler() {
        perfJob?.cancel()
        perfJob = lifecycleScope.launch {
            var previous: LongArray? = null
            var previousAt = System.nanoTime()
            while (isActive) {
                delay(1000)
                if (!hasLoadedRom) {
                    previous = null
                    continue
                }
                val counters = runCatching {
                    NativeBridge.nativeGetPerfCounters()
                }.getOrNull() ?: continue
                if (counters.size < 6) continue
                val now = System.nanoTime()
                val past = previous
                if (past != null && appSettings.value.performanceHud) {
                    val seconds = ((now - previousAt).toDouble() / 1e9).coerceAtLeast(0.01)
                    val frames = (counters[0] - past[0]).coerceAtLeast(0L)
                    val presented = (counters[2] - past[2]).coerceAtLeast(0L)
                    val fps = frames / seconds
                    val emuMs = if (frames > 0) (counters[1] - past[1]) / 1e6 / frames else 0.0
                    val videoMs = if (presented > 0)
                        (counters[3] - past[3]) / 1e6 / presented else 0.0
                    val nominal = if (counters[4] > 0) 1e9 / counters[4] else 0.0
                    val network = lastNetworkMetrics.value
                    val base = String.format(java.util.Locale.US,
                        "Emu %.1f / %.1f FPS • Núcleo %.2f ms\n" +
                            "Vídeo %.2f ms • Áudio %d quadros", fps, nominal,
                        emuMs, videoMs, counters[5])
                    performanceText.value = if (network == null) base else base +
                        if (network.rollback) String.format(java.util.Locale.US,
                            "\nRollback • RTT %.0f ms • jitter %.1f ms • %d correções",
                            network.sampledRttMs, network.jitterMs, network.rollbackCount)
                        else String.format(java.util.Locale.US,
                            "\nPeer espera %.1f / pico %.1f ms • %d travamentos/s" +
                                " • buffer %d quadros",
                            network.peerWaitMs, network.maxPeerWaitMs,
                            network.stalledFrames, network.inputDelayFrames)
                }
                previous = counters
                previousAt = now
            }
        }
    }

    private fun loadConnectionTarget() {
        val prefix = "network_" + connectionMode.value.name + "_"
        savedHost.value = prefs.getString(prefix + "host", "") ?: ""
        savedPort.value = prefs.getInt(prefix + "port", 55435).coerceIn(1024, 65535)
    }

    private fun changeNetcodeMode(next: NetcodeMode) {
        if (netplaySession != null) {
            lanStatus.value = "Encerre a sessão antes de trocar motor de rede."
            return
        }
        netcodeMode.value = next
        prefs.edit().putString("netcode_mode", next.name).apply()
        lanStatus.value = next.label
    }

    private fun changeNetplayLatencyMode(next: NetplayLatencyMode) {
        if (netplaySession != null) {
            lanStatus.value = "Encerre a sessão antes de trocar o buffer de rede."
            return
        }
        netplayLatencyMode.value = next
        prefs.edit().putString("netplay_latency_mode", next.name).apply()
        lanStatus.value = "Perfil de latência: " + next.label +
            ". O host sincroniza automaticamente o buffer com o convidado."
    }

    private fun changeConnectionMode(next: NetworkMode) {
        if (netplaySession != null) {
            lanStatus.value = "Encerre a partida antes de trocar o modo de conexão."
            return
        }
        if (connectionMode.value == next) return
        connectionMode.value = next
        prefs.edit().putString("network_mode", next.name).apply()
        loadConnectionTarget()
        if (next == NetworkMode.AUTO) refreshAutoRooms()
        if (next == NetworkMode.LOCAL) {
            setLocalMultiplayer(true)
        } else if (localPlayer2Enabled.value) {
            setLocalMultiplayer(false)
        }
        lanStatus.value = "Modo alterado sem apagar progresso: " + next.label
    }

    private fun saveConnectionTarget(host: String, port: Int) {
        if (netplaySession != null || port !in 1024..65535) return
        savedHost.value = host.take(253)
        savedPort.value = port
        val prefix = "network_" + connectionMode.value.name + "_"
        prefs.edit().putString(prefix + "host", savedHost.value)
            .putInt(prefix + "port", port).apply()
    }

    private fun unlockDev(code: String): Boolean {
        if (code != "9090") return false
        devUnlocked.value = true
        devShareEnabled.value = prefs.getBoolean("dev_share_advanced", true)
        prefs.edit().putBoolean("dev_unlocked", true)
            .putBoolean("dev_share_advanced", devShareEnabled.value).apply()
        return true
    }

    private fun setAdvancedSharing(enabled: Boolean) {
        if (!devUnlocked.value || netplaySession != null) return
        devShareEnabled.value = enabled
        prefs.edit().putBoolean("dev_share_advanced", enabled).apply()
    }

    private fun selectTouchPreset(preset: TouchPreset) {
        val prefix = profilePrefix(
            if (activeScreen.value == AppScreen.GAME) currentRomFile.value else null)
        val saved = prefs.getString(prefix + "touch_preset_" + preset.name, null)
            ?: prefs.getString("profile_global_touch_preset_" + preset.name, null)
        val chosen = (TouchLayoutCodec.decode(saved, preset) ?: preset.defaultLayout())
            .copy(enabled = appSettings.value.touch.enabled)
        persistSettings(appSettings.value.copy(touch = chosen))
    }

    private fun getLocalIpv4(): String = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val network = interfaces.nextElement()
            if (!network.isUp || network.isLoopback) continue
            val addresses = network.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && address.isSiteLocalAddress) {
                    return@runCatching address.hostAddress ?: "não detectado"
                }
            }
        }
        "não detectado"
    }.getOrDefault("não detectado")

    private fun safeFileName(input: String): String =
        input.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim().trim('.', ' ').take(110).ifBlank { "game.sfc" }

    private fun readUriBytes(uri: Uri, limitBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limitBytes) { "arquivo excede " + (limitBytes / (1024 * 1024)) + " MiB" }
                output.write(buffer, 0, count)
            }
        } ?: throw IllegalStateException("O Android não conseguiu ler o arquivo escolhido.")
        return output.toByteArray().also { require(it.isNotEmpty()) { "O arquivo está vazio." } }
    }

    private fun addRomToLibrary(uri: Uri) {
        lifecycleScope.launch {
            try {
                statusMessage.value = "Importando ROM para a biblioteca…"
                val name = withContext(Dispatchers.IO) {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && col >= 0) cursor.getString(col) else null
                        } ?: "game.sfc"
                }
                val cleaned = safeFileName(name)
                val extension = cleaned.substringAfterLast('.', "").lowercase()
                require(extension in setOf("sfc", "smc", "zip")) {
                    "Formato não suportado. Use .sfc, .smc ou ZIP com uma única ROM."
                }
                val (romName, bytes) = withContext(Dispatchers.IO) {
                    if (extension == "zip") {
                        var found: Pair<String, ByteArray>? = null
                        val source = contentResolver.openInputStream(uri)
                            ?: error("Não foi possível abrir o ZIP.")
                        ZipInputStream(source.buffered()).use { zip ->
                            var totalExpanded = 0L
                            var entryCount = 0
                            while (true) {
                                val entry = zip.nextEntry ?: break
                                entryCount++
                                require(entryCount <= 128) { "ZIP possui entradas demais." }
                                val entryName = safeFileName(entry.name)
                                val isRom = !entry.isDirectory &&
                                    entryName.substringAfterLast('.', "").lowercase() in setOf("sfc", "smc")
                                if (isRom) require(found == null) {
                                    "ZIP com várias ROMs. Importe uma por vez."
                                }
                                val output = if (isRom) ByteArrayOutputStream() else null
                                val buffer = ByteArray(65536)
                                while (true) {
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    totalExpanded += n
                                    require(totalExpanded <= 64L * 1024L * 1024L) {
                                        "ZIP excede limite de 64 MiB descompactados."
                                    }
                                    output?.write(buffer, 0, n)
                                }
                                if (isRom) found = entryName to (output?.toByteArray() ?: ByteArray(0))
                                zip.closeEntry()
                            }
                        }
                        found ?: error("ZIP sem ROM SNES .sfc/.smc.")
                    } else cleaned to readUriBytes(uri, 64 * 1024 * 1024)
                }
                require(bytes.isNotEmpty()) { "A ROM está vazia." }
                val romExtension = romName.substringAfterLast('.').lowercase()
                var target = File(romDirectory, romName)
                var suffix = 2
                while (target.exists()) {
                    target = File(romDirectory, romName.substringBeforeLast('.') + " (" + suffix + ")." + romExtension)
                    suffix++
                }
                withContext(Dispatchers.IO) { target.writeBytes(bytes) }
                refreshLibrary()
                statusMessage.value = "Adicionada à biblioteca: " + target.name
            } catch (error: Exception) {
                statusMessage.value = "Não foi possível adicionar a ROM: " + (error.message ?: "erro de leitura")
            }
        }
    }

    /**
     * Android 11+ restricts whole-device enumeration. SAF allows the user to
     * select a folder and grants scoped, revocable read access to its subtree.
     * Folder selection uses no special permission; all-files access is an
     * OPTIONAL second path that requires explicit Android system approval.
     */
    /**
     * Optional, user-approved all-files inventory of SHARED storage. Android
     * still disallows private app directories and protected /Android/data.
     * Never upload or enumerate content on a server. Only .smc/.sfc/ZIP copied.
     */
    private fun requestGlobalRomScan() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            statusMessage.value = "Para buscar em todo armazenamento compartilhado, " +
                "autorize explicitamente acesso a todos os arquivos no Android."
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"))
            try { allFilesPermissionLauncher.launch(intent) }
            catch (_: Exception) {
                allFilesPermissionLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            return
        }
        if (Build.VERSION.SDK_INT in 23..29 &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            legacyStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }
        scanAllSharedStorage()
    }

    private fun scanAllSharedStorage() {
        lifecycleScope.launch {
            statusMessage.value = "Varrendo o armazenamento compartilhado… " +
                "O Android restringe pastas protegidas."
            try {
                val result = withContext(Dispatchers.IO) {
                    val roots = mutableSetOf<File>()
                    @Suppress("DEPRECATION")
                    roots.add(Environment.getExternalStorageDirectory())
                    getExternalFilesDirs(null).filterNotNull().forEach { file ->
                        var cursor: File? = file
                        while (cursor != null && cursor.name != "Android") {
                            cursor = cursor.parentFile
                        }
                        cursor?.parentFile?.let { roots.add(it) }
                    }
                    val pending = ArrayDeque<Triple<File, Int, String>>()
                    roots.filter { it.isDirectory }.forEach { root ->
                        pending.add(Triple(root, 0, root.canonicalPath))
                    }
                    val seen = HashSet<String>()
                    var inspected = 0
                    var imported = 0
                    var ignored = 0
                    while (pending.isNotEmpty() && inspected < 30000 && imported < 500) {
                        val (folder, depth, storageRoot) = pending.removeFirst()
                        val folderCanonical = runCatching { folder.canonicalPath }.getOrNull()
                            ?: continue
                        if (!folderCanonical.startsWith(storageRoot) ||
                            !seen.add(folderCanonical)) continue
                        val contents = runCatching { folder.listFiles()?.toList() }
                            .getOrNull() ?: continue
                        for (child in contents) {
                            if (++inspected > 30000 || imported >= 500) break
                            if (child.isDirectory && depth < 12) {
                                val skip = child.name in setOf(
                                    ".Trash", ".thumbnails", ".git", "LOST.DIR") ||
                                    (folder.name == "Android" && child.name in
                                        setOf("data", "obb"))
                                if (!skip) pending.add(
                                    Triple(child, depth + 1, storageRoot))
                                continue
                            }
                            if (!child.isFile) continue
                            if (child.extension.lowercase() !in setOf("smc", "sfc", "zip") ||
                                child.length() !in 1..(64L * 1024L * 1024L)) continue
                            try {
                                val (romName, bytes) = if (child.extension.lowercase() == "zip") {
                                    var chosen: Pair<String, ByteArray>? = null
                                    ZipInputStream(child.inputStream().buffered()).use { zip ->
                                        var expanded = 0L
                                        var entries = 0
                                        while (true) {
                                            val item = zip.nextEntry ?: break
                                            check(++entries <= 128) { "ZIP com muitas entradas." }
                                            val name = safeFileName(item.name)
                                            val isRom = !item.isDirectory &&
                                                name.substringAfterLast('.', "").lowercase() in
                                                    setOf("sfc", "smc")
                                            check(!isRom || chosen == null) { "ZIP com múltiplas ROMs." }
                                            val bytesOut = if (isRom) ByteArrayOutputStream() else null
                                            val chunk = ByteArray(65536)
                                            while (true) {
                                                val n = zip.read(chunk)
                                                if (n < 0) break
                                                expanded += n
                                                check(expanded <= 64L * 1024L * 1024L) {
                                                    "ZIP grande demais."
                                                }
                                                bytesOut?.write(chunk, 0, n)
                                            }
                                            if (isRom) chosen = name to
                                                (bytesOut?.toByteArray() ?: ByteArray(0))
                                            zip.closeEntry()
                                        }
                                    }
                                    chosen ?: error("ZIP sem ROM.")
                                } else safeFileName(child.name) to child.readBytes()
                                if (bytes.isEmpty()) continue
                                val target = File(romDirectory, romName)
                                if (target.exists()) { ignored++; continue }
                                val tmp = File.createTempFile("scan-all-", ".tmp", romDirectory)
                                try {
                                    tmp.writeBytes(bytes)
                                    check(tmp.renameTo(target)) { "Arquivo não movido." }
                                } finally { tmp.delete() }
                                imported++
                            } catch (_: Exception) { ignored++ }
                        }
                    }
                    Triple(imported, inspected, ignored)
                }
                refreshLibrary()
                statusMessage.value = "Busca concluída: ${result.first} ROM(s) " +
                    "importada(s) • ${result.second} itens verificados • " +
                    "${result.third} ignorados. Pastas privadas do Android não são acessíveis."
            } catch (error: Exception) {
                statusMessage.value = "Falha ao pesquisar dispositivo: " +
                    (error.message ?: "armazenamento inacessível")
            }
        }
    }

    private fun scanRomTree(uri: Uri) {
        lifecycleScope.launch {
            statusMessage.value = "Procurando ROMs na pasta autorizada…"
            try {
                val files = withContext(Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(this@MainActivity, uri)
                        ?: error("Pasta inacessível.")
                    val pending = ArrayDeque<Pair<DocumentFile, Int>>()
                    pending.add(root to 0)
                    val matches = ArrayList<Uri>()
                    var visited = 0
                    while (pending.isNotEmpty() && visited < 3000 && matches.size < 250) {
                        val (folder, depth) = pending.removeFirst()
                        for (child in folder.listFiles()) {
                            if (++visited > 3000 || matches.size >= 250) break
                            if (child.isDirectory && depth < 8) {
                                pending.add(child to (depth + 1))
                            } else if (child.isFile &&
                                child.name.orEmpty().substringAfterLast('.', "").lowercase() in
                                    setOf("sfc", "smc")) {
                                matches.add(child.uri)
                            }
                        }
                    }
                    matches
                }
                if (files.isEmpty()) {
                    statusMessage.value = "Nenhuma ROM .sfc/.smc encontrada nessa pasta."
                    return@launch
                }
                var imported = 0
                var skipped = 0
                for (item in files) {
                    val name = withContext(Dispatchers.IO) {
                        contentResolver.query(item, arrayOf(OpenableColumns.DISPLAY_NAME),
                            null, null, null)?.use { cursor ->
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                        }
                    } ?: continue
                    val safe = safeFileName(name)
                    if (File(romDirectory, safe).exists()) {
                        skipped++
                        continue
                    }
                    try {
                        val data = withContext(Dispatchers.IO) { readUriBytes(item, 64 * 1024 * 1024) }
                        if (data.isEmpty()) continue
                        withContext(Dispatchers.IO) {
                            val dst = File(romDirectory, safe)
                            val tmp = File.createTempFile("scan-", ".partial", romDirectory)
                            try {
                                tmp.writeBytes(data)
                                check(tmp.renameTo(dst)) { "Falha ao importar arquivo." }
                            } finally { tmp.delete() }
                        }
                        imported++
                    } catch (_: Exception) { skipped++ }
                }
                refreshLibrary()
                statusMessage.value = "Busca concluída: $imported ROM(s) adicionada(s), " +
                    "$skipped ignorada(s)."
            } catch (error: Exception) {
                statusMessage.value = "Busca não concluída: " +
                    (error.message ?: "sem permissão de leitura")
            }
        }
    }

    private fun applyRomPatch(uri: Uri) {
        if (netplaySession != null) {
            statusMessage.value = "Encerre a sessão multiplayer antes de aplicar um patch."
            return
        }
        val original = patchTarget ?: currentRomFile.value
        patchTarget = null
        if (original == null || !original.isFile) {
            statusMessage.value = "Abra primeiro a ROM que receberá o patch."
            return
        }
        lifecycleScope.launch {
            try {
                statusMessage.value = "Aplicando patch IPS/BPS sem alterar a ROM original…"
                val newName = withContext(Dispatchers.IO) {
                    val patchData = readUriBytes(uri, 16 * 1024 * 1024)
                    val newBytes = RomPatcher.apply(original.readBytes(), patchData)
                    val extension = original.extension.lowercase().let {
                        if (it == "smc") "smc" else "sfc"
                    }
                    val prefix = original.nameWithoutExtension.take(70) + "_mod"
                    var destination = File(romDirectory, "$prefix.$extension")
                    var suffix = 2
                    while (destination.exists()) {
                        destination = File(romDirectory, "$prefix-${suffix++}.$extension")
                    }
                    val tmp = File.createTempFile("rom-patch-", ".tmp", romDirectory)
                    try {
                        tmp.writeBytes(newBytes)
                        check(tmp.renameTo(destination)) { "Falha ao salvar ROM modificada." }
                    } finally { tmp.delete() }
                    destination.name
                }
                refreshLibrary()
                statusMessage.value = "Patch aplicado: $newName. " +
                    "ROM original preservada. Abra o novo jogo para testar."
            } catch (error: Exception) {
                statusMessage.value = "Patch recusado: " +
                    (error.message ?: "arquivo inválido ou incompatível")
            }
        }
    }

    private fun choosePatchForRom(name: String) {
        val file = File(romDirectory, name)
        if (!file.isFile || file.parentFile != romDirectory) {
            statusMessage.value = "ROM não encontrada na biblioteca."
            return
        }
        patchTarget = file
        patchRomLauncher.launch(arrayOf("*/*"))
    }

    private fun toggleFavorite(name: String) {
        val updated = favoriteRoms.value.toMutableSet()
        if (!updated.add(name)) updated.remove(name)
        favoriteRoms.value = updated
        prefs.edit().putStringSet("library_favorites", updated).apply()
    }

    private fun removeRom(name: String) {
        val file = File(romDirectory, name)
        if (!file.isFile || file.parentFile != romDirectory) return
        if (currentRomFile.value?.absolutePath == file.absolutePath) {
            statusMessage.value = "Saia do jogo e carregue outra ROM antes de removê-la."
            return
        }
        if (file.delete()) {
            favoriteRoms.value = favoriteRoms.value - name
            recentRoms.value = recentRoms.value.filterNot { it == name }
            prefs.edit().putStringSet("library_favorites", favoriteRoms.value)
                .putString("library_recent", org.json.JSONArray(recentRoms.value).toString()).apply()
            refreshLibrary()
        }
    }

    private fun refreshLibrary() {
        romFiles.value = romDirectory.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("sfc", "smc") }
            ?.sortedBy { it.name.lowercase() }
            ?.map { it.name }
            ?: emptyList()
        romHeaders.clear()
        romFiles.value.forEach { name ->
            romHeaders[name] = SnesRomHeader.inspect(File(romDirectory, name))
        }
        refreshCovers()
    }

    private fun renameRom(oldName: String, requestedName: String) {
        val old = File(romDirectory, oldName)
        if (!old.exists() || old.parentFile != romDirectory) {
            statusMessage.value = "A ROM não está mais na biblioteca."
            refreshLibrary()
            return
        }
        var candidate = safeFileName(requestedName)
        if (!candidate.contains('.')) candidate += "." + old.extension.lowercase()
        if (candidate.substringAfterLast('.', "").lowercase() !in setOf("sfc", "smc")) {
            statusMessage.value = "Use um nome terminado em .sfc ou .smc."
            return
        }
        val target = File(romDirectory, candidate)
        if (target.exists() && target.absolutePath != old.absolutePath) {
            statusMessage.value = "Já existe uma ROM com esse nome."
            return
        }
        if (!old.renameTo(target)) {
            statusMessage.value = "O Android não conseguiu renomear esse arquivo."
            return
        }
        if (currentRomFile.value?.absolutePath == old.absolutePath) {
            currentRomFile.value = target
            currentRomName.value = target.name
        }
        refreshLibrary()
        statusMessage.value = "ROM renomeada: " + target.name
    }

    private fun openRom(name: String) {
        if (netplaySession != null) {
            statusMessage.value = "Encerre o multiplayer antes de trocar a ROM."
            return
        }
        val file = File(romDirectory, name)
        if (!file.exists() || file.parentFile != romDirectory) {
            statusMessage.value = "Essa ROM não está mais na biblioteca."
            refreshLibrary()
            return
        }
        if (hasLoadedRom && currentRomFile.value?.absolutePath == file.absolutePath) {
            continueGame()
            return
        }
        appSettings.value = readSettings(profilePrefix(file))
        runCatching {
            NativeBridge.nativeSetVideoFilter(appSettings.value.filter.nativeId)
            NativeBridge.nativeSetOutputScale(appSettings.value.outputScale)
            NativeBridge.nativeSetGpuEnabled(appSettings.value.renderBackend == RenderBackend.GLES)
        }
        loadRomFile(file)
    }

    private fun loadRomFile(file: File) {
        gameMenuOpen.value = false
        lifecycleScope.launch {
            userPaused = true
            syncPlaybackState()
            audioOutput?.stop()
            statusMessage.value = "Carregando " + file.name + "…"
            currentRomHashPrefix.value = ""
            try {
                val bytes = withContext(Dispatchers.IO) {
                    file.inputStream().use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 64 * 1024 * 1024) { "A ROM excede o limite de 64 MiB." }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
                require(bytes.isNotEmpty()) { "O arquivo está vazio." }
                val (result, romPrefix) = withContext(Dispatchers.Default) {
                    val result = NativeBridge.nativeLoadRom(bytes, file.name)
                    val prefix = if (result == 0) RomFingerprint.prefix(bytes) else ""
                    result to prefix
                }
                if (result != 0) {
                    hasLoadedRom = false
                    currentRomFile.value = null
                    currentRomName.value = null
                    activeScreen.value = AppScreen.LIBRARY
                    statusMessage.value = "O núcleo recusou a ROM (" + result + "). Verifique se é uma ROM SNES válida."
                    syncPlaybackState()
                    return@launch
                }
                hasLoadedRom = true
                cheatEntries.value = emptyList()
                activeCheatIds.value = emptySet()
                cheatRomKey = null
                currentRomFile.value = file
                currentRomName.value = file.name
                recentRoms.value = (listOf(file.name) + recentRoms.value.filterNot { it == file.name }).take(40)
                prefs.edit().putString("library_recent", org.json.JSONArray(recentRoms.value).toString()).apply()
                currentRomHashPrefix.value = romPrefix
                userPaused = false
                paused.value = false
                audioOutput = AudioOutput(NativeBridge.nativeGetAudioSampleRate(),
                    appSettings.value.audioBufferMs).also {
                    it.setVolume(appSettings.value.volume)
                    it.setEnabled(appSettings.value.audioEnabled)
                }
                activeScreen.value = AppScreen.GAME
                enterImmersiveGameplay()
                statusMessage.value = "ROM carregada: " + file.name
                syncPlaybackState()
                searchCheats()
            } catch (error: Exception) {
                statusMessage.value = "Falha ao carregar a ROM: " + (error.message ?: "erro de leitura")
                syncPlaybackState()
            }
        }
    }

    private fun romSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun searchCheats(refresh: Boolean = false) {
        val file = currentRomFile.value ?: return
        if (netplaySession != null) return
        cheatStatus.value = "Pesquisando cheats automaticamente para " + file.nameWithoutExtension + "…"
        lifecycleScope.launch {
            try {
                val (result, hash) = withContext(Dispatchers.IO) {
                    CheatCatalog.find(this@MainActivity, file, refresh) to romSha256(file)
                }
                if (currentRomFile.value?.absolutePath != file.absolutePath || netplaySession != null) return@launch
                cheatEntries.value = result.entries
                cheatRomKey = "cheat_selection_" + hash
                val stored = prefs.getStringSet(cheatRomKey, emptySet())?.toSet() ?: emptySet()
                activeCheatIds.value = stored.intersect(result.entries.map { it.id }.toSet())
                cheatStatus.value = result.message
                applyActiveCheats()
            } catch (error: Exception) {
                if (currentRomFile.value?.absolutePath == file.absolutePath) {
                    cheatStatus.value = "Não foi possível consultar cheats: " +
                        (error.message ?: "sem conexão").take(160) +
                        ". Cheats já armazenados em cache podem funcionar offline."
                }
            }
        }
    }

    private fun toggleCheat(id: String, enabled: Boolean) {
        if (netplaySession != null) {
            cheatStatus.value = "Cheats ficam desativados durante partidas LAN/online."
            return
        }
        if (cheatEntries.value.none { it.id == id }) return
        val next = if (enabled) activeCheatIds.value + id else activeCheatIds.value - id
        if (next.size > 64) {
            cheatStatus.value = "Limite de 64 cheats simultâneos."
            return
        }
        activeCheatIds.value = next
        cheatRomKey?.let { key -> prefs.edit().putStringSet(key, next).apply() }
        applyActiveCheats()
    }

    private fun applyActiveCheats() {
        if (!hasLoadedRom || netplaySession != null) return
        val selected = cheatEntries.value.filter { it.id in activeCheatIds.value }
            .map { it.code }.take(64).toTypedArray()
        val result = runCatching { NativeBridge.nativeApplyCheats(selected) }.getOrDefault(-99)
        if (result < 0) cheatStatus.value = "Não foi possível aplicar cheats (erro $result)."
    }

    private fun createDiscoveryLock(): WifiManager.MulticastLock? = runCatching {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.createMulticastLock("MaizoSNES-LAN-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }.getOrNull()

    private fun refreshAutoRooms() {
        if (autoScanActive.value || netplaySession != null) return
        autoScanActive.value = true
        autoNetworkStatus.value = "Descobrindo servidores online…"
        lifecycleScope.launch {
            try {
                val result = OnlineRegistry.scan()
                autoRooms.value = result.rooms
                autoServers.value = result.servers
                autoNetworkStatus.value = result.error ?: (
                    "${result.rooms.size} sala(s) • ${result.servers.size} servidor(es) online. " +
                        "O MaizoSNES mede a conexão real ao transporte disponível.")
            } catch (error: Exception) {
                autoRooms.value = emptyList()
                autoServers.value = emptyList()
                autoNetworkStatus.value = "Falha na busca automática: " +
                    (error.message ?: "sem conexão")
            } finally {
                autoScanActive.value = false
            }
        }
    }

    /** One-tap matchmaking: prefer nearby LAN, then compatible online room,
     * then host online, finally open an offline LAN room. No IP input.
     */
    private fun quickMatch() {
        if (netplaySession != null) return
        val rom = currentRomFile.value
        if (!hasLoadedRom || rom == null) {
            val room = autoRooms.value.firstOrNull {
                (it.tls || it.wss) && receiveRomAllowed.value && (!it.cheats || onlineCheatsOptIn.value)
            }
            if (room != null) joinAutomatic(room)
            else autoNetworkStatus.value = "Sem ROM: selecione sala TLS " +
                "e aceite a oferta de ROM autorizada, ou abra um jogo."
            return
        }
        val currentTitle = rom.nameWithoutExtension
            .lowercase().replace(Regex("[^a-z0-9]"), "")
        val lanCandidate = availableLanRooms.value.firstOrNull { room ->
            val sameHash = room.romHash.isNotBlank() &&
                room.romHash == currentRomHashPrefix.value
            val title = room.game.lowercase().replace(Regex("[^a-z0-9]"), "")
            val sameLegacyTitle = room.romHash.isBlank() &&
                title.isNotBlank() && title == currentTitle
            (sameHash || sameLegacyTitle || receiveRomAllowed.value) &&
                (!room.cheats || onlineCheatsOptIn.value)
        }
        if (lanCandidate != null) {
            lanStatus.value = "Tentando conexão LAN automática…"
            startNetplay(lanCandidate.host, lanCandidate.port)
            return
        }
        val compatibleRoom = autoRooms.value.firstOrNull { room ->
            (room.romHash.isBlank() || room.romHash == currentRomHashPrefix.value) &&
                (!room.cheats || onlineCheatsOptIn.value)
        } ?: autoRooms.value.firstOrNull { room ->
            (room.tls || room.wss) && receiveRomAllowed.value &&
                (!room.cheats || onlineCheatsOptIn.value)
        }
        if (compatibleRoom != null) {
            joinAutomatic(compatibleRoom)
            return
        }
        if (autoServers.value.isNotEmpty()) {
            hostAutomatic()
        } else {
            lanStatus.value = "Nenhum servidor público disponível. " +
                "Abrindo sala na sua rede Wi-Fi…"
            startNetplay(null, 55435)
        }
    }

    private fun hostAutomatic() {
        if (netplaySession != null) return
        if (!hasLoadedRom || currentRomFile.value == null) {
            autoNetworkStatus.value = "Abra uma ROM para criar sala online."
            return
        }
        val fastest = autoServers.value.minByOrNull { it.latencyMs }
        if (fastest == null) {
            autoNetworkStatus.value =
                "Nenhum servidor público MaizoSNES está disponível. " +
                    "Use LAN ou configure um servidor próprio no modo avançado."
            refreshAutoRooms()
            return
        }
        autoNetworkStatus.value = "Preparando sua sala no servidor ${fastest.relay.name}…"
        startRelayRoom(null, fastest.relay.endpoint)
    }

    private fun joinAutomatic(room: AutoRoom) {
        if (netplaySession != null) return
        if (!canJoinOnlineRoom(room.romHash, room.cheats, room.tls || room.wss)) return
        autoNetworkStatus.value = "Entrando na sala ${room.id}…"
        startRelayRoom(room.id, room.endpoint)
    }

    private fun canJoinOnlineRoom(hash: String, cheats: Boolean, tls: Boolean): Boolean {
        if (cheats && !onlineCheatsOptIn.value) {
            lanStatus.value = "Essa sala utiliza cheats. Ative primeiro " +
                "'Autorizar cheats sincronizados' nas opções Multiplayer."
            autoNetworkStatus.value = lanStatus.value
            return false
        }
        val matches = hash.isBlank() || hash == currentRomHashPrefix.value
        if (!OnlineRomJoinPolicy.canJoin(hasLoadedRom, matches,
                receiveRomAllowed.value, tls)) {
            lanStatus.value = "ROM ausente/diferente. Para receber, ative " +
                "'Aceitar ROM' e escolha uma sala com relay TLS confiável."
            autoNetworkStatus.value = lanStatus.value
            return false
        }
        return true
    }

    private fun setOnlineCheatsOptIn(enabled: Boolean) {
        if (netplaySession != null) return
        onlineCheatsOptIn.value = enabled
        prefs.edit().putBoolean("online_cheats_opt_in", enabled).apply()
        lanStatus.value = if (enabled)
            "Cheats online autorizados. O host controla os códigos e todos " +
                "são aplicados antes da partida. Não é possível trocar durante o jogo."
        else "Cheats online desativados. Partidas com cheats serão recusadas."
    }

    private fun changeIceOverrides(value: String) {
        if (netplaySession != null) return
        // Private app preferences, never exported by debug.
        iceServerOverrides.value = value.take(1500)
        prefs.edit().putString("ice_servers_override", iceServerOverrides.value).apply()
        iceTestStatus.value = "Configuração salva. Faça teste local ICE."
    }

    private fun testIceSettings() {
        if (netplaySession != null) {
            iceTestStatus.value = "Encerre a partida antes de testar ICE."
            return
        }
        iceTestStatus.value = "Testando STUN/TURN (até 14 segundos)…"
        lifecycleScope.launch(Dispatchers.IO) {
            val result = WebRtcRollbackTransport.probeIce(
                applicationContext, relayAddress.value, iceServerOverrides.value)
            withContext(Dispatchers.Main) { iceTestStatus.value = result }
        }
    }

    private fun changeRelayAddress(value: String) {
        if (netplaySession != null) return
        relayAddress.value = value.take(300)
        prefs.edit().putString("relay_server_url", relayAddress.value).apply()
        relayRooms.value = emptyList()
    }

    private fun refreshRelayRooms() {
        if (scanningRelay.value || netplaySession != null || relayAddress.value.isBlank()) return
        scanningRelay.value = true
        lifecycleScope.launch {
            try {
                val listing = withContext(Dispatchers.IO) {
                    RelayDirectory.list(relayAddress.value)
                }
                relayRooms.value = listing.rooms
                lanStatus.value = "${listing.rooms.size} sala(s) no servidor. " +
                    if (listing.wss) "HTTPS/WSS disponível em ${relayAddress.value}."
                    else "TCP ${listing.serverHost}:${listing.relayPort}"
            } catch (error: Exception) {
                relayRooms.value = emptyList()
                lanStatus.value = "Servidor de salas inacessível: " +
                    (error.message ?: "erro na rede")
            } finally { scanningRelay.value = false }
        }
    }

    private fun hostRelay() {
        if (netplaySession != null || !hasLoadedRom || currentRomFile.value == null) {
            lanStatus.value = "Abra uma ROM antes de criar uma sala."
            return
        }
        startRelayRoom(null)
    }

    private fun joinRelay(roomId: String) {
        if (netplaySession != null) return
        val room = relayRooms.value.firstOrNull { it.id == roomId }
        if (room != null && !canJoinOnlineRoom(room.romHash, room.cheats, room.tls || room.wss)) return
        if (!hasLoadedRom && room == null) {
            lanStatus.value = "Atualize a lista de salas antes de entrar sem ROM."
            return
        }
        startRelayRoom(roomId)
    }

    private fun startRelayRoom(joinRoomId: String?,
        serverEndpoint: String = relayAddress.value) {
        if (serverEndpoint.isBlank()) {
            lanStatus.value = "Informe primeiro o IP ou URL do servidor."
            autoNetworkStatus.value = lanStatus.value
            return
        }
        lifecycleScope.launch {
            try {
                lanStatus.value = "Consultando servidor de salas…"
                autoNetworkStatus.value = lanStatus.value
                val listing = withContext(Dispatchers.IO) {
                    RelayDirectory.list(serverEndpoint)
                }
                val roomId = joinRoomId ?: java.util.UUID.randomUUID()
                    .toString().replace("-", "").take(16)
                if (joinRoomId != null && listing.rooms.none { it.id == roomId }) {
                    lanStatus.value = "A sala não está mais disponível. Atualize a lista."
                    autoNetworkStatus.value = lanStatus.value
                    return@launch
                }
                startNetplay(
                    host = if (joinRoomId == null) null else listing.serverHost,
                    port = listing.relayPort,
                    relayRoomId = roomId,
                    relayServerHost = listing.serverHost,
                    relayTls = listing.tls,
                    wssEndpoint = if (listing.wss && (joinRoomId == null ||
                        listing.rooms.firstOrNull { it.id == roomId }?.wss == true))
                        serverEndpoint else null
                )
            } catch (error: Exception) {
                lanStatus.value = "Falha ao conectar servidor: " +
                    (error.message ?: "erro de rede")
                autoNetworkStatus.value = lanStatus.value
            }
        }
    }

    private fun scanLan() {
        if (scanningLan.value || netplaySession != null) return
        scanningLan.value = true
        lifecycleScope.launch {
            val discoveryLock = createDiscoveryLock()
            try {
                availableLanRooms.value = withContext(Dispatchers.IO) { LanDiscovery.scan() }
                if (availableLanRooms.value.isEmpty()) {
                    lanStatus.value = "Nenhuma sala encontrada via Wi-Fi. Use IP e porta manualmente."
                }
            } catch (error: Exception) {
                lanStatus.value = "Busca LAN indisponível: " + (error.message ?: "rede")
            } finally {
                runCatching { discoveryLock?.release() }
                scanningLan.value = false
            }
        }
    }

    private fun installReceivedRom(name: String, bytes: ByteArray): Boolean {
        // Invoked by TCP worker, never by the Android UI thread.
        val cleaned = safeFileName(name)
        val extension = cleaned.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("sfc", "smc") || bytes.isEmpty() ||
            bytes.size > 64 * 1024 * 1024) return false
        var destination = File(romDirectory, cleaned)
        var n = 2
        while (destination.exists()) {
            destination = File(romDirectory,
                cleaned.substringBeforeLast('.') + " (" + n + ")." + extension)
            n++
            if (n > 10000) return false
        }
        val temporary = File(romDirectory, destination.name + ".importing")
        return runCatching {
            temporary.writeBytes(bytes)
            check(temporary.renameTo(destination)) { "Não foi possível salvar ROM recebida." }
            val result = NativeBridge.nativeLoadRom(bytes, destination.name)
            if (result != 0) {
                destination.delete()
                return@runCatching false
            }
            val installed = destination
            val receivedRomPrefix = RomFingerprint.prefix(bytes)
            runOnUiThread {
                audioOutput?.stop()
                audioOutput = AudioOutput(NativeBridge.nativeGetAudioSampleRate()).also {
                    it.setVolume(appSettings.value.volume)
                    it.setEnabled(appSettings.value.audioEnabled)
                }
                hasLoadedRom = true
                currentRomFile.value = installed
                currentRomName.value = installed.name
                currentRomHashPrefix.value = receivedRomPrefix
                appSettings.value = readSettings(profilePrefix(installed))
                runCatching {
            NativeBridge.nativeSetVideoFilter(appSettings.value.filter.nativeId)
            NativeBridge.nativeSetOutputScale(appSettings.value.outputScale)
            NativeBridge.nativeSetGpuEnabled(appSettings.value.renderBackend == RenderBackend.GLES)
        }
                cheatEntries.value = emptyList()
                activeCheatIds.value = emptySet()
                cheatRomKey = null
                cheatStatus.value = "Cheats desativados durante o multiplayer."
                refreshLibrary()
                statusMessage.value = "ROM autorizada recebida na biblioteca: " + installed.name
            }
            true
        }.getOrElse { temporary.delete(); false }
    }

    private fun continueGame() {
        if (!hasLoadedRom) {
            statusMessage.value = "Escolha uma ROM na biblioteca para começar."
            activeScreen.value = AppScreen.LIBRARY
            return
        }
        userPaused = false
        paused.value = false
        activeScreen.value = AppScreen.GAME
        enterImmersiveGameplay()
        syncPlaybackState()
    }

    private fun enterImmersiveGameplay() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    private fun exitGameplay() {
        if (gameMenuOpen.value) setGameMenuOpen(false)
        netplaySession?.stop()
        userPaused = true
        paused.value = true
        activeScreen.value = AppScreen.LIBRARY
        appSettings.value = readSettings("profile_global_")
        runCatching {
            NativeBridge.nativeSetVideoFilter(appSettings.value.filter.nativeId)
            NativeBridge.nativeSetOutputScale(appSettings.value.outputScale)
            NativeBridge.nativeSetGpuEnabled(appSettings.value.renderBackend == RenderBackend.GLES)
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        syncPlaybackState()
    }

    private fun setGameMenuOpen(open: Boolean) {
        if (activeScreen.value != AppScreen.GAME || gameMenuOpen.value == open) return
        gameMenuOpen.value = open
        if (netplaySession == null) {
            if (open) pausedBeforeGameMenu = userPaused
            userPaused = if (open) true else pausedBeforeGameMenu
            pressedInputs.set(0)
            gamepadKeyMasks.clear()
            gamepadAxisMasks.clear()
            publishInputs()
            syncPlaybackState()
        } else if (open) {
            statusMessage.value = "Menu online: o jogo não é pausado individualmente para evitar dessincronização."
        }
    }

    private fun togglePause() {
        if (!hasLoadedRom) return
        if (netplaySession != null) {
            statusMessage.value = "Pausa individual não é permitida durante a sincronização."
            return
        }
        userPaused = !userPaused
        paused.value = userPaused
        syncPlaybackState()
        statusMessage.value = if (userPaused) "Jogo pausado." else "Jogo retomado."
    }

    private fun profilePrefix(file: File?): String = "profile_" + (file?.nameWithoutExtension ?: "global") + "_"

    private fun readSettings(prefix: String): AppSettings {
        // A game profile inherits global settings until the player customizes it.
        // Once saved in-game, the full snapshot is intentionally independent.
        val global = "profile_global_"
        fun key(suffix: String): String =
            if (prefix != global && !prefs.contains(prefix + suffix)) global + suffix
            else prefix + suffix
        val aspectName = prefs.getString(key("aspect"), AspectMode.CRT.name) ?: AspectMode.CRT.name
        val aspect = runCatching { AspectMode.valueOf(aspectName) }.getOrDefault(AspectMode.CRT)
        val selectedName = prefs.getString(key("touch_preset"), TouchPreset.VIRTUAL_GAMEPAD.name)
            ?: TouchPreset.VIRTUAL_GAMEPAD.name
        val preset = runCatching { TouchPreset.valueOf(selectedName) }
            .getOrDefault(TouchPreset.VIRTUAL_GAMEPAD)
        val presetDefaults = preset.defaultLayout()
        val legacy = TouchLayout(
            enabled = prefs.getBoolean(key("touch_enabled"), presetDefaults.enabled),
            dpadX = prefs.getFloat(key("dpad_x"), presetDefaults.dpadX),
            dpadY = prefs.getFloat(key("dpad_y"), presetDefaults.dpadY),
            buttonsX = prefs.getFloat(key("buttons_x"), presetDefaults.buttonsX),
            buttonsY = prefs.getFloat(key("buttons_y"), presetDefaults.buttonsY),
            scale = prefs.getFloat(key("touch_scale"), presetDefaults.scale),
            opacity = prefs.getFloat(key("touch_opacity"), presetDefaults.opacity),
            preset = preset
        )
        val touch = TouchLayoutCodec.decode(
            prefs.getString(key("touch_preset_" + preset.name), null), preset) ?: legacy
        val filterName = prefs.getString(key("video_filter"), VideoFilter.ORIGINAL.name)
            ?: VideoFilter.ORIGINAL.name
        val filter = runCatching { VideoFilter.valueOf(filterName) }
            .getOrDefault(VideoFilter.ORIGINAL)
        return AppSettings(
            aspect = aspect,
            touch = touch,
            audioEnabled = prefs.getBoolean(key("audio_enabled"), true),
            volume = prefs.getFloat(key("audio_volume"), 1f),
            filter = filter,
            outputScale = prefs.getInt(key("video_output_scale"), 1).coerceIn(1, 4),
            renderBackend = runCatching { RenderBackend.valueOf(
                prefs.getString(key("video_backend"), RenderBackend.CPU.name)
                    ?: RenderBackend.CPU.name) }.getOrDefault(RenderBackend.CPU),
            gpuShader = runCatching { GpuShader.valueOf(
                prefs.getString(key("gpu_shader"), GpuShader.NEAREST.name)
                    ?: GpuShader.NEAREST.name) }.getOrDefault(GpuShader.NEAREST),
            performanceHud = prefs.getBoolean(key("performance_hud"), false),
            automaticCovers = prefs.getBoolean("library_covers_enabled", true),
            automaticBackup = prefs.getBoolean("automatic_netplay_backups", true),
            audioBufferMs = prefs.getInt(key("audio_buffer_ms"), 40).takeIf {
                it in setOf(25, 40, 60)
            } ?: 40
        )
    }

    private fun persistSettings(next: AppSettings) {
        val backendChanged = appSettings.value.renderBackend != next.renderBackend
        val audioBufferChanged = appSettings.value.audioBufferMs != next.audioBufferMs
        if (backendChanged) {
            surfaceAttached = false
            runCatching { NativeBridge.nativeSetPaused(true) }
            runCatching { NativeBridge.nativeSetSurface(null) }
        }
        appSettings.value = next
        val prefix = profilePrefix(if (activeScreen.value == AppScreen.GAME) currentRomFile.value else null)
        val coversChanged = next.automaticCovers != prefs.getBoolean("library_covers_enabled", true)
        prefs.edit()
            .putString(prefix + "aspect", next.aspect.name)
            .putString(prefix + "video_filter", next.filter.name)
            .putInt(prefix + "audio_buffer_ms", next.audioBufferMs)
            .putInt(prefix + "video_output_scale", next.outputScale.coerceIn(1, 4))
            .putString(prefix + "video_backend", next.renderBackend.name)
            .putString(prefix + "gpu_shader", next.gpuShader.name)
            .putBoolean(prefix + "performance_hud", next.performanceHud)
            .putString(prefix + "touch_preset", next.touch.preset.name)
            .putString(prefix + "touch_preset_" + next.touch.preset.name,
                TouchLayoutCodec.encode(next.touch))
            .putBoolean("library_covers_enabled", next.automaticCovers)
            .putBoolean("automatic_netplay_backups", next.automaticBackup)
            .putBoolean(prefix + "touch_enabled", next.touch.enabled)
            .putFloat(prefix + "dpad_x", next.touch.dpadX)
            .putFloat(prefix + "dpad_y", next.touch.dpadY)
            .putFloat(prefix + "buttons_x", next.touch.buttonsX)
            .putFloat(prefix + "buttons_y", next.touch.buttonsY)
            .putFloat(prefix + "touch_scale", next.touch.scale)
            .putFloat(prefix + "touch_opacity", next.touch.opacity)
            .putBoolean(prefix + "audio_enabled", next.audioEnabled)
            .putFloat(prefix + "audio_volume", next.volume)
            .apply()
        if (audioBufferChanged && hasLoadedRom) {
            audioOutput?.stop()
            audioOutput = AudioOutput(NativeBridge.nativeGetAudioSampleRate(),
                next.audioBufferMs)
            syncPlaybackState()
        }
        audioOutput?.setEnabled(next.audioEnabled)
        audioOutput?.setVolume(next.volume)
        runCatching {
            NativeBridge.nativeSetVideoFilter(next.filter.nativeId)
            NativeBridge.nativeSetOutputScale(next.outputScale)
            NativeBridge.nativeSetGpuEnabled(next.renderBackend == RenderBackend.GLES)
        }
        if (coversChanged) refreshCovers()
    }

    private fun changeCoreOption(key: String, value: String) {
        if (netplaySession != null) {
            statusMessage.value = "Desconecte antes de alterar opções do núcleo."
            return
        }
        val definition = CoreOptionDefinitions.all.firstOrNull { it.key == key } ?: return
        if (value !in definition.values) return
        coreSettings[key] = value
        prefs.edit().putString("core_" + key, value).apply()
        runCatching { NativeBridge.nativeSetCoreOption(key, value) }
            .onFailure { statusMessage.value = "Não foi possível aplicar a opção: " + (it.message ?: key) }
    }

    private fun slotFile(slot: Int): File? {
        if (slot !in 1..3) return null
        val rom = currentRomFile.value ?: return null
        val key = rom.nameWithoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_").take(90)
        return File(stateDirectory, key + ".slot" + slot + ".mzstate")
    }

    private fun saveSlot(slot: Int) {
        if (netplaySession != null) {
            statusMessage.value = "Save states e reset ficam bloqueados no netplay."
            return
        }
        if (!hasLoadedRom) {
            statusMessage.value = "Carregue uma ROM antes de salvar."
            return
        }
        val destination = slotFile(slot) ?: return
        lifecycleScope.launch {
            try {
                statusMessage.value = "Salvando no slot " + slot + "…"
                val bytes = withContext(Dispatchers.Default) { NativeBridge.nativeSerializeState() }
                    ?: throw IllegalStateException("O núcleo não conseguiu criar o estado.")
                withContext(Dispatchers.IO) {
                    val temporary = File(destination.parentFile, destination.name + ".tmp")
                    temporary.writeBytes(bytes)
                    if (!temporary.renameTo(destination)) {
                        temporary.delete()
                        throw IllegalStateException("Não foi possível finalizar o arquivo do slot.")
                    }
                }
                statusMessage.value = "Slot " + slot + " salvo (" + (bytes.size / 1024) + " KiB)."
            } catch (error: Exception) {
                statusMessage.value = "Falha ao salvar o slot: " + (error.message ?: "erro desconhecido")
            }
        }
    }

    private fun loadSlot(slot: Int) {
        if (netplaySession != null) {
            statusMessage.value = "Save states e reset ficam bloqueados no netplay."
            return
        }
        if (!hasLoadedRom) {
            statusMessage.value = "Carregue a ROM correspondente antes de restaurar."
            return
        }
        val source = slotFile(slot) ?: return
        if (!source.exists()) {
            statusMessage.value = "O slot " + slot + " ainda está vazio."
            return
        }
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { source.readBytes() }
                applyState(bytes, "slot " + slot)
            } catch (error: Exception) {
                syncPlaybackState()
                statusMessage.value = "Falha ao carregar o slot: " + (error.message ?: "arquivo inválido")
            }
        }
    }

    private fun defaultStateName(): String = safeFileName((currentRomFile.value?.nameWithoutExtension ?: "maizosnes") + ".state")

    private fun exportState(uri: Uri) {
        if (netplaySession != null) {
            statusMessage.value = "Save states e reset ficam bloqueados no netplay."
            return
        }
        if (!hasLoadedRom) {
            statusMessage.value = "Carregue uma ROM antes de exportar o estado."
            return
        }
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) { NativeBridge.nativeSerializeState() }
                    ?: throw IllegalStateException("O núcleo não conseguiu serializar o estado.")
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: throw IllegalStateException("Não foi possível gravar o arquivo.")
                }
                statusMessage.value = "Estado exportado (" + (bytes.size / 1024) + " KiB)."
            } catch (error: Exception) {
                statusMessage.value = "Falha ao exportar estado: " + (error.message ?: "erro desconhecido")
            }
        }
    }

    private fun importState(uri: Uri) {
        if (netplaySession != null) {
            statusMessage.value = "Save states e reset ficam bloqueados no netplay."
            return
        }
        if (!hasLoadedRom) {
            statusMessage.value = "Carregue a ROM correspondente antes de importar um estado."
            return
        }
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { readUriBytes(uri, 64 * 1024 * 1024) }
                applyState(bytes, "arquivo selecionado")
            } catch (error: Exception) {
                syncPlaybackState()
                statusMessage.value = "Falha ao importar estado: " + (error.message ?: "arquivo inválido")
            }
        }
    }

    private suspend fun applyState(bytes: ByteArray, label: String) {
        audioOutput?.stop()
        val result = withContext(Dispatchers.Default) { NativeBridge.nativeUnserializeState(bytes) }
        syncPlaybackState()
        statusMessage.value = when (result) {
            0 -> "Estado restaurado: " + label + "."
            -8 -> "Este estado pertence a outra ROM."
            else -> "O núcleo recusou o estado (" + result + "). Confira se o arquivo corresponde à ROM."
        }
    }

    private fun resetGame() {
        if (netplaySession != null) {
            statusMessage.value = "Save states e reset ficam bloqueados no netplay."
            return
        }
        if (!hasLoadedRom) return
        lifecycleScope.launch {
            audioOutput?.stop()
            val result = withContext(Dispatchers.Default) { NativeBridge.nativeResetGame() }
            syncPlaybackState()
            statusMessage.value = if (result == 0) "Jogo reiniciado. Progresso não salvo perdido."
            else "Falha ao reiniciar o jogo (" + result + ")."
        }
    }

    private fun snesButtonForKey(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> 4
        KeyEvent.KEYCODE_DPAD_DOWN -> 5
        KeyEvent.KEYCODE_DPAD_LEFT -> 6
        KeyEvent.KEYCODE_DPAD_RIGHT -> 7
        KeyEvent.KEYCODE_BUTTON_B -> 0
        KeyEvent.KEYCODE_BUTTON_Y -> 1
        KeyEvent.KEYCODE_BUTTON_SELECT -> 2
        KeyEvent.KEYCODE_BUTTON_START -> 3
        KeyEvent.KEYCODE_BUTTON_A -> 8
        KeyEvent.KEYCODE_BUTTON_X -> 9
        KeyEvent.KEYCODE_BUTTON_L1 -> 10
        KeyEvent.KEYCODE_BUTTON_R1 -> 11
        KeyEvent.KEYCODE_BUTTON_L2 -> 12
        KeyEvent.KEYCODE_BUTTON_R2 -> 13
        KeyEvent.KEYCODE_BUTTON_THUMBL -> 14
        KeyEvent.KEYCODE_BUTTON_THUMBR -> 15
        else -> null
    }

    private fun gamepadIds(): List<Int> =
        InputDevice.getDeviceIds().filter { id ->
            val source = InputDevice.getDevice(id)?.sources ?: 0
            (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }.sorted()

    private fun switchGamepadTab(direction: Int) {
        if (activeScreen.value == AppScreen.GAME || shareConsentVisible.value) return
        val tabs = arrayOf(AppScreen.LIBRARY, AppScreen.MULTIPLAYER,
            AppScreen.SETTINGS, AppScreen.CREDITS)
        val position = tabs.indexOf(activeScreen.value).coerceAtLeast(0)
        activeScreen.value = tabs[(position + direction + tabs.size) % tabs.size]
        lastUiDirection = 0
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val pad = event.deviceId in gamepadIds()
        val playing = activeScreen.value == AppScreen.GAME

        // Only central PS/Xbox MODE/Guide enters the frontend menu when Android
        // exposes it. Physical START must remain SNES Start (input bit 3).
        if (playing && pad && GamepadKeyRouting.isMenuButton(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                setGameMenuOpen(!gameMenuOpen.value)
            return true
        }
        if (!playing || gameMenuOpen.value) {
            if (pad && !playing && event.keyCode in
                setOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1)) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                    switchGamepadTab(if (event.keyCode == KeyEvent.KEYCODE_BUTTON_R1) 1 else -1)
                return true
            }
            if (pad && event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                    onBackPressedDispatcher.onBackPressed()
                return true
            }
            if (pad && event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                // Compose handles ENTER for cards, Switch, Button, and FilterChip;
                // send both key edges to preserve real click semantics.
                val enter = KeyEvent(event.downTime, event.eventTime, event.action,
                    KeyEvent.KEYCODE_ENTER, event.repeatCount, event.metaState,
                    event.deviceId, event.scanCode, event.flags, event.source)
                return super.dispatchKeyEvent(enter)
            }
            return super.dispatchKeyEvent(event)
        }
        val bit = snesButtonForKey(event.keyCode) ?: return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP)
            return super.dispatchKeyEvent(event)
        if (pad) {
            val mask = 1 shl bit
            val previous = gamepadKeyMasks[event.deviceId] ?: 0
            gamepadKeyMasks[event.deviceId] =
                if (event.action == KeyEvent.ACTION_DOWN) previous or mask
                else previous and mask.inv()
            publishInputs()
        } else updateInput(bit, event.action == KeyEvent.ACTION_DOWN)
        return true
    }

    private fun moveUiFocus(keyCode: Int, event: MotionEvent) {
        val now = android.os.SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
            event.deviceId, 0, 0, event.source)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
            event.deviceId, 0, 0, event.source)
        super.dispatchKeyEvent(down)
        super.dispatchKeyEvent(up)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val joystick = (event.source and InputDevice.SOURCE_JOYSTICK) ==
            InputDevice.SOURCE_JOYSTICK
        if (joystick && event.action == MotionEvent.ACTION_MOVE) {
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val x = if (abs(hatX) > 0.5f) hatX else event.getAxisValue(MotionEvent.AXIS_X)
            val y = if (abs(hatY) > 0.5f) hatY else event.getAxisValue(MotionEvent.AXIS_Y)
            if (activeScreen.value != AppScreen.GAME || gameMenuOpen.value) {
                val direction = when {
                    y < -0.60f -> KeyEvent.KEYCODE_DPAD_UP
                    y > 0.60f -> KeyEvent.KEYCODE_DPAD_DOWN
                    x < -0.60f -> KeyEvent.KEYCODE_DPAD_LEFT
                    x > 0.60f -> KeyEvent.KEYCODE_DPAD_RIGHT
                    else -> 0
                }
                val now = android.os.SystemClock.uptimeMillis()
                if (direction != 0 &&
                    (direction != lastUiDirection || now - lastUiDirectionAt >= 225)) {
                    lastUiDirection = direction
                    lastUiDirectionAt = now
                    moveUiFocus(direction, event)
                } else if (direction == 0) lastUiDirection = 0
                return true
            }
            var mask = 0
            if (x < -0.55f) mask = mask or (1 shl 6)
            if (x > 0.55f) mask = mask or (1 shl 7)
            if (y < -0.55f) mask = mask or (1 shl 4)
            if (y > 0.55f) mask = mask or (1 shl 5)
            gamepadAxisMasks[event.deviceId] = mask
            publishInputs()
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun currentLocalInput(): Int {
        val first = gamepadIds().firstOrNull()
        return pressedInputs.get() or (if (first == null) 0 else
            (gamepadKeyMasks[first] ?: 0) or (gamepadAxisMasks[first] ?: 0))
    }

    private fun publishInputs() {
        if (netplaySession != null) return // Network thread applies per-frame P1/P2.
        val ids = gamepadIds()
        val first = ids.firstOrNull()
        val second = ids.getOrNull(1)
        // Touch + one gamepad: the gamepad becomes player 2.
        // With two gamepads: first is P1 and second is P2.
        val oneGamepadAsP2 = localPlayer2Enabled.value && ids.size == 1
        val p1 = pressedInputs.get() or
            (if (first == null || oneGamepadAsP2) 0 else
                (gamepadKeyMasks[first] ?: 0) or (gamepadAxisMasks[first] ?: 0))
        val p2Device = if (oneGamepadAsP2) first else second
        val p2 = if (!localPlayer2Enabled.value || p2Device == null) 0 else
            (gamepadKeyMasks[p2Device] ?: 0) or (gamepadAxisMasks[p2Device] ?: 0)
        runCatching {
            NativeBridge.nativeSetInput(p1)
            NativeBridge.nativeSetPlayer2Input(p2)
        }
    }

    private fun updateInput(bit: Int, isPressed: Boolean) {
        if (bit !in 0..15) return
        val mask = 1 shl bit
        var old: Int
        var next: Int
        do {
            old = pressedInputs.get()
            next = if (isPressed) old or mask else old and mask.inv()
        } while (!pressedInputs.compareAndSet(old, next))
        publishInputs()
    }

    private fun setLocalMultiplayer(enabled: Boolean) {
        if (netplaySession != null) return
        localPlayer2Enabled.value = enabled
        publishInputs()
        lanStatus.value = if (enabled) "Modo local: 1 controle = P2 e toque = P1; 2 controles = P1/P2."
        else "Modo local de dois jogadores desativado."
    }

    private fun hostMultiplayer(port: Int) = startNetplay(null, port)

    private fun joinMultiplayer(host: String, port: Int) = startNetplay(host, port)

    private fun restoreMultiplayerBackup(stage: String) {
        if (stage !in setOf("before", "after") || netplaySession != null || !hasLoadedRom) {
            statusMessage.value = "Encerre a sessão multiplayer e carregue a ROM correta."
            return
        }
        val rom = currentRomFile.value ?: return
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val hash = romSha256(rom).take(22)
                    val name = "multiplayer_" + hash + "_" + stage + ".mzstate"
                    val snapshot = File(stateDirectory, name)
                    require(snapshot.isFile) { "Backup não encontrado para esta ROM." }
                    require(snapshot.length() in 29L..(64L * 1024 * 1024 + 28)) {
                        "Tamanho do backup inválido."
                    }
                    snapshot.readBytes()
                }
                if (currentRomFile.value?.absolutePath != rom.absolutePath) return@launch
                applyState(bytes, if (stage == "before") "antes do multiplayer"
                    else "depois do multiplayer")
            } catch (error: Exception) {
                statusMessage.value = "Backup não restaurado: " + (error.message ?: "arquivo ausente")
            }
        }
    }

    private fun createMultiplayerBackup(stage: String) {
        if (!appSettings.value.automaticBackup || !hasLoadedRom) return
        val rom = currentRomFile.value ?: return
        // Snapshot is captured before switching clocks, or immediately after
        // the network session stops. Writes are independent of manual slots.
        val snapshot = runCatching { NativeBridge.nativeSerializeState() }.getOrNull()
            ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val hash = romSha256(rom).take(22)
                val destination = File(stateDirectory, "multiplayer_" + hash +
                    "_" + stage + ".mzstate")
                val partial = File(stateDirectory, destination.name + ".partial")
                partial.writeBytes(snapshot)
                if (!partial.renameTo(destination)) {
                    destination.delete()
                    check(partial.renameTo(destination)) {
                        "Falha ao gravar cópia de segurança."
                    }
                }
            }.onFailure { error ->
                runOnUiThread {
                    statusMessage.value = "Aviso: backup multiplayer não salvo: " +
                        (error.message ?: "erro de armazenamento")
                }
            }
        }
    }

    private fun resolveIncomingRomOffer(approved: Boolean) {
        incomingRomOffer.value = null
        pendingRomApproval.getAndSet(null)?.offer(approved)
    }

    /** Called only on the network worker; UI thread is free to show the dialog. */
    private fun askIncomingRomApproval(name: String, hash: String, bytes: Int): Boolean {
        val response = java.util.concurrent.ArrayBlockingQueue<Boolean>(1)
        if (!pendingRomApproval.compareAndSet(null, response)) return false
        runOnUiThread { incomingRomOffer.value = RomOffer(name, hash, bytes) }
        return try { response.poll(75, java.util.concurrent.TimeUnit.SECONDS) ?: false }
        catch (_: Exception) { false }
        finally {
            pendingRomApproval.compareAndSet(response, null)
            runOnUiThread {
                if (incomingRomOffer.value?.digest == hash) incomingRomOffer.value = null
            }
        }
    }

    private fun resolveShareConsent(authorized: Boolean) {
        val request = pendingHost
        pendingHost = null
        shareConsentVisible.value = false
        hostAnswered = true
        hostApproved = authorized
        // Declining sharing still starts the multiplayer room without ROM transfer.
        if (request != null) startNetplay(null, request.port, request.roomId, request.relayHost,
                request.relayTls, request.wssEndpoint)
    }

    private fun startNetplay(host: String?, port: Int,
        relayRoomId: String? = null, relayServerHost: String? = null,
        relayTls: Boolean = false, wssEndpoint: String? = null) {
        if (netplaySession != null) return
        // Per-session rights check; includes public-relay rooms, not only LAN.
        if (host == null && shareRomAllowed.value && !hostAnswered) {
            pendingHost = PendingHost(port, relayRoomId, relayServerHost,
                relayTls, wssEndpoint)
            shareConsentVisible.value = true
            return
        }
        val hostShareThisSession = host == null && shareRomAllowed.value &&
            hostAnswered && hostApproved
        hostApproved = false
        hostAnswered = false
        if (host == null && (!hasLoadedRom || currentRomFile.value == null)) {
            lanStatus.value = "Como host, abra uma ROM antes de criar a sala."
            return
        }
        if (host != null && !hasLoadedRom && !receiveRomAllowed.value) {
            lanStatus.value = "Abra a mesma ROM ou permita receber ROM homebrew/autorizada."
            return
        }
        if (port !in 1024..65535 || (host != null && host.isBlank())) {
            lanStatus.value = "Informe endereço e porta válidos (1024–65535)."
            return
        }
        userPaused = true
        syncPlaybackState()
        createMultiplayerBackup("before")
        lastNetworkMetrics.value = null
        lastRollbackCounter = 0L
        networkDebug.value = NetworkDebugState(
            active = true,
            mode = "Negociando " + netcodeMode.value.label,
            transport = when {
                wssEndpoint != null -> "TCP via Railway (WSS)"
                relayRoomId != null -> if (relayTls) "TCP TLS relay" else "TCP relay"
                else -> "UDP direto (tentativa) / TCP fallback"
            },
            peer = if (host == null) "P1: porta " + port else
                relayRoomId ?: host + ":" + port,
            state = "Verificação de ROM e transporte")
        val current = currentRomFile.value
        lateinit var session: NetplaySession
        val originalCoreOptions = coreSettings.toMap()
        var sessionCoreOptionsWereChanged = false
        session = NetplaySession(
            rom = current,
            coreOptions = coreSettings.toMap(),
            localInput = { currentLocalInput() },
            prepare = { enabled -> NativeBridge.nativeSetNetplayMode(enabled) },
            saveState = { NativeBridge.nativeSerializeState() },
            loadState = { NativeBridge.nativeUnserializeState(it) },
            step = { player1, player2 -> NativeBridge.nativeNetplayStep(player1, player2) },
            stateHash = { NativeBridge.nativeNetplayStateHash() },
            framePeriodNs = { NativeBridge.nativeGetFramePeriodNs() },
            allowOnlineCheats = onlineCheatsOptIn.value,
            hostCheatCodes = if (host == null && onlineCheatsOptIn.value)
                cheatEntries.value.filter { it.id in activeCheatIds.value }
                    .map { it.code }.take(64) else emptyList(),
            applyOnlineCheats = { codes -> NativeBridge.nativeApplyOnlineCheats(codes) },
            applyHostCoreOptions = { received ->
                // Never persist host options to local profiles. Validate every
                // core key/value before touching native state, and restore on exit.
                val allowed = CoreOptionDefinitions.all.associateBy { it.key }
                if (received.keys != originalCoreOptions.keys ||
                    received.any { (key, value) -> value !in (allowed[key]?.values ?: emptyList()) }) {
                    false
                } else {
                    val ok = runCatching {
                        received.forEach { (key, value) ->
                            NativeBridge.nativeSetCoreOption(key, value)
                        }
                        true
                    }.getOrDefault(false)
                    sessionCoreOptionsWereChanged = true // restore even on partial failure
                    ok
                }
            },
            mode = connectionMode.value,
            netcodeMode = netcodeMode.value,
            webrtcContext = applicationContext,
            customIceServers = iceServerOverrides.value,
            latencyMode = netplayLatencyMode.value,
            onMetrics = { metrics ->
                runOnUiThread {
                    if (netplaySession === session) {
                        lastNetworkMetrics.value = metrics
                        val old = networkDebug.value
                        val rtt = metrics.sampledRttMs.takeIf { it.isFinite() && it > 0.0 }
                        val total = if (metrics.rollback) metrics.rollbackCount else 0L
                        networkDebug.value = old.copy(
                            active = true,
                            mode = if (metrics.rollback) "Rollback (GekkoNet)"
                                else "Lockstep (MZNP TCP)",
                            transport = if (metrics.webRtc) "WebRTC DataChannel"
                                else if (metrics.rollback) "UDP direto"
                                else if (wssEndpoint != null) "TCP via Railway (WSS)"
                                else if (relayRoomId != null) "TCP relay" else "TCP direto",
                            rttMin = if (rtt == null) old.rttMin
                                else minOf(old.rttMin ?: rtt, rtt),
                            rttMax = if (rtt == null) old.rttMax
                                else maxOf(old.rttMax ?: rtt, rtt),
                            rttAvg = if (rtt == null) old.rttAvg
                                else if (old.rttAvg == null) rtt
                                else old.rttAvg!! * 0.85 + rtt * 0.15,
                            jitterMs = metrics.jitterMs.takeIf { metrics.rollback },
                            rollbacksPerSec = if (metrics.rollback)
                                (total - lastRollbackCounter).coerceAtLeast(0) else 0L,
                            totalRollbacks = total, delay = metrics.inputDelayFrames,
                            iceState = metrics.iceState,
                            natType = metrics.natType, turnUsed = metrics.turnUsed,
                            frame = metrics.frame,
                            saveLoadMs = metrics.saveLoadMs.takeIf { it > 0.0 },
                            peakFrameStallMs = maxOf(old.peakFrameStallMs,
                                metrics.maxFrameStallMs),
                            presentationStride = metrics.presentationStride,
                            limitedSaving = metrics.limitedSaving,
                            state = "Partida em andamento")
                        lastRollbackCounter = total
                        lanStatus.value = if (metrics.rollback)
                            String.format(java.util.Locale.US,
                                "GekkoNet • quadro %d • RTT %.0f ms • jitter %.1f ms • " +
                                    "rollbacks %d • atraso %d quadros",
                                metrics.frame, metrics.sampledRttMs, metrics.jitterMs,
                                metrics.rollbackCount, metrics.inputDelayFrames)
                        else String.format(java.util.Locale.US,
                            "Quadro %d • espera peer %.1f ms (pico %.1f ms) • " +
                                "%.1f FPS • atrasos %d/s • buffer %d quadros",
                            metrics.frame, metrics.peerWaitMs, metrics.maxPeerWaitMs,
                            metrics.effectiveFps, metrics.stalledFrames,
                            metrics.inputDelayFrames)
                    }
                }
            },
            shareAllowedByHost = hostShareThisSession &&
                (relayRoomId == null || (relayTls || wssEndpoint != null)),
            receiveAllowedByClient = receiveRomAllowed.value && host != null,
            onReceiveRom = ::installReceivedRom,
            onReceiveRomOffer = ::askIncomingRomApproval,
            onStatus = { message ->
                runOnUiThread {
                    if (netplaySession === session) {
                        lanStatus.value = message
                        autoNetworkStatus.value = message
                        val rollbackStarted = message.contains("Rollback UDP") ||
                            message.contains("Rollback WebRTC DataChannel")
                        val tcpStarted = message.startsWith("Conectado como") && !rollbackStarted
                        networkDebug.value = networkDebug.value.copy(
                            mode = if (rollbackStarted) "Rollback (GekkoNet)"
                                else if (tcpStarted) "Lockstep (MZNP TCP)"
                                else networkDebug.value.mode,
                            transport = if (message.contains("WebRTC DataChannel"))
                                "WebRTC DataChannel"
                                else if (rollbackStarted) "UDP direto"
                                else if (tcpStarted && wssEndpoint != null) "TCP via Railway (WSS)"
                                else if (tcpStarted && relayRoomId != null) "TCP relay"
                                else if (tcpStarted) "TCP direto"
                                else networkDebug.value.transport,
                            desyncs = networkDebug.value.desyncs +
                                if (message.contains("dessíncronia", ignoreCase = true) ||
                                    message.contains("desync", ignoreCase = true)) 1 else 0,
                            state = message,
                            iceState = if (message.startsWith("ICE:"))
                                message.removePrefix("ICE:").trim()
                                else networkDebug.value.iceState)
                        if (message.startsWith("Conectado como")) {
                            userPaused = false
                            localPlayer2Enabled.value = false
                            activeScreen.value = AppScreen.GAME
                            enterImmersiveGameplay()
                            syncPlaybackState()
                        }
                    }
                }
            },
            onFinish = { message ->
                runOnUiThread {
                    if (netplaySession === session) {
                        netplaySession = null
                        stopMultiplayerKeepAlive()
                        runCatching { NativeBridge.nativeSetPaused(true) }
                        createMultiplayerBackup("after")
                        lastNetworkMetrics.value = null
                        networkDebug.value = networkDebug.value.copy(
                            active = false, state = message)
                        runCatching { hostWifiLock?.release() }
                        hostWifiLock = null
                        if (sessionCoreOptionsWereChanged) {
                            originalCoreOptions.forEach { (key, value) ->
                                runCatching { NativeBridge.nativeSetCoreOption(key, value) }
                            }
                        }
                        lanStatus.value = message
                        autoNetworkStatus.value = message
                        statusMessage.value = message
                        userPaused = true
                        paused.value = true
                        activeScreen.value = AppScreen.MULTIPLAYER
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                        publishInputs()
                        syncPlaybackState()
                        applyActiveCheats()
                        if (currentRomFile.value != null && cheatEntries.value.isEmpty()) {
                            searchCheats()
                        }
                    }
                }
            }
        )
        netplaySession = session
        startMultiplayerKeepAlive()
        if (host == null && relayRoomId == null) hostWifiLock = createDiscoveryLock()
        lanStatus.value = if (host == null) "Abrindo sala…" else "Conectando…"
        autoNetworkStatus.value = lanStatus.value
        if (relayRoomId != null && wssEndpoint != null) {
            if (host == null) session.hostViaWebSocket(wssEndpoint, relayRoomId,
                current?.nameWithoutExtension ?: "SNES")
            else session.joinViaWebSocket(wssEndpoint, relayRoomId)
        } else if (relayRoomId != null && relayServerHost != null) {
            if (host == null) session.hostViaRelay(relayServerHost, port, relayRoomId,
                current?.nameWithoutExtension ?: "SNES", relayTls = relayTls)
            else session.joinViaRelay(relayServerHost, port, relayRoomId, relayTls = relayTls)
        } else if (host == null) session.host(port) else session.join(host, port)
    }

    private fun shareNetworkDiagnostics() {
        val debug = networkDebug.value
        val details = buildString {
            appendLine("MaizoSNES • diagnóstico de rede")
            appendLine("Data: " + java.util.Date())
            appendLine("Motor: " + debug.mode)
            appendLine("Transporte: " + debug.transport)
            appendLine("Estado: " + debug.state)
            appendLine("ICE: " + debug.iceState)
            appendLine("TURN: " + debug.turnUsed)
            appendLine("NAT: " + debug.natType)
            appendLine("RTT: " + debug.rttMin + " / " + debug.rttAvg +
                " / " + debug.rttMax)
            appendLine("Jitter: " + debug.jitterMs)
            appendLine("Snes9x save+load: " + debug.saveLoadMs + " ms")
            appendLine("Pico de processamento Gekko: " + debug.peakFrameStallMs + " ms")
            appendLine("Renderização: 1/" + debug.presentationStride + " quadros")
            appendLine("Limited Saving: " + debug.limitedSaving)
            appendLine("Perda: " + debug.packetLoss)
            appendLine("Correções/s: " + debug.rollbacksPerSec)
            appendLine("Total: " + debug.totalRollbacks)
            appendLine("Atraso: " + debug.delay)
            appendLine("Dessíncronias: " + debug.desyncs)
            appendLine("Quadro: " + debug.frame)
            appendLine("Não anexe ROMs, IP público ou dados pessoais.")
        }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MaizoSNES • diagnóstico")
                putExtra(Intent.EXTRA_TEXT, details)
            }, "Compartilhar diagnóstico"))
    }

    private fun stopMultiplayer() {
        if (netplaySession == null) return
        lanStatus.value = "Cancelando sessão e liberando sala…"
        autoNetworkStatus.value = lanStatus.value
        pendingRomApproval.getAndSet(null)?.offer(false)
        netplaySession?.stop()
    }

    private fun updateEmulatorSurface(surface: Surface?) {
        if (appSettings.value.renderBackend == RenderBackend.GLES) {
            runCatching { NativeBridge.nativeSetSurface(null) }
            return
        }
        surfaceAttached = surface != null
        runCatching { NativeBridge.nativeSetSurface(surface) }
        syncPlaybackState()
    }

    private fun updateGpuReady(ready: Boolean) {
        if (appSettings.value.renderBackend != RenderBackend.GLES) return
        surfaceAttached = ready
        syncPlaybackState()
        if (!ready) statusMessage.value = "GPU não disponível. Mude para CPU nos ajustes do jogo."
    }

    private fun syncPlaybackState() {
        val shouldRun = activityResumed && surfaceAttached && hasLoadedRom && !userPaused
        // Keep Snes9x's netplay clock running on the network worker even
        // when Surface/Activity disappear while switching to another app.
        // Only local/offline play follows Activity visibility.
        val liveSession = netplaySession != null
        if (!shouldRun) audioOutput?.stop()
        runCatching { NativeBridge.nativeSetPaused(
            NetplayBackgroundPolicy.pauseCore(liveSession, shouldRun)) }
        if (shouldRun) {
            // Only a backgrounded NETPLAY has kept producing PCM while
            // AudioTrack was stopped. Drop its stale queue before playback.
            if (liveSession && !audioOutputWasAudible) {
                runCatching { NativeBridge.nativeDiscardAudioQueue() }
            }
            audioOutput?.start()
        }
        audioOutputWasAudible = shouldRun
        paused.value = userPaused || !shouldRun
    }

    override fun onPause() {
        // A temporary Activity pause (rotation, dialogs, app switching)
        // must NOT silently cancel a hosted room. onDestroy will close it.
        pressedInputs.set(0)
        gamepadAxisMasks.clear()
        gamepadKeyMasks.clear()
        publishInputs()
        activityResumed = false
        syncPlaybackState()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        syncPlaybackState()
    }

    override fun onDestroy() {
        coverJob?.cancel()
        perfJob?.cancel()
        netplaySession?.stop()
        stopMultiplayerKeepAlive()
        runCatching { hostWifiLock?.release() }
        hostWifiLock = null
        activityResumed = false
        surfaceAttached = false
        audioOutput?.stop()
        audioOutput = null
        runCatching { NativeBridge.nativeShutdown() }
        super.onDestroy()
    }
}
