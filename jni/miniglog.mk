LOCAL_PATH := $(ROOT_PATH)/miniglog

include $(CLEAR_VARS)
LOCAL_MODULE := miniglog
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := miniglog/logging.cc
LOCAL_EXPORT_LDLIBS := -llog
include $(BUILD_STATIC_LIBRARY)
