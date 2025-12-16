#include "proto.h"

#include <rime/component.h>
#include <rime/composition.h>
#include <rime/context.h>
#include <rime/menu.h>
#include <rime/schema.h>
#include <rime/service.h>
#include <rime_api.h>
#include <utf8.h>

using namespace rime;

void rime_commit_proto(RimeSessionId session_id, uintptr_t commit_builder) {
  an<Session> session(Service::instance().GetSession(session_id));
  if (!session) return;
  const string& commit_text(session->commit_text());
  if (!commit_text.empty()) {
    auto* commit = reinterpret_cast<rime::proto::Commit*>(commit_builder);
    commit->text = commit_text;
    session->ResetCommitText();
  }
}

void rime_context_proto(RimeSessionId session_id, uintptr_t context_builder) {
  an<Session> session = Service::instance().GetSession(session_id);
  if (!session) return;
  Context* ctx = session->context();
  if (!ctx) return;
  auto* context = reinterpret_cast<rime::proto::Context*>(context_builder);
  context->input = ctx->input();
  context->caretPos = static_cast<int>(ctx->caret_pos());
  if (ctx->IsComposing()) {
    auto& composition = context->composition;
    const Preedit& preedit = ctx->GetPreedit();
    const auto& text = preedit.text;
    composition.length = static_cast<int>(
        utf8::distance(text.c_str(), text.c_str() + text.length()));
    composition.preedit = text;
    composition.cursorPos = static_cast<int>(
        utf8::distance(text.c_str(), text.c_str() + preedit.caret_pos));
    composition.selStart = static_cast<int>(
        utf8::distance(text.c_str(), text.c_str() + preedit.sel_start));
    composition.selEnd = static_cast<int>(
        utf8::distance(text.c_str(), text.c_str() + preedit.sel_end));
    const auto& commit_text = ctx->GetCommitText();
    if (!commit_text.empty()) {
      composition.commitTextPreview = commit_text;
    }
  }
  if (ctx->HasMenu()) {
    auto& menu = context->menu;
    Segment& seg = ctx->composition().back();
    Schema* schema = session->schema();
    int page_size = schema ? schema->page_size() : 5;
    int selected_index = seg.selected_index;
    int page_number = selected_index / page_size;
    the<Page> page(seg.menu->CreatePage(page_size, page_number));
    if (page) {
      menu.pageSize = page_size;
      menu.pageNumber = page_number;
      menu.isLastPage = page->is_last_page;
      menu.highlightedCandidateIndex = selected_index % page_size;
      vector<string> labels;
      if (schema) {
        const auto& select_keys = schema->select_keys();
        if (!select_keys.empty()) {
          menu.selectKeys = select_keys;
        }
        Config* config = schema->config();
        auto src_labels = config->GetList("menu/alternative_select_labels");
        if (src_labels && (size_t)page_size <= src_labels->size()) {
          for (int i = 0; i < page_size; ++i) {
            if (an<ConfigValue> value = src_labels->GetValueAt(i)) {
              menu.selectLabels.emplace_back(value->str());
              labels.emplace_back(value->str());
            }
          }
        } else if (!select_keys.empty()) {
          for (const char key : select_keys) {
            labels.emplace_back(1, key);
            if (labels.size() >= page_size) break;
          }
        }
      }
      int num_candidates = page->candidates.size();
      auto& dest_candidates = menu.candidates;
      dest_candidates.reserve(num_candidates);
      int index = 0;
      for (const an<Candidate>& src : page->candidates) {
        dest_candidates.emplace_back();
        dest_candidates.back().text = src->text();
        const auto& comment = src->comment();
        if (!comment.empty()) {
          dest_candidates.back().comment = comment;
        }
        const auto& label =
            index < labels.size() ? labels[index] : std::to_string(index + 1);
        dest_candidates.back().label = label;
        ++index;
      }
    }
  }
}

void rime_status_proto(RimeSessionId session_id, uintptr_t status_builder) {
  an<Session> session(Service::instance().GetSession(session_id));
  if (!session) return;
  Schema* schema = session->schema();
  Context* ctx = session->context();
  if (!schema || !ctx) return;
  auto* status = reinterpret_cast<rime::proto::Status*>(status_builder);
  status->schemaId = schema->schema_id();
  status->schemaName = schema->schema_name();
  status->isDisabled = Service::instance().disabled();
  status->isComposing = ctx->IsComposing();
  status->isAsciiMode = ctx->get_option("ascii_mode");
  status->isFullShape = ctx->get_option("full_shape");
  status->isSimplified = ctx->get_option("simplification");
  status->isTraditional = ctx->get_option("traditional");
  status->isAsciiPunct = ctx->get_option("ascii_punct");
}

size_t rime_get_highlighted_candidate_index(RimeSessionId session_id) {
  an<Session> session(Service::instance().GetSession(session_id));
  if (!session) return 0;
  Context* ctx = session->context();
  if (!ctx || !ctx->HasMenu()) return 0;
  auto& seg(ctx->composition().back());
  return seg.selected_index;
}
