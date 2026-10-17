// SPDX-License-Identifier: GPL-3.0-or-later
#include <rime/context.h>
#include <rime/dict/dictionary.h>
#include <rime/engine.h>
#include <rime/filter.h>
#include <rime/gear/translator_commons.h>
#include <rime/registry.h>
#include <rime/schema.h>
#include <rime/service.h>
#include <rime/translation.h>
#include <rime_api.h>

#include <iostream>
#include <stdexcept>

using namespace rime;

static void require(bool value, const char* message) {
  if (!value) throw std::runtime_error(message);
}

// Deliberately small plugin prototype: real Phrase codes, never comments.
class ConstraintFilter : public Filter {
 public:
  explicit ConstraintFilter(const Ticket& ticket) : Filter(ticket) {
    dict_.reset(Dictionary::Require("dictionary")
                    ->Create(Ticket(ticket.schema, "translator")));
    require(dict_ && dict_->Load(), "Cannot load fixture dictionary");
  }
  an<Translation> Apply(an<Translation> source, CandidateList*) override {
    auto wanted = engine_->context()->get_property("_prototype_syllable");
    if (wanted.empty()) return source;
    auto result = New<FifoTranslation>();
    while (!source->exhausted()) {
      auto candidate = source->Peek();
      auto phrase = As<Phrase>(Candidate::GetGenuineCandidate(candidate));
      if (phrase && !phrase->code().empty() &&
          dict_->primary_table()->GetSyllableById(phrase->code()[0]) == wanted)
        result->Append(candidate);
      source->Next();
    }
    return result;
  }

 private:
  the<Dictionary> dict_;
};

int main(int argc, char** argv) {
  require(argc == 3, "Usage: experiment SHARED ISOLATED_USER");
  auto api = rime_get_api();
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = argv[1];
  traits.user_data_dir = argv[2];
  traits.app_name = "rime.trime_t9_test";
  traits.log_dir = "";
  traits.min_log_level = 2;
  api->setup(&traits);
  api->initialize(&traits);
  Registry::instance().Register("prototype_constraint",
                                new Component<ConstraintFilter>);
  api->start_maintenance(true);
  api->join_maintenance_thread();
  auto id = api->create_session();
  require(api->select_schema(id, "luna_pinyin_t9"), "Schema deployment failed");
  auto schema = new Schema("luna_pinyin_t9");
  auto filters = New<ConfigList>();
  filters->Append(New<ConfigValue>("prototype_constraint"));
  schema->config()->SetItem("engine/filters", filters);
  Service::instance().GetSession(id)->ApplySchema(schema);
  auto ctx = Service::instance().GetSession(id)->context();
  auto show = [&](const string& input, const string& lock) {
    ctx->Clear();
    ctx->set_property("_prototype_syllable", lock);
    ctx->set_input(input);
    RIME_STRUCT(RimeCommit, commit);
    require(!api->get_commit(id, &commit), "Syllable selection committed text");
    std::cout << "input=" << ctx->input() << " constraint=" << lock
              << " candidates=";
    RimeCandidateListIterator it{};
    if (api->candidate_list_begin(id, &it)) {
      int count = 0;
      while (count++ < 20 && api->candidate_list_next(&it))
        std::cout << it.candidate.text << "[" << it.candidate.comment << "] ";
      api->candidate_list_end(&it);
    }
    std::cout << '\n';
    require(ctx->HasMenu(), "Test input has no candidates");
  };
  show("64", "");
  show("64426", "");
  show("ni'426", "");
  show("mi'426", "");
  show("ni'", "");
  show("64426", "ni");
  show("6442662", "mi");
  show("mi'42662", "");
  show("64'gao'62", "");
  show("xian'", "");
  show("~xian~", "");
  show("~ni~426", "");
  ctx->Clear();
  api->destroy_session(id);
  api->finalize();
}
