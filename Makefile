mainDir=app/src/main
resDir=$(mainDir)/res
jniDir=$(mainDir)/jni

.PHONY: all clean build debug spotless release install opencc-data translate ndk android

all: release

clean:
	./gradlew clean

build:
	./gradlew build

TRANSLATE=$(resDir)/values-zh-rCN/strings.xml
release: opencc-data spotless
	./gradlew assembleRelease

spotless:
	./gradlew spotlessCheck

debug:
	./gradlew assembleDebug

install: release
	./gradlew installRelease

$(TRANSLATE): $(resDir)/values-zh-rTW/strings.xml
	@echo "translate traditional to simple Chinese: $@"
	@mkdir -p $(resDir)/values-zh-rCN
	@opencc -c tw2sp -i $< -o $@

translate: $(TRANSLATE)

opencc-data: srcDir = $(jniDir)/OpenCC/data
opencc-data: targetDir = $(mainDir)/assets/rime/opencc
opencc-data:
	@echo "copy opencc data"
	@rm -rf $(targetDir)
	@mkdir -p $(targetDir)
	@cp $(srcDir)/dictionary/* $(targetDir)/
	@cp $(srcDir)/config/* $(targetDir)/
	@rm $(targetDir)/TWPhrases*.txt
	@python $(srcDir)/scripts/merge.py $(srcDir)/dictionary/TWPhrases*.txt $(targetDir)/TWPhrases.txt
	@python $(srcDir)/scripts/reverse.py $(targetDir)/TWPhrases.txt $(targetDir)/TWPhrasesRev.txt
	@python $(srcDir)/scripts/reverse.py $(srcDir)/dictionary/TWVariants.txt $(targetDir)/TWVariantsRev.txt
	@python $(srcDir)/scripts/reverse.py $(srcDir)/dictionary/HKVariants.txt $(targetDir)/HKVariantsRev.txt

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
