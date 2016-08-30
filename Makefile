.PHONY: all release install clean apk android linux win32

all: apk javadoc linux win32

release: apk win32

install: apk
	ant install

icon: icon.svg
	inkscape -z -e res/drawable-xxxhdpi/icon.png -w 192 -h 192 icon.svg
	inkscape -z -e res/drawable-xxhdpi/icon.png -w 144 -h 144 icon.svg
	inkscape -z -e res/drawable-xhdpi/icon.png -w 96 -h 96 icon.svg
	inkscape -z -e res/drawable-hdpi/icon.png -w 72 -h 72 icon.svg
	inkscape -z -e res/drawable-mdpi/icon.png -w 48 -h 48 icon.svg
	inkscape -z -e res/drawable-xxxhdpi/status.png -w 96 -h 96 icon.svg
	inkscape -z -e res/drawable-xxhdpi/status.png -w 72 -h 72 icon.svg
	inkscape -z -e res/drawable-xhdpi/status.png -w 48 -h 48 icon.svg
	inkscape -z -e res/drawable-hdpi/status.png -w 36 -h 38 icon.svg
	inkscape -z -e res/drawable-mdpi/status.png -w 24 -h 24 icon.svg

apk: opencc-data android
	@mkdir -p res/values-zh-rCN/
	@opencc -c tw2sp -i res/values/strings.xml -o res/values-zh-rCN/strings.xml
	@sed -i '/translatable="false"/d' res/values-zh-rCN/strings.xml
	ant release

opencc-data:
	@echo "copy opencc data:"
	@rm -rf assets/rime/opencc
	@mkdir -p assets/rime/opencc
	@cp jni/OpenCC/data/dictionary/* assets/rime/opencc/
	@cp jni/OpenCC/data/config/* assets/rime/opencc/
	@rm assets/rime/opencc/TWPhrases*.txt
	python jni/OpenCC/data/scripts/merge.py jni/OpenCC/data/dictionary/TWPhrases*.txt assets/rime/opencc/TWPhrases.txt
	python jni/OpenCC/data/scripts/reverse.py assets/rime/opencc/TWPhrases.txt assets/rime/opencc/TWPhrasesRev.txt
	python jni/OpenCC/data/scripts/reverse.py jni/OpenCC/data/dictionary/TWVariants.txt assets/rime/opencc/TWVariantsRev.txt
	python jni/OpenCC/data/scripts/reverse.py jni/OpenCC/data/dictionary/HKVariants.txt assets/rime/opencc/HKVariantsRev.txt

javadoc:
	ant javadoc
	@echo "convert javadoc by opencc:"
	@find docs -type f -name *.html| xargs -i opencc -i {} -o {}

ndk:
	ndk-build

android:
	mkdir -p build-android
	(cd build-android; cmake \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_TOOLCHAIN_FILE=../android-cmake/android.toolchain.cmake \
		-DLIBRARY_OUTPUT_PATH_ROOT=.. \
		-DANDROID_TOOLCHAIN_NAME=arm-linux-androideabi-clang \
		-DANDROID_ABI=armeabi \
		-DANDROID_STL=c++_static \
		-DANDROID_NATIVE_API_LEVEL=14 ../jni)
	${MAKE} -C build-android rime_jni

linux:
	mkdir -p build-linux
	(cd build-linux; cmake -DCMAKE_BUILD_TYPE=Release ../jni)
	${MAKE} -C build-linux

win32:
	mkdir -p build-win32
	(cd build-win32; i686-w64-mingw32-cmake -DCMAKE_BUILD_TYPE=Release ../jni)
	${MAKE} -C build-win32 rime
	mkdir -p bin
	(cd build-win32; 7z a ../bin/rime-win32-`date +%Y%m%d`.dll.7z rime.dll)

clean:
	git clean -fd
