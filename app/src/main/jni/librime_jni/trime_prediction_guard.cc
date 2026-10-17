// SPDX-License-Identifier: GPL-3.0-or-later
#include "trime_prediction_guard.h"

#include <atomic>

namespace trime {
namespace {
std::atomic_bool prediction_requested{false};
std::atomic_uint prediction_transition_depth{0};
}  // namespace

bool IsPredictionRequested() noexcept {
  return prediction_requested.load(std::memory_order_acquire);
}

void SetPredictionRequested(bool enabled) noexcept {
  prediction_requested.store(enabled, std::memory_order_release);
}

bool IsPredictionTransitionBoundary() noexcept {
  return prediction_transition_depth.load(std::memory_order_acquire) != 0;
}

PredictionTransitionBoundary::PredictionTransitionBoundary() noexcept {
  prediction_transition_depth.fetch_add(1, std::memory_order_acq_rel);
}

PredictionTransitionBoundary::~PredictionTransitionBoundary() noexcept {
  prediction_transition_depth.fetch_sub(1, std::memory_order_acq_rel);
}

}  // namespace trime
