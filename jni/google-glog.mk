LOCAL_PATH := $(ROOT_PATH)/google-glog/src

include $(CLEAR_VARS)
LOCAL_MODULE := glog #1.1.2
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := \
  demangle.cc \
  logging.cc \
  raw_logging.cc \
  signalhandler.cc \
  symbolize.cc \
  utilities.cc \
  vlog_is_on.cc \

include $(BUILD_STATIC_LIBRARY)
