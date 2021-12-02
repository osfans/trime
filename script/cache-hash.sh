#!/usr/bin/env bash

JNI_FILES="magic.txt app/build.gradle app/src/main/jni/cmake/* app/src/main/jni/librime_jni/* app/src/main/jni/CMakeLists.txt"

hash=$(git submodule status)
hash=$hash$(sha256sum $JNI_FILES)
hash=$(echo $hash | sha256sum | cut -c-64)

echo "::set-output name=hash::$hash"

