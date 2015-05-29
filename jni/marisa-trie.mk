include $(CLEAR_VARS)
LOCAL_MODULE := marisa
LOCAL_CPP_EXTENSION := .cc

LOCAL_SRC_FILES := \
	marisa-trie/lib/marisa/agent.cc \
	marisa-trie/lib/marisa/keyset.cc \
	marisa-trie/lib/marisa/trie.cc \
	marisa-trie/lib/marisa/grimoire/io/mapper.cc \
	marisa-trie/lib/marisa/grimoire/io/reader.cc \
	marisa-trie/lib/marisa/grimoire/io/writer.cc \
	marisa-trie/lib/marisa/grimoire/trie/louds-trie.cc \
	marisa-trie/lib/marisa/grimoire/trie/tail.cc \
	marisa-trie/lib/marisa/grimoire/vector/bit-vector.cc

LOCAL_LDLIBS := -latomic
include $(BUILD_SHARED_LIBRARY)
