mainDir=app/src/main
resDir=$(mainDir)/res
jniDir=$(mainDir)/jni

.PHONY: all clean build debug release install icon opencc-data translate ndk android

all: release

clean:
	gradle clean

debug:
	gradle assembleDebug

build:
	gradle build

TRANSLATE=$(resDir)/values-zh-rCN/strings.xml
release: opencc-data $(TRANSLATE)
release:
	gradle assembleRelease

install: release
	gradle installRelease

icon: icon.svg
	@echo "updating the icons..."
	inkscape -z -e $(resDir)/drawable-xxxhdpi/icon.png -w 192 -h 192 $<
	inkscape -z -e $(resDir)/drawable-xxhdpi/icon.png -w 144 -h 144 $<
	inkscape -z -e $(resDir)/drawable-xhdpi/icon.png -w 96 -h 96 $<
	inkscape -z -e $(resDir)/drawable-hdpi/icon.png -w 72 -h 72 $<
	inkscape -z -e $(resDir)/drawable-mdpi/icon.png -w 48 -h 48 $<
	# just copy the already converted icon to status
	yes | cp $(resDir)/drawable-xhdpi/icon.png $(resDir)/drawable-xxxhdpi/status.png
	yes | cp $(resDir)/drawable-hdpi/icon.png $(resDir)/drawable-xxhdpi/status.png
	yes | cp $(resDir)/drawable-mdpi/icon.png $(resDir)/drawable-xhdpi/status.png
	inkscape -z -e $(resDir)/drawable-hdpi/status.png -w 36 -h 36 $<
	inkscape -z -e $(resDir)/drawable-mdpi/status.png -w 24 -h 24 $<

$(TRANSLATE): $(resDir)/values-zh-rTW/strings.xml
	@echo "translate traditional to simple Chinese: $@"
	@mkdir -p $(resDir)/values-zh-rCN
	@opencc -c tw2sp -i $< -o $@

translate: $(TRANSLATE)

opencc-data: srcDir = $(jniDir)/OpenCC/data
opencc-data: targetDir = $(mainDir)/assets/rime/opencc
opencc-data:
	@echo "copy opencc data:"
	@rm -rf $(targetDir)
	@mkdir -p $(targetDir)
	@cp $(srcDir)/dictionary/* $(targetDir)/
	@cp $(srcDir)/config/* $(targetDir)/
	@rm $(targetDir)/TWPhrases*.txt
	python $(srcDir)/scripts/merge.py $(srcDir)/dictionary/TWPhrases*.txt $(targetDir)/TWPhrases.txt
	python $(srcDir)/scripts/reverse.py $(targetDir)/TWPhrases.txt $(targetDir)/TWPhrasesRev.txt
	python $(srcDir)/scripts/reverse.py $(srcDir)/dictionary/TWVariants.txt $(targetDir)/TWVariantsRev.txt
	python $(srcDir)/scripts/reverse.py $(srcDir)/dictionary/HKVariants.txt $(targetDir)/HKVariantsRev.txt

ndk:
	(cd $(mainDir); ndk-build)

android:
	cmake -Bbuild-$@ -H$(jniDir)\
		-DCMAKE_SYSTEM_NAME=Android \
		-DCMAKE_ANDROID_STL_TYPE=c++_static \
		-DCMAKE_SYSTEM_VERSION=14 \
		-DCMAKE_ANDROID_NDK_TOOLCHAIN_VERSION=clang \
		-DCMAKE_ANDROID_ARCH_ABI=armeabi
	${MAKE} -C build-$@ rime_jni
