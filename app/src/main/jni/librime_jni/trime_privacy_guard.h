// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

namespace trime {

// Process-wide because Trime owns exactly one native Rime session and all
// session work is serialized by RimeDispatcher. Atomic access also protects
// plugin callbacks which may run outside the Java caller's stack.
bool IsPrivacyModeEnabled() noexcept;
void SetPrivacyModeEnabled(bool enabled) noexcept;

// True only while setPrivacyMode is clearing the context shared by two editor
// sessions. Components use this narrower signal to erase cross-editor caches
// without disabling their normal behavior for the duration of a private input.
bool IsPrivacyTransitionBoundary() noexcept;

class PrivacyTransitionBoundary final {
 public:
  PrivacyTransitionBoundary() noexcept;
  ~PrivacyTransitionBoundary() noexcept;

  PrivacyTransitionBoundary(const PrivacyTransitionBoundary&) = delete;
  PrivacyTransitionBoundary& operator=(const PrivacyTransitionBoundary&) = delete;
};

}  // namespace trime
