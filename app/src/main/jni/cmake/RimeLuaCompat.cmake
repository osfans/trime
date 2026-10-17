# SPDX-License-Identifier: GPL-3.0-or-later

# Keep the pinned plugin untouched while adapting Android 32-bit compatibility
# and enforcing the process privacy gate at Lua persistence and gear callbacks.
function(trime_adapt_rime_lua)
  set(original "${CMAKE_SOURCE_DIR}/librime-lua/src/types.cc")
  set(stage_root "${CMAKE_BINARY_DIR}/trime-rime-lua-stage")
  set(output_root "${CMAKE_BINARY_DIR}/trime-rime-lua")
  set(stage "${stage_root}/types.cc")
  set(output "${output_root}/types.cc")
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${original}")
  configure_file("${original}" "${stage}" COPYONLY)
  foreach(relative lua_gears.cc lua_gears.h modules.cc)
    set(lua_gear_original "${CMAKE_SOURCE_DIR}/librime-lua/src/${relative}")
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
                 "${lua_gear_original}")
    configure_file("${lua_gear_original}" "${stage_root}/${relative}" COPYONLY)
  endforeach()

  trime_replace_dependency_cmake(
    "${stage}"
    "#include <chrono>"
    "#include <chrono>\n#include <limits>")
  trime_replace_dependency_cmake(
    "${stage}"
    "#include <limits>"
    "#include <limits>\n\n#include \"trime_privacy_guard.h\"")
  trime_replace_dependency_cmake(
    "${stage}"
    "    limit = limit == 0 ? 0xffffffffffffffff : limit;"
    "    limit = limit == 0 ? std::numeric_limits<size_t>::max() : limit;")
  trime_replace_dependency_cmake(
    "${stage}"
    [=[  int raw_push(lua_State *L){
    C_State C;]=]
    [=[  int raw_push(lua_State *L){
    if (trime::IsPrivacyModeEnabled())
      return 0;
    C_State C;]=])
  trime_replace_dependency_cmake(
    "${stage}"
    [=[  bool set_string(T &t, const string &value) {
    return t.SetString( value);
  }]=]
    [=[  bool set_bool(T &t, bool value) {
    return !trime::IsPrivacyModeEnabled() && t.SetBool(value);
  }

  bool set_int(T &t, int value) {
    return !trime::IsPrivacyModeEnabled() && t.SetInt(value);
  }

  bool set_double(T &t, double value) {
    return !trime::IsPrivacyModeEnabled() && t.SetDouble(value);
  }

  bool set_string(T &t, const string &value) {
    return !trime::IsPrivacyModeEnabled() && t.SetString(value);
  }]=])
  trime_replace_dependency_cmake(
    "${stage}"
    [=[    {"set_bool", WRAPMEM(T::SetBool)},
    {"set_int", WRAPMEM(T::SetInt)},
    {"set_double", WRAPMEM(T::SetDouble)},]=]
    [=[    {"set_bool", WRAP(set_bool)},
    {"set_int", WRAP(set_int)},
    {"set_double", WRAP(set_double)},]=])
  trime_replace_dependency_cmake(
    "${stage}"
    [=[  an<E> element(an<T> t){
    return t;
  }

  static const luaL_Reg funcs[] = {
    {"ConfigList", WRAP(make)},]=]
    [=[  an<E> element(an<T> t){
    return t;
  }

  bool set_at(T &t, size_t i, an<E> value) {
    return !trime::IsPrivacyModeEnabled() && t.SetAt(i, value);
  }

  bool append(T &t, an<E> value) {
    return !trime::IsPrivacyModeEnabled() && t.Append(value);
  }

  bool insert(T &t, size_t i, an<E> value) {
    return !trime::IsPrivacyModeEnabled() && t.Insert(i, value);
  }

  bool clear(T &t) {
    return !trime::IsPrivacyModeEnabled() && t.Clear();
  }

  bool resize(T &t, size_t size) {
    return !trime::IsPrivacyModeEnabled() && t.Resize(size);
  }

  static const luaL_Reg funcs[] = {
    {"ConfigList", WRAP(make)},]=])
  trime_replace_dependency_cmake(
    "${stage}"
    [=[    {"set_at", WRAPMEM(T::SetAt)},
    {"append", WRAPMEM(T::Append)},
    {"insert", WRAPMEM(T::Insert)},
    {"clear", WRAPMEM(T::Clear)},
    {"empty", WRAPMEM(T::empty)},
    {"resize", WRAPMEM(T::Resize)},]=]
    [=[    {"set_at", WRAP(set_at)},
    {"append", WRAP(append)},
    {"insert", WRAP(insert)},
    {"clear", WRAP(clear)},
    {"empty", WRAPMEM(T::empty)},
    {"resize", WRAP(resize)},]=])
  trime_replace_dependency_cmake(
    "${stage}"
    [=[  std::vector<string> get_keys(T &t){
    std::vector<string> keys;
    for (auto it : t)
      keys.push_back(it.first);
    return keys;
  }

  static const luaL_Reg funcs[] = {]=]
    [=[  std::vector<string> get_keys(T &t){
    std::vector<string> keys;
    for (auto it : t)
      keys.push_back(it.first);
    return keys;
  }

  bool set(T &t, const string& key, an<E> value) {
    return !trime::IsPrivacyModeEnabled() && t.Set(key, value);
  }

  bool clear(T &t) {
    return !trime::IsPrivacyModeEnabled() && t.Clear();
  }

  static const luaL_Reg funcs[] = {]=])
  trime_replace_dependency_cmake(
    "${stage}"
    [=[    {"set", WRAPMEM(T::Set)},
    {"get", WRAPMEM(T::Get)},
    {"get_value", WRAPMEM(T::GetValue)},
    {"has_key", WRAPMEM(T::HasKey)},
    {"clear", WRAPMEM(T::Clear)},]=]
    [=[    {"set", WRAP(set)},
    {"get", WRAPMEM(T::Get)},
    {"get_value", WRAPMEM(T::GetValue)},
    {"has_key", WRAPMEM(T::HasKey)},
    {"clear", WRAP(clear)},]=])
  trime_replace_dependency_cmake(
    "${stage}"
    [=[  // GetString : overload function
  bool set_string(T &t, const string &path, const string &value) {
    return t.SetString(path, value);
  }]=]
    [=[  bool set_bool(T &t, const string &path, bool value) {
    return !trime::IsPrivacyModeEnabled() && t.SetBool(path, value);
  }

  bool set_int(T &t, const string &path, int value) {
    return !trime::IsPrivacyModeEnabled() && t.SetInt(path, value);
  }

  bool set_double(T &t, const string &path, double value) {
    return !trime::IsPrivacyModeEnabled() && t.SetDouble(path, value);
  }

  // GetString : overload function
  bool set_string(T &t, const string &path, const string &value) {
    return !trime::IsPrivacyModeEnabled() && t.SetString(path, value);
  }]=])
  trime_replace_dependency_cmake(
    "${stage}"
    [=[  bool set_item(T &t ,const string &path, an<ConfigItem> item){
    return t.SetItem(path,item);
  }]=]
    [=[  bool set_item(T &t ,const string &path, an<ConfigItem> item){
    return !trime::IsPrivacyModeEnabled() && t.SetItem(path,item);
  }]=])
  foreach(config_type Value List)
    string(TOLOWER "${config_type}" config_name)
    trime_replace_dependency_cmake(
      "${stage}"
      "  bool set_${config_name}(T &t, const string &path,  an<Config${config_type}>  value) {\n    return t.SetItem(path, value);\n  }"
      "  bool set_${config_name}(T &t, const string &path,  an<Config${config_type}>  value) {\n    return !trime::IsPrivacyModeEnabled() && t.SetItem(path, value);\n  }")
  endforeach()
  trime_replace_dependency_cmake(
    "${stage}"
    [=[  bool set_map(T &t, const string &path, an<ConfigMap> value) {
    return t.SetItem(path, value);
  }]=]
    [=[  bool set_map(T &t, const string &path, an<ConfigMap> value) {
    return !trime::IsPrivacyModeEnabled() && t.SetItem(path, value);
  }]=])
  trime_replace_dependency_cmake(
    "${stage}"
    [=[    { "set_bool", WRAPMEM(T::SetBool) },
    { "set_int", WRAPMEM(T::SetInt) },
    { "set_double", WRAPMEM(T::SetDouble) },]=]
    [=[    { "set_bool", WRAP(set_bool) },
    { "set_int", WRAP(set_int) },
    { "set_double", WRAP(set_double) },]=])
  trime_replace_dependency_cmake(
    "${stage}"
    [=[  bool save_to_file(T &t, const string &f) {
    return t.SaveToFile(COMPAT<Deployer>::to_path(f));
  }]=]
    [=[  bool save_to_file(T &t, const string &f) {
    return !trime::IsPrivacyModeEnabled() &&
           t.SaveToFile(COMPAT<Deployer>::to_path(f));
  }]=])
  trime_replace_dependency_cmake(
    "${stage}"
    [=[  auto f = [lua, o](I... i) {
    auto r = lua->void_call<an<LuaObj>, Context *>(o, i...);]=]
    [=[  auto f = [lua, o](I... i) {
    if (trime::IsPrivacyModeEnabled())
      return;
    auto r = lua->void_call<an<LuaObj>, Context *>(o, i...);]=])

  # No Lua callback may observe a private key, segment, candidate stream, or
  # context. Stage every translation unit that includes lua_gears.h so the
  # inline filter entry point has one definition throughout the plugin.
  set(lua_gears_header "${stage_root}/lua_gears.h")
  set(lua_gears_source "${stage_root}/lua_gears.cc")
  trime_replace_dependency_cmake(
    "${lua_gears_header}"
    [=[#include "lib/lua.h"]=]
    [=[#include "lib/lua.h"
#include "trime_privacy_guard.h"]=])
  trime_replace_dependency_cmake(
    "${lua_gears_header}"
    [=[  an<Candidate> Peek() {
    return c_;
  }]=]
    [=[  an<Candidate> Peek() {
    if (trime::IsPrivacyModeEnabled()) {
      c_.reset();
      f_.reset();
      set_exhausted(true);
      return an<Candidate>();
    }
    return c_;
  }]=])
  trime_replace_dependency_cmake(
    "${lua_gears_header}"
    [=[  virtual bool AppliesToSegment(Segment* segment) {
    if ( ! tags_match_ )]=]
    [=[  virtual bool AppliesToSegment(Segment* segment) {
    if (trime::IsPrivacyModeEnabled() || !EnsureInitialized())
      return false;
    if ( ! tags_match_ )]=])
  trime_replace_dependency_cmake(
    "${lua_gears_header}"
    [=[private:
  Lua *lua_;
  an<LuaObj> env_;
  an<LuaObj> func_;
  an<LuaObj> fini_;
  an<LuaObj> tags_match_;]=]
    [=[private:
  bool EnsureInitialized();
  Lua *lua_;
  Ticket ticket_;
  bool initialized_ = false;
  an<LuaObj> env_;
  an<LuaObj> func_;
  an<LuaObj> fini_;
  an<LuaObj> tags_match_;]=])
  trime_replace_dependency_cmake(
    "${lua_gears_header}"
    [=[class LuaTranslator : public Translator {
public:
  explicit LuaTranslator(const Ticket& ticket, Lua* lua);
  virtual ~LuaTranslator();

  virtual an<Translation> Query(const string& input,
                                const Segment& segment);

private:
  Lua *lua_;]=]
    [=[class LuaTranslator : public Translator {
public:
  explicit LuaTranslator(const Ticket& ticket, Lua* lua);
  virtual ~LuaTranslator();

  virtual an<Translation> Query(const string& input,
                                const Segment& segment);

private:
  bool EnsureInitialized();
  Lua *lua_;
  Ticket ticket_;
  bool initialized_ = false;]=])
  trime_replace_dependency_cmake(
    "${lua_gears_header}"
    [=[class LuaSegmentor : public Segmentor {
public:
  explicit LuaSegmentor(const Ticket& ticket, Lua *lua);
  virtual ~LuaSegmentor();

  virtual bool Proceed(Segmentation* Segmentation);

private:
  Lua *lua_;]=]
    [=[class LuaSegmentor : public Segmentor {
public:
  explicit LuaSegmentor(const Ticket& ticket, Lua *lua);
  virtual ~LuaSegmentor();

  virtual bool Proceed(Segmentation* Segmentation);

private:
  bool EnsureInitialized();
  Lua *lua_;
  Ticket ticket_;
  bool initialized_ = false;]=])
  trime_replace_dependency_cmake(
    "${lua_gears_header}"
    [=[class LuaProcessor : public Processor {
public:
  LuaProcessor(const Ticket& ticket, Lua *lua);
  virtual ~LuaProcessor();

  virtual ProcessResult ProcessKeyEvent(const KeyEvent& key_event);

private:
  Lua *lua_;]=]
    [=[class LuaProcessor : public Processor {
public:
  LuaProcessor(const Ticket& ticket, Lua *lua);
  virtual ~LuaProcessor();

  virtual ProcessResult ProcessKeyEvent(const KeyEvent& key_event);

private:
  bool EnsureInitialized();
  Lua *lua_;
  Ticket ticket_;
  bool initialized_ = false;]=])

  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[bool LuaTranslation::Next() {
  if (exhausted()) {]=]
    [=[bool LuaTranslation::Next() {
  if (trime::IsPrivacyModeEnabled()) {
    c_.reset();
    f_.reset();
    set_exhausted(true);
    return false;
  }
  if (exhausted()) {]=])
  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[LuaTranslation::~LuaTranslation() {
  lua_->gc();
}]=]
    [=[LuaTranslation::~LuaTranslation() {
  c_.reset();
  f_.reset();
  // Lua GC can execute __gc metamethods; defer it until ordinary mode.
  if (!trime::IsPrivacyModeEnabled())
    lua_->gc();
}]=])

  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[LuaFilter::LuaFilter(const Ticket& ticket, Lua* lua)
  : Filter(ticket), TagMatching(ticket), lua_(lua) {
  lua->to_state([&](lua_State *L) {raw_init(L, ticket, &env_, &func_, &fini_, &tags_match_);});
}]=]
    [=[LuaFilter::LuaFilter(const Ticket& ticket, Lua* lua)
  : Filter(ticket), TagMatching(ticket), lua_(lua), ticket_(ticket) {
  EnsureInitialized();
}

bool LuaFilter::EnsureInitialized() {
  if (initialized_)
    return true;
  if (trime::IsPrivacyModeEnabled())
    return false;
  lua_->to_state([&](lua_State *L) {
    raw_init(L, ticket_, &env_, &func_, &fini_, &tags_match_);
  });
  initialized_ = true;
  return true;
}]=])
  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[an<Translation> LuaFilter::Apply(
  an<Translation> translation, CandidateList* candidates) {
  auto f = lua_->newthread<an<LuaObj>, an<Translation>,]=]
    [=[an<Translation> LuaFilter::Apply(
  an<Translation> translation, CandidateList* candidates) {
  if (trime::IsPrivacyModeEnabled() || !EnsureInitialized())
    return translation;
  auto f = lua_->newthread<an<LuaObj>, an<Translation>,]=])
  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[LuaFilter::~LuaFilter() {
  if (fini_) {]=]
    [=[LuaFilter::~LuaFilter() {
  if (!trime::IsPrivacyModeEnabled() && initialized_ && fini_) {]=])

  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[LuaTranslator::LuaTranslator(const Ticket& ticket, Lua* lua)
  : Translator(ticket), lua_(lua) {
  lua->to_state([&](lua_State *L) {raw_init(L, ticket, &env_, &func_, &fini_);});
}]=]
    [=[LuaTranslator::LuaTranslator(const Ticket& ticket, Lua* lua)
  : Translator(ticket), lua_(lua), ticket_(ticket) {
  EnsureInitialized();
}

bool LuaTranslator::EnsureInitialized() {
  if (initialized_)
    return true;
  if (trime::IsPrivacyModeEnabled())
    return false;
  lua_->to_state([&](lua_State *L) {
    raw_init(L, ticket_, &env_, &func_, &fini_);
  });
  initialized_ = true;
  return true;
}]=])
  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[an<Translation> LuaTranslator::Query(const string& input,
                                     const Segment& segment) {
  auto f = lua_->newthread<an<LuaObj>, const string &, const Segment &,]=]
    [=[an<Translation> LuaTranslator::Query(const string& input,
                                     const Segment& segment) {
  if (trime::IsPrivacyModeEnabled() || !EnsureInitialized())
    return an<Translation>();
  auto f = lua_->newthread<an<LuaObj>, const string &, const Segment &,]=])
  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[LuaTranslator::~LuaTranslator() {
  if (fini_) {]=]
    [=[LuaTranslator::~LuaTranslator() {
  if (!trime::IsPrivacyModeEnabled() && initialized_ && fini_) {]=])

  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[LuaSegmentor::LuaSegmentor(const Ticket& ticket, Lua *lua)
  : Segmentor(ticket), lua_(lua) {
  lua->to_state([&](lua_State *L) {raw_init(L, ticket, &env_, &func_, &fini_);});
}]=]
    [=[LuaSegmentor::LuaSegmentor(const Ticket& ticket, Lua *lua)
  : Segmentor(ticket), lua_(lua), ticket_(ticket) {
  EnsureInitialized();
}

bool LuaSegmentor::EnsureInitialized() {
  if (initialized_)
    return true;
  if (trime::IsPrivacyModeEnabled())
    return false;
  lua_->to_state([&](lua_State *L) {
    raw_init(L, ticket_, &env_, &func_, &fini_);
  });
  initialized_ = true;
  return true;
}]=])
  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[bool LuaSegmentor::Proceed(Segmentation* segmentation) {
  auto r = lua_->call<bool, an<LuaObj>, Segmentation &,]=]
    [=[bool LuaSegmentor::Proceed(Segmentation* segmentation) {
  if (trime::IsPrivacyModeEnabled() || !EnsureInitialized())
    return true;
  auto r = lua_->call<bool, an<LuaObj>, Segmentation &,]=])
  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[LuaSegmentor::~LuaSegmentor() {
  if (fini_) {]=]
    [=[LuaSegmentor::~LuaSegmentor() {
  if (!trime::IsPrivacyModeEnabled() && initialized_ && fini_) {]=])

  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[LuaProcessor::LuaProcessor(const Ticket& ticket, Lua* lua)
  : Processor(ticket), lua_(lua) {
  lua->to_state([&](lua_State *L) {raw_init(L, ticket, &env_, &func_, &fini_);});
}]=]
    [=[LuaProcessor::LuaProcessor(const Ticket& ticket, Lua* lua)
  : Processor(ticket), lua_(lua), ticket_(ticket) {
  EnsureInitialized();
}

bool LuaProcessor::EnsureInitialized() {
  if (initialized_)
    return true;
  if (trime::IsPrivacyModeEnabled())
    return false;
  lua_->to_state([&](lua_State *L) {
    raw_init(L, ticket_, &env_, &func_, &fini_);
  });
  initialized_ = true;
  return true;
}]=])
  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[ProcessResult LuaProcessor::ProcessKeyEvent(const KeyEvent& key_event) {
  auto r = lua_->call<int, an<LuaObj>, const KeyEvent&,]=]
    [=[ProcessResult LuaProcessor::ProcessKeyEvent(const KeyEvent& key_event) {
  if (trime::IsPrivacyModeEnabled() || !EnsureInitialized())
    return kNoop;
  auto r = lua_->call<int, an<LuaObj>, const KeyEvent&,]=])
  trime_replace_dependency_cmake(
    "${lua_gears_source}"
    [=[LuaProcessor::~LuaProcessor() {
  if (fini_) {]=]
    [=[LuaProcessor::~LuaProcessor() {
  if (!trime::IsPrivacyModeEnabled() && initialized_ && fini_) {]=])

  # Publish only changed content so repeated configuration stays incremental.
  configure_file("${stage}" "${output}" COPYONLY)
  foreach(relative lua_gears.cc lua_gears.h modules.cc)
    configure_file("${stage_root}/${relative}" "${output_root}/${relative}"
                   COPYONLY)
  endforeach()
  get_target_property(sources rime-lua-objs SOURCES)
  foreach(adapted_source types lua_gears modules)
    set(matches 0)
    foreach(source IN LISTS sources)
      if(source MATCHES "(^|/)src/${adapted_source}\\.cc$")
        list(REMOVE_ITEM sources "${source}")
        math(EXPR matches "${matches} + 1")
      endif()
    endforeach()
    if(NOT matches EQUAL 1)
      message(FATAL_ERROR
              "Re-audit Rime Lua source registration: src/${adapted_source}.cc")
    endif()
    list(APPEND sources "${output_root}/${adapted_source}.cc")
  endforeach()
  set_property(TARGET rime-lua-objs PROPERTY SOURCES "${sources}")
  target_include_directories(rime-lua-objs BEFORE PRIVATE "${output_root}")
  target_include_directories(rime-lua-objs PRIVATE
                             "${CMAKE_SOURCE_DIR}/librime-lua/src"
                             "${CMAKE_SOURCE_DIR}/librime_jni")

  if(ANDROID
     AND ANDROID_PLATFORM_LEVEL LESS 24
     AND ANDROID_ABI MATCHES "^(armeabi-v7a|x86)$")
    target_compile_definitions(rime-lua-objs PRIVATE _FILE_OFFSET_BITS=32)
  endif()
endfunction()
