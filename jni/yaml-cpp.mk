LOCAL_PATH := $(ROOT_PATH)/yaml-cpp

include $(CLEAR_VARS)
LOCAL_MODULE := yaml-cpp #0.5.2

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_SRC_FILES := \
    src/binary.cpp \
    src/convert.cpp \
    src/directives.cpp \
    src/emit.cpp \
    src/emitfromevents.cpp \
    src/emitter.cpp \
    src/emitterstate.cpp \
    src/emitterutils.cpp \
    src/exp.cpp \
    src/memory.cpp \
    src/nodebuilder.cpp \
    src/node.cpp \
    src/node_data.cpp \
    src/nodeevents.cpp \
    src/null.cpp \
    src/ostream_wrapper.cpp \
    src/parse.cpp \
    src/parser.cpp \
    src/regex_yaml.cpp \
    src/scanner.cpp \
    src/scanscalar.cpp \
    src/scantag.cpp \
    src/scantoken.cpp \
    src/simplekey.cpp \
    src/singledocparser.cpp \
    src/stream.cpp \
    src/tag.cpp

#LOCAL_LDLIBS := -latomic
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := yaml_parse
LOCAL_SRC_FILES := util/parse.cpp
LOCAL_STATIC_LIBRARIES := yaml-cpp
LOCAL_LDLIBS := -latomic
include $(BUILD_EXECUTABLE)
