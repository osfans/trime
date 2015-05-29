LOCAL_PATH := $(ROOT_PATH)/snappy

include $(CLEAR_VARS)
LOCAL_MODULE := snappy #1.1.2
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := snappy.cc snappy-sinksource.cc

include $(BUILD_STATIC_LIBRARY)
