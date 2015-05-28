include $(CLEAR_VARS)
LOCAL_MODULE := leveldb #1.18
LOCAL_CFLAGS := -D_REENTRANT -DOS_ANDROID -DLEVELDB_PLATFORM_POSIX -DNDEBUG -DSNAPPY
LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES := $(LOCAL_PATH)/leveldb $(LOCAL_PATH)/leveldb/include $(LOCAL_PATH)/snappy

LOCAL_SRC_FILES := \
    leveldb/db/builder.cc \
    leveldb/db/c.cc \
    leveldb/db/db_impl.cc \
    leveldb/db/db_iter.cc \
    leveldb/db/dbformat.cc \
    leveldb/db/filename.cc \
    leveldb/db/log_reader.cc \
    leveldb/db/log_writer.cc \
    leveldb/db/memtable.cc \
    leveldb/db/repair.cc \
    leveldb/db/table_cache.cc \
    leveldb/db/version_edit.cc \
    leveldb/db/version_set.cc \
    leveldb/db/write_batch.cc \
    leveldb/table/block.cc \
    leveldb/table/block_builder.cc \
    leveldb/table/filter_block.cc \
    leveldb/table/format.cc \
    leveldb/table/iterator.cc \
    leveldb/table/merger.cc \
    leveldb/table/table.cc \
    leveldb/table/table_builder.cc \
    leveldb/table/two_level_iterator.cc \
    leveldb/util/arena.cc \
    leveldb/util/bloom.cc \
    leveldb/util/cache.cc \
    leveldb/util/coding.cc \
    leveldb/util/comparator.cc \
    leveldb/util/crc32c.cc \
    leveldb/util/env.cc \
    leveldb/util/env_posix.cc \
    leveldb/util/filter_policy.cc \
    leveldb/util/hash.cc \
    leveldb/util/histogram.cc \
    leveldb/util/logging.cc \
    leveldb/util/options.cc \
    leveldb/util/status.cc \
    leveldb/port/port_posix.cc
 
LOCAL_STATIC_LIBRARIES += snappy

include $(BUILD_SHARED_LIBRARY)
