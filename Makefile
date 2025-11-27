# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

mainDir=app/src/main
resDir=$(mainDir)/res
jniDir=$(mainDir)/jni

ifdef ComSpec
    # Windows
    GRADLEW := gradlew.bat
else
    # Unix-like system
    GRADLEW := ./gradlew
endif


.PHONY: all clean build debug spotlessCheck spotlessApply clang-format-lint clang-format style-lint \
style-apply patch-apply release install translate ndk android

all: release

clean:
	rm -rf build app/build app/.cxx/
	$(GRADLEW) clean

build: style-lint
	$(GRADLEW) build

spotlessCheck:
	$(GRADLEW) spotlessCheck

spotlessApply:
	$(GRADLEW) spotlessApply

cmake-format:
	cmake-format -i app/src/main/jni/cmake/*.cmake app/src/main/jni/CMakeLists.txt

clang-format-lint:
	./script/clang-format.sh -n

clang-format:
	./script/clang-format.sh -i

style-lint: spotlessCheck clang-format-lint

style-apply: spotlessApply clang-format

patch-apply:
	-git apply --directory=$(jniDir)/librime-lua-deps patches/lua.patch

debug: patch-apply
	$(GRADLEW) :app:assembleDebug

# add SPDX license header
reuse:
	pipx run reuse annotate \
	 --recursive --skip-unrecognised \
	 --merge-copyrights \
	 --copyright="Rime community" \
	 --license="GPL-3.0-or-later" .
	# remove binary file
	find . -type f -name "*.license" -delete
	# checkout ignore file
	git checkout gradlew gradlew.bat gradle/* CHANGELOG.md

# generate changlog
cliff:
	git-cliff -o CHANGELOG.md

TRANSLATE=$(resDir)/values-zh-rCN/strings.xml
release: patch-apply
	$(GRADLEW) :app:assembleRelease

install: release
	$(GRADLEW) installRelease

$(TRANSLATE): $(resDir)/values-zh-rTW/strings.xml
	@echo "translate traditional to simple Chinese: $@"
	@mkdir -p $(resDir)/values-zh-rCN
	@opencc -c tw2sp -i $< -o $@

translate: $(TRANSLATE)

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
