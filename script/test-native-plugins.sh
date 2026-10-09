#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Rime community
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
: "${ANDROID_SERIAL:?Select a dedicated emulator with ANDROID_SERIAL}"
adb="$ANDROID_HOME/platform-tools/adb"
case "$ANDROID_SERIAL" in
emulator-*) ;;
*) echo "Use a dedicated emulator" >&2; exit 1 ;;
esac
[[ "$("$adb" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ ! -d app/prebuilt ]] || { echo "This test requires a source build without app/prebuilt" >&2; exit 1; }
abi=$("$adb" shell getprop ro.product.cpu.abi | tr -d '\r')

make patch-apply
BUILD_ABI="$abi" BUILD_VERSION_NAME=native-plugin-test ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

shopt -s nullglob
caches=(app/.cxx/Debug/*/"$abi"/CMakeCache.txt)
[[ ${#caches[@]} == 1 ]] || { echo "Expected one native build; clean app/.cxx and retry" >&2; exit 1; }
cache="${caches[0]}"
cache_value() {
	sed -n "s/^$1:[^=]*=//p" "$cache"
}
cmake=$(cache_value CMAKE_COMMAND)
test_build="$PWD/app/build/native-plugin-test/$abi"
"$cmake" -S app/src/androidTest/jni -B "$test_build" -G Ninja \
	"-DCMAKE_MAKE_PROGRAM=$(cache_value CMAKE_MAKE_PROGRAM)" \
	"-DCMAKE_TOOLCHAIN_FILE=$(cache_value CMAKE_TOOLCHAIN_FILE)" \
	"-DANDROID_ABI=$abi" \
	"-DANDROID_PLATFORM=$(cache_value ANDROID_PLATFORM)" \
	-DANDROID_STL=c++_shared \
	"-DTRIME_BUILD_DIR=$PWD/$(dirname "$cache")" \
	"-DTRIME_LIBRARY=$(cache_value CMAKE_LIBRARY_OUTPUT_DIRECTORY)/librime_jni.so"
"$cmake" --build "$test_build"

package=com.osfans.trime.debug
"$adb" install -r "app/build/outputs/apk/debug/com.osfans.trime-native-plugin-test-$abi-debug.apk"
"$adb" install -r app/build/outputs/apk/androidTest/debug/com.osfans.trime-native-plugin-test-debug-androidTest.apk
"$adb" shell pm clear "$package"
"$adb" shell run-as "$package" mkdir -p files/plugin-test
"$adb" shell "run-as $package sh -c 'cat > files/plugin-test/librime-sample.so'" < "$test_build/librime-sample.so"

reports=app/build/reports/native-plugins
mkdir -p "$reports"
trap '"$adb" shell am force-stop "$package"; "$adb" logcat -d > "$reports/logcat.txt"' EXIT
for filename in sample.so rime-sample.so librime-sample.so; do
	for action in none install replace remove; do
		"$adb" shell am force-stop "$package"
		"$adb" shell am instrument -w -r \
			-e class com.osfans.trime.core.NativePluginIntegrationTest \
			-e filename "$filename" -e action "$action" \
			"$package.test/androidx.test.runner.AndroidJUnitRunner" | tee "$reports/$filename-$action.txt"
		grep -qx 'OK (1 test)' "$reports/$filename-$action.txt"
	done
done
