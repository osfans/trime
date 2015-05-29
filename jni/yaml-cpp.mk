LOCAL_PATH := $(ROOT_PATH)/yaml-cpp

include $(CLEAR_VARS)
LOCAL_MODULE := yaml-cpp #0.5.2

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include/ \
    #$(ROOT_PATH)/boost/smart_ptr/include \
    $(LOCAL_PATH)/boost/config/include/ \
    $(LOCAL_PATH)/boost/assert/include/ \
    $(LOCAL_PATH)/boost/core/include/ \
    $(LOCAL_PATH)/boost/throw_exception/include/ \
    $(LOCAL_PATH)/boost/iterator/include/ \
    $(LOCAL_PATH)/boost/mpl/include/ \
    $(LOCAL_PATH)/boost/preprocessor/include/ \
    $(LOCAL_PATH)/boost/type_traits/include/ \
    $(LOCAL_PATH)/boost/static_assert/include/ \
    $(LOCAL_PATH)/boost/detail/include/ \
    $(LOCAL_PATH)/boost/utility/include/

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

LOCAL_LDLIBS := -latomic
include $(BUILD_SHARED_LIBRARY)
