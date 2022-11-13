#ifndef TRIME_JNI_COMMON_H
#define TRIME_JNI_COMMON_H

#include "jni-utils.h"

static GlobalRefSingleton *GlobalRef;

#define TAG "rime.jni"
#ifdef ANDROID
#include <android/log.h>
#define ALOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##__VA_ARGS__)
#define ALOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO, TAG, fmt, ##__VA_ARGS__)
#endif

#define BUFSIZE 256

#endif //TRIME_JNI_COMMON_H
