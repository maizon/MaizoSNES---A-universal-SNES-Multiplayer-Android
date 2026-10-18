package com.maizon.maizosnes

import android.content.pm.ActivityInfo
import android.net.Uri
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("maizosnes", MODE_PRIVATE) }
    private val romDirectory by lazy { File(filesDir, "roms").apply { mkdirs() } }
    private val stateDirectory by lazy { File(filesDir, "states").apply { mkdirs() } }

    private val statusMessage = androidx.compose.runtime.mutableStateOf("Preparando Snes9x 1.63…")
    private val romFiles = androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
    private val activeScreen = androidx.compose.runtime.mutableStateOf(AppScreen.LIBRARY)
    private val currentRomFile = androidx.compose.runtime.mutableStateOf<File?>(null)
    private val currentRomName = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val appSettings = androidx.compose.runtime.mutableStateOf(AppSettings())
    private val coreSettings = androidx.compose.runtime.mutableStateMapOf<String, String>()
    private val paused = androidx.compose.runtime.mutableStateOf(false)
    private val pressedInputs = AtomicInteger(0)
    private val lanStatus = androidx.compose.runtime.mutableStateOf("Sem sessão ativa.")
    private val localPlayer2Enabled = androidx.compose.runtime.mutableStateOf(false)
    private val gamepadKeyMasks = ConcurrentHashMap<Int, Int>()
    private val gamepadAxisMasks = ConcurrentHashMap<Int, Int>()
    private var netplaySession: NetplaySession? = null

    private var audioOutput: AudioOutput? = null
    private var hasLoadedRom = false
    private var activityResumed = false
    private var surfaceAttached = false
    private var userPaused = false

    private val openRomLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addRomToLibrary(uri)
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
        val localIp = getLocalIpv4()

        setContent {
            MaizoTheme {
                MaizoEmulatorApp(
                    romNames = romFiles.value,
                    screen = activeScreen.value,
                    statusMessage = statusMessage.value,
                    currentRomName = currentRomName.value,
                    hasLoadedRom = hasLoadedRom,
                    paused = paused.value,
                    settings = appSettings.value,
                    coreOptions = coreSettings.toMap(),
                    multiplayerStatus = lanStatus.value,
                    localIpAddress = localIp,
                    multiplayerActive = netplaySession != null,
                    localMultiplayer = localPlayer2Enabled.value,
                    multiplayerReady = hasLoadedRom,
                    onHostMultiplayer = ::hostMultiplayer,
                    onJoinMultiplayer = ::joinMultiplayer,
                    onStopMultiplayer = ::stopMultiplayer,
                    onLocalMultiplayerChanged = ::setLocalMultiplayer,
                    onChooseAddRom = { openRomLauncher.launch(arrayOf("*/*")) },
                    onOpenRom = ::openRom,
                    onContinueGame = ::continueGame,
                    onRenameRom = ::renameRom,
                    onSetScreen = { requested -> if (requested != AppScreen.GAME) activeScreen.value = requested },
                    onSettingsChanged = ::persistSettings,
                    onCoreOptionChanged = ::changeCoreOption,
                    onSaveSlot = ::saveSlot,
                    onLoadSlot = ::loadSlot,
                    onExportState = { exportStateLauncher.launch(defaultStateName()) },
                    onImportState = { importStateLauncher.launch(arrayOf("*/*")) },
                    onResetGame = ::resetGame,
                    onTogglePause = ::togglePause,
                    onExitGame = ::exitGameplay,
                    onInputChanged = ::updateInput,
                    onSurfaceChanged = ::updateEmulatorSurface
                )
            }
        }
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

    private fun refreshLibrary() {
        romFiles.value = romDirectory.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("sfc", "smc") }
            ?.sortedBy { it.name.lowercase() }
            ?.map { it.name }
            ?: emptyList()
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
        loadRomFile(file)
    }

    private fun loadRomFile(file: File) {
        lifecycleScope.launch {
            userPaused = true
            syncPlaybackState()
            audioOutput?.stop()
            statusMessage.value = "Carregando " + file.name + "…"
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
                val result = withContext(Dispatchers.Default) { NativeBridge.nativeLoadRom(bytes, file.name) }
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
                currentRomFile.value = file
                currentRomName.value = file.name
                userPaused = false
                paused.value = false
                audioOutput = AudioOutput(NativeBridge.nativeGetAudioSampleRate()).also {
                    it.setVolume(appSettings.value.volume)
                    it.setEnabled(appSettings.value.audioEnabled)
                }
                activeScreen.value = AppScreen.GAME
                enterImmersiveGameplay()
                statusMessage.value = "ROM carregada: " + file.name
                syncPlaybackState()
            } catch (error: Exception) {
                statusMessage.value = "Falha ao carregar a ROM: " + (error.message ?: "erro de leitura")
                syncPlaybackState()
            }
        }
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
        netplaySession?.stop()
        userPaused = true
        paused.value = true
        activeScreen.value = AppScreen.LIBRARY
        appSettings.value = readSettings("profile_global_")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        syncPlaybackState()
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
        val touch = TouchLayout(
            enabled = prefs.getBoolean(key("touch_enabled"), true),
            dpadX = prefs.getFloat(key("dpad_x"), 0.02f),
            dpadY = prefs.getFloat(key("dpad_y"), 0.025f),
            buttonsX = prefs.getFloat(key("buttons_x"), 0.02f),
            buttonsY = prefs.getFloat(key("buttons_y"), 0.025f),
            scale = prefs.getFloat(key("touch_scale"), 1f),
            opacity = prefs.getFloat(key("touch_opacity"), 0.62f)
        )
        return AppSettings(
            aspect = aspect,
            touch = touch,
            audioEnabled = prefs.getBoolean(key("audio_enabled"), true),
            volume = prefs.getFloat(key("audio_volume"), 1f)
        )
    }

    private fun persistSettings(next: AppSettings) {
        appSettings.value = next
        val prefix = profilePrefix(if (activeScreen.value == AppScreen.GAME) currentRomFile.value else null)
        prefs.edit()
            .putString(prefix + "aspect", next.aspect.name)
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
        audioOutput?.setEnabled(next.audioEnabled)
        audioOutput?.setVolume(next.volume)
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val bit = snesButtonForKey(event.keyCode) ?: return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) {
            return super.dispatchKeyEvent(event)
        }
        if (event.deviceId in gamepadIds()) {
            val mask = 1 shl bit
            val previous = gamepadKeyMasks[event.deviceId] ?: 0
            gamepadKeyMasks[event.deviceId] = if (event.action == KeyEvent.ACTION_DOWN) {
                previous or mask
            } else previous and mask.inv()
            publishInputs()
        } else {
            updateInput(bit, event.action == KeyEvent.ACTION_DOWN)
        }
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (isJoystick && event.action == MotionEvent.ACTION_MOVE) {
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val stickX = if (abs(hatX) > 0.5f) hatX else event.getAxisValue(MotionEvent.AXIS_X)
            val stickY = if (abs(hatY) > 0.5f) hatY else event.getAxisValue(MotionEvent.AXIS_Y)
            val deadZone = 0.55f
            var mask = 0
            if (stickX < -deadZone) mask = mask or (1 shl 6)
            if (stickX > deadZone) mask = mask or (1 shl 7)
            if (stickY < -deadZone) mask = mask or (1 shl 4)
            if (stickY > deadZone) mask = mask or (1 shl 5)
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

    private fun startNetplay(host: String?, port: Int) {
        if (!hasLoadedRom || currentRomFile.value == null) {
            lanStatus.value = "Abra a mesma ROM nos dois aparelhos e volte ao Multiplayer."
            return
        }
        if (netplaySession != null) return
        if (port !in 1024..65535 || (host != null && host.isBlank())) {
            lanStatus.value = "Informe endereço e porta válidos (1024–65535)."
            return
        }
        userPaused = true
        syncPlaybackState()
        val rom = currentRomFile.value ?: return
        lateinit var session: NetplaySession
        session = NetplaySession(
            rom = rom,
            coreOptions = coreSettings.toMap(),
            localInput = { currentLocalInput() },
            prepare = { enabled -> NativeBridge.nativeSetNetplayMode(enabled) },
            saveState = { NativeBridge.nativeSerializeState() },
            loadState = { NativeBridge.nativeUnserializeState(it) },
            step = { player1, player2 -> NativeBridge.nativeNetplayStep(player1, player2) },
            stateHash = { NativeBridge.nativeNetplayStateHash() },
            framePeriodNs = { NativeBridge.nativeGetFramePeriodNs() },
            onStatus = { message ->
                runOnUiThread {
                    if (netplaySession === session) {
                        lanStatus.value = message
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
                        lanStatus.value = message
                        statusMessage.value = message
                        userPaused = true
                        paused.value = true
                        activeScreen.value = AppScreen.MULTIPLAYER
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                        publishInputs()
                        syncPlaybackState()
                    }
                }
            }
        )
        netplaySession = session
        lanStatus.value = if (host == null) "Abrindo sala…" else "Conectando…"
        if (host == null) session.host(port) else session.join(host, port)
    }

    private fun stopMultiplayer() {
        netplaySession?.stop()
    }

    private fun updateEmulatorSurface(surface: Surface?) {
        surfaceAttached = surface != null
        runCatching { NativeBridge.nativeSetSurface(surface) }
        syncPlaybackState()
    }

    private fun syncPlaybackState() {
        val shouldRun = activityResumed && surfaceAttached && hasLoadedRom && !userPaused
        if (!shouldRun) audioOutput?.stop()
        runCatching { NativeBridge.nativeSetPaused(!shouldRun) }
        if (shouldRun) audioOutput?.start()
        paused.value = userPaused || !shouldRun
    }

    override fun onPause() {
        netplaySession?.stop()
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
        netplaySession?.stop()
        activityResumed = false
        surfaceAttached = false
        audioOutput?.stop()
        audioOutput = null
        runCatching { NativeBridge.nativeShutdown() }
        super.onDestroy()
    }
}
