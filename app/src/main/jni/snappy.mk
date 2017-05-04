LOCAL_PATH := $(ROOT_PATH)/snappy

include $(CLEAR_VARS)
LOCAL_MODULE := snappy
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := snappy.cc snappy-sinksource.cc
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)

include $(BUILD_STATIC_LIBRARY)
