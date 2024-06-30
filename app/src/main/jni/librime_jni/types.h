#pragma once

#include <rime_levers_api.h>

#include <string_view>

static RimeLeversApi *rime_get_levers_api() {
  return (RimeLeversApi *)(RimeFindModule("levers")->get_api());
}

template <typename API>
class SchemaList {
 public:
  SchemaList(API *api) : api_(api) {}
  ~SchemaList() {
    if constexpr (std::is_same_v<API, RimeApi>) {
      ((RimeApi *)api_)->free_schema_list(&list_);
    } else if constexpr (std::is_same_v<API, RimeLeversApi>) {
      ((RimeLeversApi *)api_)->schema_list_destroy(&list_);
    }
  }

  RimeSchemaList &operator*() { return list_; }
  RimeSchemaList *operator&() { return &list_; }
  operator RimeSchemaList() { return list_; }

 private:
  API *api_;
  RimeSchemaList list_;
};

class Config {
 public:
  using Action = std::function<void(RimeApi *, RimeConfig *)>;
  Config(std::string_view id, bool user = false) {
    if (user) {
      api_->user_config_open(id.data(), config_);
    } else {
      api_->config_open(id.data(), config_);
    }
  };
  ~Config() { api_->config_close(config_); }

  void use(const Action &action) { action(api_, config_); }

  inline bool any(
      std::string_view key,
      std::function<bool(RimeApi *, RimeConfig *, RimeConfigIterator *)> pred) {
    RimeConfigIterator iter;
    if (!api_->config_begin_list(&iter, config_, key.data())) return false;
    while (api_->config_next(&iter)) {
      if (pred(api_, config_, &iter)) {
        api_->config_end(&iter);
        return true;
      }
    }
    api_->config_end(&iter);
    return false;
  }

 private:
  RimeApi *api_ = rime_get_api();
  RimeConfig *config_;
};

class CustomConfig {
 public:
  using Action = std::function<void(RimeLeversApi *, RimeCustomSettings *)>;

  CustomConfig(std::string_view configId)
      : CustomConfig(rime_get_levers_api()->custom_settings_init(
            configId.data(), "rime.trime")) {}
  CustomConfig()
      : CustomConfig((RimeCustomSettings *)(rime_get_levers_api()
                                                ->switcher_settings_init())) {}

  ~CustomConfig() {
    api_->save_settings((RimeCustomSettings *)settings_);
    api_->custom_settings_destroy((RimeCustomSettings *)settings_);
  }

  void use(const Action &action) { action(api_, settings_); }

 private:
  CustomConfig(RimeCustomSettings *settings) : settings_(settings) {
    api_->load_settings((RimeCustomSettings *)settings_);
  };

  RimeLeversApi *api_ = rime_get_levers_api();
  RimeCustomSettings *settings_;
};
