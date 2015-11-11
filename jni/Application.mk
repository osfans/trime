#APP_ABI := armeabi armeabi-v7a arm64-v8a x86 mips x86_64 mips64
APP_PLATFORM := android-4
#APP_PIE := true
#APP_STL := c++_static #system stlport_static stlport_shared gnustl_static gnustl_shared gabi++_static gabi++_shared c++_static c++_shared none

APP_THIN_ARCHIVE := true
APP_CFLAGS := -Ijni/include -w
APP_CPPFLAGS := -fpic -fexceptions -frtti
APP_GNUSTL_FORCE_CPP_FEATURES := pic exceptions rtti

ifneq ($(NDK_GCC),)
APP_STL := c++_shared
NDK_TOOLCHAIN_VERSION := 4.9
else
NDK_TOOLCHAIN_VERSION := clang3.6
APP_STL := c++_static
endif
