#include <jni.h>
#include <stdint.h>
#include "echo_processor.h"

JNIEXPORT jlong JNICALL Java_com_pitchpop_SoftwareEchoCanceller_nativeCreate(JNIEnv *env, jobject self) {
    (void)env; (void)self;
    return (jlong)(intptr_t)pp_echo_create();
}
JNIEXPORT void JNICALL Java_com_pitchpop_SoftwareEchoCanceller_nativeDestroy(JNIEnv *env, jobject self, jlong handle) {
    (void)env; (void)self;
    pp_echo_destroy((void *)(intptr_t)handle);
}
JNIEXPORT void JNICALL Java_com_pitchpop_SoftwareEchoCanceller_nativeProcess(
        JNIEnv *env, jobject self, jlong handle, jshortArray mic, jshortArray reference, jshortArray out) {
    (void)self;
    if (!handle || (*env)->GetArrayLength(env, mic) != PP_ECHO_FRAME ||
            (*env)->GetArrayLength(env, reference) != PP_ECHO_FRAME || (*env)->GetArrayLength(env, out) != PP_ECHO_FRAME) {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        (*env)->ThrowNew(env, error, "Echo processing requires a live handle and 320-sample frames");
        return;
    }
    int16_t mic_frame[PP_ECHO_FRAME], reference_frame[PP_ECHO_FRAME], cleaned[PP_ECHO_FRAME];
    (*env)->GetShortArrayRegion(env, mic, 0, PP_ECHO_FRAME, mic_frame);
    (*env)->GetShortArrayRegion(env, reference, 0, PP_ECHO_FRAME, reference_frame);
    if ((*env)->ExceptionCheck(env)) return;
    pp_echo_process((void *)(intptr_t)handle, mic_frame, reference_frame, cleaned);
    (*env)->SetShortArrayRegion(env, out, 0, PP_ECHO_FRAME, cleaned);
}
