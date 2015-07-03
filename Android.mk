LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
         $(call all-subdir-java-files)

LOCAL_STATIC_JAVA_LIBRARIES := libsnakeyaml

#LOCAL_AAPT_FLAGS := -0 .db

LOCAL_PACKAGE_NAME := trime

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := libsnakeyaml:libs/snakeyaml-1.15-local-android.jar

include $(BUILD_MULTI_PREBUILT)

