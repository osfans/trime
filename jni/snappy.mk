include $(CLEAR_VARS)
LOCAL_MODULE := snappy #1.1.2
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := snappy/snappy.cc snappy/snappy-sinksource.cc

include $(BUILD_STATIC_LIBRARY)
