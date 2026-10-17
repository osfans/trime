// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2023, RIME Developers
// Derived from librime-predict; Trime modifications retain the BSD license.
// See LICENSE in this directory for the conditions and disclaimer.
#include "predict_translator.h"

#include <rime/context.h>
#include <rime/engine.h>
#include <rime/segmentation.h>
#include <rime/translation.h>

#include "predict_engine.h"
#include "trime_prediction_guard.h"
#include "trime_privacy_guard.h"

namespace rime {
PredictTranslator::PredictTranslator(const Ticket& ticket,
                                     an<PredictEngine> predict_engine)
    : Translator(ticket), predict_engine_(predict_engine) {}

an<Translation> PredictTranslator::Query(const string& input,
                                         const Segment& segment) {
  const auto* ctx = engine_ ? engine_->context() : nullptr;
  if (!predict_engine_ || !ctx || !trime::IsPredictionRequested() ||
      trime::IsPrivacyModeEnabled() || !ctx->get_option("prediction") ||
      ctx->get_option("ascii_mode") || !input.empty() ||
      predict_engine_->query().empty() || !segment.HasTag("prediction"))
    return nullptr;
  return predict_engine_->Translate(segment);
}

PredictTranslatorComponent::PredictTranslatorComponent(
    an<PredictEngineComponent> engine_factory)
    : engine_factory_(engine_factory) {}
PredictTranslatorComponent::~PredictTranslatorComponent() = default;
PredictTranslator* PredictTranslatorComponent::Create(const Ticket& ticket) {
  return new PredictTranslator(ticket, engine_factory_->GetInstance(ticket));
}
}  // namespace rime
