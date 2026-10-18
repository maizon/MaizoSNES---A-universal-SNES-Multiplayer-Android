#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <libretro.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdarg>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {
constexpr char kLogTag[] = "MaizoSNES";
constexpr size_t kAudioCapacity = 65536; // int16 values; SPSC ring buffer.
constexpr size_t kMaxRomBytes = 64u * 1024u * 1024u;

std::string g_appDataPath;
std::string g_romName;
std::atomic<bool> g_initialized{false};
std::atomic<bool> g_gameLoaded{false};
std::atomic<bool> g_runFrames{false};
std::atomic<uint32_t> g_inputMask{0};
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

void logMessage(int priority, const char* format, ...) {
    va_list args;
    va_start(args, format);
    __android_log_vprint(priority, kLogTag, format, args);
    va_end(args);
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
        case RETRO_ENVIRONMENT_GET_VARIABLE:
            if (data) static_cast<retro_variable*>(data)->value = nullptr;
            return false;
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            if (data) *static_cast<bool*>(data) = false;
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
    if (!samples || frames == 0) return;
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
    if (port != 0 || index != 0 || device != RETRO_DEVICE_JOYPAD ||
        id >= RETRO_DEVICE_ID_JOYPAD_LAST) {
        return 0;
    }
    return (g_inputMask.load(std::memory_order_relaxed) & (1u << id)) ? 1 : 0;
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
    if (!data || width == 0 || height == 0 || width > 1024 || height > 1024) return;

    std::lock_guard<std::mutex> guard(g_surfaceMutex);
    if (!g_window) return;

    if (g_surfaceWidth != static_cast<int>(width) ||
        g_surfaceHeight != static_cast<int>(height)) {
        if (ANativeWindow_setBuffersGeometry(
                g_window, static_cast<int32_t>(width),
                static_cast<int32_t>(height), WINDOW_FORMAT_RGB_565) != 0) {
            return;
        }
        g_surfaceWidth = static_cast<int>(width);
        g_surfaceHeight = static_cast<int>(height);
    }

    ANativeWindow_Buffer buffer{};
    if (ANativeWindow_lock(g_window, &buffer, nullptr) != 0 || !buffer.bits) return;

    const unsigned copyWidth = std::min(width, static_cast<unsigned>(buffer.width));
    const unsigned copyHeight = std::min(height, static_cast<unsigned>(buffer.height));
    auto* destination = static_cast<uint16_t*>(buffer.bits);
    const auto* sourceBytes = static_cast<const uint8_t*>(data);
    const size_t sourceBpp = g_pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888 ? 4u : 2u;
    const size_t requiredPitch = static_cast<size_t>(width) * sourceBpp;

    if (pitch < requiredPitch) {
        ANativeWindow_unlockAndPost(g_window);
        return;
    }

    for (unsigned y = 0; y < copyHeight; ++y) {
        uint16_t* dst = destination + static_cast<size_t>(y) * buffer.stride;
        const uint8_t* src = sourceBytes + static_cast<size_t>(y) * pitch;
        if (g_pixelFormat == RETRO_PIXEL_FORMAT_RGB565) {
            std::memcpy(dst, src, static_cast<size_t>(copyWidth) * sizeof(uint16_t));
        } else if (g_pixelFormat == RETRO_PIXEL_FORMAT_0RGB1555) {
            const auto* src16 = reinterpret_cast<const uint16_t*>(src);
            for (unsigned x = 0; x < copyWidth; ++x) dst[x] = convert1555(src16[x]);
        } else {
            const auto* src32 = reinterpret_cast<const uint32_t*>(src);
            for (unsigned x = 0; x < copyWidth; ++x) dst[x] = convertXrgb8888(src32[x]);
        }
    }
    ANativeWindow_unlockAndPost(g_window);
}

void stopFrameThread() {
    g_runFrames.store(false, std::memory_order_release);
    if (g_frameThread.joinable()) g_frameThread.join();
}

void startFrameThread() {
    if (g_frameThread.joinable()) return;
    g_runFrames.store(true, std::memory_order_release);
    g_frameThread = std::thread([] {
        using clock = std::chrono::steady_clock;
        constexpr auto frameDuration = std::chrono::microseconds(16667);
        while (g_runFrames.load(std::memory_order_acquire)) {
            const auto frameStart = clock::now();
            {
                std::lock_guard<std::mutex> lock(g_coreMutex);
                if (!g_gameLoaded.load(std::memory_order_acquire)) break;
                retro_run();
            }
            std::this_thread::sleep_until(frameStart + frameDuration);
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

    retro_set_environment(environmentCallback);
    retro_set_video_refresh(videoRefresh);
    retro_set_audio_sample(audioSample);
    retro_set_audio_sample_batch(audioSampleBatch);
    retro_set_input_poll(inputPoll);
    retro_set_input_state(inputState);
    retro_init();
    retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
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

    stopFrameThread();
    bool loaded = false;
    {
        std::lock_guard<std::mutex> lock(g_coreMutex);
        if (g_gameLoaded.load(std::memory_order_acquire)) retro_unload_game();
        g_gameLoaded.store(false, std::memory_order_release);
        g_romName = name;
        retro_game_info info{};
        info.path = g_romName.c_str();
        info.data = bytes.data();
        info.size = bytes.size();
        info.meta = nullptr;
        loaded = retro_load_game(&info);
        g_gameLoaded.store(loaded, std::memory_order_release);
    }

    const uint64_t currentWrite = g_audioWrite.load(std::memory_order_acquire);
    g_audioRead.store(currentWrite, std::memory_order_release);
    if (loaded) {
        logMessage(ANDROID_LOG_INFO, "ROM loaded: %s (%d bytes)", g_romName.c_str(), length);
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

extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeGetAudioSampleRate(JNIEnv*, jobject) {
    if (!g_initialized.load(std::memory_order_acquire)) return 32040;
    std::lock_guard<std::mutex> lock(g_coreMutex);
    retro_system_av_info info{};
    retro_get_system_av_info(&info);
    const int rate = static_cast<int>(info.timing.sample_rate + 0.5);
    return (rate >= 8000 && rate <= 192000) ? rate : 32040;
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_NativeBridge_nativeShutdown(JNIEnv*, jobject) {
    stopFrameThread();
    {
        std::lock_guard<std::mutex> lock(g_coreMutex);
        if (g_gameLoaded.exchange(false, std::memory_order_acq_rel)) retro_unload_game();
        if (g_initialized.exchange(false, std::memory_order_acq_rel)) retro_deinit();
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
    g_audioWrite.store(0, std::memory_order_release);
    g_audioRead.store(0, std::memory_order_release);
    logMessage(ANDROID_LOG_INFO, "Snes9x native bridge shut down");
}
