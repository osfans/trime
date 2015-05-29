#APP_ABI := armeabi #armeabi-v7a x86
#APP_STL := stlport_shared #stlport_static system
#APP_PLATFORM    := android-3
#APP_PIE := true
#NDK_TOOLCHAIN_VERSION := 4.9
NDK_TOOLCHAIN_VERSION := clang

APP_STL         := c++_static
APP_CPPFLAGS    := -fexceptions -frtti -Wno-format-security -Wno-extern-c-compat -Wno-constant-conversion -Wno-deprecated-register -Wno-format
#APP_GNUSTL_FORCE_CPP_FEATURES := exceptions rtti
