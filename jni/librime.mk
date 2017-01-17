LOCAL_PATH := $(ROOT_PATH)/librime
CXX_DEFINES = -DBOOST_NO_CXX11_SCOPED_ENUMS -DRIME_BUILD_SHARED_LIBS -DRIME_EXPORTS -DRIME_VERSION=\"1.2.9\" -DRIME_ENABLE_LOGGING -O3 -fPIC -DNDEBUG

include $(CLEAR_VARS)
LOCAL_MODULE := rime
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES += $(LOCAL_PATH)/src $(LOCAL_PATH)/thirdparty/include
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/src $(LOCAL_PATH)/thirdparty/include

LOCAL_SRC_FILES += \
  $(subst $(LOCAL_PATH)/,,$(wildcard $(LOCAL_PATH)/src/*.cc)) \
  $(subst $(LOCAL_PATH)/,,$(wildcard $(LOCAL_PATH)/src/**/*.cc)) \
  $(subst $(LOCAL_PATH)/,,$(wildcard $(LOCAL_PATH)/src/**/**/*.cc))

LOCAL_CFLAGS := $(CXX_DEFINES)
ifeq ($(BOOST_USE_SIGNALS2),n)
LOCAL_CFLAGS +=-DBOOST_SIGNALS_NO_DEPRECATION_WARNING
else
LOCAL_CFLAGS += -DBOOST_SIGNALS2
endif
LOCAL_STATIC_LIBRARIES := boost leveldb marisa yaml-cpp miniglog opencc
LOCAL_LDLIBS := -latomic
include $(BUILD_SHARED_LIBRARY)

ifneq ($(RIME_TOOLS),)
include $(CLEAR_VARS)
LOCAL_MODULE    := rime_api_console
LOCAL_SRC_FILES := tools/rime_api_console.cc
LOCAL_SHARED_LIBRARIES := rime
LOCAL_CFLAGS := $(CXX_DEFINES)
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := rime_console
LOCAL_SRC_FILES := tools/rime_console.cc
LOCAL_SHARED_LIBRARIES := rime
LOCAL_C_INCLUDES += $(LOCAL_PATH)/thirdparty/include
LOCAL_CFLAGS := $(CXX_DEFINES)
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := rime_deployer
LOCAL_SRC_FILES := tools/rime_deployer.cc
LOCAL_SHARED_LIBRARIES := rime
LOCAL_CFLAGS := $(CXX_DEFINES)
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := rime_dict_manager
LOCAL_SRC_FILES := tools/rime_dict_manager.cc
LOCAL_SHARED_LIBRARIES := rime
LOCAL_CFLAGS := $(CXX_DEFINES)
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := rime_patch
LOCAL_SRC_FILES := tools/rime_patch.cc
LOCAL_SHARED_LIBRARIES := rime
LOCAL_CFLAGS := $(CXX_DEFINES)
include $(BUILD_EXECUTABLE)
endif
