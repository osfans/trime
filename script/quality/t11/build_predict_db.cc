// SPDX-License-Identifier: GPL-3.0-or-later
// Host-only reproducibility/lookup check against the pinned predict_db.cc.
#include <cmath>
#include <iostream>
#include <sstream>
#include <string>
#include "predict_db.h"

namespace {
bool ReadData(rime::predict::RawData* data) {
  std::string line;
  size_t rows = 0;
  while (std::getline(std::cin, line)) {
    std::istringstream input(line);
    std::string query, text, extra;
    double weight = 0;
    if (!(input >> query >> text >> weight) || input >> extra ||
        query.empty() || text.empty() || !std::isfinite(weight) || weight <= 0 ||
        ++rows > 128) {
      return false;
    }
    auto& entries = (*data)[query];
    if (entries.size() >= 32) return false;
    for (const auto& entry : entries) {
      if (entry.text == text) return false;
    }
    entries.push_back({text, weight});
  }
  return !data->empty() && std::cin.eof();
}

bool Verify(rime::PredictDb* db, const rime::predict::RawData& data) {
  if (!db->Load()) return false;
  size_t checked = 0;
  for (const auto& [query, entries] : data) {
    const auto* candidates = db->Lookup(query);
    if (!candidates || candidates->size != entries.size()) return false;
    for (size_t i = 0; i < entries.size(); ++i) {
      if (db->GetEntryText(candidates->at[i]) != entries[i].text ||
          candidates->at[i].weight != static_cast<float>(entries[i].weight)) {
        return false;
      }
      ++checked;
    }
  }
  if (db->Lookup("$") || db->Lookup("不存在的上下文")) return false;
  std::cout << "verified " << data.size() << " queries / " << checked
            << " next words\n";
  return true;
}
}  // namespace

int main(int argc, char* argv[]) {
  if (argc != 3 || (std::string(argv[1]) != "--build" &&
                    std::string(argv[1]) != "--verify")) return 2;
  google::InitGoogleLogging(argv[0]);
  FLAGS_logtostderr = true;
  rime::predict::RawData data;
  if (!ReadData(&data)) return 2;
  rime::PredictDb db{rime::path{argv[2]}};
  if (std::string(argv[1]) == "--build" &&
      (!db.Build(data) || !db.Save())) return 1;
  return Verify(&db, data) ? 0 : 1;
}
