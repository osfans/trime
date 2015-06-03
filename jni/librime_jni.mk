LOCAL_PATH := $(ROOT_PATH)/librime_jni

include $(CLEAR_VARS)
LOCAL_MODULE := rime_jni
LOCAL_SRC_FILES := rime_jni.cpp
LOCAL_SHARED_LIBRARIES := rime
LOCAL_CFLAGS := -DCLASSNAME=\"com/osfans/trime/Rime\"
LOCAL_LDLIBS := -llog -latomic
include $(BUILD_SHARED_LIBRARY)
