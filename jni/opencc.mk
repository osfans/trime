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
include $(CLEAR_VARS)
LOCAL_MODULE    := opencc #1.0.2
LOCAL_SRC_FILES := OpenCC/src/BinaryDict.cpp \
  OpenCC/src/Config.cpp \
  OpenCC/src/Conversion.cpp \
  OpenCC/src/ConversionChain.cpp \
  OpenCC/src/Converter.cpp \
  OpenCC/src/DartsDict.cpp \
  OpenCC/src/Dict.cpp \
  OpenCC/src/DictEntry.cpp \
  OpenCC/src/DictGroup.cpp \
  OpenCC/src/MaxMatchSegmentation.cpp \
  OpenCC/src/PhraseExtract.cpp \
  OpenCC/src/SimpleConverter.cpp \
  OpenCC/src/Segmentation.cpp \
  OpenCC/src/TextDict.cpp \
  OpenCC/src/UTF8StringSlice.cpp \
  OpenCC/src/UTF8Util.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/OpenCC/deps/rapidjson-0.11 $(LOCAL_PATH)/OpenCC/deps/darts-clone

LOCAL_LDLIBS := -latomic
#LOCAL_STATIC_LIBRARIES := c++_static
#LOCAL_CLANG :=true
#LOCAL_CFLAGS := -std=c++11
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := opencc_tool
LOCAL_SRC_FILES := OpenCC/src/tools/CommandLine.cpp
LOCAL_SHARED_LIBRARIES := libopencc
LOCAL_C_INCLUDES := $(LOCAL_PATH)/OpenCC/src $(LOCAL_PATH)/OpenCC/deps/tclap-1.2.1
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := opencc_dict
LOCAL_SRC_FILES := OpenCC/src/tools/DictConverter.cpp
LOCAL_SHARED_LIBRARIES := libopencc
LOCAL_C_INCLUDES := $(LOCAL_PATH)/OpenCC/src $(LOCAL_PATH)/OpenCC/deps/tclap-1.2.1
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := opencc_phrase_extract
LOCAL_SRC_FILES := OpenCC/src/tools/PhraseExtract.cpp
LOCAL_SHARED_LIBRARIES := libopencc
LOCAL_C_INCLUDES := $(LOCAL_PATH)/OpenCC/src $(LOCAL_PATH)/OpenCC/deps/tclap-1.2.1
include $(BUILD_EXECUTABLE)
