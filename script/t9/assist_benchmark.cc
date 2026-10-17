// SPDX-License-Identifier: GPL-3.0-or-later
#include <rime/dict/dictionary.h>
#include <rime/schema.h>
#include <rime/ticket.h>
#include <rime_api.h>
#include <algorithm>
#include <chrono>
#include <iostream>
#include "../../app/src/main/jni/librime_jni/t9_assist.h"

// Build-only measurement using a deployed system dictionary, never a user DB.
int main(int argc, char** argv) {
  if (argc != 3) return 2;
  auto api = rime_get_api();
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = argv[1];
  traits.user_data_dir = argv[2];
  traits.app_name = "rime.trime_t05_index_benchmark";
  traits.log_dir = "";
  traits.min_log_level = 2;
  api->setup(&traits);
  api->initialize(&traits);
  int result = 0;
  {
    rime::Schema schema("luna_pinyin_t9");
    std::unique_ptr<rime::Dictionary> dictionary(
        rime::Dictionary::Require("dictionary")->Create(rime::Ticket(&schema, "translator")));
    if (!dictionary || !dictionary->Load()) return 3;
    rime::Script syllables;
    auto count = dictionary->primary_table()->metadata()->num_syllables;
    if (count > 4096) return 4;
    for (uint32_t id = 0; id < count; ++id) {
      auto spelling = dictionary->primary_table()->GetSyllableById(id);
      if (spelling.empty() || spelling.size() > 6 || spelling.find_first_not_of("abcdefghijklmnopqrstuvwxyz") != std::string::npos) continue;
      int value;
      if (!dictionary->prism()->GetValue("~" + spelling + "~", &value)) continue;
      bool valid = true, found = false;
      for (auto access = dictionary->prism()->QuerySpelling(value); !access.exhausted(); access.Next()) {
        found = true;
        valid &= access.syllable_id() == static_cast<int>(id) && !access.properties().is_correction;
      }
      if (valid && found) syllables.AddSyllable(spelling);
    }
    std::cout << "SYLLABLES " << syllables.size() << '\n';
    for (int run = 0; run < 5; ++run) {
      trime::T9Assist index;
      auto start = std::chrono::steady_clock::now();
      bool built = index.Build(schema.config(), syllables, 511);
      auto elapsed = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count();
      if (!built) { result = 5; break; }
      std::cout << "BUILD_MS " << run << ' ' << elapsed << '\n';
      // Deterministic full result, not a candidate-comment protocol.
      if (run == 0)
        for (const auto* input : {"54", "64426", "9426", "96", "64664"})
          for (const auto& alternative : index.Find(input, 511))
            std::cout << "CHOICE " << input << ' ' << alternative.length << ' ' << alternative.spelling << ' ' << alternative.sources << '\n';
    }
  }
  api->finalize();
  return result;
}
