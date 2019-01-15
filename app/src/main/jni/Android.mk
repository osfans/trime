# Copyright (C) 2010 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

ROOT_PATH := $(call my-dir)
include jni/opencc.mk #1.0.5
include jni/yaml-cpp.mk #0.6.2
include jni/snappy.mk #1.1.7
include jni/leveldb.mk #1.20
include jni/marisa.mk #0.2.5
include jni/boost.mk #1.69.0
include jni/libiconv.mk #1.15
include jni/librime.mk #1.3.2
include jni/librime_jni.mk
include jni/miniglog.mk
