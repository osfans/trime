// SPDX-License-Identifier: GPL-3.0-or-later
// The included bridge is generated from the actual JNI and CMake method bodies.
// Only session lookup is adapted; real librime Engine/Context/Menu callbacks run.
#pragma once
#include "ranking_boundary_bridge.h"

inline void CheckOrdinaryRankingPredictionBoundaries() {
  RankingSessionFixture session;
  auto* context = session.context();
  context->set_input("abc");
  context->composition().clear();
  rime::Segment segment(0, 3);
  segment.status = rime::Segment::kGuess;
  auto original = rime::New<rime::Menu>();
  auto translation = rime::New<rime::FifoTranslation>();
  for (const auto& text : {"alpha", "beta", "gamma"})
    translation->Append(rime::New<rime::SimpleCandidate>("phrase", 0, 3, text));
  original->AddTranslation(translation);
  segment.menu = original;
  context->composition().AddSegment(segment);
  const auto alpha = original->GetCandidateAt(0);
  const auto beta = original->GetCandidateAt(1);
  const auto gamma = original->GetCandidateAt(2);
  RankingBridgeFixture bridge(&session);
  bridge.setCandidatePins({session.schema()->schema_id()}, {"abc"}, {"gamma"});
  bridge.applyCandidatePins();
  auto pinned = context->composition().back().menu;
  Check(pinned != original && pinned->GetCandidateAt(0) == gamma,
        "ordinary ranking fixture did not pin the actual candidate");
  int changed_menus = 0;
  for (int index = 0; index < 100; ++index) {
    bridge.clearPredictionContext();
    bridge.applyCandidatePins();  // getRimeResponse's actual production hook
    changed_menus += context->composition().back().menu != pinned;
    Check(context->input() == "abc" && !HasPrediction(context),
          "prediction context clear changed ordinary raw input");
  }
  bridge.setCandidatePins({}, {}, {});
  if (changed_menus)
    std::cerr << "ordinary pinned menu rewrapped on " << changed_menus
              << " of 100 clear/response transitions\n";
  Check(context->composition().back().menu == original &&
            original->GetCandidateAt(0) == alpha &&
            original->GetCandidateAt(1) == beta &&
            original->GetCandidateAt(2) == gamma,
        "reset ranking did not restore the actual original menu and order");
  Check(changed_menus == 0, "prediction context clear rewrapped ordinary ranking");
  // Preferences refresh normal nonconfirmed composition through the actual
  // ConcreteEngine option callback. They may legitimately rebuild its menu;
  // the input survives and no next-word result is submitted or replayed.
  for (int index = 0; index < 100; ++index) {
    bridge.setPredictionEnabled((index % 2) == 0);
    bridge.applyCandidatePins();
    Check(context->input() == "abc" &&
              !HasPrediction(context),
          "prediction preference transition changed ordinary input");
  }
  bridge.setPredictionEnabled(false);
  bridge.setCandidatePins({}, {}, {});
  Check(context->input() == "abc", "prediction preference boundary lost ordinary input");
}
