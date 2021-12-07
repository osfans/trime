#!/usr/bin/env bash

DIGEST_ALGORITHM=sha256sum
MAGIC=magic.txt
echo auto generated contents >> $MAGIC

# fetch jni relative elements
grep ndkVersion app/build.gradle >> $MAGIC

elments=("buildTypes" "externalNativeBuild" "splits")
for element in ${elments[@]}; do
  awk "/$element/,/\}/" app/build.gradle >> $MAGIC
done

JNI_FILES="$MAGIC app/src/main/jni/cmake/* app/src/main/jni/librime_jni/* app/src/main/jni/CMakeLists.txt"

hash=$(git submodule status)
hash=$hash$($DIGEST_ALGORITHM $JNI_FILES)
hash=$(echo $hash | $DIGEST_ALGORITHM | cut -c-64)

echo "::set-output name=hash::$hash"
