# SPDX-License-Identifier: GPL-3.0-or-later

# Keep the pinned submodule untouched. Missing optional inputs are not errors;
# malformed or required inputs must still fail and retain their diagnostics.
function(trime_adapt_rime_config)
  set(output "${CMAKE_BINARY_DIR}/trime-rime-config")
  set(root "${CMAKE_BINARY_DIR}/trime-rime-config-stage")
  set(files rime/config/config_data.h rime/config/config_data.cc
            rime/config/config_compiler.h rime/config/config_compiler.cc
            rime/config/config_component.cc rime/lever/deployment_tasks.cc)
  foreach(relative IN LISTS files)
    set(original "${CMAKE_SOURCE_DIR}/librime/src/${relative}")
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${original}")
    configure_file("${original}" "${root}/${relative}" COPYONLY)
  endforeach()

  trime_replace_dependency_cmake("${root}/rime/config/config_data.h"
    "  bool LoadFromFile(const path& file_path, ConfigCompiler* compiler);"
    "  bool LoadFromFile(const path& file_path, ConfigCompiler* compiler);\n  bool LoadFromFile(const path& file_path, ConfigCompiler* compiler, bool optional);")
  trime_replace_dependency_cmake("${root}/rime/config/config_data.cc"
    "bool ConfigData::LoadFromFile(const path& file_path, ConfigCompiler* compiler) {"
    "bool ConfigData::LoadFromFile(const path& file_path, ConfigCompiler* compiler) {\n  return LoadFromFile(file_path, compiler, false);\n}\n\nbool ConfigData::LoadFromFile(const path& file_path, ConfigCompiler* compiler, bool optional) {")
  trime_replace_dependency_cmake("${root}/rime/config/config_data.cc"
    "    if (!boost::ends_with(file_path.to_utf8_string(), \".custom.yaml\"))"
    "    if (!optional && !boost::ends_with(file_path.to_utf8_string(), \".custom.yaml\"))")

  trime_replace_dependency_cmake("${root}/rime/config/config_compiler.h"
    "  an<ConfigResource> Compile(const string& file_name);"
    "  an<ConfigResource> Compile(const string& file_name);\n  an<ConfigResource> Compile(const string& file_name, bool optional);")
  trime_replace_dependency_cmake("${root}/rime/config/config_compiler.cc"
    "an<ConfigResource> ConfigCompiler::Compile(const string& file_name) {"
    "an<ConfigResource> ConfigCompiler::Compile(const string& file_name) {\n  return Compile(file_name, false);\n}\n\nan<ConfigResource> ConfigCompiler::Compile(const string& file_name, bool optional) {")
  trime_replace_dependency_cmake("${root}/rime/config/config_compiler.cc"
    "      resource_resolver_->ResolvePath(resource_id), this);"
    "      resource_resolver_->ResolvePath(resource_id), this, optional);")
  trime_replace_dependency_cmake("${root}/rime/config/config_compiler.cc"
    "    resource = compiler->Compile(reference.resource_id);"
    "    resource = compiler->Compile(reference.resource_id, reference.optional);")
  trime_replace_dependency_cmake("${root}/rime/config/config_compiler.cc"
    "static bool MergeTree(an<ConfigItemRef> target, an<ConfigMap> map);"
    [=[static bool MergeTree(an<ConfigItemRef> target, an<ConfigMap> map);

static bool OptionalReferenceMissing(ConfigCompiler* compiler, const Reference& reference) {
  if (!reference.optional) return false;
  auto resource = compiler->GetCompiledResource(reference.resource_id);
  if (!resource) return false;
  if (!resource->loaded) return !std::filesystem::exists(resource->data->file_path());
  string path = resource->resource_id + ":";
  if (compiler->blocking(path)) return false;
  for (const auto& key : ConfigData::SplitPath(reference.local_path)) {
    path += "/" + key;
    if (compiler->blocking(path)) return false;
  }
  return !resource->data->Traverse(reference.local_path);
}]=])
  foreach(item included item)
    trime_replace_dependency_cmake("${root}/rime/config/config_compiler.cc"
      "  if (!${item}) {\n    return reference.optional;\n  }"
      "  if (!${item}) {\n    return OptionalReferenceMissing(compiler, reference);\n  }")
  endforeach()

  trime_replace_dependency_cmake("${root}/rime/config/config_component.cc"
    "  data->LoadFromFile(resource_resolver->ResolvePath(config_id), nullptr);"
    "  data->LoadFromFile(resource_resolver->ResolvePath(config_id), nullptr, auto_save_);")
  trime_replace_dependency_cmake("${root}/rime/config/config_component.cc"
    [=[  if (resource->loaded && !compiler.Link(resource)) {
    LOG(ERROR) << "error building config: " << config_id;
  }]=]
    [=[  if (!resource->loaded || !compiler.Link(resource)) {
    LOG(ERROR) << "error building config: " << config_id;
    resource->data->root.reset();
  }]=])

  trime_replace_dependency_cmake("${root}/rime/lever/deployment_tasks.cc"
    "  if (config.LoadFromFile(installation_info)) {"
    "  if (fs::exists(installation_info) && config.LoadFromFile(installation_info)) {")
  trime_replace_dependency_cmake("${root}/rime/lever/deployment_tasks.cc"
    [=[  LOG(INFO) << "updating workspace.";]=]
    [=[  LOG(INFO) << "updating workspace.";
  // An interrupted/failed deployment must remain retryable across processes,
  // including removal of a broken config within the same timestamp second.
  the<Config> user_config(Config::Require("user_config")->Create("user"));
  if (!user_config ||
      !user_config->SetInt("var/last_build_time", 0) ||
      !user_config->SaveToFile(deployer->user_data_dir / "user.yaml")) {
    return false;
  }]=])
  trime_replace_dependency_cmake("${root}/rime/lever/deployment_tasks.cc"
    [=[  the<Config> user_config(Config::Require("user_config")->Create("user"));
  // TODO: store as 64-bit number to avoid the year 2038 problem
  user_config->SetInt("var/last_build_time", (int)time(NULL));

  return failure == 0;]=]
    [=[  if (failure != 0) return false;
  // TODO: store as 64-bit number to avoid the year 2038 problem
  user_config->SetInt("var/last_build_time", (int)time(NULL));
  return user_config->SaveToFile(deployer->user_data_dir / "user.yaml");]=])
  trime_replace_dependency_cmake("${root}/rime/lever/deployment_tasks.cc"
    [=[    t.reset(new ConfigFileUpdate("default.yaml", "config_version"));
    t->Run(deployer);]=]
    [=[    t.reset(new ConfigFileUpdate("default.yaml", "config_version"));
    if (!t->Run(deployer)) return false;]=])
  trime_replace_dependency_cmake("${root}/rime/lever/deployment_tasks.cc"
    [=[  the<Config> config(Config::Require("config")->Create(file_name_));
  if (ConfigNeedsUpdate(config.get())) {]=]
    [=[  the<ResourceResolver> resolver(Service::instance().CreateDeployedResourceResolver(
      {"compiled_config", "", ".yaml"}));
  the<Config> config(new Config);
  if (fs::exists(resolver->ResolvePath(file_name_))) {
    config.reset(Config::Require("config")->Create(file_name_));
  }
  if (ConfigNeedsUpdate(config.get())) {]=])
  trime_replace_dependency_cmake("${root}/rime/lever/deployment_tasks.cc"
    [=[    config.reset(Config::Require("config_builder")->Create(file_name_));
  }
  return true;]=]
    [=[    config.reset(Config::Require("config_builder")->Create(file_name_));
  }
  return config && !config->IsNull("");]=])

  # Publish only changed content: reconfiguring must not touch public headers and
  # force a complete native rebuild on a low-memory workstation.
  foreach(relative IN LISTS files)
    configure_file("${root}/${relative}" "${output}/${relative}" COPYONLY)
  endforeach()
  set(root "${output}")
  get_target_property(sources rime-static SOURCES)
  foreach(relative IN LISTS files)
    if(relative MATCHES "\\.cc$")
      set(matches 0)
      foreach(source IN LISTS sources)
        if(source STREQUAL relative OR source STREQUAL "${CMAKE_SOURCE_DIR}/librime/src/${relative}")
          list(REMOVE_ITEM sources "${source}")
          math(EXPR matches "${matches} + 1")
        endif()
      endforeach()
      if(NOT matches EQUAL 1)
        message(FATAL_ERROR "Re-audit Rime source registration: ${relative}")
      endif()
      list(APPEND sources "${root}/${relative}")
    endif()
  endforeach()
  set_property(TARGET rime-static PROPERTY SOURCES "${sources}")
  target_include_directories(rime-static BEFORE PUBLIC "${root}")
endfunction()
