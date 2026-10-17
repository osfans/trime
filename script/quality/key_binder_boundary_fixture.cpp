// SPDX-License-Identifier: GPL-3.0-or-later
// Only app-owned stubs. The implementation under test is extracted unchanged
// from the real pinned KeyBinder after its production CMake overlay is applied.
#include <iostream>
#include <initializer_list>
#include <limits>
#include <stdexcept>
#include <string>

namespace audit {
int log_calls = 0;
int cases = 0;
int assertions = 0;
struct LogSink {
  LogSink() { ++log_calls; }
  template <typename T>
  LogSink& operator<<(const T&) { return *this; }
};
void Require(bool condition, const std::string& label) {
  ++assertions;
  if (!condition) throw std::runtime_error(label);
}
}  // namespace audit

#define LOG(level) ::audit::LogSink()

namespace rime {
using string = std::string;
struct KeyEvent {
  int code;
  int modifiers;
  bool released;
  int keycode() const { return code; }
  int modifier() const { return modifiers; }
  bool release() const { return released; }
};
struct Context {
  string text;
  int pushes = 0;
  const string& input() const { return text; }
  void PushInput(int key) { text.push_back(static_cast<char>(key)); ++pushes; }
};
struct Engine {
  Context* current;
  Context* context() const { return current; }
};
struct KeyBinder {
  Engine* engine_;
  int last_paging_key_;
  int paging_keystroke_count_;
  bool ReinterpretPagingKey(const KeyEvent& key_event);
};
}  // namespace rime

#include "key_binder_method.inc"

namespace audit {
void Case(int key, bool expected, int count = 1, int paging = '.',
          const std::string& input = "han", int modifiers = 0,
          bool released = false) {
  ++cases;
  log_calls = 0;
  rime::Context context{input};
  rime::Engine engine{&context};
  rime::KeyBinder binder{&engine, paging, count};
  const bool result = binder.ReinterpretPagingKey({key, modifiers, released});
  const std::string label = "key=" + std::to_string(key) + ",count=" +
      std::to_string(count) + ",paging=" + std::to_string(paging) +
      ",modifiers=" + std::to_string(modifiers) + ",release=" +
      std::to_string(released);
  Require(result == expected, "result: " + label);
  Require(context.text == input + (expected ? "." : ""), "input: " + label);
  Require(context.pushes == (expected ? 1 : 0), "push count: " + label);
  Require(binder.last_paging_key_ == (expected ? 0 : paging), "paging state: " + label);
  Require(binder.paging_keystroke_count_ == (expected ? 0 : count), "count state: " + label);
  Require(log_calls == (expected ? 1 : 0), "original log branch: " + label);
}
void OnePagingEventIsConsumedOnce() {
  ++cases;
  log_calls = 0;
  rime::Context context{"han"};
  rime::Engine engine{&context};
  rime::KeyBinder binder{&engine, '.', 1};
  Require(binder.ReinterpretPagingKey({'a', 0, false}), "first letter must reinterpret");
  Require(!binder.ReinterpretPagingKey({'b', 0, false}), "one paging event consumed only once");
  Require(context.text == "han." && context.pushes == 1, "sequence must append exactly one dot");
  Require(binder.last_paging_key_ == 0 && binder.paging_keystroke_count_ == 0,
          "sequence must retain cleared paging state");
  Require(log_calls == 1, "sequence must execute the original success branch only once");
}
}  // namespace audit

int main() {
  try {
    const std::string letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    // Exhaust every byte value: high-bit bytes are not URL ASCII letters.
    for (int key = 0; key <= 255; ++key) {
      audit::Case(key, letters.find(static_cast<char>(key)) != std::string::npos);
    }
    for (int key : {-1, -128, -256, std::numeric_limits<int>::min(),
                    256, 257, 1024, 0xff08, 0xff0d, 0xffe1, 0x110000,
                    std::numeric_limits<int>::max()}) {
      audit::Case(key, false);
    }
    for (char key : letters) {
      audit::Case(key, false, 1, '.', "han", 0, true);
      for (int modifiers : {1, 2, 4, 8, 16, 0x40000000})
        audit::Case(key, false, 1, '.', "han", modifiers);
      for (int count : {-1, 0, 2, 3}) audit::Case(key, false, count);
      for (int paging : std::initializer_list<int>{0, ',', '/', 0xff0d})
        audit::Case(key, false, 1, paging);
      audit::Case(key, false, 1, '.', "");
      audit::Case(key, false, 1, '.', "han.");
    }
    audit::OnePagingEventIsConsumedOnce();
    std::cout << "{\"passed\":true,\"cases\":" << audit::cases
              << ",\"assertions\":" << audit::assertions << "}\n";
    return 0;
  } catch (const std::exception& failure) {
    std::cerr << failure.what() << '\n';
    return 1;
  }
}
