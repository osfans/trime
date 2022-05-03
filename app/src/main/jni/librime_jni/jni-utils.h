#ifndef TRIME_JNI_UTILS_H
#define TRIME_JNI_UTILS_H

#include <jni.h>
#include <string>

class JString {
private:
    JNIEnv *env_;
    jstring jstring_;

public:
    JString(JNIEnv *env, const char *chars)
            : env_(env), jstring_(env->NewStringUTF(chars)) {}

    JString(JNIEnv *env, const std::string &string)
            : JString(env, string.c_str()) {}

    ~JString() {
        env_->DeleteLocalRef(jstring_);
    }

    operator jstring() { return jstring_; }

    jstring operator*() { return jstring_; }
};

class JClass {
private:
    JNIEnv *env_;
    jclass jclass_;

public:
    JClass(JNIEnv *env, const char *name)
            : env_(env), jclass_(env->FindClass(name)) {}

    ~JClass() {
        env_->DeleteLocalRef(jclass_);
    }

    operator jclass() { return jclass_; }

    jclass operator*() { return jclass_; }
};

class JEnv {
private:
    JNIEnv *env;

public:
    JEnv(JavaVM *jvm) {
        if (jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
            jvm->AttachCurrentThread(&env, nullptr);
        }
    }

    operator JNIEnv *() { return env; }

    JNIEnv *operator->() { return env; }
};

class GlobalRefSingleton {
public:
    JavaVM *jvm;

    jclass Object;

    jclass String;

    jclass Integer;
    jmethodID IntegerInit;

    jclass Boolean;
    jmethodID BooleanInit;

    jclass ArrayList;
    jmethodID ArrayListInit;
    jmethodID ArrayListAdd;

    jclass HashMap;
    jmethodID HashMapInit;
    jmethodID HashMapPut;

    GlobalRefSingleton(JavaVM *jvm_) : jvm(jvm_) {
        JNIEnv *env;
        jvm->AttachCurrentThread(&env, nullptr);

        Object = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Object")));

        String = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/String")));

        Integer = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Integer")));
        IntegerInit = env->GetMethodID(Integer, "<init>", "(I)V");

        Boolean = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Boolean")));
        BooleanInit = env->GetMethodID(Boolean, "<init>", "(Z)V");

        ArrayList = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("java/util/ArrayList")));
        ArrayListInit = env->GetMethodID(ArrayList, "<init>", "()V");
        ArrayListAdd = env->GetMethodID(ArrayList, "add", "(ILjava/lang/Object;)V");

        HashMap = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("java/util/HashMap")));
        HashMapInit = env->GetMethodID(HashMap, "<init>", "()V");
        HashMapPut = env->GetMethodID(HashMap, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    }

    const JEnv AttachEnv() const { return JEnv(jvm); }
};

#endif //TRIME_JNI_UTILS_H
