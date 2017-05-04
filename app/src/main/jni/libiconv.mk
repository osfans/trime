LOCAL_PATH := $(ROOT_PATH)/libiconv

include $(CLEAR_VARS)

LOCAL_MODULE := iconv

LOCAL_CFLAGS := \
  -Wno-multichar \
  -DLIBDIR="\"c\"" \
  -DBUILDING_LIBICONV \
  -DIN_LIBRARY

LOCAL_SRC_FILES := \
  libcharset/lib/localcharset.c \
  lib/iconv.c \
  lib/relocatable.c

LOCAL_C_INCLUDES += \
  $(LOCAL_PATH)/include \
  $(LOCAL_PATH)/lib \
  $(LOCAL_PATH)/libcharset/include \

LOCAL_EXPORT_C_INCLUDES       := $(LOCAL_PATH)/include

#include $(BUILD_SHARED_LIBRARY)
include $(BUILD_STATIC_LIBRARY)
