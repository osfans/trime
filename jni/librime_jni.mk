LOCAL_PATH := $(ROOT_PATH)/librime_jni

include $(CLEAR_VARS)
LOCAL_MODULE := rime_jni
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := rime_jni.cc rime.cc levers.cc opencc.cc
LOCAL_SHARED_LIBRARIES := rime
LOCAL_STATIC_LIBRARIES := opencc
LOCAL_CFLAGS := -DCLASSNAME=\"com/osfans/trime/Rime\" -DOPENCC_VERSION="\"$(shell git --git-dir $(ROOT_PATH)/OpenCC/.git describe --tags)\"" -DLIBRIME_VERSION="\"$(shell git --git-dir $(ROOT_PATH)/librime/.git describe --tags)\""
LOCAL_LDLIBS := -llog -latomic
include $(BUILD_SHARED_LIBRARY)
