LOCAL_PATH := $(ROOT_PATH)/boost

include $(CLEAR_VARS)
LOCAL_MODULE := boost

LOCAL_SRC_FILES += \
  $(subst $(LOCAL_PATH)/,,$(wildcard $(LOCAL_PATH)/libs/filesystem/src/*.cpp)) \
  $(subst $(LOCAL_PATH)/,,$(wildcard $(LOCAL_PATH)/libs/regex/src/*.cpp)) \
  libs/system/src/error_code.cpp \
  \
  libs/locale/src/encoding/codepage.cpp \

ifeq ($(BOOST_USE_SIGNALS2),n)
LOCAL_SRC_FILES += \
  $(subst $(LOCAL_PATH)/,,$(wildcard $(LOCAL_PATH)/libs/signals/src/*.cpp))
endif

LOCAL_CFLAGS += -DBOOST_NO_CXX11_SCOPED_ENUMS -DBOOST_LOCALE_WITH_ICONV
LOCAL_STATIC_LIBRARIES += iconv
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)

#LOCAL_LDLIBS := -latomic
# 如果要把boost集成到动态库里，-fPIC是必须的，不然会有链接错误。原因请自行Google
#LOCAL_CFLAGS += -fPIC -frtti -fexceptions
include $(BUILD_STATIC_LIBRARY)
