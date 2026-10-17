// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2023, RIME Developers
// Derived from librime-predict; Trime modifications retain the BSD license.
// See LICENSE in this directory for the conditions and disclaimer.
// Trime overlay for the pinned librime-predict processor.
#ifndef RIME_PREDICTOR_H_
#define RIME_PREDICTOR_H_

#include <rime/processor.h>

namespace rime {

class Context;
class PredictEngine;
class PredictEngineComponent;

class Predictor : public Processor {
 public:
  Predictor(const Ticket& ticket, an<PredictEngine> predict_engine);
  ~Predictor() override;
  ProcessResult ProcessKeyEvent(const KeyEvent& key_event) override;

 private:
  void OnContextUpdate(Context* ctx);
  void OnCommit(Context* ctx);
  void OnOptionUpdate(Context* ctx, const string& option);
  void Reset(Context* ctx, bool clear_prediction);

  bool self_updating_ = false;
  bool pending_commit_ = false;
  bool pending_prediction_ = false;
  int iteration_counter_ = 0;
  string pending_text_;
  an<PredictEngine> predict_engine_;
  connection commit_connection_;
  connection context_update_connection_;
  connection option_update_connection_;
};

class PredictorComponent : public Predictor::Component {
 public:
  explicit PredictorComponent(an<PredictEngineComponent> engine_factory);
  ~PredictorComponent() override;
  Predictor* Create(const Ticket& ticket) override;

 private:
  an<PredictEngineComponent> engine_factory_;
};

}  // namespace rime
#endif  // RIME_PREDICTOR_H_
