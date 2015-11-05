LOCAL_PATH := $(ROOT_PATH)/libiconv

include $(CLEAR_VARS)

LOCAL_MODULE := iconv

LOCAL_CFLAGS := \
  -Wno-multichar \
  -DANDROID \
  -DLIBDIR="c" \
  -DBUILDING_LIBICONV \
  -DIN_LIBRARY

LOCAL_SRC_FILES := \
  libiconv-1.14/libcharset/lib/localcharset.c \
  libiconv-1.14/lib/iconv.c \
  libiconv-1.14/lib/relocatable.c

LOCAL_C_INCLUDES += \
  $(LOCAL_PATH)/libiconv-1.14/include \
  $(LOCAL_PATH)/libiconv-1.14/libcharset \
  $(LOCAL_PATH)/libiconv-1.14/lib \
  $(LOCAL_PATH)/libiconv-1.14/libcharset/include \
  $(LOCAL_PATH)/libiconv-1.14/srclib

LOCAL_EXPORT_C_INCLUDES       := $(LOCAL_PATH)/libiconv-1.14/include

#include $(BUILD_SHARED_LIBRARY)
include $(BUILD_STATIC_LIBRARY)
