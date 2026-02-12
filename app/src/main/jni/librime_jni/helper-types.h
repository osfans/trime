#pragma once

#include <rime_api.h>
#include <utf8.h>

#include <cstring>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

class SchemaItem {
 public:
  std::string schemaId;
  std::string name;

  explicit SchemaItem(const RimeSchemaListItem& item)
      : schemaId(item.schema_id), name(item.name ? item.name : "") {}

  static std::vector<SchemaItem> fromCList(const RimeSchemaList& list) {
    std::vector<SchemaItem> result;
    result.reserve(list.size);
    for (int i = 0; i < list.size; ++i) {
      const SchemaItem item{list.list[i]};
      result.emplace_back(item);
    }
    return std::move(result);
  }
};

class CandidateItem {
 public:
  std::string text;
  std::string comment;

  explicit CandidateItem(const RimeCandidate& candidate)
      : text(candidate.text),
        comment(candidate.comment ? candidate.comment : "") {}
};

using CandidateList = std::vector<CandidateItem>;

class CommitProto {
 public:
  std::optional<std::string> text;

  CommitProto() = default;

  explicit CommitProto(const RimeCommit* commit) : text(commit->text) {}
};

class CandidateProto {
 public:
  std::string text;
  std::optional<std::string> comment;
  std::string label;
};

class CompositionProto {
 public:
  int length = 0;
  int cursorPos = 0;
  int selStart = 0;
  int selEnd = 0;
  std::optional<std::string> preedit;
  std::optional<std::string> commitTextPreview;
};

class MenuProto {
 public:
  int pageSize = 0;
  int pageNumber = 0;
  bool isLastPage = false;
  int highlightedCandidateIndex = 0;
  std::vector<CandidateProto> candidates;
  std::string selectKeys;
  std::vector<std::string> selectLabels;
};

static inline int distance(const char* start, const char* end) {
  return static_cast<int>(utf8::unchecked::distance(start, end));
}

class ContextProto {
 public:
  CompositionProto composition;
  MenuProto menu;
  std::string input;
  int caretPos = 0;

  ContextProto() = default;

  ContextProto(const RimeContext* context, std::string_view input,
               int caretPos) {
    this->input = input;
    this->caretPos = caretPos;
    if (context->composition.length > 0) {
      auto& c = context->composition;
      auto t = c.preedit;
      composition.length = distance(t, t + c.length);
      composition.cursorPos = distance(t, t + c.cursor_pos);
      composition.selStart = distance(t, t + c.sel_start);
      composition.selEnd = distance(t, t + c.sel_end);
      composition.preedit = t;
      if (context->commit_text_preview) {
        composition.commitTextPreview = context->commit_text_preview;
      }
    }
    if (context->menu.num_candidates > 0) {
      auto& m = context->menu;
      menu.pageSize = m.page_size;
      menu.pageNumber = m.page_no;
      menu.isLastPage = m.is_last_page;
      menu.highlightedCandidateIndex = m.highlighted_candidate_index;
      auto selectKeysSize = m.select_keys ? strlen(m.select_keys) : 0;
      auto& destCandidates = menu.candidates;
      destCandidates.reserve(m.num_candidates);
      for (int i = 0; i < m.num_candidates; ++i) {
        std::string label;
        if (i < m.page_size && RIME_PROVIDED(context, select_labels)) {
          label = context->select_labels[i];
        } else if (i < selectKeysSize) {
          label = std::string(1, m.select_keys[i]);
        } else {
          label = std::to_string((i + 1) % 10);
        }
        menu.selectLabels.emplace_back(label);
        const auto& candidate = m.candidates[i];
        destCandidates.emplace_back();
        destCandidates.back().text = candidate.text;
        if (candidate.comment) {
          destCandidates.back().comment = candidate.comment;
        }
        destCandidates.back().label = label;
      }
      menu.selectKeys = m.select_keys ? m.select_keys : "";
    }
  }
};

class StatusProto {
 public:
  std::string schemaId;
  std::string schemaName;
  bool isDisabled = false;
  bool isComposing = false;
  bool isAsciiMode = false;
  bool isFullShape = false;
  bool isSimplified = false;
  bool isTraditional = false;
  bool isAsciiPunct = false;

  StatusProto() = default;

  explicit StatusProto(const RimeStatus* status)
      : schemaId(status->schema_id),
        schemaName(status->schema_name ? status->schema_name : ""),
        isDisabled(status->is_disabled),
        isComposing(status->is_composing),
        isAsciiMode(status->is_ascii_mode),
        isFullShape(status->is_full_shape),
        isSimplified(status->is_simplified),
        isTraditional(status->is_traditional),
        isAsciiPunct(status->is_ascii_punct) {}
};
