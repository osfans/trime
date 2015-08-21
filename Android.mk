LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

#LOCAL_AAPT_FLAGS := -0 .db

LOCAL_PACKAGE_NAME := trime

include $(BUILD_PACKAGE)
