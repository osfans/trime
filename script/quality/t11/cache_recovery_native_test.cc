// SPDX-License-Identifier: GPL-3.0-or-later
// Real generated-cache readers and normal deployment; no replacement mmap,
// DictCompiler, or user-dictionary implementation is used here.
#include <rime_api.h>
#include <rime/dict/dictionary.h>
#include <rime/dict/level_db.h>
#include <rime/dict/mapped_file.h>
#include <rime/dict/prism.h>
#include <rime/dict/reverse_lookup_dictionary.h>
#include <rime/dict/table.h>
#include <rime/dict/user_db.h>
#include <rime/lever/deployment_tasks.h>
#include <rime/service.h>

#include <cstdint>
#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <iterator>
#include <limits>
#include <map>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
namespace fs = std::filesystem;
using Bytes = std::vector<char>;
int checks = 0;

void Check(bool valid, const std::string& message) {
  if (!valid)
    throw std::runtime_error(message);
  ++checks;
}

Bytes Read(const fs::path& file) {
  std::ifstream stream(file, std::ios::binary);
  if (!stream)
    throw std::runtime_error("cannot read " + file.string());
  Bytes result((std::istreambuf_iterator<char>(stream)), {});
  if (stream.bad())
    throw std::runtime_error("cannot finish reading " + file.string());
  return result;
}

void Write(const fs::path& file, const Bytes& bytes) {
  std::ofstream stream(file, std::ios::binary | std::ios::trunc);
  if (!stream)
    throw std::runtime_error("cannot write " + file.string());
  if (!bytes.empty())
    stream.write(bytes.data(), static_cast<std::streamsize>(bytes.size()));
  stream.close();
  if (!stream)
    throw std::runtime_error("cannot finish writing " + file.string());
}

void WriteText(const fs::path& file, const std::string& text) {
  Write(file, Bytes(text.begin(), text.end()));
}

struct FileImage {
  Bytes bytes;
  fs::file_time_type modified;
  bool operator==(const FileImage& other) const {
    return bytes == other.bytes && modified == other.modified;
  }
};

using Images = std::map<std::string, FileImage>;
Images Snapshot(const fs::path& root) {
  Images result;
  Check(fs::is_directory(root), "snapshot directory missing: " + root.string());
  // Directory entries are included, so deleting an empty directory also fails.
  result.emplace("./", FileImage{{}, fs::last_write_time(root)});
  for (const auto& entry : fs::recursive_directory_iterator(root)) {
    Check(!entry.is_symlink(), "unexpected fixture symlink");
    const auto relative = fs::relative(entry.path(), root).generic_string();
    if (entry.is_directory()) {
      result.emplace(relative + '/', FileImage{{}, fs::last_write_time(entry)});
    } else {
      Check(entry.is_regular_file(), "unexpected fixture special file");
      result.emplace(relative,
                     FileImage{Read(entry), fs::last_write_time(entry)});
    }
  }
  return result;
}

class MappedProbe : public rime::MappedFile {
 public:
  explicit MappedProbe(const fs::path& file) : MappedFile(file) {}
  using MappedFile::OpenReadOnly;
};

void MappedBoundaries(const fs::path& directory) {
  const auto file = directory / "mapped-boundary.bin";
  Write(file, Bytes(16, 'x'));
  MappedProbe mapped(file);
  Check(mapped.OpenReadOnly(), "nonempty mapped fixture must open");
  Check(mapped.Find<std::uint64_t>(8) != nullptr, "exact-end object rejected");
  Check(mapped.Find<std::uint64_t>(9) == nullptr, "partial object admitted");
  Check(mapped.Find<char>(15) != nullptr, "last actual byte rejected");
  Check(mapped.Find<char>(16) == nullptr, "one-past-end object admitted");
  Check(mapped.Find<char>(17) == nullptr, "outside object admitted");
  Check(mapped.Find<std::uint64_t>(std::numeric_limits<std::size_t>::max()) ==
            nullptr,
        "overflowing offset admitted");
  mapped.Close();
  Write(file, {});
  Check(!mapped.OpenReadOnly() && !mapped.IsOpen(), "empty mapping admitted");
  std::cout << "PASS mapped-object boundaries\n";
}

std::string Schema(const std::string& id, const std::string& dependencies = "") {
  return "schema:\n  schema_id: " + id + "\n  name: " + id +
         "\n  version: '1.0'\n" + dependencies +
         "engine:\n  processors: [speller]\n  segmentors: [abc_segmentor]\n"
         "  translators: [table_translator]\nspeller:\n"
         "  alphabet: abcdefghijklmnopqrstuvwxyz\ntranslator:\n"
         "  dictionary: " + id + "\n  enable_user_dict: false\n";
}

void DictionarySource(const fs::path& shared, const std::string& id,
                      const std::string& word, const std::string& code) {
  WriteText(shared / (id + ".dict.yaml"),
            "---\nname: " + id + "\nversion: '1.0'\nsort: by_weight\n"
            "use_preset_vocabulary: false\n...\n" + word + '\t' + code +
                "\t10\n");
}

void Query(const fs::path& build, const std::string& id,
           const std::string& code, const std::string& word) {
  rime::Dictionary dictionary(
      id, {}, {rime::New<rime::Table>(build / (id + ".table.bin"))},
      rime::New<rime::Prism>(build / (id + ".prism.bin")));
  Check(dictionary.Load(), "rebuilt dictionary failed to load: " + id);
  rime::DictEntryIterator entries;
  Check(dictionary.LookupWords(&entries, code, false) == 1,
        "rebuilt dictionary code lookup failed: " + id);
  Check(!entries.exhausted() && entries.Peek()->text == word,
        "rebuilt dictionary returned wrong text: " + id);
  rime::ReverseDb reverse(build / (id + ".reverse.bin"));
  std::string actual;
  Check(reverse.Load() && reverse.Lookup(word, &actual) && actual == code,
        "rebuilt reverse dictionary lookup failed: " + id);
}

void Queries(const fs::path& build) {
  Query(build, "stroke", "h", "一");
  Query(build, "cache_main", "han", "汉");
  Query(build, "cache_after", "yu", "语");
}

void SeedUserDb(const fs::path& file, const std::string& name) {
  rime::UserDbWrapper<rime::LevelDb> db(file, name);
  Check(db.Open(), "real user dictionary creation failed");
  const std::string key = "h \t用户词";
  const std::string value = "c=7 d=2.5 t=42";
  Check(db.Update(key, value), "real user dictionary learning fixture failed");
  std::string actual;
  Check(db.Fetch(key, &actual) && actual == value, "learned fixture readback");
  Check(db.Close(), "real user dictionary close failed");
}

Images UserData(const fs::path& user) {
  Images result;
  for (const auto* name : {"stroke.userdb", "cache_main.userdb"}) {
    for (const auto& entry : Snapshot(user / name))
      result.emplace(std::string(name) + '/' + entry.first, entry.second);
  }
  for (const auto* name : {"custom_phrase.txt", "export.userdb.txt"}) {
    const auto file = user / name;
    result.emplace(name, FileImage{Read(file), fs::last_write_time(file)});
  }
  return result;
}

template <class Loader, class Metadata>
void ShortHeaders(const fs::path& file, const Bytes& valid) {
  Check(valid.size() >= sizeof(Metadata), "valid metadata fixture is too short");
  for (std::size_t length = 0; length < sizeof(Metadata); ++length) {
    Write(file, Bytes(valid.begin(), valid.begin() + length));
    Loader cache(file);
    // No catch around Load: an mmap exception fails the entire regression.
    Check(!cache.Load() && !cache.IsOpen(),
          "truncated metadata was accepted: " + file.string() + ':' +
              std::to_string(length));
  }
}

Images BinaryCaches(const fs::path& build) {
  Images result;
  for (const auto* id : {"stroke", "cache_main", "cache_after"}) {
    for (const auto* suffix : {".prism.bin", ".table.bin", ".reverse.bin"}) {
      const auto name = std::string(id) + suffix;
      const auto file = build / name;
      result.emplace(name, FileImage{Read(file), fs::last_write_time(file)});
    }
  }
  return result;
}

void WorkspaceRecovery(const fs::path& shared, const fs::path& user,
                       const fs::path& invalid) {
  auto& deployer = rime::Service::instance().deployer();
  const auto build = user / "build";
  Check(rime::InstallationUpdate().Run(&deployer), "installation setup failed");
  Check(rime::SchemaUpdate(rime::path(shared / "stroke.schema.yaml")).Run(&deployer),
        "baseline dependency schema deployment failed");
  Check(!fs::exists(build / "cache_after.schema.yaml"),
        "later schema unexpectedly precompiled");
  SeedUserDb(user / "stroke.userdb", "stroke");
  SeedUserDb(user / "cache_main.userdb", "cache_main");
  WriteText(user / "custom_phrase.txt", "用户词\th\t7\n");
  WriteText(user / "export.userdb.txt", "# saved user dictionary\n用户词\th\t7\n");
  const auto learned_before = UserData(user);
  const auto sources_before = Snapshot(shared);
  const auto custom_file = user / "default.custom.yaml";
  const FileImage custom_before{Read(custom_file), fs::last_write_time(custom_file)};
  const auto preserved = [&] {
    Check(UserData(user) == learned_before,
          "cache recovery deleted or rewrote learned/user dictionary data");
    Check(Snapshot(shared) == sources_before, "cache recovery changed source assets");
    Check((FileImage{Read(custom_file), fs::last_write_time(custom_file)} ==
           custom_before),
          "cache recovery changed user configuration");
  };

  // This exact real dependency name reproduces interrupted stroke.prism.bin.
  Write(build / "stroke.prism.bin", {});
  {
    rime::Prism empty(build / "stroke.prism.bin");
    Check(!empty.Load() && !empty.IsOpen(), "zero-byte stroke prism not rejected");
  }
  Check(rime::WorkspaceUpdate().Run(&deployer),
        "normal workspace deployment did not recover empty stroke dependency");
  Check(fs::is_regular_file(build / "cache_after.schema.yaml"),
        "deployment aborted before the subsequent schema");
  Queries(build);
  preserved();
  std::cout << "PASS empty stroke dependency and subsequent schema recovery\n";

  const auto prism = Read(build / "stroke.prism.bin");
  const auto table = Read(build / "stroke.table.bin");
  const auto reverse = Read(build / "stroke.reverse.bin");
  ShortHeaders<rime::Prism, rime::prism::Metadata>(invalid / "stroke.prism.bin", prism);
  ShortHeaders<rime::Table, rime::table::Metadata>(invalid / "stroke.table.bin", table);
  ShortHeaders<rime::ReverseDb, rime::reverse::Metadata>(invalid / "stroke.reverse.bin",
                                                     reverse);
  std::cout << "PASS every shorter metadata prefix for prism/table/reverse\n";

  struct DamagedCache {
    const char* suffix;
    std::size_t short_size;
  };
  for (const auto& entry : {
           DamagedCache{".prism.bin", sizeof(rime::prism::Metadata) - 1},
           DamagedCache{".table.bin", sizeof(rime::table::Metadata) - 1},
           DamagedCache{".reverse.bin", sizeof(rime::reverse::Metadata) - 1}}) {
    const auto file = build / (std::string("stroke") + entry.suffix);
    for (const auto length : {std::size_t(0), entry.short_size}) {
      const auto valid = Read(file);
      Check(valid.size() > length, "damage must truncate an actual valid cache");
      Write(file, Bytes(valid.begin(), valid.begin() + length));
      Check(rime::WorkspaceUpdate().Run(&deployer),
            "ordinary deployment did not rebuild " + file.string());
      Queries(build);
      preserved();
    }
  }

  // A cache hit must keep both bytes and modification time. Move only generated
  // cache timestamps into the past so a redundant identical rewrite is visible.
  const auto prior = fs::file_time_type::clock::now() - std::chrono::minutes(5);
  for (const auto& entry : BinaryCaches(build))
    fs::last_write_time(build / entry.first, prior);
  const auto current = BinaryCaches(build);
  Check(rime::WorkspaceUpdate().Run(&deployer), "valid cache deployment failed");
  Queries(build);
  Check(BinaryCaches(build) == current, "valid binary caches were rewritten");
  preserved();
  // The exact directory/file comparison above covers the closed-database
  // deployment interval. Reopening LevelDB can legitimately change LOG/LOCK;
  // after that point check the actual learned records, not log-file equality.
  for (const auto* name : {"stroke", "cache_main"}) {
    rime::UserDbWrapper<rime::LevelDb> db(user / (std::string(name) + ".userdb"),
                                       name);
    Check(db.OpenReadOnly(), "preserved user dictionary cannot reopen");
    std::string value;
    Check(db.Fetch("h \t用户词", &value) && value == "c=7 d=2.5 t=42",
          "cache recovery lost or altered the actual learned entry");
    Check(db.Close(), "preserved user dictionary cannot close");
  }
  std::cout << "PASS damaged cache rebuild, preserved user data, stable valid caches\n";
}
}  // namespace

int main(int argc, char** argv) {
  if (argc != 2)
    return 2;
  const fs::path scratch = fs::absolute(argv[1]);
  if (fs::exists(scratch)) {
    std::cerr << "use a nonexistent, owned cache-regression scratch directory\n";
    return 2;
  }
  auto* api = rime_get_api();
  bool initialized = false;
  try {
    const auto shared = scratch / "shared";
    const auto user = scratch / "user";
    const auto invalid = scratch / "invalid";
    fs::create_directories(shared);
    fs::create_directory(user);
    fs::create_directory(invalid);
    WriteText(shared / "default.yaml",
              "config_version: '1.0'\nschema_list:\n  - schema: cache_main\n"
              "  - schema: cache_after\n");
    WriteText(user / "default.custom.yaml", "patch:\n  menu/page_size: 7\n");
    WriteText(shared / "cache_main.schema.yaml",
              Schema("cache_main", "  dependencies: [stroke]\n"));
    WriteText(shared / "stroke.schema.yaml", Schema("stroke"));
    WriteText(shared / "cache_after.schema.yaml", Schema("cache_after"));
    DictionarySource(shared, "stroke", "一", "h");
    DictionarySource(shared, "cache_main", "汉", "han");
    DictionarySource(shared, "cache_after", "语", "yu");
    const auto shared_name = shared.string();
    const auto user_name = user.string();
    // Explicit linked modules avoid loading host-distribution dynamic plugins.
    const char* modules[] = {"default", "deployer", nullptr};
    RIME_STRUCT(RimeTraits, traits);
    traits.shared_data_dir = shared_name.c_str();
    traits.user_data_dir = user_name.c_str();
    traits.app_name = "trime.cache_recovery_test";
    traits.modules = modules;
    // Deliberately damaged caches emit expected loader ERRORs; keep their raw
    // diagnostics in the runner's stderr instead of silencing negative cases.
    traits.min_log_level = 0;
    traits.log_dir = "";
    api->setup(&traits);
    api->initialize(&traits);
    initialized = true;
    MappedBoundaries(invalid);
    WorkspaceRecovery(shared, user, invalid);
    api->finalize();
    initialized = false;
    std::cout << "PASS 3 actual mapped-cache regression groups (" << checks
              << " checks)\n";
    return 0;
  } catch (const std::exception& error) {
    if (initialized)
      api->finalize();
    std::cerr << error.what() << '\n';
    return 1;
  }
}
