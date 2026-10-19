#include <jni.h>
#include <gekkonet.h>
// Minimal linkage smoke before integrating deterministic frame events.
extern "C" JNIEXPORT jint JNICALL
Java_com_maizon_maizosnes_GekkoNative_nativeApiVersion(JNIEnv*, jobject) {
    GekkoSession* session = nullptr;
    if (!gekko_create(&session, GekkoGameSession)) return -1;
    return gekko_destroy(&session) ? 1 : -2;
}
