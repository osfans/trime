// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2023, RIME Developers
// Derived from librime-predict; Trime modifications retain the BSD license.
// See LICENSE in this directory for the conditions and disclaimer.
#include "predict_engine.h"

#include <rime/candidate.h>
#include <rime/context.h>
#include <rime/dict/db_pool_impl.h>
#include <rime/schema.h>
#include <rime/segmentation.h>
#include <rime/service.h>
#include <rime/ticket.h>
#include <rime/translation.h>

#include "prediction_policy.h"
#include "trime_prediction_guard.h"
#include "trime_privacy_guard.h"

namespace rime {
namespace {
const ResourceType kPredictDbResourceType = {"predict_db", "", ""};
}  // namespace

PredictEngine::PredictEngine(an<PredictDb> db, int max_iterations,
                             int max_candidates)
    : db_(db),
      max_iterations_(trime::prediction::IterationLimit(max_iterations)),
      max_candidates_(trime::prediction::CandidateLimit(max_candidates)) {}
PredictEngine::~PredictEngine() = default;

bool PredictEngine::Predict(Context* ctx, const string& context_query) {
  if (!ctx || !db_ || !trime::IsPredictionRequested() ||
      trime::IsPrivacyModeEnabled() || !ctx->get_option("prediction") ||
      ctx->get_option("ascii_mode") || context_query.empty() ||
      context_query.size() > trime::prediction::kMaximumQueryBytes) {
    Clear();
    return false;
  }
  // Default-off prediction does not read, hash or map a model. Loading begins
  // only for an enabled, fresh word commit and is idempotent thereafter.
  if (db_->Load()) {
    if (const auto* candidates = db_->Lookup(context_query)) {
      query_ = context_query;
      candidates_ = candidates;
      return true;
    }
  }
  Clear();
  return false;
}

void PredictEngine::Clear() {
  query_.clear();
  candidates_ = nullptr;
}

void PredictEngine::CreatePredictSegment(Context* ctx) const {
  if (!ctx || !candidates_ || candidates_->size == 0) return;
  const int end = int(ctx->input().length());
  Segment segment(end, end);
  segment.tags.insert("prediction");
  segment.tags.insert("placeholder");
  ctx->composition().AddSegment(segment);
  ctx->composition().back().tags.erase("raw");
}

an<Translation> PredictEngine::Translate(const Segment& segment) const {
  if (!candidates_ || !db_ || !trime::IsPredictionRequested() ||
      trime::IsPrivacyModeEnabled())
    return nullptr;
  auto translation = New<FifoTranslation>();
  const auto count = std::min<std::size_t>(candidates_->size, max_candidates_);
  for (std::size_t i = 0; i < count; ++i) {
    const auto text = db_->GetEntryText(candidates_->at[i]);
    if (!text.empty())
      translation->Append(
          New<SimpleCandidate>("prediction", segment.end, segment.end, text));
  }
  return translation;
}

PredictEngineComponent::PredictEngineComponent()
    : db_pool_(the<ResourceResolver>(
          Service::instance().CreateResourceResolver(kPredictDbResourceType))) {
}
PredictEngineComponent::~PredictEngineComponent() = default;

PredictEngine* PredictEngineComponent::Create(const Ticket& ticket) {
  string db_name = "trime_predict.db";
  int max_candidates = trime::prediction::kDefaultCandidates;
  int max_iterations = trime::prediction::kDefaultIterations;
  if (auto* schema = ticket.schema) {
    auto* config = schema->config();
    config->GetString("predictor/db", &db_name);
    config->GetInt("predictor/max_candidates", &max_candidates);
    config->GetInt("predictor/max_iterations", &max_iterations);
  }
  if (auto db = db_pool_.GetDb(db_name)) {
    return new PredictEngine(db, max_iterations, max_candidates);
  }
  // Prediction is optional; missing or unsupported model data never prevents
  // the basic translators and processors from loading.
  return nullptr;
}

an<PredictEngine> PredictEngineComponent::GetInstance(const Ticket& ticket) {
  if (Schema* schema = ticket.schema) {
    auto found = predict_engine_by_schema_id.find(schema->schema_id());
    if (found != predict_engine_by_schema_id.end()) {
      if (auto instance = found->second.lock()) return instance;
    }
    an<PredictEngine> instance{Create(ticket)};
    if (instance) predict_engine_by_schema_id[schema->schema_id()] = instance;
    return instance;
  }
  return nullptr;
}
}  // namespace rime
