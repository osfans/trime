#APP_ABI := armeabi armeabi-v7a arm64-v8a x86 mips x86_64 mips64
APP_PLATFORM := android-9
APP_STL := c++_static

APP_THIN_ARCHIVE := true
APP_CFLAGS := -Ijni/include -w
APP_CPPFLAGS := -fpic -fexceptions -frtti
APP_GNUSTL_FORCE_CPP_FEATURES := pic exceptions rtti

NDK_TOOLCHAIN_VERSION := clang
