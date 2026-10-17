/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
#pragma once

#include <jni.h>
#include <utf8.h>

#include <iterator>
#include <stdexcept>
#include <string>
#include <string_view>
#include <type_traits>

// Rime/OpenCC use UTF-8; JNI's NewStringUTF/GetStringUTFChars use Modified
// UTF-8 instead. Cross this boundary through UTF-16, including supplementary
// Han.
static inline jstring makeJavaString(JNIEnv* env, std::string_view text) {
  if (env->ExceptionCheck()) return nullptr;
  std::u16string result;
  if (utf8::is_valid(text.begin(), text.end())) {
    utf8::utf8to16(text.begin(), text.end(), std::back_inserter(result));
  } else {
    std::string normalized;
    utf8::replace_invalid(text.begin(), text.end(),
                          std::back_inserter(normalized));
    utf8::utf8to16(normalized.begin(), normalized.end(),
                   std::back_inserter(result));
  }
  return env->NewString(reinterpret_cast<const jchar*>(result.data()),
                        result.size());
}

static inline void throwJavaException(JNIEnv* env, const char* msg) {
  if (env->ExceptionCheck()) return;
  jclass c = env->FindClass("java/lang/Exception");
  if (!c) return;
  auto init = env->GetMethodID(c, "<init>", "(Ljava/lang/String;)V");
  if (init) {
    auto message = makeJavaString(env, msg);
    if (message) {
      auto exception =
          static_cast<jthrowable>(env->NewObject(c, init, message));
      if (exception) {
        env->Throw(exception);
        env->DeleteLocalRef(exception);
      }
      env->DeleteLocalRef(message);
    }
  }
  env->DeleteLocalRef(c);
}

template <typename F>
static auto jniCall(JNIEnv* env, F&& call) -> decltype(call()) {
  try {
    return call();
  } catch (const std::exception& error) {
    throwJavaException(env, error.what());
    if constexpr (!std::is_void_v<decltype(call())>) return {};
  }
}

class CString {
 private:
  std::string value_;

 public:
  CString(JNIEnv* env, jstring str) {
    if (!str) return;
    auto size = env->GetStringLength(str);
    auto chars = env->GetStringChars(str, nullptr);
    if (!chars) throw std::runtime_error("Cannot access Java string");
    try {
      try {
        utf8::utf16to8(chars, chars + size, std::back_inserter(value_));
      } catch (const utf8::invalid_utf16&) {
        // Java strings may contain unpaired surrogates. Normalize only those;
        // never read past the supplied length or discard the valid prefix.
        std::u16string normalized;
        for (jsize i = 0; i < size; ++i) {
          auto c = chars[i];
          if (c >= 0xd800 && c <= 0xdbff && i + 1 < size &&
              chars[i + 1] >= 0xdc00 && chars[i + 1] <= 0xdfff) {
            normalized += c;
            normalized += chars[++i];
          } else {
            normalized += c >= 0xd800 && c <= 0xdfff ? 0xfffd : c;
          }
        }
        value_.clear();
        utf8::utf16to8(normalized.begin(), normalized.end(),
                       std::back_inserter(value_));
      }
    } catch (...) {
      env->ReleaseStringChars(str, chars);
      throw;
    }
    env->ReleaseStringChars(str, chars);
  }

  operator std::string() const { return value_; }
  operator const char*() const { return value_.c_str(); }
  const char* operator*() const { return value_.c_str(); }
};

template <typename T = jobject>
class JRef {
 private:
  JNIEnv* env_;
  T ref_;

 public:
  JRef(JNIEnv* env, jobject ref) : env_(env), ref_(reinterpret_cast<T>(ref)) {}

  ~JRef() { env_->DeleteLocalRef(ref_); }

  operator T() { return ref_; }

  T operator*() { return ref_; }
};

class JString {
 private:
  JNIEnv* env_;
  jstring jstring_;

 public:
  JString(JNIEnv* env, const char* chars)
      : env_(env), jstring_(chars ? makeJavaString(env, chars) : nullptr) {}

  JString(JNIEnv* env, const std::string& string)
      : env_(env), jstring_(makeJavaString(env, string)) {}

  ~JString() { env_->DeleteLocalRef(jstring_); }

  operator jstring() { return jstring_; }

  jstring operator*() { return jstring_; }
};

class JEnv {
 private:
  JNIEnv* env = nullptr;
  JavaVM* jvm_;
  bool attached_ = false;

 public:
  explicit JEnv(JavaVM* jvm) : jvm_(jvm) {
    auto status = jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
      if (jvm->AttachCurrentThread(&env, nullptr) != JNI_OK)
        throw std::runtime_error("Cannot attach native Rime thread");
      attached_ = true;
    } else if (status != JNI_OK) {
      throw std::runtime_error("Cannot access Java environment");
    }
  }

  ~JEnv() {
    if (attached_) jvm_->DetachCurrentThread();
  }
  JEnv(const JEnv&) = delete;
  JEnv& operator=(const JEnv&) = delete;

  operator JNIEnv*() { return env; }

  JNIEnv* operator->() { return env; }
};

class GlobalRefSingleton {
 public:
  JavaVM* jvm;

  jclass Object;

  jclass String;

  jclass Integer;
  jmethodID IntegerInit;

  jclass Boolean;
  jmethodID BooleanInit;

  jclass Rime;
  jmethodID HandleRimeMessage;

  jclass CandidateProto;
  jmethodID CandidateProtoInit;

  jclass CommitProto;
  jmethodID CommitProtoInit;

  jclass ContextProto;
  jmethodID ContextProtoInit;

  jclass CompositionProto;
  jmethodID CompositionProtoInit;

  jclass StatusProto;
  jmethodID StatusProtoInit;

  jclass RimeResponse;
  jmethodID RimeResponseInit;

  jclass CandidatesPaged;
  jmethodID CandidatesPagedInit;
  jmethodID CandidatesPagedSetPrediction;

  jclass CandidatesBulk;
  jmethodID CandidatesBulkInit;
  jmethodID CandidatesBulkSetPrediction;

  jclass SchemaListItem;
  jmethodID SchemaListItemInit;

  jclass KeyEvent;
  jmethodID KeyEventInit;

  explicit GlobalRefSingleton(JavaVM* jvm_) : jvm(jvm_) {
    JNIEnv* env;
    jvm->AttachCurrentThread(&env, nullptr);

    Object = reinterpret_cast<jclass>(
        env->NewGlobalRef(env->FindClass("java/lang/Object")));

    String = reinterpret_cast<jclass>(
        env->NewGlobalRef(env->FindClass("java/lang/String")));

    Integer = reinterpret_cast<jclass>(
        env->NewGlobalRef(env->FindClass("java/lang/Integer")));
    IntegerInit = env->GetMethodID(Integer, "<init>", "(I)V");

    Boolean = reinterpret_cast<jclass>(
        env->NewGlobalRef(env->FindClass("java/lang/Boolean")));
    BooleanInit = env->GetMethodID(Boolean, "<init>", "(Z)V");

    Rime = reinterpret_cast<jclass>(
        env->NewGlobalRef(env->FindClass("com/osfans/trime/core/Rime")));
    HandleRimeMessage = env->GetStaticMethodID(Rime, "handleRimeMessage",
                                               "(I[Ljava/lang/Object;)V");

    CandidateProto = reinterpret_cast<jclass>(env->NewGlobalRef(
        env->FindClass("com/osfans/trime/core/CandidateProto")));
    CandidateProtoInit = env->GetMethodID(
        CandidateProto, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");

    CommitProto = reinterpret_cast<jclass>(
        env->NewGlobalRef(env->FindClass("com/osfans/trime/core/CommitProto")));
    CommitProtoInit =
        env->GetMethodID(CommitProto, "<init>", "(Ljava/lang/String;)V");

    ContextProto = reinterpret_cast<jclass>(env->NewGlobalRef(
        env->FindClass("com/osfans/trime/core/ContextProto")));
    ContextProtoInit =
        env->GetMethodID(ContextProto, "<init>",
                         "(Lcom/osfans/trime/core/CompositionProto;Ljava/lang/"
                         "String;I)V");

    CompositionProto = reinterpret_cast<jclass>(env->NewGlobalRef(
        env->FindClass("com/osfans/trime/core/CompositionProto")));
    CompositionProtoInit =
        env->GetMethodID(CompositionProto, "<init>",
                         "(IIIILjava/lang/String;Ljava/lang/String;)V");

    StatusProto = reinterpret_cast<jclass>(
        env->NewGlobalRef(env->FindClass("com/osfans/trime/core/StatusProto")));
    StatusProtoInit =
        env->GetMethodID(StatusProto, "<init>",
                         "(Ljava/lang/String;Ljava/lang/String;ZZZZZZZ)V");

    RimeResponse = reinterpret_cast<jclass>(env->NewGlobalRef(
        env->FindClass("com/osfans/trime/core/RimeResponse")));
    RimeResponseInit = env->GetMethodID(
        RimeResponse, "<init>",
        "(Lcom/osfans/trime/core/CommitProto;Lcom/osfans/trime/core/"
        "CompositionProto;Lcom/osfans/trime/core/Candidates;"
        "Lcom/osfans/trime/core/StatusProto;)V");

    CandidatesPaged = reinterpret_cast<jclass>(env->NewGlobalRef(
        env->FindClass("com/osfans/trime/core/Candidates$Paged")));
    CandidatesPagedInit =
        env->GetMethodID(CandidatesPaged, "<init>",
                         "(ZZZI[Lcom/osfans/trime/core/CandidateProto;)V");
    CandidatesPagedSetPrediction =
        env->GetMethodID(CandidatesPaged, "setPrediction", "(Z)V");

    CandidatesBulk = reinterpret_cast<jclass>(env->NewGlobalRef(
        env->FindClass("com/osfans/trime/core/Candidates$Bulk")));
    CandidatesBulkInit =
        env->GetMethodID(CandidatesBulk, "<init>",
                         "(II[Lcom/osfans/trime/core/CandidateProto;)V");
    CandidatesBulkSetPrediction =
        env->GetMethodID(CandidatesBulk, "setPrediction", "(Z)V");

    SchemaListItem = reinterpret_cast<jclass>(
        env->NewGlobalRef(env->FindClass("com/osfans/trime/core/SchemaItem")));
    SchemaListItemInit = env->GetMethodID(
        SchemaListItem, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");

    KeyEvent = reinterpret_cast<jclass>(env->NewGlobalRef(
        env->FindClass("com/osfans/trime/core/RimeKeyEvent")));
    KeyEventInit =
        env->GetMethodID(KeyEvent, "<init>", "(IILjava/lang/String;)V");
  }

  [[nodiscard]] JEnv AttachEnv() const { return JEnv(jvm); }
};

extern GlobalRefSingleton* GlobalRef;
