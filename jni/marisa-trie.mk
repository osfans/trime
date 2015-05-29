LOCAL_PATH := $(ROOT_PATH)/marisa-trie

include $(CLEAR_VARS)
LOCAL_MODULE := marisa
LOCAL_CPP_EXTENSION := .cc

LOCAL_SRC_FILES := \
	lib/marisa/agent.cc \
	lib/marisa/keyset.cc \
	lib/marisa/trie.cc \
	lib/marisa/grimoire/io/mapper.cc \
	lib/marisa/grimoire/io/reader.cc \
	lib/marisa/grimoire/io/writer.cc \
	lib/marisa/grimoire/trie/louds-trie.cc \
	lib/marisa/grimoire/trie/tail.cc \
	lib/marisa/grimoire/vector/bit-vector.cc

#LOCAL_LDLIBS := -latomic
include $(BUILD_STATIC_LIBRARY)
