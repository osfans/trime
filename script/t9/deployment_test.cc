// SPDX-License-Identifier: GPL-3.0-or-later
#include <rime_api.h>

#include <atomic>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {
std::atomic_bool failed{false};
int checks = 0;

void check(bool value, const char* message) {
  if (!value) throw std::runtime_error(message);
  std::cout << "PASS " << ++checks << " " << message << std::endl;
}

void notification(void*, RimeSessionId, const char* type, const char* value) {
  if (std::strcmp(type, "deploy") == 0 && std::strcmp(value, "failure") == 0)
    failed = true;
}

int build_time(RimeApi* api) {
  RimeConfig config{};
  if (!api->user_config_open("user", &config))
    throw std::runtime_error("Cannot inspect deployment marker");
  int timestamp = -1;
  const bool found =
      api->config_get_int(&config, "var/last_build_time", &timestamp);
  api->config_close(&config);
  if (!found) throw std::runtime_error("Deployment marker missing");
  return timestamp;
}

bool valid_schema_list(RimeApi* api) {
  RimeSchemaList list{};
  if (!api->get_schema_list(&list)) return false;
  const bool valid = list.size == 1 &&
                     std::strcmp(list.list[0].schema_id, "luna_pinyin_t9") == 0;
  api->free_schema_list(&list);
  return valid;
}
}  // namespace

int main(int argc, char** argv) {
  if (argc != 3 && argc != 4) return 2;
  auto api = rime_get_api();
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = argv[1];
  traits.user_data_dir = argv[2];
  traits.app_name = "rime.trime_deployment_test";
  traits.log_dir = "";
  traits.min_log_level = 2;
  api->setup(&traits);
  bool initialized = false;
  auto initialize = [&] {
    if (initialized) api->finalize();
    api->initialize(&traits);
    initialized = true;
    api->set_notification_handler(notification, nullptr);
  };
  auto maintain = [&](bool full) {
    failed = false;
    const bool started = api->start_maintenance(full);
    api->join_maintenance_thread();
    return started;
  };
  const auto custom = std::filesystem::path(argv[2]) / "default.custom.yaml";
  auto write_custom = [&](const char* contents) {
    std::ofstream output(custom);
    output << contents;
    output.close();
    if (!output) throw std::runtime_error("Cannot write owned fixture");
  };
  try {
    initialize();
    if (argc == 4) {
      check(build_time(api) == 0, "failed marker survives a separate process");
      check(maintain(false), "new process retries an incomplete deployment");
      check(!failed && valid_schema_list(api),
            "new process restores the valid schema list");
      check(build_time(api) > 0,
            "new process publishes success only after recovery");
    } else {
      check(maintain(true) && !failed, "deploy small baseline fixture");
      check(build_time(api) > 0, "successful workspace publishes build time");
      write_custom(
          "patch:\n  schema_list:\n    - schema: __deployment_missing__\n");
      initialize();
      check(maintain(true) && failed,
            "missing selected schema reports deployment failure");
      check(build_time(api) == 0,
            "failed schema deployment never publishes success time");
      check(std::filesystem::remove(custom),
            "remove the owned broken schema patch");
      initialize();
      check(maintain(false),
            "retry does not skip rebuilding after a failed deployment");
      check(!failed && valid_schema_list(api),
            "retry does not retain the missing schema");
      check(build_time(api) > 0, "recovery publishes a successful marker");
      initialize();
      check(!maintain(false),
            "unchanged successful workspace does not redeploy");
      write_custom("patch: [unterminated\n");
      initialize();
      check(maintain(true) && failed,
            "malformed optional config fails the workspace");
      check(build_time(api) == 0,
            "early config failure also invalidates success time");
      check(std::filesystem::remove(custom),
            "remove the owned malformed patch before exit");
    }
    api->finalize();
    return 0;
  } catch (const std::exception& error) {
    std::cerr << "FAIL: " << error.what() << std::endl;
    if (initialized) api->finalize();
    return 1;
  }
}
