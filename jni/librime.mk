LOCAL_PATH := $(ROOT_PATH)/librime

CXX_DEFINES = -O3 -fPIC -DBOOST_NO_CXX11_SCOPED_ENUMS -DRIME_BUILD_SHARED_LIBS -DRIME_EXPORTS -DRIME_VERSION=\"1.2.9\" -DNDEBUG #-DRIME_ENABLE_LOGGING

include $(CLEAR_VARS)
LOCAL_MODULE := rime #1.2.9
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include $(LOCAL_PATH)/thirdparty/include

LOCAL_SRC_FILES += \
  src/commit_history.cc \
  src/rime_api.cc \
  src/registry.cc \
  src/module.cc \
  src/key_table.cc \
  src/key_event.cc \
  src/candidate.cc \
  src/service.cc \
  src/core_module.cc \
  src/ticket.cc \
  src/signature.cc \
  src/setup.cc \
  src/deployer.cc \
  src/config.cc \
  src/menu.cc \
  src/composition.cc \
  src/segmentation.cc \
  src/engine.cc \
  src/switcher.cc \
  src/schema.cc \
  src/context.cc \
  src/translation.cc \
  src/algo/encoder.cc \
  src/algo/utilities.cc \
  src/algo/calculus.cc \
  src/algo/syllabifier.cc \
  src/algo/algebra.cc \
  src/dict/db_utils.cc \
  src/dict/dict_compiler.cc \
  src/dict/user_db.cc \
  src/dict/string_table.cc \
  src/dict/user_db_recovery_task.cc \
  src/dict/mapped_file.cc \
  src/dict/preset_vocabulary.cc \
  src/dict/user_dictionary.cc \
  src/dict/entry_collector.cc \
  src/dict/reverse_lookup_dictionary.cc \
  src/dict/prism.cc \
  src/dict/level_db.cc \
  src/dict/vocabulary.cc \
  src/dict/db.cc \
  src/dict/table.cc \
  src/dict/dict_settings.cc \
  src/dict/dictionary.cc \
  src/dict/dict_module.cc \
  src/dict/table_db.cc \
  src/dict/text_db.cc \
  src/dict/tsv.cc \
  src/gear/echo_translator.cc \
  src/gear/shape.cc \
  src/gear/schema_list_translator.cc \
  src/gear/editor.cc \
  src/gear/speller.cc \
  src/gear/ascii_segmentor.cc \
  src/gear/navigator.cc \
  src/gear/memory.cc \
  src/gear/unity_table_encoder.cc \
  src/gear/gears_module.cc \
  src/gear/translator_commons.cc \
  src/gear/uniquifier.cc \
  src/gear/single_char_filter.cc \
  src/gear/charset_filter.cc \
  src/gear/affix_segmentor.cc \
  src/gear/key_binder.cc \
  src/gear/simplifier.cc \
  src/gear/switch_translator.cc \
  src/gear/script_translator.cc \
  src/gear/chord_composer.cc \
  src/gear/filter_commons.cc \
  src/gear/selector.cc \
  src/gear/abc_segmentor.cc \
  src/gear/reverse_lookup_filter.cc \
  src/gear/recognizer.cc \
  src/gear/ascii_composer.cc \
  src/gear/fallback_segmentor.cc \
  src/gear/reverse_lookup_translator.cc \
  src/gear/poet.cc \
  src/gear/table_translator.cc \
  src/gear/matcher.cc \
  src/gear/punctuator.cc \
  src/lever/levers_module.cc \
  src/lever/switcher_settings.cc \
  src/lever/user_dict_manager.cc \
  src/lever/deployment_tasks.cc \
  src/lever/customizer.cc \
  src/lever/custom_settings.cc \

LOCAL_CFLAGS := $(CXX_DEFINES)
LOCAL_STATIC_LIBRARIES := boost leveldb marisa opencc yaml-cpp
LOCAL_LDLIBS := -latomic
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := rime_api_console
LOCAL_SRC_FILES := tools/rime_api_console.cc
LOCAL_SHARED_LIBRARIES := rime
LOCAL_CFLAGS := $(CXX_DEFINES)
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := rime_console
LOCAL_SRC_FILES := tools/rime_console.cc
LOCAL_SHARED_LIBRARIES := rime
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include $(LOCAL_PATH)/thirdparty/include
LOCAL_CFLAGS := $(CXX_DEFINES)
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := rime_deployer
LOCAL_SRC_FILES := tools/rime_deployer.cc
LOCAL_SHARED_LIBRARIES := rime
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CFLAGS := $(CXX_DEFINES)
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := rime_dict_manager
LOCAL_SRC_FILES := tools/rime_dict_manager.cc
LOCAL_SHARED_LIBRARIES := rime
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CFLAGS := $(CXX_DEFINES)
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE    := rime_patch
LOCAL_SRC_FILES := tools/rime_patch.cc
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SHARED_LIBRARIES := rime
LOCAL_CFLAGS := $(CXX_DEFINES)
include $(BUILD_EXECUTABLE)
