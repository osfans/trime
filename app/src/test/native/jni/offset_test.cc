// SPDX-License-Identifier: GPL-3.0-or-later
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include "helper-types.h"
#include "jni-utils.h"

static int checks = 0;
static void Check(bool value, const char* message) {
  if (!value) throw std::runtime_error(message);
  std::cout << "PASS " << ++checks << ' ' << message << '\n';
}

int main() {
  JavaVMOption options[] = {
      {const_cast<char*>("-Xcheck:jni"), nullptr},
      {const_cast<char*>("-Xms16m"), nullptr},
      {const_cast<char*>("-Xmx128m"), nullptr},
      {const_cast<char*>("-XX:MaxMetaspaceSize=64m"), nullptr},
      {const_cast<char*>("-XX:ReservedCodeCacheSize=32m"), nullptr},
      {const_cast<char*>("-XX:ActiveProcessorCount=1"), nullptr},
      {const_cast<char*>("-XX:+UseSerialGC"), nullptr}};
  JavaVMInitArgs args{};
  args.version = JNI_VERSION_1_6;
  args.nOptions = sizeof(options) / sizeof(options[0]);
  args.options = options;
  JavaVM* vm = nullptr;
  JNIEnv* env = nullptr;
  if (JNI_CreateJavaVM(&vm, &env, &args) != JNI_OK || !env) return 2;
  int exit = 0;
  try {
    struct Sample {
      std::string utf8;
      std::u16string utf16;
      int cursorByte, startByte, endByte;
      int cursorUnit, startUnit, endUnit;
    };
    // Byte offsets are the Rime API input. Expected Java offsets are explicit
    // UTF-16 code-unit positions, independently specified for each selection.
    const std::vector<Sample> samples = {
        {"ni hao426", u"ni hao426", 6, 3, 6, 6, 3, 6},
        {u8"汉语 ü", u"汉语 ü", 6, 3, 9, 2, 1, 4},
        {u8"𠼞", u"𠼞", 4, 0, 4, 2, 0, 2},
        {u8"𠼞你😀 426", u"𠼞你😀 426", 11, 4, 11, 5, 2, 5},
        {u8"𠼞你😀 426", u"𠼞你😀 426", 13, 11, 15, 7, 5, 9},
        {u8"e\u0301😀你", u"e\u0301😀你", 7, 1, 7, 4, 1, 4}};
    for (const auto& sample : samples) {
      RimeContext context{};
      RIME_STRUCT_INIT(RimeContext, context);
      context.composition.length = static_cast<int>(sample.utf8.size());
      context.composition.preedit = const_cast<char*>(sample.utf8.c_str());
      context.composition.cursor_pos = sample.cursorByte;
      context.composition.sel_start = sample.startByte;
      context.composition.sel_end = sample.endByte;
      ContextProto converted(&context, "", 0, false);
      JRef<jstring> expected(
          env,
          env->NewString(reinterpret_cast<const jchar*>(sample.utf16.data()),
                         static_cast<jsize>(sample.utf16.size())));
      Check(*expected && !env->ExceptionCheck(),
            "build independent Java oracle");
      Check(converted.composition.length == env->GetStringLength(*expected),
            "composition length matches Java UTF-16 length");
      Check(converted.composition.cursorPos == sample.cursorUnit,
            "cursor byte position maps to Java code units");
      Check(converted.composition.selStart == sample.startUnit,
            "selection start maps to Java code units");
      Check(converted.composition.selEnd == sample.endUnit,
            "selection end maps to Java code units");
      JString text(env, *converted.composition.preedit);
      Check(*text && !env->ExceptionCheck(), "transport composition preedit");
      auto length = env->GetStringLength(*text);
      std::u16string actual(static_cast<size_t>(length), u'\0');
      env->GetStringRegion(*text, 0, length,
                           reinterpret_cast<jchar*>(actual.data()));
      Check(!env->ExceptionCheck() && actual == sample.utf16,
            "composition text and offsets share the same UTF-16 string");
    }
    RimeContext empty{};
    RIME_STRUCT_INIT(RimeContext, empty);
    const auto composition = ContextProto(&empty, "", 0, false).composition;
    Check(composition.length == 0, "empty composition has zero length");
    Check(composition.cursorPos == 0, "empty composition has zero cursor");
    Check(composition.selStart == 0,
          "empty composition has zero selection start");
    Check(composition.selEnd == 0, "empty composition has zero selection end");
  } catch (const std::exception& error) {
    if (env->ExceptionCheck()) env->ExceptionClear();
    std::cerr << "FAIL: " << error.what() << '\n';
    exit = 1;
  }
  auto destroyed = vm->DestroyJavaVM();
  std::cout << "SUMMARY checks=" << checks << " destroy=" << destroyed
            << " exit=" << exit << '\n';
  return destroyed == JNI_OK ? exit : 2;
}
