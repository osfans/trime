#ifndef RIME_JNI_H_
#define RIME_JNI_H_

#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <string>

#define TAG "Rime-JNI"
#ifdef ANDROID
#include <android/log.h>
#define ALOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##__VA_ARGS__)
#define ALOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO, TAG, fmt, ##__VA_ARGS__)
#else
#define ALOGE printf
#define ALOGI printf
#endif

#define BUFSIZE 256

jstring newJstring(JNIEnv* env, const char* pat);

#endif  // RIME_JNI_H_
