// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include <fcntl.h>
#include <rime/deployer.h>
#include <rime/dict/db_utils.h>
#include <rime/dict/user_db.h>
#include <rime/service.h>
#include <rime_levers_api.h>
#include <unistd.h>

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <memory>

static bool ensureDictionaryInstallation() {
  auto& deployer = rime::Service::instance().deployer();
  // Levers can be called in a fresh process without start_maintenance(). Never
  // let the default sync/unknown resolve under the process working directory.
  if (!deployer.RunTask("installation_update") ||
      !deployer.sync_dir.is_absolute())
    return false;
  const auto& user_id = deployer.user_id;
  return !user_id.empty() && user_id != "unknown" && user_id != "." &&
         user_id != ".." && user_id.find_first_of("/\\") == std::string::npos &&
         std::none_of(user_id.begin(), user_id.end(),
                      [](unsigned char c) { return c < 0x20 || c == 0x7f; });
}

static bool backupDictionaryChecked(const std::string& name) {
  auto component = rime::UserDb::Require("userdb");
  if (!component) return false;
  std::unique_ptr<rime::Db> db(component->Create(name));
  if (!db->OpenReadOnly() || !rime::UserDbHelper(db.get()).IsUserDb())
    return false;
  const auto directory =
      rime::Service::instance().deployer().user_data_sync_dir();
  std::filesystem::create_directories(directory);
  const auto destination =
      directory / (name + rime::UserDb::snapshot_extension());
  auto pattern = destination.string() + ".tmp.XXXXXX";
  std::vector<char> filename(pattern.begin(), pattern.end());
  filename.push_back('\0');
  int descriptor = mkstemp(filename.data());
  if (descriptor < 0) return false;
  struct Temporary {
    const char* path;
    ~Temporary() { unlink(path); }
  } temporary{filename.data()};
  FILE* stream = fdopen(descriptor, "wb");
  if (!stream) {
    close(descriptor);
    return false;
  }
  std::unique_ptr<FILE, decltype(&fclose)> output(stream, fclose);
  auto write = [&](const std::string& text) {
    return fwrite(text.data(), 1, text.size(), stream) == text.size();
  };
  auto field = [](const std::string& text) {
    return text.find_first_of("\t\r\n") == std::string::npos;
  };
  if (!write("# Rime user dictionary\n")) return false;
  {
    // DbSource owns metadata/data iterators. Release them before closing the
    // database, including when both iterators have reached the end of the dump.
    rime::DbSource source(db.get());
    std::string key, value;
    while (source.MetaGet(&key, &value)) {
      if (!field(key) || !field(value) ||
          !write("#@" + key + "\t" + value + "\n"))
        return false;
    }
    while (source.Get(&key, &value)) {
      const auto tab = key.find('\t');
      if (tab == std::string::npos || tab == 0 || tab + 1 == key.size() ||
          key.find('\t', tab + 1) != std::string::npos ||
          key.find_first_of("\r\n") != std::string::npos || !field(value) ||
          !write(key + "\t" + value + "\n"))
        return false;
    }
  }
  if (fflush(stream) != 0 || fsync(fileno(stream)) != 0) return false;
  if (fclose(output.release()) != 0 || !db->Close()) return false;
  std::error_code error;
  std::filesystem::rename(filename.data(), destination, error);
  return !error;
}

#include "jni-utils.h"
#include "objconv.h"

static RimeLeversApi* rime_get_levers_api() {
  return (RimeLeversApi*)rime_get_api()->find_module("levers")->get_api();
}

class SwitcherSettings {
 public:
  SwitcherSettings()
      : levers(rime_get_levers_api()),
        switcher(rime_get_levers_api()->switcher_settings_init()) {
    levers->load_settings((RimeCustomSettings*)switcher);
  }
  ~SwitcherSettings() {
    if (switcher)
      levers->custom_settings_destroy((RimeCustomSettings*)switcher);
  }

  std::vector<SchemaItem> availableSchemas() {
    std::vector<SchemaItem> result;
    RimeSchemaList list{};
    if (levers->get_available_schema_list(switcher, &list)) {
      result = SchemaItem::fromCList(list);
      levers->schema_list_destroy(&list);
    }
    return std::move(result);
  }

  std::vector<SchemaItem> selectedSchemas() {
    std::vector<SchemaItem> result;
    RimeSchemaList list{};
    if (levers->get_selected_schema_list(switcher, &list)) {
      result = SchemaItem::fromCList(list);
      levers->schema_list_destroy(&list);
    }
    return std::move(result);
  }

  bool selectSchemas(const std::vector<std::string>& schemas) {
    auto length = schemas.size();
    const char** input = new const char*[length];
    int i = 0;
    for (const auto& id : schemas) {
      input[i++] = id.c_str();
    }
    bool result =
        levers->select_schemas(switcher, input, static_cast<int>(length));
    if (result) {
      levers->save_settings((RimeCustomSettings*)switcher);
    }
    delete[] input;
    return result;
  }

  std::vector<std::string> userDictList() {
    std::vector<std::string> result;
    RimeUserDictIterator iter{};
    if (levers->user_dict_iterator_init(&iter)) {
      while (true) {
        auto name = levers->next_user_dict(&iter);
        if (!name) break;
        result.emplace_back(name);
      }
      levers->user_dict_iterator_destroy(&iter);
    }
    return result;
  }

  bool backupUserDict(std::string_view dictName) {
    return backupDictionaryChecked(std::string(dictName));
  }

  bool restoreUserDict(std::string_view snapshotFile) {
    return levers->restore_user_dict(snapshotFile.data());
  }

  int exportUserDict(std::string_view dictName, std::string_view textFile) {
    return levers->export_user_dict(dictName.data(), textFile.data());
  }

  int importUserDict(std::string_view dictName, std::string_view textFile) {
    return levers->import_user_dict(dictName.data(), textFile.data());
  }

 private:
  RimeLeversApi* levers;
  RimeSwitcherSettings* switcher;
};

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getAvailableRimeSchemaList(JNIEnv* env,
                                                           jclass /* thiz */) {
  SwitcherSettings switcher;
  return rimeSchemaListToJObjectArray(env, switcher.availableSchemas());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getSelectedRimeSchemaList(JNIEnv* env,
                                                          jclass /* thiz */) {
  SwitcherSettings switcher;
  return rimeSchemaListToJObjectArray(env, switcher.selectedSchemas());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_selectRimeSchemas(JNIEnv* env,
                                                  jclass /* thiz */,
                                                  jobjectArray array) {
  SwitcherSettings switcher;
  return switcher.selectSchemas(stringArrayToStringVector(env, array));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_data_userdict_UserDictManager_getUserDictList(
    JNIEnv* env, jclass clazz) {
  return jniCall(env, [&] {
    SwitcherSettings switcher;
    return stringVectorToJStringArray(env, switcher.userDictList());
  });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_data_userdict_UserDictManager_backupUserDict(
    JNIEnv* env, jclass clazz, jstring dict_name) {
  return jniCall(env, [&]() -> jboolean {
    if (!ensureDictionaryInstallation()) return false;
    SwitcherSettings switcher;
    const std::string name(*CString(env, dict_name));
    if (!switcher.backupUserDict(name)) return false;
    const auto file =
        rime::Service::instance().deployer().user_data_sync_dir() /
        (name + rime::UserDb::snapshot_extension());
    return std::filesystem::is_regular_file(file) &&
           std::filesystem::file_size(file) > 0;
  });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_data_userdict_UserDictManager_restoreUserDict(
    JNIEnv* env, jclass clazz, jstring snapshot_file) {
  return jniCall(env, [&]() -> jboolean {
    if (!ensureDictionaryInstallation()) return false;
    SwitcherSettings switcher;
    return switcher.restoreUserDict(*CString(env, snapshot_file));
  });
}

// The upstream levers restore path returns true after Source::Dump even when a
// destination Put fails. Check every merge write and the final metadata here;
// Kotlin retains an exact pre-operation backup and rolls back on false/error.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_data_userdict_UserDictManager_restoreUserDictChecked(
    JNIEnv* env, jclass clazz, jstring dict_name, jstring snapshot_file) {
  return jniCall(env, [&]() -> jboolean {
    if (!ensureDictionaryInstallation()) return false;
    auto component = rime::UserDb::Require("userdb");
    if (!component) return false;
    const std::string expected(*CString(env, dict_name));
    const std::string snapshot(*CString(env, snapshot_file));
    auto cleanup = [](rime::Db* db) {
      try {
        if (db->loaded()) db->Close();
        if (db->Exists()) db->Remove();
      } catch (const std::exception&) {
        // A cleanup failure must not terminate an in-flight Java exception.
      }
      delete db;
    };
    std::unique_ptr<rime::Db, decltype(cleanup)> temporary(
        component->Create(".trime-restore"), cleanup);
    if (temporary->Exists() && !temporary->Remove()) return false;
    if (!temporary->Open() || !temporary->Restore(rime::path(snapshot)) ||
        !rime::UserDbHelper(temporary.get()).IsUserDb() ||
        rime::UserDbHelper(temporary.get()).GetDbName() != expected)
      return false;
    std::unique_ptr<rime::Db> destination(component->Create(expected));
    if (!destination->Open()) return false;
    auto tick = [](rime::Db* db, uint64_t fallback) -> uint64_t {
      std::string value;
      if (db->MetaFetch("/tick", &value)) {
        try {
          return std::stoull(value);
        } catch (const std::exception&) {
        }
      }
      return fallback;
    };
    const auto expected_tick =
        std::max(tick(destination.get(), 1), tick(temporary.get(), 0));
    rime::DbSource source(temporary.get());
    rime::UserDbMerger merger(destination.get());
    std::string key, value;
    while (source.MetaGet(&key, &value)) {
      if (!merger.MetaPut(key, value)) return false;
    }
    bool entries = false;
    while (source.Get(&key, &value)) {
      if (!merger.Put(key, value)) return false;
      entries = true;
    }
    merger.CloseMerge();
    if (entries) {
      std::string stored_tick;
      if (!destination->MetaFetch("/tick", &stored_tick) ||
          stored_tick != std::to_string(expected_tick) ||
          rime::UserDbHelper(destination.get()).GetUserId() !=
              rime::Service::instance().deployer().user_id)
        return false;
    }
    return destination->Close();
  });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_data_userdict_UserDictManager_exportUserDict(
    JNIEnv* env, jclass clazz, jstring dict_name, jstring text_file) {
  return jniCall(env, [&]() -> jint {
    SwitcherSettings switcher;
    return switcher.exportUserDict(*CString(env, dict_name),
                                   *CString(env, text_file));
  });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_data_userdict_UserDictManager_importUserDict(
    JNIEnv* env, jclass clazz, jstring dict_name, jstring text_file) {
  return jniCall(env, [&]() -> jint {
    if (!ensureDictionaryInstallation()) return -1;
    SwitcherSettings switcher;
    return switcher.importUserDict(*CString(env, dict_name),
                                   *CString(env, text_file));
  });
}
