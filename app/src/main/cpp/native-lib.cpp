#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <libretro.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdarg>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

namespace {
constexpr char kLogTag[] = "MaizoSNES";
constexpr size_t kAudioCapacity = 65536; // int16 values; SPSC ring buffer.
constexpr size_t kMaxRomBytes = 64u * 1024u * 1024u;
constexpr size_t kMaxStateBytes = 64u * 1024u * 1024u;
constexpr uint8_t kStateMagic[8] = {'M', 'Z', 'S', 'S', 'T', 'A', 'T', 'E'};
constexpr size_t kStateHeaderSize = 28;
constexpr uint32_t kStateFormatVersion = 1;

std::string g_appDataPath;
std::string g_romName;
uint64_t g_romFingerprint = 0; // Protected by g_coreMutex.
std::atomic<bool> g_initialized{false};
std::atomic<bool> g_gameLoaded{false};
std::atomic<bool> g_runFrames{false};
std::atomic<bool> g_paused{false};
std::atomic<uint32_t> g_inputMask{0};
std::atomic<uint32_t> g_player2InputMask{0};
std::atomic<uint32_t> g_player3InputMask{0};
std::atomic<uint32_t> g_player4InputMask{0};
std::atomic<bool> g_multitapEnabled{false};
// Upstream Snes9x 1.63: RETRO_DEVICE_JOYPAD_MULTITAP is subclass #0.
constexpr unsigned kSnes9xMultitap = ((1u << 8) | RETRO_DEVICE_JOYPAD);
std::atomic<bool> g_netplayMode{false};
std::atomic<bool> g_rollbackReplaying{false};
std::atomic<int> g_netplayRenderStride{1};
std::atomic<unsigned int> g_netplayVideoCount{0};
std::atomic<bool> g_netplayCheatSetupAllowed{false};
std::atomic<uint64_t> g_audioWrite{0};
std::atomic<uint64_t> g_audioRead{0};
int16_t g_audioRing[kAudioCapacity]{};
retro_pixel_format g_pixelFormat = RETRO_PIXEL_FORMAT_RGB565;

std::mutex g_coreMutex;
std::mutex g_surfaceMutex;
ANativeWindow* g_window = nullptr;
int g_surfaceWidth = 0;
int g_surfaceHeight = 0;
std::thread g_frameThread;
std::atomic<int64_t> g_frameDurationNs{16683333};
std::atomic<int> g_videoFilter{0}; // 0..8 selectable video filters
std::atomic<int> g_outputScale{1}; // 1x..4x display framebuffer, not SNES internal resolution
std::atomic<bool> g_gpuEnabled{false};
std::mutex g_gpuFrameMutex;
std::vector<uint16_t> g_gpuFrame;
unsigned g_gpuWidth = 0;
unsigned g_gpuHeight = 0;
uint32_t g_gpuRevision = 0;
std::atomic<uint64_t> g_emuFrames{0};
std::atomic<uint64_t> g_emuNs{0};
std::atomic<uint64_t> g_presentFrames{0};
std::atomic<uint64_t> g_presentNs{0};
std::atomic<bool> g_coreOptionsDirty{false};

// Defaults use option values from the pinned Snes9x 1.63 libretro_core_options.h.
std::unordered_map<std::string, std::string> g_coreOptions = {
    {"snes9x_aspect", "4:3"}, {"snes9x_overscan", "enabled"},
    {"snes9x_hires_blend", "disabled"}, {"snes9x_blargg", "disabled"},
    {"snes9x_audio_interpolation", "gaussian"}, {"snes9x_up_down_allowed", "disabled"},
    {"snes9x_overclock_superfx", "100%"}, {"snes9x_overclock_cycles", "disabled"},
    {"snes9x_reduce_sprite_flicker", "disabled"}, {"snes9x_randomize_memory", "disabled"},
    {"snes9x_block_invalid_vram_access", "enabled"}, {"snes9x_echo_buffer_hack", "disabled"},
    {"snes9x_layer_1", "enabled"}, {"snes9x_layer_2", "enabled"},
    {"snes9x_layer_3", "enabled"}, {"snes9x_layer_4", "enabled"},
    {"snes9x_layer_5", "enabled"}, {"snes9x_gfx_clip", "enabled"},
    {"snes9x_gfx_transp", "enabled"}, {"snes9x_sndchan_1", "enabled"},
    {"snes9x_sndchan_2", "enabled"}, {"snes9x_sndchan_3", "enabled"},
    {"snes9x_sndchan_4", "enabled"}, {"snes9x_sndchan_5", "enabled"},
    {"snes9x_sndchan_6", "enabled"}, {"snes9x_sndchan_7", "enabled"},
    {"snes9x_sndchan_8", "enabled"}, {"snes9x_region", "auto"}
};

void logMessage(int priority, const char* format, ...);

/*
 * Core selection is only at ROM load boundaries. Snes9x 1.63 stays statically
 * linked and its sources untouched. FCEUmm is a separate Libretro DSO which
 * implements the identical frame/audio/video/input/state callbacks used by
 * the existing offline and GekkoNet execution paths.
 */
struct CoreApi {
    decltype(&retro_set_environment) set_environment = nullptr;
    decltype(&retro_set_video_refresh) set_video_refresh = nullptr;
    decltype(&retro_set_audio_sample) set_audio_sample = nullptr;
    decltype(&retro_set_audio_sample_batch) set_audio_sample_batch = nullptr;
    decltype(&retro_set_input_poll) set_input_poll = nullptr;
    decltype(&retro_set_input_state) set_input_state = nullptr;
    decltype(&retro_init) init = nullptr;
    decltype(&retro_set_controller_port_device) set_controller_port_device = nullptr;
    decltype(&retro_deinit) deinit = nullptr;
    decltype(&retro_load_game) load_game = nullptr;
    decltype(&retro_unload_game) unload_game = nullptr;
    decltype(&retro_run) run = nullptr;
    decltype(&retro_reset) reset = nullptr;
    decltype(&retro_serialize) serialize = nullptr;
    decltype(&retro_unserialize) unserialize = nullptr;
    decltype(&retro_serialize_size) serialize_size = nullptr;
    decltype(&retro_get_system_av_info) get_system_av_info = nullptr;
    decltype(&retro_get_system_info) get_system_info = nullptr;
    decltype(&retro_cheat_reset) cheat_reset = nullptr;
    decltype(&retro_cheat_set) cheat_set = nullptr;
};
CoreApi g_snesApi {
    &retro_set_environment,
    &retro_set_video_refresh,
    &retro_set_audio_sample,
    &retro_set_audio_sample_batch,
    &retro_set_input_poll,
    &retro_set_input_state,
    &retro_init,
    &retro_set_controller_port_device,
    &retro_deinit,
    &retro_load_game,
    &retro_unload_game,
    &retro_run,
    &retro_reset,
    &retro_serialize,
    &retro_unserialize,
    &retro_serialize_size,
    &retro_get_system_av_info,
    &retro_get_system_info,
    &retro_cheat_reset,
    &retro_cheat_set
};
CoreApi g_nesApi{};
CoreApi* g_api = &g_snesApi;
void* g_nesDso = nullptr;
bool g_nesReady = false;
bool g_isNes = false;

bool loadFceummDso() {
    if (g_nesDso) return true;
    void* dso = dlopen("libfceumm_libretro.so", RTLD_NOW | RTLD_LOCAL);
    if (!dso) {
        logMessage(ANDROID_LOG_ERROR, "FCEUmm dlopen failed: %s", dlerror());
        return false;
    }
    CoreApi api{};
    api.set_environment = reinterpret_cast<decltype(api.set_environment)>(dlsym(dso, "retro_set_environment"));
    api.set_video_refresh = reinterpret_cast<decltype(api.set_video_refresh)>(dlsym(dso, "retro_set_video_refresh"));
    api.set_audio_sample = reinterpret_cast<decltype(api.set_audio_sample)>(dlsym(dso, "retro_set_audio_sample"));
    api.set_audio_sample_batch = reinterpret_cast<decltype(api.set_audio_sample_batch)>(dlsym(dso, "retro_set_audio_sample_batch"));
    api.set_input_poll = reinterpret_cast<decltype(api.set_input_poll)>(dlsym(dso, "retro_set_input_poll"));
    api.set_input_state = reinterpret_cast<decltype(api.set_input_state)>(dlsym(dso, "retro_set_input_state"));
    api.init = reinterpret_cast<decltype(api.init)>(dlsym(dso, "retro_init"));
    api.set_controller_port_device = reinterpret_cast<decltype(api.set_controller_port_device)>(dlsym(dso, "retro_set_controller_port_device"));
    api.deinit = reinterpret_cast<decltype(api.deinit)>(dlsym(dso, "retro_deinit"));
    api.load_game = reinterpret_cast<decltype(api.load_game)>(dlsym(dso, "retro_load_game"));
    api.unload_game = reinterpret_cast<decltype(api.unload_game)>(dlsym(dso, "retro_unload_game"));
    api.run = reinterpret_cast<decltype(api.run)>(dlsym(dso, "retro_run"));
    api.reset = reinterpret_cast<decltype(api.reset)>(dlsym(dso, "retro_reset"));
    api.serialize = reinterpret_cast<decltype(api.serialize)>(dlsym(dso, "retro_serialize"));
    api.unserialize = reinterpret_cast<decltype(api.unserialize)>(dlsym(dso, "retro_unserialize"));
    api.serialize_size = reinterpret_cast<decltype(api.serialize_size)>(dlsym(dso, "retro_serialize_size"));
    api.get_system_av_info = reinterpret_cast<decltype(api.get_system_av_info)>(dlsym(dso, "retro_get_system_av_info"));
    api.get_system_info = reinterpret_cast<decltype(api.get_system_info)>(dlsym(dso, "retro_get_system_info"));
    api.cheat_reset = reinterpret_cast<decltype(api.cheat_reset)>(dlsym(dso, "retro_cheat_reset"));
    api.cheat_set = reinterpret_cast<decltype(api.cheat_set)>(dlsym(dso, "retro_cheat_set"));
    if (!(api.set_environment && api.set_video_refresh && api.set_audio_sample && api.set_audio_sample_batch && api.set_input_poll && api.set_input_state && api.init && api.set_controller_port_device && api.deinit && api.load_game && api.unload_game && api.run && api.reset && api.serialize && api.unserialize && api.serialize_size && api.get_system_av_info && api.get_system_info && api.cheat_reset && api.cheat_set)) {
        logMessage(ANDROID_LOG_ERROR, "FCEUmm is missing required Libretro symbols");
        dlclose(dso);
        return false;
    }
    g_nesApi = api;
    g_nesDso = dso;
    return true;
}
#define retro_set_environment g_api->set_environment
#define retro_set_video_refresh g_api->set_video_refresh
#define retro_set_audio_sample g_api->set_audio_sample
#define retro_set_audio_sample_batch g_api->set_audio_sample_batch
#define retro_set_input_poll g_api->set_input_poll
#define retro_set_input_state g_api->set_input_state
#define retro_init g_api->init
#define retro_set_controller_port_device g_api->set_controller_port_device
#define retro_deinit g_api->deinit
#define retro_load_game g_api->load_game
#define retro_unload_game g_api->unload_game
#define retro_run g_api->run
#define retro_reset g_api->reset
#define retro_serialize g_api->serialize
#define retro_unserialize g_api->unserialize
#define retro_serialize_size g_api->serialize_size
#define retro_get_system_av_info g_api->get_system_av_info
#define retro_get_system_info g_api->get_system_info
#define retro_cheat_reset g_api->cheat_reset
#define retro_cheat_set g_api->cheat_set

void logMessage(int priority, const char* format, ...) {
    va_list args;
    va_start(args, format);
    __android_log_vprint(priority, kLogTag, format, args);
    va_end(args);
}


uint64_t fingerprintBytes(const uint8_t* data, size_t size) {
    // FNV-1a 64-bit: a stable lightweight identity for matching save files to ROM bytes.
    uint64_t hash = 14695981039346656037ull;
    for (size_t i = 0; i < size; ++i) {
        hash ^= data[i];
        hash *= 1099511628211ull;
    }
    return hash;
}

void writeU32LE(uint8_t* dst, uint32_t value) {
    for (unsigned i = 0; i < 4; ++i) dst[i] = static_cast<uint8_t>(value >> (i * 8));
}

void writeU64LE(uint8_t* dst, uint64_t value) {
    for (unsigned i = 0; i < 8; ++i) dst[i] = static_cast<uint8_t>(value >> (i * 8));
}

uint32_t readU32LE(const uint8_t* src) {
    uint32_t value = 0;
    for (unsigned i = 0; i < 4; ++i) value |= static_cast<uint32_t>(src[i]) << (i * 8);
    return value;
}

uint64_t readU64LE(const uint8_t* src) {
    uint64_t value = 0;
    for (unsigned i = 0; i < 8; ++i) value |= static_cast<uint64_t>(src[i]) << (i * 8);
    return value;
}

void RETRO_CALLCONV coreLog(enum retro_log_level level, const char* format, ...) {
    int priority = ANDROID_LOG_DEBUG;
    switch (level) {
        case RETRO_LOG_ERROR: priority = ANDROID_LOG_ERROR; break;
        case RETRO_LOG_WARN:  priority = ANDROID_LOG_WARN; break;
        case RETRO_LOG_INFO:  priority = ANDROID_LOG_INFO; break;
        case RETRO_LOG_DEBUG: priority = ANDROID_LOG_DEBUG; break;
        default: break;
    }
    va_list args;
    va_start(args, format);
    __android_log_vprint(priority, kLogTag, format, args);
    va_end(args);
}

bool RETRO_CALLCONV environmentCallback(unsigned command, void* data) {
    switch (command) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            if (!data) return false;
            const auto requested = *static_cast<retro_pixel_format*>(data);
            if (requested != RETRO_PIXEL_FORMAT_RGB565 &&
                requested != RETRO_PIXEL_FORMAT_0RGB1555 &&
                requested != RETRO_PIXEL_FORMAT_XRGB8888) {
                return false;
            }
            g_pixelFormat = requested;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            if (data) *static_cast<bool*>(data) = true;
            return data != nullptr;
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
        case RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY:
            if (data) {
                *static_cast<const char**>(data) = g_appDataPath.c_str();
                return true;
            }
            return false;
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            if (data) {
                static_cast<retro_log_callback*>(data)->log = coreLog;
                return true;
            }
            return false;
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            if (!data) return false;
            auto* variable = static_cast<retro_variable*>(data);
            if (!variable->key) {
                variable->value = nullptr;
                return false;
            }
            const auto found = g_coreOptions.find(variable->key);
            if (found == g_coreOptions.end()) {
                variable->value = nullptr;
                return false;
            }
            variable->value = found->second.c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            if (data) *static_cast<bool*>(data) = g_coreOptionsDirty.exchange(false, std::memory_order_acq_rel);
            return data != nullptr;
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
            return true;
        default:
            return false;
    }
}

void audioPush(const int16_t* samples, size_t frames) {
    if (!samples || frames == 0 || g_rollbackReplaying.load(std::memory_order_relaxed)) return;
    const size_t count = frames * 2; // stereo frames -> interleaved int16 values.
    if (count > kAudioCapacity) return;

    const uint64_t write = g_audioWrite.load(std::memory_order_relaxed);
    const uint64_t read = g_audioRead.load(std::memory_order_acquire);
    const uint64_t queued = write - read;
    if (queued > kAudioCapacity || count > kAudioCapacity - static_cast<size_t>(queued)) {
        return; // Drop this batch rather than overwrite data being consumed.
    }
    for (size_t i = 0; i < count; ++i) {
        g_audioRing[(write + i) % kAudioCapacity] = samples[i];
    }
    g_audioWrite.store(write + count, std::memory_order_release);
}

void RETRO_CALLCONV audioSample(int16_t left, int16_t right) {
    const int16_t frame[2] = {left, right};
    audioPush(frame, 1);
}

size_t RETRO_CALLCONV audioSampleBatch(const int16_t* data, size_t frames) {
    audioPush(data, frames);
    return frames;
}

void RETRO_CALLCONV inputPoll() {}

int16_t RETRO_CALLCONV inputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port > 4 || index != 0 || device != RETRO_DEVICE_JOYPAD ||
        id > RETRO_DEVICE_ID_JOYPAD_R3) return 0;
    // In Snes9x port 1 multitap maps virtual libretro ports 1..4 to P2..P5.
    // With normal joypad the port 1 callback remains P2, unchanged.
    const uint32_t bits =
        port == 0 ? g_inputMask.load(std::memory_order_relaxed) :
        port == 1 ? g_player2InputMask.load(std::memory_order_relaxed) :
        port == 2 ? g_player3InputMask.load(std::memory_order_relaxed) :
        port == 3 ? g_player4InputMask.load(std::memory_order_relaxed) : 0u;
    return (bits & (1u << id)) ? 1 : 0;
}

uint16_t convert1555(uint16_t pixel) {
    const uint16_t r = static_cast<uint16_t>((pixel >> 10) & 0x1f);
    const uint16_t g = static_cast<uint16_t>((pixel >> 5) & 0x1f);
    const uint16_t b = static_cast<uint16_t>(pixel & 0x1f);
    const uint16_t g6 = static_cast<uint16_t>((g << 1) | (g >> 4));
    return static_cast<uint16_t>((r << 11) | (g6 << 5) | b);
}

uint16_t convertXrgb8888(uint32_t pixel) {
    const uint16_t r = static_cast<uint16_t>((pixel >> 19) & 0x1f);
    const uint16_t g = static_cast<uint16_t>((pixel >> 10) & 0x3f);
    const uint16_t b = static_cast<uint16_t>((pixel >> 3) & 0x1f);
    return static_cast<uint16_t>((r << 11) | (g << 5) | b);
}

void RETRO_CALLCONV videoRefresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (g_rollbackReplaying.load(std::memory_order_relaxed)) return;
    // Network stalls: skip only presentation, NEVER SNES emulation,
    // sound, input, save states or checksum.
    const int stride = g_netplayRenderStride.load(std::memory_order_relaxed);
    if (g_netplayMode.load(std::memory_order_relaxed) && stride > 1 &&
        g_netplayVideoCount.fetch_add(1, std::memory_order_relaxed) %
            static_cast<unsigned int>(stride) != 0) return;
    if (!data || width == 0 || height == 0 || width > 1024 || height > 1024) return;

    if (g_gpuEnabled.load(std::memory_order_acquire)) {
        const auto start = std::chrono::steady_clock::now();
        const unsigned bpp = g_pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888 ? 4u : 2u;
        if (pitch < width * bpp) return;
        std::lock_guard<std::mutex> frameLock(g_gpuFrameMutex);
        g_gpuFrame.resize(static_cast<size_t>(width) * height);
        for (unsigned y = 0; y < height; ++y) {
            const auto* row = static_cast<const uint8_t*>(data) + y * pitch;
            uint16_t* dst = g_gpuFrame.data() + static_cast<size_t>(y) * width;
            if (g_pixelFormat == RETRO_PIXEL_FORMAT_RGB565) {
                std::memcpy(dst, row, static_cast<size_t>(width) * sizeof(uint16_t));
            } else {
                for (unsigned x = 0; x < width; ++x) {
                    dst[x] = g_pixelFormat == RETRO_PIXEL_FORMAT_0RGB1555 ?
                        convert1555(reinterpret_cast<const uint16_t*>(row)[x]) :
                        convertXrgb8888(reinterpret_cast<const uint32_t*>(row)[x]);
                }
            }
        }
        g_gpuWidth = width;
        g_gpuHeight = height;
        ++g_gpuRevision;
        const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now() - start).count();
        g_presentNs.fetch_add(static_cast<uint64_t>(std::max<int64_t>(0, elapsed)),
                              std::memory_order_relaxed);
        g_presentFrames.fetch_add(1, std::memory_order_relaxed);
        return;
    }
    std::lock_guard<std::mutex> guard(g_surfaceMutex);
    if (!g_window) return;

    // SNES PPU resolution does not increase; display framebuffer is nearest-neighbor
    // replicated to preserve crisp pixel edges. Cap memory bandwidth on hires modes.
    const int requestedScale = g_outputScale.load(std::memory_order_relaxed);
    const int scale = (width * requestedScale <= 2048 &&
                       height * requestedScale <= 1536) ? requestedScale : 1;
    const unsigned outputWidth = width * static_cast<unsigned>(scale);
    const unsigned outputHeight = height * static_cast<unsigned>(scale);
    if (g_surfaceWidth != static_cast<int>(outputWidth) ||
        g_surfaceHeight != static_cast<int>(outputHeight)) {
        if (ANativeWindow_setBuffersGeometry(
                g_window, static_cast<int32_t>(outputWidth),
                static_cast<int32_t>(outputHeight), WINDOW_FORMAT_RGB_565) != 0) {
            return;
        }
        g_surfaceWidth = static_cast<int>(outputWidth);
        g_surfaceHeight = static_cast<int>(outputHeight);
    }

    ANativeWindow_Buffer buffer{};
    if (ANativeWindow_lock(g_window, &buffer, nullptr) != 0 || !buffer.bits) return;

    const unsigned copyWidth = std::min(width,
        static_cast<unsigned>(buffer.width / scale));
    const unsigned copyHeight = std::min(height,
        static_cast<unsigned>(buffer.height / scale));
    auto* destination = static_cast<uint16_t*>(buffer.bits);
    const auto* sourceBytes = static_cast<const uint8_t*>(data);
    const size_t sourceBpp = g_pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888 ? 4u : 2u;
    const size_t requiredPitch = static_cast<size_t>(width) * sourceBpp;

    if (pitch < requiredPitch) {
        ANativeWindow_unlockAndPost(g_window);
        return;
    }

    const int filter = g_videoFilter.load(std::memory_order_relaxed);
    const auto presentBegin = std::chrono::steady_clock::now();
    auto to565 = [&](const uint8_t* row, unsigned index) -> uint16_t {
        if (g_pixelFormat == RETRO_PIXEL_FORMAT_RGB565)
            return reinterpret_cast<const uint16_t*>(row)[index];
        if (g_pixelFormat == RETRO_PIXEL_FORMAT_0RGB1555)
            return convert1555(reinterpret_cast<const uint16_t*>(row)[index]);
        return convertXrgb8888(reinterpret_cast<const uint32_t*>(row)[index]);
    };
    for (unsigned y = 0; y < copyHeight; ++y) {
        uint16_t* dst = destination + static_cast<size_t>(y) * scale * buffer.stride;
        const uint8_t* src = sourceBytes + static_cast<size_t>(y) * pitch;
        if (filter == 0 && scale == 1 && g_pixelFormat == RETRO_PIXEL_FORMAT_RGB565) {
            std::memcpy(dst, src, static_cast<size_t>(copyWidth) * sizeof(uint16_t));
            continue;
        }
        uint16_t previous = 0;
        for (unsigned x = 0; x < copyWidth; ++x) {
            const uint16_t pixel = to565(src, x);
            uint16_t result = pixel;
            if ((filter == 2 || filter == 3) && x > 0) {
                result = static_cast<uint16_t>(((pixel & 0xF7DEu) >> 1) +
                                               ((previous & 0xF7DEu) >> 1));
            }
            if ((filter == 1 || filter == 3) && (y & 1u) != 0) {
                result = static_cast<uint16_t>(((result & 0xF7DEu) >> 1) +
                                               ((result & 0xE79Cu) >> 2));
            }
            if (filter == 4 && (x % 3 == 2 || y % 3 == 2)) {
                // LCD light grid, no textures/shaders required.
                result = static_cast<uint16_t>(((pixel & 0xF7DEu) >> 1) +
                                               ((pixel & 0xE79Cu) >> 2));
            }
            if (filter == 5 || filter == 6) {
                const int red = ((pixel >> 11) & 31) * 255 / 31;
                const int green = ((pixel >> 5) & 63) * 255 / 63;
                const int blue = (pixel & 31) * 255 / 31;
                const int lum = (77 * red + 150 * green + 29 * blue) >> 8;
                if (filter == 5) {
                    result = static_cast<uint16_t>(((lum >> 3) << 11) |
                                                   ((lum >> 2) << 5) | (lum >> 3));
                } else {
                    result = static_cast<uint16_t>((lum >> 3) << 11 |
                        ((std::min(255, lum + 48) >> 2) << 5) | (lum >> 4));
                }
            }
            if (filter == 7 && x > 0 && x + 1 < copyWidth) {
                // RGB565 unsharp mask over neighbors; avoids allocations.
                const uint16_t next = to565(src, x + 1);
                const int r = (pixel >> 11) & 31, g = (pixel >> 5) & 63, b = pixel & 31;
                const int nr = (((previous >> 11) & 31) + ((next >> 11) & 31)) / 2;
                const int ng = (((previous >> 5) & 63) + ((next >> 5) & 63)) / 2;
                const int nb = ((previous & 31) + (next & 31)) / 2;
                const unsigned rr = static_cast<unsigned>(std::clamp(2 * r - nr, 0, 31));
                const unsigned gg = static_cast<unsigned>(std::clamp(2 * g - ng, 0, 63));
                const unsigned bb = static_cast<unsigned>(std::clamp(2 * b - nb, 0, 31));
                result = static_cast<uint16_t>((rr << 11) | (gg << 5) | bb);
            }
            if (filter == 8) {
                // RGB phosphor triads: preserve dominant channel, dim the others.
                const int channel = static_cast<int>(x % 3);
                const uint16_t mask = channel == 0 ? 0xF800u :
                    channel == 1 ? 0x07E0u : 0x001Fu;
                result = static_cast<uint16_t>((pixel & mask) +
                    (((pixel & static_cast<uint16_t>(~mask)) & 0xF7DEu) >> 1));
            }
            previous = pixel;
            for (int sx = 0; sx < scale; ++sx) dst[x * scale + sx] = result;
        }
        for (int sy = 1; sy < scale; ++sy) {
            std::memcpy(destination + static_cast<size_t>(y * scale + sy) * buffer.stride,
                        dst, static_cast<size_t>(copyWidth) * scale * sizeof(uint16_t));
        }
    }
    ANativeWindow_unlockAndPost(g_window);
    const auto elapsed = std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now() - presentBegin).count();
    g_presentNs.fetch_add(static_cast<uint64_t>(std::max<int64_t>(elapsed, 0)), std::memory_order_relaxed);
    g_presentFrames.fetch_add(1, std::memory_order_relaxed);
}


bool hasNesExtension(const std::string& name) {
    const size_t pos = name.rfind('.');
    if (pos == std::string::npos) return false;
    std::string ext = name.substr(pos + 1);
    std::transform(ext.begin(), ext.end(), ext.begin(),
        [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return ext == "nes" || ext == "unf" || ext == "unif" || ext == "fds";
}

bool chooseCoreForGame(bool nes) {
    if (!nes) {
        g_api = &g_snesApi;
        g_isNes = false;
        return true;
    }
    if (!loadFceummDso()) return false;
    g_api = &g_nesApi;
    g_isNes = true;
    if (!g_nesReady) {
        retro_set_environment(environmentCallback);
        retro_set_video_refresh(videoRefresh);
        retro_set_audio_sample(audioSample);
        retro_set_audio_sample_batch(audioSampleBatch);
        retro_set_input_poll(inputPoll);
        retro_set_input_state(inputState);
        retro_init();
        retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
        retro_set_controller_port_device(1, RETRO_DEVICE_JOYPAD);
        g_nesReady = true;
    }
    return true;
}

void stopFrameThread() {
    g_runFrames.store(false, std::memory_order_release);
    if (g_frameThread.joinable()) g_frameThread.join();
}

void startFrameThread() {
    if (g_frameThread.joinable() || g_netplayMode.load(std::memory_order_acquire)) return;
    g_runFrames.store(true, std::memory_order_release);
    g_frameThread = std::thread([] {
        using clock = std::chrono::steady_clock;
        auto deadline = clock::now();
        while (g_runFrames.load(std::memory_order_acquire)) {
            if (g_paused.load(std::memory_order_acquire)) {
                deadline = clock::now();
                std::this_thread::sleep_for(std::chrono::milliseconds(2));
                continue;
            }
            const auto begin = clock::now();
            {
                std::lock_guard<std::mutex> lock(g_coreMutex);
                if (!g_gameLoaded.load(std::memory_order_acquire)) break;
                retro_run();
            }
            const auto after = clock::now();
            g_emuFrames.fetch_add(1, std::memory_order_relaxed);
            g_emuNs.fetch_add(static_cast<uint64_t>(std::chrono::duration_cast<
                std::chrono::nanoseconds>(after - begin).count()), std::memory_order_relaxed);
            const auto duration = std::chrono::nanoseconds(
                g_frameDurationNs.load(std::memory_order_acquire));
            deadline += duration;
            if (after > deadline + duration * 2) deadline = after;
            if (deadline > after) std::this_thread::sleep_until(deadline);
        }
        g_runFrames.store(false, std::memory_order_release);
    });
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeInit(JNIEnv* env, jobject, jstring appDataPath) {
    if (g_initialized.load(std::memory_order_acquire)) return 0;
    if (!appDataPath) return -1;

    const char* path = env->GetStringUTFChars(appDataPath, nullptr);
    if (!path) return -2;
    g_appDataPath.assign(path);
    env->ReleaseStringUTFChars(appDataPath, path);
    g_paused.store(false, std::memory_order_release);

    retro_set_environment(environmentCallback);
    retro_set_video_refresh(videoRefresh);
    retro_set_audio_sample(audioSample);
    retro_set_audio_sample_batch(audioSampleBatch);
    retro_set_input_poll(inputPoll);
    retro_set_input_state(inputState);
    retro_init();
    retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
    retro_set_controller_port_device(1, RETRO_DEVICE_JOYPAD);
    g_initialized.store(true, std::memory_order_release);
    logMessage(ANDROID_LOG_INFO, "Snes9x 1.63 libretro core initialized");
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeLoadRom(
    JNIEnv* env, jobject, jbyteArray romBytes, jstring displayName) {
    if (!g_initialized.load(std::memory_order_acquire) || !romBytes) return -1;

    const jsize length = env->GetArrayLength(romBytes);
    if (length <= 0 || static_cast<size_t>(length) > kMaxRomBytes) return -2;

    std::vector<uint8_t> bytes(static_cast<size_t>(length));
    env->GetByteArrayRegion(romBytes, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
    if (env->ExceptionCheck()) return -3;

    std::string name = "game.sfc";
    if (displayName) {
        const char* chars = env->GetStringUTFChars(displayName, nullptr);
        if (chars) {
            name.assign(chars);
            env->ReleaseStringUTFChars(displayName, chars);
        }
    }
    const size_t slash = name.find_last_of("/\\");
    if (slash != std::string::npos) name.erase(0, slash + 1);
    if (name.empty()) name = "game.sfc";
    const uint64_t romFingerprint = fingerprintBytes(bytes.data(), bytes.size());

    stopFrameThread();
    bool loaded = false;
    {
        std::lock_guard<std::mutex> lock(g_coreMutex);
        if (g_gameLoaded.load(std::memory_order_acquire)) retro_unload_game();
        g_gameLoaded.store(false, std::memory_order_release);
        g_romFingerprint = 0;
        g_romName = name;
        g_pixelFormat = RETRO_PIXEL_FORMAT_RGB565;
        if (!chooseCoreForGame(hasNesExtension(name))) {
            logMessage(ANDROID_LOG_ERROR, "Unable to load optional NES FCEUmm core");
            return -11;
        }
        retro_game_info info{};
        // FCEUmm requires need_fullpath=true; Android imports local files to
        // filesDir/roms. The supplied displayName is reduced to a basename.
        static std::string fullRomPath;
        fullRomPath = g_appDataPath + "/roms/" + g_romName;
        info.path = g_isNes ? fullRomPath.c_str() : g_romName.c_str();
        info.data = g_isNes ? nullptr : bytes.data();
        info.size = g_isNes ? 0 : bytes.size();
        info.meta = nullptr;
        loaded = retro_load_game(&info);
        if (loaded) retro_cheat_reset(); // No cheats leak across games.
        g_romFingerprint = loaded ? romFingerprint : 0;
        g_gameLoaded.store(loaded, std::memory_order_release);
    }

    const uint64_t currentWrite = g_audioWrite.load(std::memory_order_acquire);
    g_audioRead.store(currentWrite, std::memory_order_release);
    if (loaded) {
        // Respect the core's reported region timing (NTSC/PAL) instead of forcing 60 Hz.
        retro_system_av_info avInfo{};
        retro_get_system_av_info(&avInfo);
        const double fps = avInfo.timing.fps;
        if (fps >= 20.0 && fps <= 75.0) {
            g_frameDurationNs.store(
                static_cast<int64_t>(1000000000.0 / fps),
                std::memory_order_release);
        } else {
            g_frameDurationNs.store(16683333, std::memory_order_release);
        }
        logMessage(ANDROID_LOG_INFO, "ROM loaded: %s (%d bytes), core FPS %.3f",
                   g_romName.c_str(), length, fps);
        startFrameThread();
        return 0;
    }
    logMessage(ANDROID_LOG_ERROR, "retro_load_game rejected ROM: %s", g_romName.c_str());
    return -4;
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetSurface(JNIEnv* env, jobject, jobject surface) {
    ANativeWindow* next = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    ANativeWindow* previous = nullptr;
    {
        std::lock_guard<std::mutex> guard(g_surfaceMutex);
        previous = g_window;
        g_window = next;
        g_surfaceWidth = 0;
        g_surfaceHeight = 0;
    }
    if (previous) ANativeWindow_release(previous);
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetInput(JNIEnv*, jobject, jint bits) {
    g_inputMask.store(static_cast<uint32_t>(bits), std::memory_order_release);
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetPlayer2Input(JNIEnv*, jobject, jint bits) {
    g_player2InputMask.store(static_cast<uint32_t>(bits) & 0xffffu, std::memory_order_release);
}

// Atomic per-pad update. Remote UDP receiver calls this directly on its own
// thread; no UI message queue and no emulated-frame lock on individual inputs.
extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetPlayerInput(
    JNIEnv*, jobject, jint player, jint bits) {
    const uint32_t value = static_cast<uint32_t>(bits) & 0xffffu;
    switch (player) {
        case 1: g_inputMask.store(value, std::memory_order_release); break;
        case 2: g_player2InputMask.store(value, std::memory_order_release); break;
        case 3: g_player3InputMask.store(value, std::memory_order_release); break;
        case 4: g_player4InputMask.store(value, std::memory_order_release); break;
        default: break;
    }
}

// Snes9x supports up to four pads on its second SNES controller port.
// Reconfigure only while holding the same mutex as retro_run().
extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetMultitapEnabled(
    JNIEnv*, jobject, jboolean enabled) {
    const bool requested = enabled == JNI_TRUE;
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (g_isNes) { // SNES-specific multitap subclass is invalid on NES.
        g_multitapEnabled.store(false, std::memory_order_release);
        return;
    }
    if (g_multitapEnabled.load(std::memory_order_relaxed) == requested) return;
    retro_set_controller_port_device(1, requested
        ? kSnes9xMultitap : RETRO_DEVICE_JOYPAD);
    g_multitapEnabled.store(requested, std::memory_order_release);
    if (!requested) {
        g_player3InputMask.store(0, std::memory_order_release);
        g_player4InputMask.store(0, std::memory_order_release);
    }
}

// Netplay owns the frame clock while enabled. The offline frame thread must be
// fully joined before serializing initial state or executing the first frame.
extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetNetplayMode(JNIEnv*, jobject, jboolean enabled) {
    if (enabled == JNI_TRUE) {
        g_rollbackReplaying.store(false, std::memory_order_release);
        g_netplayMode.store(true, std::memory_order_release);
        g_netplayCheatSetupAllowed.store(true, std::memory_order_release);
        stopFrameThread();
        g_inputMask.store(0, std::memory_order_release);
        g_player2InputMask.store(0, std::memory_order_release);
        g_player3InputMask.store(0, std::memory_order_release);
        g_player4InputMask.store(0, std::memory_order_release);
        {
            std::lock_guard<std::mutex> lock(g_coreMutex);
            if (g_multitapEnabled.exchange(false, std::memory_order_acq_rel))
                retro_set_controller_port_device(1, RETRO_DEVICE_JOYPAD);
        }
        // Cheats are not frame-deterministic across all cores/ROMs. Disable them.
        std::lock_guard<std::mutex> lock(g_coreMutex);
        if (g_gameLoaded.load(std::memory_order_acquire)) retro_cheat_reset();
    } else {
        g_rollbackReplaying.store(false, std::memory_order_release);
        g_netplayCheatSetupAllowed.store(false, std::memory_order_release);
        stopFrameThread();
        g_netplayMode.store(false, std::memory_order_release);
        g_netplayRenderStride.store(1, std::memory_order_release);
        if (g_gameLoaded.load(std::memory_order_acquire)) startFrameThread();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetNetplayPresentationStride(
    JNIEnv*, jobject, jint stride) {
    g_netplayRenderStride.store(stride == 2 ? 2 : 1, std::memory_order_release);
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetRollbackReplaying(
    JNIEnv*, jobject, jboolean value) {
    g_rollbackReplaying.store(value == JNI_TRUE, std::memory_order_release);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeNetplayStep(
    JNIEnv*, jobject, jint player1, jint player2) {
    if (!g_netplayMode.load(std::memory_order_acquire)) return -1;
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_gameLoaded.load(std::memory_order_acquire)) return -2;
    g_inputMask.store(static_cast<uint32_t>(player1) & 0xffffu, std::memory_order_release);
    g_player2InputMask.store(static_cast<uint32_t>(player2) & 0xffffu, std::memory_order_release);
    g_netplayCheatSetupAllowed.store(false, std::memory_order_release);
    const auto begin = std::chrono::steady_clock::now();
    retro_run();
    const auto after = std::chrono::steady_clock::now();
    g_emuFrames.fetch_add(1, std::memory_order_relaxed);
    g_emuNs.fetch_add(static_cast<uint64_t>(std::chrono::duration_cast<
        std::chrono::nanoseconds>(after - begin).count()), std::memory_order_relaxed);
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeGetFramePeriodNs(JNIEnv*, jobject) {
    return static_cast<jlong>(g_frameDurationNs.load(std::memory_order_acquire));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeNetplayStateHash(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_gameLoaded.load(std::memory_order_acquire)) return 0;
    const size_t size = retro_serialize_size();
    if (size == 0 || size > kMaxStateBytes) return 0;
    // Reuse one buffer per netplay thread instead of reallocating it at
    // every periodic deterministic-state verification.
    thread_local std::vector<uint8_t> bytes;
    bytes.resize(size);
    if (!retro_serialize(bytes.data(), bytes.size())) return 0;
    return static_cast<jlong>(fingerprintBytes(bytes.data(), bytes.size()));
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetVideoFilter(
    JNIEnv*, jobject, jint filter) {
    g_videoFilter.store(std::max(0, std::min(8, static_cast<int>(filter))),
                        std::memory_order_release);
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetOutputScale(
    JNIEnv*, jobject, jint scale) {
    g_outputScale.store(std::clamp(static_cast<int>(scale), 1, 4),
                        std::memory_order_release);
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetGpuEnabled(
    JNIEnv*, jobject, jboolean enabled) {
    g_gpuEnabled.store(enabled == JNI_TRUE, std::memory_order_release);
    if (enabled != JNI_TRUE) {
        std::lock_guard<std::mutex> guard(g_gpuFrameMutex);
        g_gpuFrame.clear();
        g_gpuWidth = g_gpuHeight = 0;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeCopyGpuFrame(
    JNIEnv* env, jobject, jobject buffer, jintArray metadata) {
    if (!buffer || !metadata || env->GetArrayLength(metadata) < 3)
        return JNI_FALSE;
    auto* target = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (!target || capacity <= 0) return JNI_FALSE;
    std::lock_guard<std::mutex> guard(g_gpuFrameMutex);
    const size_t byteCount = g_gpuFrame.size() * sizeof(uint16_t);
    if (byteCount == 0 || static_cast<uint64_t>(capacity) < byteCount)
        return JNI_FALSE;
    std::memcpy(target, g_gpuFrame.data(), byteCount);
    const jint info[3] = { static_cast<jint>(g_gpuWidth),
        static_cast<jint>(g_gpuHeight), static_cast<jint>(g_gpuRevision) };
    env->SetIntArrayRegion(metadata, 0, 3, info);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeGetPerfCounters(JNIEnv* env, jobject) {
    const jlong stats[6] = {
        static_cast<jlong>(g_emuFrames.load(std::memory_order_relaxed)),
        static_cast<jlong>(g_emuNs.load(std::memory_order_relaxed)),
        static_cast<jlong>(g_presentFrames.load(std::memory_order_relaxed)),
        static_cast<jlong>(g_presentNs.load(std::memory_order_relaxed)),
        static_cast<jlong>(g_frameDurationNs.load(std::memory_order_relaxed)),
        static_cast<jlong>((g_audioWrite.load(std::memory_order_acquire) -
                            g_audioRead.load(std::memory_order_acquire)) / 2u)
    };
    jlongArray result = env->NewLongArray(6);
    if (result) env->SetLongArrayRegion(result, 0, 6, stats);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeReadAudio(JNIEnv* env, jobject, jshortArray output) {
    if (!output) return 0;
    const jsize arrayLength = env->GetArrayLength(output);
    const size_t capacity = static_cast<size_t>(arrayLength) & ~static_cast<size_t>(1);
    if (capacity == 0) return 0;

    const uint64_t read = g_audioRead.load(std::memory_order_relaxed);
    const uint64_t write = g_audioWrite.load(std::memory_order_acquire);
    const size_t available = static_cast<size_t>(std::min<uint64_t>(write - read, kAudioCapacity));
    const size_t count = std::min(capacity, available) & ~static_cast<size_t>(1);
    if (count == 0) return 0;

    auto* destination = static_cast<jshort*>(env->GetPrimitiveArrayCritical(output, nullptr));
    if (!destination) return 0;
    for (size_t i = 0; i < count; ++i) {
        destination[i] = static_cast<jshort>(g_audioRing[(read + i) % kAudioCapacity]);
    }
    env->ReleasePrimitiveArrayCritical(output, destination, 0);
    g_audioRead.store(read + count, std::memory_order_release);
    return static_cast<jint>(count);
}


// Rebuild from a complete selected set to allow deactivation. In pinned
// Snes9x libretro.cpp, retro_cheat_set() uses a 256-byte unchecked strcpy:
// reject untrusted cheat strings longer than 190 ASCII chars *before* calling.
static jint applyValidatedCheats(
    JNIEnv* env, jobjectArray codesArray, bool netplayHandshake) {
    if (!codesArray) return -1;
    const jsize count = env->GetArrayLength(codesArray);
    if (count > 64 || count < 0) return -2;
    std::vector<std::string> selected;
    selected.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto* item = static_cast<jstring>(env->GetObjectArrayElement(codesArray, i));
        if (!item) return -3;
        const char* chars = env->GetStringUTFChars(item, nullptr);
        if (!chars) {
            env->DeleteLocalRef(item);
            return -3;
        }
        std::string code(chars);
        env->ReleaseStringUTFChars(item, chars);
        env->DeleteLocalRef(item);
        if (code.empty() || code.size() > 190 ||
            !std::all_of(code.begin(), code.end(), [](unsigned char c) {
                return (c >= '0' && c <= '9') ||
                    (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f') ||
                    c == '-' || c == ':' || c == '+' ||
                    c == ',' || c == '.' || c == ';' || c == ' ';
            })) return -4;
        selected.emplace_back(std::move(code));
    }
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_gameLoaded.load(std::memory_order_acquire)) return -5;
    const bool netplay = g_netplayMode.load(std::memory_order_acquire);
    const bool allowed = g_netplayCheatSetupAllowed.load(std::memory_order_acquire);
    if (netplayHandshake ? (!netplay || !allowed) : netplay) return -6;
    retro_cheat_reset();
    for (size_t i = 0; i < selected.size(); ++i) {
        retro_cheat_set(static_cast<unsigned>(i), true, selected[i].c_str());
    }
    return static_cast<jint>(selected.size());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeApplyCheats(
    JNIEnv* env, jobject, jobjectArray codesArray) {
    return applyValidatedCheats(env, codesArray, false);
}

// Called ONLY during MZNP v3 handshake. The native gate shuts permanently
// at first netplay frame, preventing unilateral mid-session cheats.
extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeApplyOnlineCheats(
    JNIEnv* env, jobject, jobjectArray codesArray) {
    return applyValidatedCheats(env, codesArray, true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetCoreOption(
    JNIEnv* env, jobject, jstring keyString, jstring valueString) {
    if (!keyString || !valueString) return;
    const char* keyChars = env->GetStringUTFChars(keyString, nullptr);
    if (!keyChars) return;
    const std::string key(keyChars);
    env->ReleaseStringUTFChars(keyString, keyChars);
    const char* valueChars = env->GetStringUTFChars(valueString, nullptr);
    if (!valueChars) return;
    const std::string value(valueChars);
    env->ReleaseStringUTFChars(valueString, valueChars);

    std::lock_guard<std::mutex> lock(g_coreMutex);
    const auto found = g_coreOptions.find(key);
    if (found == g_coreOptions.end() || found->second == value) return;
    found->second = value;
    g_coreOptionsDirty.store(true, std::memory_order_release);
    logMessage(ANDROID_LOG_INFO, "Core option updated: %s=%s", key.c_str(), value.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeGetAudioSampleRate(JNIEnv*, jobject) {
    if (!g_initialized.load(std::memory_order_acquire)) return 32040;
    std::lock_guard<std::mutex> lock(g_coreMutex);
    retro_system_av_info info{};
    retro_get_system_av_info(&info);
    const int rate = static_cast<int>(info.timing.sample_rate + 0.5);
    return (rate >= 8000 && rate <= 192000) ? rate : 32040;
}




extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeResetGame(JNIEnv*, jobject) {
    if (!g_initialized.load(std::memory_order_acquire)) return -1;

    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_gameLoaded.load(std::memory_order_acquire)) return -2;

    retro_reset();
    // Kotlin stops the audio consumer before calling reset, so dropping old queued audio is safe.
    const uint64_t latestWrite = g_audioWrite.load(std::memory_order_acquire);
    g_audioRead.store(latestWrite, std::memory_order_release);
    logMessage(ANDROID_LOG_INFO, "Game reset requested");
    return 0;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSerializeState(JNIEnv* env, jobject) {
    std::vector<uint8_t> stateBytes;
    uint64_t romFingerprint = 0;
    {
        std::lock_guard<std::mutex> lock(g_coreMutex);
        if (!g_initialized.load(std::memory_order_acquire) ||
            !g_gameLoaded.load(std::memory_order_acquire)) {
            return nullptr;
        }
        const size_t size = retro_serialize_size();
        if (size == 0 || size > kMaxStateBytes || g_romFingerprint == 0) {
            logMessage(ANDROID_LOG_WARN, "Invalid save-state size or ROM fingerprint: %zu", size);
            return nullptr;
        }
        stateBytes.resize(size);
        if (!retro_serialize(stateBytes.data(), stateBytes.size())) {
            logMessage(ANDROID_LOG_ERROR, "Core failed to serialize save state");
            return nullptr;
        }
        romFingerprint = g_romFingerprint;
    }

    // Container header: magic[8], format version u32 LE, ROM fingerprint u64 LE,
    // and payload size u64 LE. This prevents restoring data into a different ROM.
    uint8_t header[kStateHeaderSize]{};
    std::memcpy(header, kStateMagic, sizeof(kStateMagic));
    writeU32LE(header + 8, kStateFormatVersion);
    writeU64LE(header + 12, romFingerprint);
    writeU64LE(header + 20, static_cast<uint64_t>(stateBytes.size()));

    const size_t totalSize = kStateHeaderSize + stateBytes.size();
    jbyteArray output = env->NewByteArray(static_cast<jsize>(totalSize));
    if (!output) return nullptr;
    env->SetByteArrayRegion(output, 0, static_cast<jsize>(kStateHeaderSize),
                            reinterpret_cast<const jbyte*>(header));
    if (env->ExceptionCheck()) return nullptr;
    env->SetByteArrayRegion(output, static_cast<jsize>(kStateHeaderSize),
                            static_cast<jsize>(stateBytes.size()),
                            reinterpret_cast<const jbyte*>(stateBytes.data()));
    if (env->ExceptionCheck()) return nullptr;
    return output;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeUnserializeState(
    JNIEnv* env, jobject, jbyteArray stateArray) {
    if (!g_initialized.load(std::memory_order_acquire) || !stateArray) return -1;

    const jsize length = env->GetArrayLength(stateArray);
    if (length <= static_cast<jsize>(kStateHeaderSize) ||
        static_cast<size_t>(length) > kStateHeaderSize + kMaxStateBytes) return -2;

    uint8_t header[kStateHeaderSize]{};
    env->GetByteArrayRegion(
        stateArray, 0, static_cast<jsize>(kStateHeaderSize),
        reinterpret_cast<jbyte*>(header));
    if (env->ExceptionCheck()) return -3;
    if (std::memcmp(header, kStateMagic, sizeof(kStateMagic)) != 0 ||
        readU32LE(header + 8) != kStateFormatVersion) {
        logMessage(ANDROID_LOG_WARN, "Save-state header/version is not recognized");
        return -4;
    }

    const uint64_t savedRomFingerprint = readU64LE(header + 12);
    const uint64_t declaredPayloadSize = readU64LE(header + 20);
    const size_t actualPayloadSize = static_cast<size_t>(length) - kStateHeaderSize;
    if (declaredPayloadSize == 0 || declaredPayloadSize > kMaxStateBytes ||
        declaredPayloadSize != actualPayloadSize) {
        logMessage(ANDROID_LOG_WARN, "Save-state payload size is invalid");
        return -5;
    }

    std::vector<uint8_t> stateBytes(actualPayloadSize);
    env->GetByteArrayRegion(
        stateArray, static_cast<jsize>(kStateHeaderSize),
        static_cast<jsize>(actualPayloadSize),
        reinterpret_cast<jbyte*>(stateBytes.data()));
    if (env->ExceptionCheck()) return -6;

    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_gameLoaded.load(std::memory_order_acquire)) return -7;
    if (savedRomFingerprint != g_romFingerprint) {
        logMessage(ANDROID_LOG_WARN, "Save state belongs to a different ROM image");
        return -8;
    }
    if (!retro_unserialize(stateBytes.data(), stateBytes.size())) {
        logMessage(ANDROID_LOG_WARN, "Core rejected save state payload (%zu bytes)", actualPayloadSize);
        return -9;
    }

    // Kotlin stops the audio consumer before restore, so this ring reset is race-free.
    const uint64_t latestWrite = g_audioWrite.load(std::memory_order_acquire);
    g_audioRead.store(latestWrite, std::memory_order_release);
    logMessage(ANDROID_LOG_INFO, "Save state restored (%zu bytes)", actualPayloadSize);
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeSetPaused(JNIEnv*, jobject, jboolean paused) {
    const bool shouldPause = paused == JNI_TRUE;
    g_paused.store(shouldPause, std::memory_order_release);
    if (shouldPause) {
        // Wait for a currently executing retro_run() to finish, then discard queued audio.
        std::lock_guard<std::mutex> lock(g_coreMutex);
        const uint64_t latestWrite = g_audioWrite.load(std::memory_order_acquire);
        g_audioRead.store(latestWrite, std::memory_order_release);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeDiscardAudioQueue(JNIEnv*, jobject) {
    // The game/netplay must not stop advancing just because the display is
    // backgrounded. Discard only unheard PCM so foreground resume has no
    // seconds-long backlog from the previous Surface.
    const auto head = g_audioWrite.load(std::memory_order_acquire);
    g_audioRead.store(head, std::memory_order_release);
}

/**
 * Manual, OFFLINE-ONLY NES rollback determinism smoke test.
 * Serialize initial FCEUmm state, replay identical neutral input frames twice
 * from the same snapshot, and compare final states byte-for-byte.
 * Restores the original state even when the comparison fails.
 *
 * This never runs for Snes9x, never uses network sockets or Gekko callbacks,
 * and never runs while a netplay session is active. Its work is capped.
 * Returns: 0 = stable, -1 = wrong mode, -2 = too large/invalid state,
 * -3 = serialize failed, -4 = restore/advance failed, -5 = mismatch.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeNesDeterminismCheck(
    JNIEnv*, jobject, jint requestedFrames) {
    if (!g_initialized.load(std::memory_order_acquire) ||
        !g_gameLoaded.load(std::memory_order_acquire) ||
        !g_isNes || g_netplayMode.load(std::memory_order_acquire) ||
        !g_paused.load(std::memory_order_acquire)) return -1;

    stopFrameThread();
    int result = -1;
    {
        std::lock_guard<std::mutex> lock(g_coreMutex);
        if (g_isNes && g_gameLoaded.load(std::memory_order_acquire) &&
            !g_netplayMode.load(std::memory_order_acquire) &&
            g_paused.load(std::memory_order_acquire)) {
            const size_t bytes = retro_serialize_size();
            if (bytes == 0 || bytes > 2u * 1024u * 1024u)
                result = -2;
            else {
                std::vector<uint8_t> initial(bytes), first(bytes), second(bytes);
                if (!retro_serialize(initial.data(), bytes)) result = -3;
                else {
                    const uint32_t previousP1 =
                        g_inputMask.exchange(0, std::memory_order_acq_rel);
                    const uint32_t previousP2 =
                        g_player2InputMask.exchange(0, std::memory_order_acq_rel);
                    const bool previousReplay =
                        g_rollbackReplaying.exchange(true, std::memory_order_acq_rel);
                    const int frames = std::max(1, std::min(60,
                        static_cast<int>(requestedFrames)));
                    const auto attempt = [&](std::vector<uint8_t>& destination) {
                        if (!retro_unserialize(initial.data(), bytes)) return false;
                        for (int i = 0; i < frames; ++i) retro_run();
                        return retro_serialize(destination.data(), bytes);
                    };
                    const bool a = attempt(first);
                    const bool b = a && attempt(second);
                    result = !a || !b ? -4 :
                        (std::memcmp(first.data(), second.data(), bytes) == 0 ? 0 : -5);
                    // Always restore the game, even if one replay failed.
                    if (!retro_unserialize(initial.data(), bytes)) result = -4;
                    g_inputMask.store(previousP1, std::memory_order_release);
                    g_player2InputMask.store(previousP2, std::memory_order_release);
                    g_rollbackReplaying.store(previousReplay, std::memory_order_release);
                    const uint64_t head =
                        g_audioWrite.load(std::memory_order_acquire);
                    g_audioRead.store(head, std::memory_order_release);
                }
            }
        }
    }
    // The offline frame thread resumes sleeping while paused; the Activity
    // restores the previous user pause state when the manual test finishes.
    if (g_gameLoaded.load(std::memory_order_acquire) &&
        !g_netplayMode.load(std::memory_order_acquire)) startFrameThread();
    logMessage(result == 0 ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
        "NES offline determinism diagnostic returned %d", result);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeShutdown(JNIEnv*, jobject) {
    g_paused.store(true, std::memory_order_release);
    stopFrameThread();
    {
        std::lock_guard<std::mutex> lock(g_coreMutex);
        if (g_gameLoaded.exchange(false, std::memory_order_acq_rel)) retro_unload_game();
        g_romFingerprint = 0;
        if (g_nesReady) {
            g_nesApi.deinit();
            g_nesReady = false;
        }
        g_isNes = false;
        g_api = &g_snesApi;
        if (g_initialized.exchange(false, std::memory_order_acq_rel)) g_snesApi.deinit();
        if (g_nesDso) {
            dlclose(g_nesDso);
            g_nesDso = nullptr;
            g_nesApi = CoreApi{};
        }
    }
    {
        std::lock_guard<std::mutex> guard(g_surfaceMutex);
        if (g_window) {
            ANativeWindow_release(g_window);
            g_window = nullptr;
            g_surfaceWidth = 0;
            g_surfaceHeight = 0;
        }
    }
    g_inputMask.store(0, std::memory_order_release);
    g_player2InputMask.store(0, std::memory_order_release);
    g_player3InputMask.store(0, std::memory_order_release);
    g_player4InputMask.store(0, std::memory_order_release);
    g_multitapEnabled.store(false, std::memory_order_release);
    g_netplayMode.store(false, std::memory_order_release);
    g_audioWrite.store(0, std::memory_order_release);
    g_audioRead.store(0, std::memory_order_release);
    g_emuFrames.store(0, std::memory_order_release);
    g_emuNs.store(0, std::memory_order_release);
    g_presentFrames.store(0, std::memory_order_release);
    g_presentNs.store(0, std::memory_order_release);
    logMessage(ANDROID_LOG_INFO, "Snes9x native bridge shut down");
}
