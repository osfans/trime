// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2023, RIME Developers
// Derived from librime-predict; Trime modifications retain the BSD license.
// See LICENSE in this directory for the conditions and disclaimer.
// Only a new commit can start next-word prediction. Clear/update notifications
// never replay commit_history, including editor and option transitions.
#include "predictor.h"

#include <rime/candidate.h>
#include <rime/context.h>
#include <rime/engine.h>
#include <rime/key_event.h>
#include <rime/segmentation.h>

#include "predict_engine.h"
#include "prediction_key_policy.h"
#include "prediction_policy.h"
#include "trime_prediction_guard.h"
#include "trime_privacy_guard.h"

namespace rime {
namespace {
bool HasPrediction(Context* ctx) {
  return ctx && !ctx->composition().empty() &&
         ctx->composition().back().HasTag("prediction");
}

bool PredictionEnabled(Context* ctx) {
  return ctx && trime::IsPredictionRequested() &&
         !trime::IsPrivacyModeEnabled() && ctx->get_option("prediction") &&
         !ctx->get_option("ascii_mode");
}

}  // namespace

Predictor::Predictor(const Ticket& ticket, an<PredictEngine> predict_engine)
    : Processor(ticket), predict_engine_(predict_engine) {
  auto* context = engine_->context();
  commit_connection_ = context->commit_notifier().connect(
      [this](Context* ctx) { OnCommit(ctx); });
  context_update_connection_ = context->update_notifier().connect(
      [this](Context* ctx) { OnContextUpdate(ctx); });
  option_update_connection_ = context->option_update_notifier().connect(
      [this](Context* ctx, const string& option) {
        OnOptionUpdate(ctx, option);
      });
}

Predictor::~Predictor() {
  commit_connection_.disconnect();
  context_update_connection_.disconnect();
  option_update_connection_.disconnect();
}

void Predictor::Reset(Context* ctx, bool clear_prediction) {
  if (predict_engine_) predict_engine_->Clear();
  pending_commit_ = false;
  pending_prediction_ = false;
  pending_text_.clear();
  iteration_counter_ = 0;
  if (clear_prediction && HasPrediction(ctx)) {
    self_updating_ = true;
    ctx->Clear();
    self_updating_ = false;
  }
}

ProcessResult Predictor::ProcessKeyEvent(const KeyEvent& key_event) {
  if (!engine_ || key_event.release()) return kNoop;
  auto* ctx = engine_->context();
  if (!PredictionEnabled(ctx)) {
    Reset(ctx, true);
    return kNoop;
  }
  // Navigation may highlight a candidate; only the frontend candidate click
  // selects it. Every typing/editing key proceeds after removing prediction.
  if (HasPrediction(ctx) && trime::prediction::ShouldDismissForKey(key_event)) {
    Reset(ctx, true);
  }
  return kNoop;
}

void Predictor::OnCommit(Context* ctx) {
  if (!predict_engine_ || !PredictionEnabled(ctx) ||
      trime::IsPrivacyTransitionBoundary() ||
      trime::IsPredictionTransitionBoundary()) {
    Reset(ctx, false);
    return;
  }
  const auto candidate = ctx->GetSelectedCandidate();
  const auto genuine = Candidate::GetGenuineCandidate(candidate);
  // simplifier/uniquifier wrap candidates and change type(). The prediction
  // segment retains its identity across those filters and enforces the cap.
  const bool predicted = HasPrediction(ctx);
  const string type = predicted ? "prediction"
                      : genuine ? genuine->type()
                                : "raw";
  const string text = ctx->GetCommitText();
  if (!trime::prediction::IsPredictableCommit(type, text)) {
    Reset(ctx, false);
    return;
  }
  pending_commit_ = true;
  pending_prediction_ = predicted;
  pending_text_ = text;
}

void Predictor::OnOptionUpdate(Context* ctx, const string& option) {
  if (option == "ascii_mode") {
    Reset(ctx, true);
    ctx->commit_history().clear();
    ctx->set_option("prediction", trime::IsPredictionRequested() &&
                                      !trime::IsPrivacyModeEnabled() &&
                                      !ctx->get_option("ascii_mode"));
    ctx->commit_history().clear();
    return;
  }
  if (option == "prediction" || option == "trime_privacy_mode") {
    Reset(ctx, true);
  }
}

void Predictor::OnContextUpdate(Context* ctx) {
  if (self_updating_ || !ctx) return;
  if (trime::IsPrivacyTransitionBoundary() ||
      trime::IsPredictionTransitionBoundary() || !PredictionEnabled(ctx)) {
    Reset(ctx, true);
    return;
  }
  if (!ctx->composition().empty()) return;
  if (!predict_engine_ || !pending_commit_) {
    Reset(ctx, false);
    return;
  }
  const string query = std::move(pending_text_);
  const bool was_prediction = pending_prediction_;
  pending_commit_ = false;
  pending_prediction_ = false;
  if (was_prediction) {
    if (++iteration_counter_ >= predict_engine_->max_iterations()) {
      Reset(ctx, false);
      return;
    }
  } else {
    iteration_counter_ = 0;
  }
  if (predict_engine_->Predict(ctx, query)) {
    predict_engine_->CreatePredictSegment(ctx);
    self_updating_ = true;
    ctx->update_notifier()(ctx);
    self_updating_ = false;
  }
}

PredictorComponent::PredictorComponent(
    an<PredictEngineComponent> engine_factory)
    : engine_factory_(engine_factory) {}
PredictorComponent::~PredictorComponent() = default;
Predictor* PredictorComponent::Create(const Ticket& ticket) {
  return new Predictor(ticket, engine_factory_->GetInstance(ticket));
}
}  // namespace rime
