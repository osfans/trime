.PHONY: all install clean apk android linux win32 win64

all: android linux win32

install: android
	ant release install

apk: android
	ant release

android:
	mkdir -p build-android
	(cd build-android; cmake \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_TOOLCHAIN_FILE=../android-cmake/android.toolchain.cmake \
		-DLIBRARY_OUTPUT_PATH_ROOT=.. \
		-DANDROID_TOOLCHAIN_NAME=arm-linux-androideabi-4.9 \
		-DANDROID_ABI=armeabi \
		-DANDROID_STL=c++_static \
		-DANDROID_NATIVE_API_LEVEL=4 ../jni)
	${MAKE} -C build-android rime_jni

linux:
	mkdir -p build-linux
	(cd build-linux; cmake -DCMAKE_BUILD_TYPE=Release ../jni)
	${MAKE} -C build-linux

win32:
	mkdir -p build-win32
	(cd build-win32; i686-w64-mingw32-cmake -DCMAKE_BUILD_TYPE=Release ../jni)
	${MAKE} -C build-win32 rime

win64:
	mkdir -p build-win64
	(cd build-win64; x86_64-w64-mingw32-cmake -DCMAKE_BUILD_TYPE=Release ../jni)
	${MAKE} -C build-win64 rime

clean:
	git clean -fd
