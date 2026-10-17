// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2023, RIME Developers
// Derived from librime-predict; Trime modifications retain the BSD license.
// See LICENSE in this directory for the conditions and disclaimer.
// Trime overlay for the pinned librime-predict engine.
#ifndef RIME_PREDICT_ENGINE_H_
#define RIME_PREDICT_ENGINE_H_

#include "predict_db.h"
#include <rime/component.h>
#include <rime/dict/db_pool.h>

namespace rime {

class Context;
struct Segment;
struct Ticket;
class Translation;

class PredictEngine : public Class<PredictEngine, const Ticket&> {
 public:
  PredictEngine(an<PredictDb> db, int max_iterations, int max_candidates);
  virtual ~PredictEngine();

  bool Predict(Context* ctx, const string& context_query);
  void Clear();
  void CreatePredictSegment(Context* ctx) const;
  an<Translation> Translate(const Segment& segment) const;

  int max_iterations() const { return max_iterations_; }
  int max_candidates() const { return max_candidates_; }
  const string& query() const { return query_; }
  int num_candidates() const { return candidates_ ? candidates_->size : 0; }
  string candidate(size_t i) const {
    return candidates_ && i < candidates_->size && i < static_cast<size_t>(max_candidates_)
               ? db_->GetEntryText(candidates_->at[i]) : string();
  }

 private:
  an<PredictDb> db_;
  int max_iterations_;  // prediction times limit
  int max_candidates_;  // bounded prediction candidate count limit
  string query_;        // cache last query
  const predict::Candidates* candidates_ = nullptr;  // cache last result
};

class PredictEngineComponent : public PredictEngine::Component {
 public:
  PredictEngineComponent();
  virtual ~PredictEngineComponent();

  PredictEngine* Create(const Ticket& ticket) override;

  an<PredictEngine> GetInstance(const Ticket& ticket);

 protected:
  map<string, weak<PredictEngine>> predict_engine_by_schema_id;
  DbPool<PredictDb> db_pool_;
};

}  // namespace rime

#endif  // RIME_PREDICT_ENGINE_H_
