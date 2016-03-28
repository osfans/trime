LOCAL_PATH := $(ROOT_PATH)/boost

include $(CLEAR_VARS)
LOCAL_MODULE := boost

LOCAL_SRC_FILES += \
  libs/filesystem/src/operations.cpp \
  libs/filesystem/src/utf8_codecvt_facet.cpp \
  libs/filesystem/src/codecvt_error_category.cpp \
  libs/filesystem/src/portability.cpp \
  libs/filesystem/src/unique_path.cpp \
  libs/filesystem/src/path_traits.cpp \
  libs/filesystem/src/windows_file_codecvt.cpp \
  libs/filesystem/src/path.cpp \
  \
  libs/regex/src/c_regex_traits.cpp \
  libs/regex/src/winstances.cpp \
  libs/regex/src/icu.cpp \
  libs/regex/src/cregex.cpp \
  libs/regex/src/regex_raw_buffer.cpp \
  libs/regex/src/fileiter.cpp \
  libs/regex/src/wide_posix_api.cpp \
  libs/regex/src/usinstances.cpp \
  libs/regex/src/static_mutex.cpp \
  libs/regex/src/cpp_regex_traits.cpp \
  libs/regex/src/w32_regex_traits.cpp \
  libs/regex/src/wc_regex_traits.cpp \
  libs/regex/src/regex_debug.cpp \
  libs/regex/src/posix_api.cpp \
  libs/regex/src/instances.cpp \
  libs/regex/src/regex_traits_defaults.cpp \
  libs/regex/src/regex.cpp \
  \
  libs/system/src/error_code.cpp \
  \
  libs/locale/src/encoding/codepage.cpp \

ifeq ($(BOOST_USE_SIGNALS2),)
LOCAL_SRC_FILES += \
  libs/signals/src/connection.cpp \
  libs/signals/src/signal_base.cpp \
  libs/signals/src/trackable.cpp \
  libs/signals/src/named_slot_map.cpp \
  libs/signals/src/slot.cpp
endif

LOCAL_CFLAGS += -DBOOST_NO_CXX11_SCOPED_ENUMS -DBOOST_LOCALE_WITH_ICONV
LOCAL_STATIC_LIBRARIES += iconv
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)

#LOCAL_LDLIBS := -latomic
# 如果要把boost集成到动态库里，-fPIC是必须的，不然会有链接错误。原因请自行Google
#LOCAL_CFLAGS += -fPIC -frtti -fexceptions
include $(BUILD_STATIC_LIBRARY)
