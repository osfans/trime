// SPDX-License-Identifier: GPL-3.0-or-later
#include "trime_privacy_guard.h"

#include <atomic>

namespace trime {
namespace {

std::atomic_bool privacy_mode{false};
std::atomic_uint privacy_transition_depth{0};

}  // namespace

bool IsPrivacyModeEnabled() noexcept {
  return privacy_mode.load(std::memory_order_acquire);
}

void SetPrivacyModeEnabled(bool enabled) noexcept {
  privacy_mode.store(enabled, std::memory_order_release);
}

bool IsPrivacyTransitionBoundary() noexcept {
  return privacy_transition_depth.load(std::memory_order_acquire) != 0;
}

PrivacyTransitionBoundary::PrivacyTransitionBoundary() noexcept {
  privacy_transition_depth.fetch_add(1, std::memory_order_acq_rel);
}

PrivacyTransitionBoundary::~PrivacyTransitionBoundary() noexcept {
  privacy_transition_depth.fetch_sub(1, std::memory_order_acq_rel);
}

}  // namespace trime
