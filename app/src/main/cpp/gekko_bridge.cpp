#include <jni.h>
#include <gekkonet.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <atomic>
#include <string>
#include <cstdlib>
#include <vector>

// One active local emulation engine; all entrypoints run on the rollback worker.
// Stable Snes9x and TCP implementations live in other compilation units.
namespace {
std::mutex sessionLock;
std::string persistentRemoteEndpoint;
GekkoSession* session = nullptr;
int localHandle = -1, remoteHandle = -1;
unsigned int stateCapacity = 0;
int64_t rollbackCount = 0, advances = 0;
bool connected = false;
bool aborted = false;
std::atomic<bool> requestedLimitedSaving{false};
JavaVM* rtcVm = nullptr;
jobject rtcPort = nullptr; // global ref, only while active
std::vector<GekkoNetResult*> rtcResults;

static JNIEnv* rtcEnv() {
    JNIEnv* env = nullptr;
    return rtcVm && rtcVm->GetEnv(reinterpret_cast<void**>(&env),
                                  JNI_VERSION_1_6) == JNI_OK ? env : nullptr;
}
static void rtcSend(GekkoNetAddress*, const char* bytes, int length) {
    auto* env = rtcEnv();
    if (!env || !rtcPort || !bytes || length <= 0 || length > 16384) return;
    jclass cls = env->GetObjectClass(rtcPort);
    jmethodID method = env->GetMethodID(cls, "send", "([B)Z");
    if (!method || env->ExceptionCheck()) {
        env->ExceptionClear(); env->DeleteLocalRef(cls); return;
    }
    jbyteArray payload = env->NewByteArray(length);
    if (payload) {
        env->SetByteArrayRegion(payload, 0, length,
                                reinterpret_cast<const jbyte*>(bytes));
        env->CallBooleanMethod(rtcPort, method, payload);
        env->DeleteLocalRef(payload);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
}
static GekkoNetResult** rtcReceive(int* total) {
    *total = 0;
    auto* env = rtcEnv();
    if (!env || !rtcPort) return nullptr;
    rtcResults.clear(); // only previous POINTER ARRAY, packet structs were freed
    jclass cls = env->GetObjectClass(rtcPort);
    jmethodID method = env->GetMethodID(cls, "takePacket", "()[B");
    if (!method || env->ExceptionCheck()) {
        env->ExceptionClear(); env->DeleteLocalRef(cls); return nullptr;
    }
    for (int i = 0; i < 64; ++i) {
        auto payload = static_cast<jbyteArray>(env->CallObjectMethod(
            rtcPort, method));
        if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
        if (!payload) break;
        const auto len = env->GetArrayLength(payload);
        if (len > 0 && len <= 16384) {
            auto* packet = static_cast<GekkoNetResult*>(
                std::calloc(1, sizeof(GekkoNetResult)));
            if (packet) {
                packet->addr.size = static_cast<unsigned int>(
                    persistentRemoteEndpoint.size());
                packet->addr.data = std::malloc(packet->addr.size);
                packet->data_len = static_cast<unsigned int>(len);
                packet->data = std::malloc(len);
                if (packet->addr.data && packet->data) {
                    std::memcpy(packet->addr.data,
                                persistentRemoteEndpoint.data(), packet->addr.size);
                    env->GetByteArrayRegion(payload, 0, len,
                                            static_cast<jbyte*>(packet->data));
                    rtcResults.push_back(packet);
                } else {
                    std::free(packet->addr.data);
                    std::free(packet->data);
                    std::free(packet);
                }
            }
        }
        env->DeleteLocalRef(payload);
    }
    env->DeleteLocalRef(cls);
    *total = static_cast<int>(rtcResults.size());
    return rtcResults.data();
}
static void rtcFree(void* p) { std::free(p); }
GekkoNetAdapter rtcAdapter{rtcSend, rtcReceive, rtcFree};

constexpr unsigned int kMaxBytes = 16u * 1024u * 1024u;

uint32_t fnv1a(const unsigned char* data, unsigned int size) {
    uint32_t hash = 2166136261u;
    for (unsigned int i = 0; i < size; ++i) {
        hash ^= data[i];
        hash *= 16777619u;
    }
    return hash;
}
void stopUnsafe() {
    if (session) {
        gekko_destroy(&session);
        session = nullptr;
    }
    gekko_default_adapter_destroy();
    if (rtcPort) {
        if (JNIEnv* env = rtcEnv()) env->DeleteGlobalRef(rtcPort);
        rtcPort = nullptr;
    }
    rtcResults.clear();
    persistentRemoteEndpoint.clear();
    localHandle = remoteHandle = -1;
    connected = false;
    stateCapacity = 0;
}
bool javaException(JNIEnv* env) {
    if (!env->ExceptionCheck()) return false;
    env->ExceptionDescribe();
    env->ExceptionClear();
    return true;
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_GekkoNative_nativeConfigureLimitedSaving(
    JNIEnv*, jobject, jboolean enabled) {
    requestedLimitedSaving.store(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_GekkoNative_nativeApiVersion(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(sessionLock);
    GekkoSession* temporary = nullptr;
    if (!gekko_create(&temporary, GekkoGameSession)) return -1;
    return gekko_destroy(&temporary) ? 1 : -2;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_maizon_maizosnes_GekkoNative_nativeStart(
    JNIEnv* env, jobject, jint localPort, jstring address, jint remotePort,
    jint localPlayer, jint size, jint prediction, jint delay) {
    std::lock_guard<std::mutex> lock(sessionLock);
    if (session || !address || localPort < 1024 || localPort > 65535 ||
        remotePort < 1024 || remotePort > 65535 ||
        localPlayer < 0 || localPlayer > 1 ||
        size < 32 || size > static_cast<jint>(kMaxBytes) ||
        prediction < 1 || prediction > 10 || delay < 0 || delay > 8) return JNI_FALSE;
    const char* host = env->GetStringUTFChars(address, nullptr);
    if (!host) return JNI_FALSE;
    std::string remote = std::string(host) + ":" + std::to_string(remotePort);
    env->ReleaseStringUTFChars(address, host);
    if (remote.size() > 260) return JNI_FALSE;

    if (!gekko_create(&session, GekkoGameSession)) return JNI_FALSE;
    GekkoConfig config{};
    config.num_players = 2;
    config.input_size = sizeof(uint32_t);
    config.state_size = static_cast<unsigned int>(size);
    config.max_input_prediction_window = static_cast<unsigned char>(prediction);
    // The pinned GekkoNet release only guarantees health checks with
    // full saving; Limited Saving is chosen only for expensive Snes9x
    // serialization before creating a session.
    const bool limited = requestedLimitedSaving.exchange(false);
    config.desync_detection = !limited;
    config.limited_saving = limited;
    gekko_start(session, &config);
    GekkoNetAdapter* adapter = rtcPort ? &rtcAdapter :
        gekko_default_adapter(static_cast<unsigned short>(localPort));
    if (!adapter) {
        stopUnsafe();
        return JNI_FALSE;
    }
    gekko_net_adapter_set(session, adapter);
    persistentRemoteEndpoint = remote;
    GekkoNetAddress remoteAddress{const_cast<char*>(persistentRemoteEndpoint.c_str()),
                                  static_cast<unsigned int>(persistentRemoteEndpoint.size())};
    // Actor handles occupy the same index as the SNES player (P1/P2).
    for (int actor = 0; actor < 2; ++actor) {
        const int handle = gekko_add_actor(session,
            actor == localPlayer ? GekkoLocalPlayer : GekkoRemotePlayer,
            actor == localPlayer ? nullptr : &remoteAddress);
        if (handle < 0) {
            stopUnsafe();
            return JNI_FALSE;
        }
        if (actor == localPlayer) localHandle = handle;
        else remoteHandle = handle;
    }
    gekko_set_local_delay(session, localHandle, static_cast<unsigned char>(delay));
    gekko_set_input_prediction_window(session, static_cast<unsigned char>(prediction));
    // Network changes and Android scheduling can delay incoming packets.
    // Do not tear down a valid multiplayer session after a 6-second hiccup.
    gekko_set_disconnect_timeout(session, 30000);
    stateCapacity = static_cast<unsigned int>(size);
    rollbackCount = 0;
    advances = 0;
    connected = false;
    aborted = false;
    return JNI_TRUE;
}

// Attach DataChannel packet callbacks to GekkoNet's official custom adapter.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_maizon_maizosnes_GekkoNative_nativeStartRtc(
    JNIEnv* env, jobject instance, jint player, jint stateSize,
    jint prediction, jint delay, jobject port) {
    if (!port || rtcPort || session) return JNI_FALSE;
    env->GetJavaVM(&rtcVm);
    rtcPort = env->NewGlobalRef(port);
    if (!rtcPort) return JNI_FALSE;
    jstring peer = env->NewStringUTF("rtc");
    jboolean ok = Java_com_maizon_maizosnes_GekkoNative_nativeStart(
        env, instance, 16384, peer, 16385, player, stateSize, prediction, delay);
    env->DeleteLocalRef(peer);
    if (!ok) {
        if (rtcPort) env->DeleteGlobalRef(rtcPort);
        rtcPort = nullptr;
    }
    return ok;
}

// Java callback interface: save():ByteArray, load(ByteArray):Boolean,
// step(p1:Int, p2:Int, isResimulation:Boolean):Boolean
extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_GekkoNative_nativeTick(
    JNIEnv* env, jobject, jint inputMask, jobject callbacks) {
    std::lock_guard<std::mutex> lock(sessionLock);
    if (!session || !callbacks || aborted) return -1;
    jclass cls = env->GetObjectClass(callbacks);
    jmethodID save = env->GetMethodID(cls, "save", "()[B");
    jmethodID load = env->GetMethodID(cls, "load", "([B)Z");
    jmethodID step = env->GetMethodID(cls, "step", "(IIZ)Z");
    if (!save || !load || !step || javaException(env)) {
        aborted = true;
        env->DeleteLocalRef(cls);
        return -2;
    }
    uint32_t local = static_cast<uint32_t>(inputMask) & 0xffffu;
    gekko_add_local_input(session, localHandle, &local);
    gekko_network_poll(session);
    int evCount = 0;
    GekkoSessionEvent** status = gekko_session_events(session, &evCount);
    for (int i = 0; i < evCount; ++i) {
        if (!status || !status[i]) continue;
        const auto& event = *status[i];
        if (event.type == GekkoPlayerConnected &&
            event.data.connected.handle == remoteHandle) connected = true;
        if (event.type == GekkoPlayerDisconnected) { aborted = true; break; }
        if (event.type == GekkoDesyncDetected) { aborted = true; break; }
    }
    if (aborted) { env->DeleteLocalRef(cls); return -3; }
    int count = 0;
    GekkoGameEvent** updates = gekko_update_session(session, &count);
    int advancedThisTick = 0;
    for (int i = 0; i < count; ++i) {
        if (!updates || !updates[i]) continue;
        GekkoGameEvent* ev = updates[i];
        switch (ev->type) {
        case GekkoSaveEvent: {
            jbyteArray bytes = static_cast<jbyteArray>(env->CallObjectMethod(callbacks, save));
            if (javaException(env) || !bytes) { aborted = true; break; }
            const jsize len = env->GetArrayLength(bytes);
            if (len <= 0 || static_cast<unsigned int>(len) > stateCapacity) {
                aborted = true;
                env->DeleteLocalRef(bytes);
                break;
            }
            env->GetByteArrayRegion(bytes, 0, len,
                reinterpret_cast<jbyte*>(ev->data.save.state));
            if (javaException(env)) aborted = true;
            else {
                *ev->data.save.state_len = static_cast<unsigned int>(len);
                *ev->data.save.checksum =
                    fnv1a(ev->data.save.state, static_cast<unsigned int>(len));
            }
            env->DeleteLocalRef(bytes);
            break;
        }
        case GekkoLoadEvent: {
            const auto len = ev->data.load.state_len;
            if (!len || len > stateCapacity) { aborted = true; break; }
            jbyteArray bytes = env->NewByteArray(static_cast<jsize>(len));
            if (!bytes) { aborted = true; break; }
            env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(len),
                reinterpret_cast<jbyte*>(ev->data.load.state));
            if (javaException(env) ||
                !env->CallBooleanMethod(callbacks, load, bytes) ||
                javaException(env)) aborted = true;
            env->DeleteLocalRef(bytes);
            ++rollbackCount;
            break;
        }
        case GekkoAdvanceEvent: {
            if (ev->data.adv.input_len < 2 * sizeof(uint32_t)) {
                aborted = true;
                break;
            }
            uint32_t p1 = 0, p2 = 0;
            std::memcpy(&p1, ev->data.adv.inputs, sizeof(p1));
            std::memcpy(&p2, ev->data.adv.inputs + sizeof(p1), sizeof(p2));
            if (!env->CallBooleanMethod(callbacks, step,
                 static_cast<jint>(p1), static_cast<jint>(p2),
                 ev->data.adv.rolling_back ? JNI_TRUE : JNI_FALSE) ||
                javaException(env)) aborted = true;
            ++advancedThisTick;
            ++advances;
            break;
        }
        default: break;
        }
        if (aborted) break;
    }
    env->DeleteLocalRef(cls);
    return aborted ? -4 : advancedThisTick;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_maizon_maizosnes_GekkoNative_nativeStats(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(sessionLock);
    jint values[6]{};
    if (session && remoteHandle >= 0) {
        GekkoNetworkStats stats{};
        gekko_network_stats(session, remoteHandle, &stats);
        values[0] = static_cast<jint>(stats.last_ping);
        values[1] = static_cast<jint>(stats.jitter * 10.0f);
        values[2] = static_cast<jint>(std::min<int64_t>(rollbackCount, INT32_MAX));
        values[3] = static_cast<jint>(std::min<int64_t>(advances, INT32_MAX));
        values[4] = connected ? 1 : 0;
        values[5] = aborted ? 1 : 0;
    }
    jintArray array = env->NewIntArray(6);
    if (array) env->SetIntArrayRegion(array, 0, 6, values);
    return array;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_maizon_maizosnes_GekkoNative_nativeSetLocalDelay(
    JNIEnv*, jobject, jint frames) {
    std::lock_guard<std::mutex> lock(sessionLock);
    if (!session || localHandle < 0 || frames < 0 || frames > 8)
        return JNI_FALSE;
    gekko_set_local_delay(session, localHandle, static_cast<unsigned char>(frames));
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_maizon_maizosnes_GekkoNative_nativeStop(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(sessionLock);
    stopUnsafe();
}
