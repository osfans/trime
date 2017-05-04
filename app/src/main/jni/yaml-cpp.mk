LOCAL_PATH := $(ROOT_PATH)/yaml-cpp

include $(CLEAR_VARS)
LOCAL_MODULE := yaml-cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_SRC_FILES := \
  $(subst $(LOCAL_PATH)/,,$(wildcard $(LOCAL_PATH)/src/*.cpp))

#LOCAL_LDLIBS := -latomic
LOCAL_STATIC_LIBRARIES := boost
include $(BUILD_STATIC_LIBRARY)

ifneq ($(YAML_CPP_TOOLS),)
include $(CLEAR_VARS)
LOCAL_MODULE    := yaml_parse
LOCAL_SRC_FILES := util/parse.cpp
LOCAL_STATIC_LIBRARIES := yaml-cpp
LOCAL_LDLIBS := -latomic
include $(BUILD_EXECUTABLE)
endif
