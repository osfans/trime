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
LOCAL_PATH := $(ROOT_PATH)/OpenCC
CXX_DEFINES := -DVERSION="\"1.0.5\"" -DOpencc_BUILT_AS_STATIC

include $(CLEAR_VARS)
LOCAL_MODULE    := opencc
LOCAL_SRC_FILES := src/BinaryDict.cpp \
  src/Config.cpp \
  src/Conversion.cpp \
  src/ConversionChain.cpp \
  src/Converter.cpp \
  src/DartsDict.cpp \
  src/Dict.cpp \
  src/DictConverter.cpp \
  src/DictEntry.cpp \
  src/DictGroup.cpp \
  src/MaxMatchSegmentation.cpp \
  src/PhraseExtract.cpp \
  src/SimpleConverter.cpp \
  src/Segmentation.cpp \
  src/TextDict.cpp \
  src/UTF8StringSlice.cpp \
  src/UTF8Util.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/deps/rapidjson-0.11 $(LOCAL_PATH)/deps/darts-clone
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/ $(LOCAL_PATH)/src $(LOCAL_PATH)/deps/tclap-1.2.1
LOCAL_CFLAGS := $(CXX_DEFINES)
LOCAL_EXPORT_CFLAGS := -DOpencc_BUILT_AS_STATIC
include $(BUILD_STATIC_LIBRARY)

ifneq ($(OPENCC_TOOLS),)
include $(CLEAR_VARS)
LOCAL_MODULE    := opencc_tool
LOCAL_SRC_FILES := src/tools/CommandLine.cpp
LOCAL_CFLAGS := $(CXX_DEFINES)
LOCAL_STATIC_LIBRARIES := opencc
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := opencc_dict
LOCAL_SRC_FILES := src/tools/DictConverter.cpp
LOCAL_CFLAGS := $(CXX_DEFINES)
LOCAL_STATIC_LIBRARIES := opencc
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := opencc_phrase_extract
LOCAL_SRC_FILES := src/tools/PhraseExtract.cpp
LOCAL_CFLAGS := $(CXX_DEFINES)
LOCAL_STATIC_LIBRARIES := opencc
include $(BUILD_EXECUTABLE)
endif
