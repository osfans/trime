LOCAL_PATH := $(ROOT_PATH)/leveldb

include $(CLEAR_VARS)
LOCAL_MODULE := leveldb
LOCAL_CFLAGS := -D_REENTRANT -DOS_ANDROID -DLEVELDB_PLATFORM_POSIX -DNDEBUG -DLEVELDB_ATOMIC_PRESENT

ifneq ($(SNAPPY),n)
LOCAL_CFLAGS += -DSNAPPY
LOCAL_STATIC_LIBRARIES += snappy
endif

LOCAL_CPP_EXTENSION := .cc
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_SRC_FILES := \
    db/builder.cc \
    db/c.cc \
    db/db_impl.cc \
    db/db_iter.cc \
    db/dbformat.cc \
    db/dumpfile.cc \
    db/filename.cc \
    db/log_reader.cc \
    db/log_writer.cc \
    db/memtable.cc \
    db/repair.cc \
    db/table_cache.cc \
    db/version_edit.cc \
    db/version_set.cc \
    db/write_batch.cc \
    table/block.cc \
    table/block_builder.cc \
    table/filter_block.cc \
    table/format.cc \
    table/iterator.cc \
    table/merger.cc \
    table/table.cc \
    table/table_builder.cc \
    table/two_level_iterator.cc \
    util/arena.cc \
    util/bloom.cc \
    util/cache.cc \
    util/coding.cc \
    util/comparator.cc \
    util/crc32c.cc \
    util/env.cc \
    util/env_posix.cc \
    util/filter_policy.cc \
    util/hash.cc \
    util/histogram.cc \
    util/logging.cc \
    util/options.cc \
    util/status.cc \
    port/port_posix.cc \
    port/port_posix_sse.cc

include $(BUILD_STATIC_LIBRARY)
