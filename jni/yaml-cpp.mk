include $(CLEAR_VARS)

LOCAL_MODULE := yaml-cpp #0.5.2

LOCAL_C_INCLUDES := $(LOCAL_PATH) \
    $(LOCAL_PATH)/yaml-cpp/include/ \
    $(LOCAL_PATH)/boost/smart_ptr/include \
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
    yaml-cpp/src/binary.cpp \
    yaml-cpp/src/convert.cpp \
    yaml-cpp/src/directives.cpp \
    yaml-cpp/src/emit.cpp \
    yaml-cpp/src/emitfromevents.cpp \
    yaml-cpp/src/emitter.cpp \
    yaml-cpp/src/emitterstate.cpp \
    yaml-cpp/src/emitterutils.cpp \
    yaml-cpp/src/exp.cpp \
    yaml-cpp/src/memory.cpp \
    yaml-cpp/src/nodebuilder.cpp \
    yaml-cpp/src/node.cpp \
    yaml-cpp/src/node_data.cpp \
    yaml-cpp/src/nodeevents.cpp \
    yaml-cpp/src/null.cpp \
    yaml-cpp/src/ostream_wrapper.cpp \
    yaml-cpp/src/parse.cpp \
    yaml-cpp/src/parser.cpp \
    yaml-cpp/src/regex_yaml.cpp \
    yaml-cpp/src/scanner.cpp \
    yaml-cpp/src/scanscalar.cpp \
    yaml-cpp/src/scantag.cpp \
    yaml-cpp/src/scantoken.cpp \
    yaml-cpp/src/simplekey.cpp \
    yaml-cpp/src/singledocparser.cpp \
    yaml-cpp/src/stream.cpp \
    yaml-cpp/src/tag.cpp

LOCAL_LDLIBS := -latomic
include $(BUILD_SHARED_LIBRARY)
