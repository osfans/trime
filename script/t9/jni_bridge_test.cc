// SPDX-License-Identifier: GPL-3.0-or-later
#include "../../app/src/main/jni/librime_jni/jni-utils.h"

#include <cassert>
#include <iostream>
#include <type_traits>

namespace {
bool attached = false;
int attachments = 0;
int detachments = 0;
int attach_status = JNI_OK;
int env_status = JNI_OK;
JNIEnv fake_env{};
std::u16string java_result;
int utf_calls = 0;
int releases = 0;
bool pending = false;
int thrown = 0;

jint get_env(JavaVM*, void** result, jint) {
  *result = attached ? &fake_env : nullptr;
  return env_status != JNI_OK ? env_status : attached ? JNI_OK : JNI_EDETACHED;
}
jint attach(JavaVM*, JNIEnv** result, void*) {
  if (attach_status != JNI_OK) return attach_status;
  attached = true;
  ++attachments;
  *result = &fake_env;
  return JNI_OK;
}
jint detach(JavaVM*) {
  assert(attached);
  attached = false;
  ++detachments;
  return JNI_OK;
}
jsize length(JNIEnv*, jstring value) {
  return static_cast<jsize>(reinterpret_cast<std::u16string*>(value)->size());
}
const jchar* chars(JNIEnv*, jstring value, jboolean*) {
  return reinterpret_cast<const jchar*>(reinterpret_cast<std::u16string*>(value)->data());
}
void release(JNIEnv*, jstring, const jchar*) { ++releases; }
jstring new_string(JNIEnv*, const jchar* value, jsize size) {
  java_result.assign(reinterpret_cast<const char16_t*>(value), size);
  return reinterpret_cast<jstring>(&java_result);
}
jstring new_utf(JNIEnv*, const char*) {
  ++utf_calls;
  return nullptr;
}
void delete_ref(JNIEnv*, jobject) {}
}  // namespace

int main() {
  JNIInvokeInterface invoke{};
  invoke.GetEnv = get_env;
  invoke.AttachCurrentThread = attach;
  invoke.DetachCurrentThread = detach;
  JavaVM vm{&invoke};
  JNINativeInterface native{};
  native.GetStringLength = length;
  native.GetStringChars = chars;
  native.ReleaseStringChars = release;
  native.NewString = new_string;
  native.NewStringUTF = new_utf;
  native.DeleteLocalRef = delete_ref;
  native.ExceptionCheck = [](JNIEnv*) -> jboolean { return pending; };
  native.FindClass = [](JNIEnv*, const char*) -> jclass { return reinterpret_cast<jclass>(&java_result); };
  native.GetMethodID = [](JNIEnv*, jclass, const char*, const char*) -> jmethodID { return reinterpret_cast<jmethodID>(&java_result); };
  native.NewObjectV = [](JNIEnv*, jclass, jmethodID, va_list) -> jobject { return reinterpret_cast<jobject>(&java_result); };
  native.Throw = [](JNIEnv*, jthrowable) -> jint { pending = true; ++thrown; return JNI_OK; };
  fake_env.functions = &native;

  attached = true;
  { JEnv env(&vm); assert(static_cast<JNIEnv*>(env) == &fake_env); }
  assert(attached && attachments == 0 && detachments == 0);
  attached = false;
  for (int i = 0; i < 100; ++i) {
    {
      JEnv outer(&vm);
      { JEnv inner(&vm); }
      assert(attached && detachments == i);
    }
    assert(!attached && attachments == i + 1 && detachments == i + 1);
  }
  static_assert(!std::is_copy_constructible<JEnv>::value);
  attach_status = JNI_ERR;
  bool failed = false;
  try { JEnv env(&vm); } catch (const std::exception&) { failed = true; }
  assert(failed && !attached && detachments == 100);
  attach_status = JNI_OK;
  env_status = JNI_EVERSION;
  failed = false;
  try { JEnv env(&vm); } catch (const std::exception&) { failed = true; }
  assert(failed && attachments == 100);
  env_status = JNI_OK;

  for (std::u16string text : {u"ascii", u"\u4f60\u597d", u"\U00020bb7\U0001f30f", u""}) {
    const auto before = releases;
    const auto utf8 = static_cast<std::string>(CString(&fake_env, reinterpret_cast<jstring>(&text)));
    JString java(&fake_env, utf8);
    assert(java_result == text && releases == before + 1);
    if (text == u"\U00020bb7\U0001f30f") assert(utf8 == "\xf0\xa0\xae\xb7\xf0\x9f\x8c\x8f");
  }
  std::u16string with_nul = {u'a', 0, u'b'};
  const auto utf8 = static_cast<std::string>(CString(&fake_env, reinterpret_cast<jstring>(&with_nul)));
  assert(utf8.size() == 3 && utf8[1] == 0);
  { JString java(&fake_env, utf8); assert(java_result == with_nul); }
  std::u16string invalid = {u'a', 0xd800, u'x', 0xdc00};
  const auto normalized = static_cast<std::string>(CString(&fake_env, reinterpret_cast<jstring>(&invalid)));
  { JString java(&fake_env, normalized); assert(java_result == u"a\ufffdx\ufffd"); }
  { JString java(&fake_env, std::string("\xffx")); assert(java_result == u"\ufffdx"); }
  assert(utf_calls == 0);
  const auto result = jniCall(&fake_env, []() -> bool { throw std::runtime_error("\xf0\xa0\xae\xb7"); });
  assert(!result && pending && thrown == 1 && java_result == u"\U00020bb7");
  throwJavaException(&fake_env, "must not overwrite pending exception");
  assert(thrown == 1 && makeJavaString(&fake_env, "no allocation while pending") == nullptr);
  pending = false;
  int calls = 0;
  jniCall(&fake_env, [&] { ++calls; });
  assert(calls == 1 && !pending);
  std::cout << "PASS: JNI attachment ownership, nested/repeated calls, failure paths, UTF-16/UTF-8, supplementary characters, NUL and malformed input\n";
}
