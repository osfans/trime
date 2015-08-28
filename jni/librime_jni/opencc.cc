#include "opencc.h"
#include <opencc/Common.hpp>
#include <opencc/SimpleConverter.hpp>
#include <opencc/DartsDict.hpp>
#include <opencc/TextDict.hpp>

using namespace opencc;

// opencc

jstring get_opencc_version(JNIEnv *env, jobject thiz) {
  return newJstring(env, OPENCC_VERSION);
}

jstring opencc_convert(JNIEnv *env, jobject thiz, jstring line, jstring name) {
  if (name == NULL) return line;
  const char* s = env->GetStringUTFChars(name, NULL);
  string str(s);
  SimpleConverter converter(str);
  env->ReleaseStringUTFChars(name, s);
  const char* input = env->GetStringUTFChars(line, NULL);
  const string& converted = converter.Convert(input);
  env->ReleaseStringUTFChars(line, input);
  s = converted.c_str();
  return newJstring(env, s);
}

DictPtr LoadDictionary(const string& format, const string& inputFileName) {
  if (format == "text") {
    return SerializableDict::NewFromFile<TextDict>(inputFileName);
  } else if (format == "ocd") {
    return SerializableDict::NewFromFile<DartsDict>(inputFileName);
  } else {
    ALOGE("Unknown dictionary format: %s\n", format.c_str());
  }
  return nullptr;
}

SerializableDictPtr ConvertDictionary(const string& format,
                                      const DictPtr dict) {
  if (format == "text") {
    return TextDict::NewFromDict(*dict.get());
  } else if (format == "ocd") {
    return DartsDict::NewFromDict(*dict.get());
  } else {
    ALOGE("Unknown dictionary format: %s\n", format.c_str());
  }
  return nullptr;
}

void ConvertDictionary(const string inputFileName, const string outputFileName,
                       const string formatFrom, const string formatTo) {
  DictPtr dictFrom = LoadDictionary(formatFrom, inputFileName);
  SerializableDictPtr dictTo = ConvertDictionary(formatTo, dictFrom);
  dictTo->SerializeToFile(outputFileName);
}

void opencc_convert_dictionary(JNIEnv *env, jobject thiz, jstring jinputFileName,
    jstring joutputFileName, jstring jformatFrom, jstring jformatTo) {
  const char* s = env->GetStringUTFChars(jinputFileName, NULL);
  string inputFileName(s);
  env->ReleaseStringUTFChars(jinputFileName, s);
  s = env->GetStringUTFChars(joutputFileName, NULL);
  string outputFileName(s);
  env->ReleaseStringUTFChars(joutputFileName, s);
  s = env->GetStringUTFChars(jformatFrom, NULL);
  string formatFrom(s);
  env->ReleaseStringUTFChars(jformatFrom, s);
  s = env->GetStringUTFChars(jformatTo, NULL);
  string formatTo(s);
  env->ReleaseStringUTFChars(jformatTo, s);
  ConvertDictionary(inputFileName, outputFileName, formatFrom, formatTo);
}
