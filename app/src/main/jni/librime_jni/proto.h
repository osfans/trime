#pragma once

#include <rime_api.h>

#include <optional>
#include <string>
#include <vector>

namespace rime::proto {
class Commit {
 public:
  std::optional<std::string> text;
};

class Candidate {
 public:
  std::string text;
  std::optional<std::string> comment;
  std::string label;
};

class Composition {
 public:
  int length = 0;
  int cursorPos = 0;
  int selStart = 0;
  int selEnd = 0;
  std::optional<std::string> preedit;
  std::optional<std::string> commitTextPreview;
};

class Menu {
 public:
  int pageSize = 0;
  int pageNumber = 0;
  bool isLastPage = false;
  int highlightedCandidateIndex = 0;
  std::vector<Candidate> candidates;
  std::string selectKeys;
  std::vector<std::string> selectLabels;
};

class Context {
 public:
  Composition composition;
  Menu menu;
  std::string input;
  int caretPos = 0;
};

class Status {
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
};
}  // namespace rime::proto

#ifdef __cplusplus
extern "C" {
#endif

typedef struct rime_proto_api_t {
  int data_size;

  void (*commit_proto)(RimeSessionId session_id, uintptr_t commit_builder);
  void (*context_proto)(RimeSessionId session_id, uintptr_t context_builder);
  void (*status_proto)(RimeSessionId session_id, uintptr_t status_builder);
} RimeProtoApi;

#ifdef __cplusplus
}
#endif
