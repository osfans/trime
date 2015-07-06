#APP_ABI := armeabi armeabi-v7a x86 mips arm64-v8a x86_64 mips64
APP_PLATFORM := android-4
#APP_PIE := true
#NDK_TOOLCHAIN_VERSION := 4.9
NDK_TOOLCHAIN_VERSION := clang

APP_STL := c++_static #c++_static system stlport_static stlport_shared gnustl_static gnustl_shared gabi++_static gabi++_shared c++_static c++_shared none
APP_CPPFLAGS := -fPIC -fexceptions -frtti -Wno-format-security -Wno-extern-c-compat -Wno-constant-conversion -Wno-deprecated-register -Wno-format
#APP_GNUSTL_FORCE_CPP_FEATURES := exceptions rtti
