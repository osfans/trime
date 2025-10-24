#pragma once

#include <rime_api.h>

#include <stdexcept>

class SessionHolder {
 public:
  SessionHolder() {
    auto *api = rime_get_api();
    id_ = api->create_session();

    if (!id_) {
      throw std::runtime_error("Failed to create session");
    }
  }

  SessionHolder(SessionHolder &&) = delete;

  ~SessionHolder() {
    if (id_) {
      rime_get_api()->destroy_session(id_);
    }
  }

  RimeSessionId id() const { return id_; }

 private:
  RimeSessionId id_ = 0;
};
