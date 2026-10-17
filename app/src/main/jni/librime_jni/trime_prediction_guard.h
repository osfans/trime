// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

namespace trime {

// The frontend request is process-wide, like the privacy gate. A schema switch
// alone must never enable prediction while the frontend has disabled it.
bool IsPredictionRequested() noexcept;
void SetPredictionRequested(bool enabled) noexcept;
bool IsPredictionTransitionBoundary() noexcept;

class PredictionTransitionBoundary final {
 public:
  PredictionTransitionBoundary() noexcept;
  ~PredictionTransitionBoundary() noexcept;
  PredictionTransitionBoundary(const PredictionTransitionBoundary&) = delete;
  PredictionTransitionBoundary& operator=(const PredictionTransitionBoundary&) =
      delete;
};

}  // namespace trime
