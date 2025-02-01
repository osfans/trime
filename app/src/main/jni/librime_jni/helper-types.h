#pragma once

#include <rime_api.h>

#include <string>
#include <vector>

class SchemaItem {
 public:
  std::string schemaId;
  std::string name;

  explicit SchemaItem(const RimeSchemaListItem &item)
      : schemaId(item.schema_id), name(item.name ? item.name : "") {}

  static std::vector<SchemaItem> fromCList(const RimeSchemaList &list) {
    std::vector<SchemaItem> result;
    result.reserve(list.size);
    for (int i = 0; i < list.size; ++i) {
      const SchemaItem item{list.list[i]};
      result.emplace_back(item);
    }
    return std::move(result);
  }
};
