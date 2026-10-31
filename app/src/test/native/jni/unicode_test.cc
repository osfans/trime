// SPDX-License-Identifier: GPL-3.0-or-later
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include "objconv.h"

static int checks = 0;
static void Check(bool value, const char* message) {
  if (!value) throw std::runtime_error(message);
  std::cout << "PASS " << ++checks << ' ' << message << '\n';
}

static std::u16string Read(JNIEnv* env, jstring text) {
  Check(text && !env->ExceptionCheck(), "Java string exists without exception");
  auto length = env->GetStringLength(text);
  auto chars = env->GetStringChars(text, nullptr);
  Check(chars && !env->ExceptionCheck(), "read Java UTF-16 string");
  std::u16string result;
  for (jsize i = 0; i < length; ++i) result.push_back(chars[i]);
  env->ReleaseStringChars(text, chars);
  return result;
}

static void CheckOutgoing(JNIEnv* env, const std::string& input,
                          const std::u16string& expected) {
  JString text(env, input);
  Check(Read(env, *text) == expected, "standard UTF-8 becomes expected UTF-16");
}

static void CheckIncoming(JNIEnv* env, const std::u16string& input,
                          const std::string& expected) {
  JRef<jstring> text(
      env, env->NewString(reinterpret_cast<const jchar*>(input.data()),
                          static_cast<jsize>(input.size())));
  CString actual(env, *text);
  Check(!env->ExceptionCheck(),
        "valid Java input is converted without exception");
  Check(static_cast<std::string>(actual) == expected,
        "Java UTF-16 becomes expected standard UTF-8");
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
    };
    const std::vector<Sample> samples = {
        {"", u""},
        {"ni hao 426", u"ni hao 426"},
        {u8"汉语 ü", u"汉语 ü"},
        {u8"𠮷", u"𠮷"},
        {u8"😀", u"😀"},
        {u8"𠮷你 426", u"𠮷你 426"},
        {u8"𠮷野家😀", u"𠮷野家😀"},
        {std::string("a\0b", 3), std::u16string(u"a\0b", 3)}};
    for (const auto& sample : samples) {
      CheckOutgoing(env, sample.utf8, sample.utf16);
      CheckIncoming(env, sample.utf16, sample.utf8);
    }
#ifndef TRIME_JNI_BASELINE
    {
      CheckOutgoing(env, std::string("a\x80") + "b", u"a�b");
      CheckOutgoing(env, std::string("a\xe4\xb8", 3), u"a�");
      CheckOutgoing(env, std::string("a\xc0\xaf") + "b", u"a�b");
      CheckOutgoing(env, std::string("a\xed\xa0\x80") + "b", u"a�b");
      CheckOutgoing(env, std::string("a\xf4\x90\x80\x80") + "b", u"a�b");
      CheckIncoming(env, {u'a', 0xd800}, u8"a�");
      CheckIncoming(env, {u'a', 0xdc00, u'b'}, u8"a�b");
      CheckIncoming(env, {u'a', 0xd800, u'b'}, u8"a�b");
      CheckIncoming(env, {0xd800, 0xd842, 0xdfb7}, u8"�𠮷");
      CheckIncoming(env, {0xd842, 0xdfb7, 0xd800}, u8"𠮷�");
      {
        JString nullable(env, static_cast<const char*>(nullptr));
        Check(!*nullable && !env->ExceptionCheck(),
              "native null remains Java null");
      }
      {
        CString invalid(env, nullptr);
        Check(env->ExceptionCheck(), "Java null input raises an exception");
        env->ExceptionClear();
        Check(static_cast<std::string>(invalid).empty(),
              "null conversion exposes no partial native value");
      }
      auto stringClass = JRef<jclass>(env, env->FindClass("java/lang/String"));
      Check(*stringClass && !env->ExceptionCheck(),
            "load real Java String class");
      const std::string repeated = u8"𠮷你😀";
      for (int repeat = 0; repeat < 32; ++repeat) {
        JRef<jobjectArray> array(
            env, env->NewObjectArray(1024, *stringClass, nullptr));
        for (int i = 0; i < 1024; ++i) {
          JString text(env, i == 17 ? std::string("a\0b", 3)
                                    : repeated + std::to_string(i));
          env->SetObjectArrayElement(*array, i, *text);
        }
        auto converted = stringArrayToStringVector(env, *array);
        Check(!env->ExceptionCheck() && converted.size() == 1024,
              "repeated 1024-element array conversion succeeds");
        for (int i = 0; i < 1024; ++i)
          if (converted[i] !=
              (i == 17 ? std::string("a\0b", 3) : repeated + std::to_string(i)))
            throw std::runtime_error("array order or Unicode changed");
        if (repeat == 0)
          Check(converted[17].size() == 3 && converted[17][1] == '\0',
                "array conversion retains embedded NUL and suffix bytes");
      }
      {
        JRef<jobjectArray> array(env,
                                 env->NewObjectArray(0, *stringClass, nullptr));
        Check(stringArrayToStringVector(env, *array).empty() &&
                  !env->ExceptionCheck(),
              "empty array remains valid");
      }
      {
        JRef<jobjectArray> array(env,
                                 env->NewObjectArray(2, *stringClass, nullptr));
        JString text(env, "valid-prefix");
        env->SetObjectArrayElement(*array, 0, *text);
        auto converted = stringArrayToStringVector(env, *array);
        Check(env->ExceptionCheck(), "null array element raises an exception");
        env->ExceptionClear();
        Check(converted.empty(),
              "failed array conversion discards partial vector");
      }
      Check(stringArrayToStringVector(env, nullptr).empty() &&
                env->ExceptionCheck(),
            "null array raises an exception");
      env->ExceptionClear();
      throwJavaException(env, u8"错误𠮷😀");
      Check(env->ExceptionCheck(), "throw Unicode exception message");
      auto exception = JRef<jthrowable>(env, env->ExceptionOccurred());
      env->ExceptionClear();
      auto exceptionClass = JRef<jclass>(env, env->GetObjectClass(*exception));
      auto getMessage = env->GetMethodID(*exceptionClass, "getMessage",
                                         "()Ljava/lang/String;");
      Check(getMessage && !env->ExceptionCheck(),
            "find real exception message getter");
      auto message =
          JRef<jstring>(env, env->CallObjectMethod(*exception, getMessage));
      Check(!env->ExceptionCheck(), "read Unicode exception message");
      Check(Read(env, *message) == u"错误𠮷😀",
            "exception message preserves Unicode");
      env->Throw(*exception);
      {
        JString blocked(env, "ignored");
        CString incoming(env, *message);
        Check(!*blocked && static_cast<std::string>(incoming).empty(),
              "pending exception prevents string conversion");
        Check(!makeJavaString(env, "ignored") &&
                  stringArrayToStringVector(env, nullptr).empty(),
              "pending exception prevents helper and array conversion");
        throwJavaException(env, "must not replace original");
      }
      auto retained = JRef<jthrowable>(env, env->ExceptionOccurred());
      env->ExceptionClear();
      Check(env->IsSameObject(*exception, *retained),
            "original pending exception identity is retained");
    }
#endif
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
