#include <string>
#include "rime_jni.h"
#include "jni-common.h"

extern void rime_require_module_lua();
extern void rime_require_module_charcode();
extern void rime_require_module_octagram();
// librime is compiled as a static library, we have to link modules explicitly
static void declare_librime_module_dependencies() {
    rime_require_module_lua();
    rime_require_module_charcode();
    rime_require_module_octagram();
}

class Rime {
public:
    Rime(): rime(rime_get_api()) {}
    Rime(Rime const &) = delete;
    void operator=(Rime const &) = delete;

    static Rime &Instance() {
        static Rime instance;
        return instance;
    }

    bool isRunning() {
        return session != 0;
    }

    void startup(bool fullCheck) {
        if (!rime) return;
        const char *userDir = getenv("RIME_USER_DATA_DIR");
        const char *sharedDir = getenv("RIME_SHARED_DATA_DIR");

        RIME_STRUCT(RimeTraits, trime_traits)
        trime_traits.shared_data_dir = sharedDir;
        trime_traits.user_data_dir = userDir;
        trime_traits.app_name = "rime.trime";
        trime_traits.distribution_name = "Rime";
        trime_traits.distribution_code_name = "trime";
        trime_traits.distribution_version = TRIME_VERSION;

        if (firstRun) {
            rime->setup(&trime_traits);
            firstRun = false;
        }
        rime->initialize(&trime_traits);
        rime->set_notification_handler([](void *context_object, RimeSessionId session_id,
                                          const char *message_type, const char *message_value) {
            auto env = GlobalRef->AttachEnv();
            env->CallStaticVoidMethod(GlobalRef->Rime, GlobalRef->HandleRimeNotification,
                                      *JString(env, message_type),
                                      *JString(env, message_value));
        }, GlobalRef->jvm);
        if (rime->start_maintenance(fullCheck) && rime->is_maintenance_mode()) {
            rime->join_maintenance_thread();
        }

        session = rime->create_session();
        if (!session) {
            return;
        }
    }

    void deploy() {
        rime->destroy_session(session);
        session = 0;
        rime->finalize();
        startup(true);
    }

    RimeSessionId sessionId() const {
        return session;
    }

private:
    RimeApi *rime;
    RimeSessionId session = 0;

    bool firstRun = true;
};

#define DO_IF_NOT_RUNNING(expr) \
    if (!Rime::Instance().isRunning()) { \
        expr; \
    }
#define RETURN_IF_NOT_RUNNING DO_IF_NOT_RUNNING(return)
#define RETURN_VALUE_IF_NOT_RUNNING(v) DO_IF_NOT_RUNNING(return (v))

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* jvm, void* reserved)
{
    GlobalRef = new GlobalRefSingleton(jvm);
    declare_librime_module_dependencies();
    return JNI_VERSION_1_6;
}

static jobject rimeConfigValueToJObject(JNIEnv *env, RimeConfig* config, const std::string &key);

void init_traits(JNIEnv *env, jstring shared_data_dir, jstring user_data_dir, void (*func)(RimeTraits *)) {
    RIME_STRUCT(RimeTraits, traits);
    const char* p_shared_data_dir = shared_data_dir == nullptr ? nullptr : env->GetStringUTFChars(shared_data_dir, nullptr);
    const char* p_user_data_dir = user_data_dir == nullptr ? nullptr : env->GetStringUTFChars(user_data_dir, nullptr);
    traits.shared_data_dir = p_shared_data_dir;
    traits.user_data_dir = p_user_data_dir;
    traits.app_name = "rime.trime";
    func(&traits);
    env->ReleaseStringUTFChars(shared_data_dir, p_shared_data_dir);
    env->ReleaseStringUTFChars(user_data_dir, p_user_data_dir);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_startupRime(JNIEnv *env, jclass clazz, jstring shared_dir, jstring user_dir,
                                            jboolean full_check) {
    if (Rime::Instance().isRunning()) {
        return;
    }

    // for rime shared data dir
    setenv("RIME_SHARED_DATA_DIR", CString(env, shared_dir), 1);
    // for rime user data dir
    setenv("RIME_USER_DATA_DIR", CString(env, user_dir), 1);

    Rime::Instance().startup(full_check);
}

// deployment
extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_deployer_1initialize(JNIEnv *env, jclass /* thiz */, jstring shared_data_dir, jstring user_data_dir) {
    init_traits(env, shared_data_dir, user_data_dir, RimeDeployerInitialize);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_prebuild(JNIEnv *env, jclass /* thiz */) {
    return RimePrebuildAllSchemas();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_deployRime(JNIEnv *env, jclass /* thiz */) {
    Rime::Instance().deploy();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deploy_1schema(JNIEnv *env, jclass /* thiz */, jstring schema_file) {
    const char* s = schema_file == nullptr ? nullptr : env->GetStringUTFChars(schema_file, nullptr);
    bool b = RimeDeploySchema(s);
    env->ReleaseStringUTFChars(schema_file, s);
    return b;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deployRimeConfigFile(JNIEnv *env, jclass /* thiz */, jstring file_name, jstring version_key) {
    return rime_get_api()->deploy_config_file(CString(env, file_name), CString(env, version_key));
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_sync_1user_1data(JNIEnv *env, jclass /* thiz */) {
    ALOGI("sync user data...");
    return RimeSyncUserData();
}

// session management

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_cleanup_1stale_1sessions(JNIEnv *env, jclass /* thiz */) {
    RimeCleanupStaleSessions();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_cleanup_1all_1sessions(JNIEnv *env, jclass /* thiz */) {
    RimeCleanupAllSessions();
}

// input
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_process_1key(JNIEnv *env, jclass /* thiz */, jint keycode, jint mask) {
    return RimeProcessKey(Rime::Instance().sessionId(), keycode, mask);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_commit_1composition(JNIEnv *env, jclass /* thiz */) {
    return RimeCommitComposition(Rime::Instance().sessionId());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_clear_1composition(JNIEnv *env, jclass /* thiz */) {
    RimeClearComposition(Rime::Instance().sessionId());
}

void rimeCommitToJObject(JNIEnv *env, const RimeCommit &commit, const jobject &jcommit) {
    env->SetObjectField(jcommit, GlobalRef->RimeCommitText, JString(env, commit.text));
}

// output
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_get_1commit(JNIEnv *env, jclass /* thiz */, jobject jcommit) {
    RIME_STRUCT(RimeCommit, commit);
    Bool r = RimeGetCommit(Rime::Instance().sessionId(), &commit);
    if (r) {
        rimeCommitToJObject(env, commit, jcommit);
        RimeFreeCommit(&commit);
    }
    return r;
}

void rimeContextToJObject(JNIEnv *env, const RimeContext &context, const jobject &jcontext) {
    {
        auto composition = env->AllocObject(GlobalRef->RimeComposition);
        env->SetIntField(composition, GlobalRef->RimeCompositionLength, context.composition.length);
        env->SetIntField(composition, GlobalRef->RimeCompositionCursorPos,
                         context.composition.cursor_pos);
        env->SetIntField(composition, GlobalRef->RimeCompositionSelStart,
                         context.composition.sel_start);
        env->SetIntField(composition, GlobalRef->RimeCompositionSelEnd,
                         context.composition.sel_end);
        env->SetObjectField(composition, GlobalRef->RimeCompositionPreedit,
                            JString(env, context.composition.preedit));
        env->SetObjectField(jcontext, GlobalRef->RimeContextComposition, composition);
    }
    {
        auto menu = env->AllocObject(GlobalRef->RimeMenu);
        env->SetIntField(menu, GlobalRef->RimeMenuPageSize, context.menu.page_size);
        env->SetIntField(menu, GlobalRef->RimeMenuPageNo, context.menu.page_no);
        env->SetBooleanField(menu, GlobalRef->RimeMenuIsLastPage, context.menu.is_last_page);
        env->SetIntField(menu, GlobalRef->RimeMenuHighlightedCandidateIndex,
                         context.menu.highlighted_candidate_index);
        env->SetIntField(menu, GlobalRef->RimeMenuNumCandidates, context.menu.num_candidates);
        {
            int num = context.menu.num_candidates;
            auto candidates = env->NewObjectArray(num, GlobalRef->RimeCandidate, nullptr);
            for (int i = 0; i < num; ++i) {
                auto candidate = env->AllocObject(GlobalRef->RimeCandidate);
                env->SetObjectField(candidate, GlobalRef->RimeCandidateText,
                                    JString(env, context.menu.candidates[i].text));
                env->SetObjectField(candidate, GlobalRef->RimeCandidateComment,
                                    JString(env, context.menu.candidates[i].comment));
                env->SetObjectArrayElement(candidates, i, candidate);
                env->DeleteLocalRef(candidate);
            }
            env->SetObjectField(menu, GlobalRef->RimeMenuCandidates, candidates);
        }
            env->SetObjectField(menu, GlobalRef->RimeMenuSelectKeys,
                                JString(env, context.menu.select_keys));
            env->SetObjectField(jcontext, GlobalRef->RimeContextMenu, menu);
    }
    env->SetObjectField(jcontext, GlobalRef->RimeContextCommitTextPreview, JString(env, context.commit_text_preview));

    {
        if (RIME_STRUCT_HAS_MEMBER(context, context.select_labels)
            && context.select_labels) {
            int pageSize = context.menu.page_size;
            auto selectLabels = env->NewObjectArray(pageSize, GlobalRef->String, nullptr);
            for (int i = 0; i < pageSize; ++i) {
                env->SetObjectArrayElement(selectLabels, i,
                                           JString(env, context.select_labels[i]));
            }
            env->SetObjectField(jcontext, GlobalRef->RimeContextSelectLabels, selectLabels);
        } else {
            env->SetObjectField(jcontext, GlobalRef->RimeContextSelectLabels, nullptr);
        }
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_get_1context(JNIEnv *env, jclass /* thiz */, jobject jcontext) {
    RIME_STRUCT(RimeContext, context);
    Bool r = RimeGetContext(Rime::Instance().sessionId(), &context);
    if (r) {
        rimeContextToJObject(env, context, jcontext);
        RimeFreeContext(&context);
    }
    return r;
}

void rimeStatusToJObject(JNIEnv *env, const RimeStatus &status, const jobject &jstatus) {
    env->SetObjectField(jstatus, GlobalRef->RimeStatusSchemaId, JString(env, status.schema_id));
    env->SetObjectField(jstatus, GlobalRef->RimeStatusSchemaName, JString(env, status.schema_name));
    env->SetBooleanField(jstatus, GlobalRef->RimeStatusDisable, status.is_disabled);
    env->SetBooleanField(jstatus, GlobalRef->RimeStatusComposing, status.is_composing);
    env->SetBooleanField(jstatus, GlobalRef->RimeStatusAsciiMode, status.is_ascii_mode);
    env->SetBooleanField(jstatus, GlobalRef->RimeStatusFullShape, status.is_full_shape);
    env->SetBooleanField(jstatus, GlobalRef->RimeStatusSimplified, status.is_simplified);
    env->SetBooleanField(jstatus, GlobalRef->RimeStatusTraditional, status.is_traditional);
    env->SetBooleanField(jstatus, GlobalRef->RimeStatusAsciiPunct, status.is_ascii_punct);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_get_1status(JNIEnv *env, jclass /* thiz */, jobject jstatus) {
    RIME_STRUCT(RimeStatus, status);
    Bool r = RimeGetStatus(Rime::Instance().sessionId(), &status);
    if (r) {
        rimeStatusToJObject(env, status, jstatus);
        RimeFreeStatus(&status);
    }
    return r;
}

static bool is_save_option(const char* p) {
    bool is_save = false;
    std::string option_name(p);
    if (option_name.empty()) return is_save;
    RimeConfig config = {nullptr};
    bool b = RimeConfigOpen("default", &config);
    if (!b) return is_save;
    const char *key = "switcher/save_options";
    RimeConfigIterator iter = {nullptr};
    RimeConfigBeginList(&iter, &config, key);
    while(RimeConfigNext(&iter)) {
        std::string item(RimeConfigGetCString(&config, iter.path));
        if (option_name == item) {
            is_save = true;
            break;
        }
    }
    RimeConfigEnd(&iter);
    RimeConfigClose(&config);
    return is_save;
}

// runtime options
extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_set_1option(JNIEnv *env, jclass /* thiz */, jstring option, jboolean value) {
    const char* s = option == nullptr ? nullptr : env->GetStringUTFChars(option, nullptr);
    std::string option_name(s);
    RimeConfig config = {nullptr};
    bool b;
    if (is_save_option(s)) {
        b = RimeUserConfigOpen("user", &config);
        if (b) {
            std::string str("var/option/");
            str += option_name;
            b = RimeConfigSetBool(&config, str.c_str(), value);
        }
        RimeConfigClose(&config);
    }
    RimeSetOption(Rime::Instance().sessionId(), s, value);
    env->ReleaseStringUTFChars(option, s);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_get_1option(JNIEnv *env, jclass /* thiz */, jstring option) {
    const char* s = option == nullptr ? nullptr : env->GetStringUTFChars(option, nullptr);
    bool value = RimeGetOption(Rime::Instance().sessionId(), s);
    env->ReleaseStringUTFChars(option, s);
    return value;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_set_1property(JNIEnv *env, jclass /* thiz */, jstring prop, jstring value) {
    const char* s = prop == nullptr ? nullptr : env->GetStringUTFChars(prop, nullptr);
    const char* v = value == nullptr ? nullptr : env->GetStringUTFChars(value, nullptr);
    RimeSetProperty(Rime::Instance().sessionId(), s, v);
    env->ReleaseStringUTFChars(prop, s);
    env->ReleaseStringUTFChars(value, v);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1property(JNIEnv *env, jclass /* thiz */, jstring prop) {
    const char* s = prop == nullptr ? nullptr : env->GetStringUTFChars(prop, nullptr);
    char value[MAX_BUFFER_LENGTH] = {0};
    bool b = RimeGetProperty(Rime::Instance().sessionId(), s, value, MAX_BUFFER_LENGTH);
    env->ReleaseStringUTFChars(prop, s);
    return b ? env->NewStringUTF(value) : nullptr;
}

jobjectArray rimeSchemaListToJObjectArray(JNIEnv *env, RimeSchemaList &list) {
    jobjectArray array = env->NewObjectArray(static_cast<int>(list.size), GlobalRef->SchemaListItem,
                                             nullptr);
    for (int i = 0; i < list.size; i++) {
        auto item = list.list[i];
        auto obj = JRef<>(env, env->NewObject(GlobalRef->SchemaListItem, GlobalRef->SchemaListItemInit,
                                              *JString(env, item.schema_id),
                                              *JString(env, item.name)));
        env->SetObjectArrayElement(array, i, obj);
    }
    return array;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getRimeSchemaList(JNIEnv *env, jclass /* thiz */) {
    auto rime = rime_get_api();
    RimeSchemaList list = {0};
    rime->get_schema_list(&list);
    auto array = rimeSchemaListToJObjectArray(env, list);
    rime->free_schema_list(&list);
    return array;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getCurrentRimeSchema(JNIEnv *env, jclass /* thiz */) {
    auto rime = rime_get_api();
    char current[MAX_BUFFER_LENGTH] = {0};
    if (rime->get_current_schema(Rime::Instance().sessionId(), current, MAX_BUFFER_LENGTH)) {
        return env->NewStringUTF(current);
    } else {
        return env->NewStringUTF("");
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_selectRimeSchema(JNIEnv *env, jclass /* thiz */, jstring schema_id) {
    auto rime = rime_get_api();
    RimeConfig user = {nullptr};
    auto schema = CString(env, schema_id);
    if (rime->user_config_open("user", &user)) {
        rime->config_set_string(&user, "var/previously_selected_schema", schema);
        std::string key = "var/schema_access_time/" + std::string (schema);
        rime->config_set_int(&user, key.c_str(), time(nullptr));
        rime->config_close(&user);
    }
    return rime->select_schema(Rime::Instance().sessionId(), schema);
}

// configuration
extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1bool(JNIEnv *env, jclass /* thiz */, jstring name, jstring key) {
    const char* s = env->GetStringUTFChars(name, nullptr);
    RimeConfig config = {0};
    Bool b = RimeConfigOpen(s, &config);
    env->ReleaseStringUTFChars(name, s);
    Bool value;
    if (b) {
        s = env->GetStringUTFChars(key, nullptr);
        b = RimeConfigGetBool(&config, s, &value);
        env->ReleaseStringUTFChars(key, s);
    }
    RimeConfigClose(&config);
    if (!b) return nullptr;
    jclass jc = env->FindClass("java/lang/Boolean");
    jmethodID ctorID = env->GetMethodID(jc, "<init>", "(Z)V");
    auto ret = env->NewObject(jc, ctorID, value);
    env->DeleteLocalRef(jc);
    return ret;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_config_1set_1bool(JNIEnv *env, jclass /* thiz */, jstring name, jstring key, jboolean value) {
    const char* s = env->GetStringUTFChars(name, nullptr);
    RimeConfig config = {nullptr};
    Bool b = RimeConfigOpen(s, &config);
    env->ReleaseStringUTFChars(name, s);
    if (b) {
        s = env->GetStringUTFChars(key, nullptr);
        b = RimeConfigSetBool(&config, s, value);
        env->ReleaseStringUTFChars(key, s);
    }
    RimeConfigClose(&config);
    return b;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1int(JNIEnv *env, jclass /* thiz */, jstring name, jstring key) {
    const char* s = env->GetStringUTFChars(name, nullptr);
    RimeConfig config = {nullptr};
    Bool b = RimeConfigOpen(s, &config);
    env->ReleaseStringUTFChars(name, s);
    int value;
    if (b) {
        s = env->GetStringUTFChars(key, nullptr);
        b = RimeConfigGetInt(&config, s, &value);
        env->ReleaseStringUTFChars(key, s);
    }
    RimeConfigClose(&config);
    if (!b) return nullptr;
    jclass jc = env->FindClass("java/lang/Integer");
    jmethodID ctorID = env->GetMethodID(jc, "<init>", "(I)V");
    auto ret = env->NewObject(jc, ctorID, value);
    env->DeleteLocalRef(jc);
    return ret;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_config_1set_1int(JNIEnv *env, jclass /* thiz */, jstring name, jstring key, jint value) {
    const char* s = env->GetStringUTFChars(name, nullptr);
    RimeConfig config = {nullptr};
    Bool b = RimeConfigOpen(s, &config);
    env->ReleaseStringUTFChars(name, s);
    if (b) {
        s = env->GetStringUTFChars(key, nullptr);
        b = RimeConfigSetInt(&config, s, value);
        env->ReleaseStringUTFChars(key, s);
    }
    RimeConfigClose(&config);
    return b;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1double(JNIEnv *env, jclass /* thiz */, jstring name, jstring key) {
    const char* s = env->GetStringUTFChars(name, nullptr);
    RimeConfig config = {nullptr};
    Bool b = RimeConfigOpen(s, &config);
    env->ReleaseStringUTFChars(name, s);
    double value;
    if (b) {
        s = env->GetStringUTFChars(key, nullptr);
        b = RimeConfigGetDouble(&config, s, &value);
        env->ReleaseStringUTFChars(key, s);
    }
    RimeConfigClose(&config);
    if (!b) return nullptr;
    jclass jc = env->FindClass("java/lang/Double");
    jmethodID ctorID = env->GetMethodID(jc, "<init>", "(D)V");
    auto ret = env->NewObject(jc, ctorID, value);
    env->DeleteLocalRef(jc);
    return ret;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_config_1set_1double(JNIEnv *env, jclass /* thiz */, jstring name, jstring key, jdouble value) {
    const char* s = env->GetStringUTFChars(name, nullptr);
    RimeConfig config = {0};
    Bool b = RimeConfigOpen(s, &config);
    env->ReleaseStringUTFChars(name, s);
    if (b) {
        s = env->GetStringUTFChars(key, nullptr);
        b = RimeConfigSetDouble(&config, s, value);
        env->ReleaseStringUTFChars(key, s);
    }
    RimeConfigClose(&config);
    return b;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1string(JNIEnv *env, jclass /* thiz */, jstring name, jstring key) {
    const char* s = env->GetStringUTFChars(name, nullptr);
    RimeConfig config = {nullptr};
    Bool b = RimeConfigOpen(s, &config);
    env->ReleaseStringUTFChars(name, s);
    char value[MAX_BUFFER_LENGTH] = {0};
    if (b) {
        s = env->GetStringUTFChars(key, nullptr);
        b = RimeConfigGetString(&config, s, value, MAX_BUFFER_LENGTH);
        env->ReleaseStringUTFChars(key, s);
    }
    RimeConfigClose(&config);
    return b ? env->NewStringUTF(value) : nullptr;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_config_1set_1string(JNIEnv *env, jclass /* thiz */, jstring name, jstring key, jstring value) {
    const char* s = env->GetStringUTFChars(name, nullptr);
    RimeConfig config = {nullptr};
    Bool b = RimeConfigOpen(s, &config);
    env->ReleaseStringUTFChars(name, s);
    if (b) {
        s = env->GetStringUTFChars(key, nullptr);
        const char* v = env->GetStringUTFChars(value, nullptr);
        b = RimeConfigSetString(&config, s, v);
        env->ReleaseStringUTFChars(key, s);
        env->ReleaseStringUTFChars(key, v);
    }
    RimeConfigClose(&config);
    return b;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_config_1list_1size(JNIEnv *env, jclass /* thiz */, jstring name, jstring key) {
    const char* s = env->GetStringUTFChars(name, nullptr);
    RimeConfig config = {nullptr};
    Bool b = RimeConfigOpen(s, &config);
    env->ReleaseStringUTFChars(name, s);
    int value = 0;
    if (b) {
        s = env->GetStringUTFChars(key, nullptr);
        value = RimeConfigListSize(&config, s);
        env->ReleaseStringUTFChars(key, s);
    }
    RimeConfigClose(&config);
    return value;
}

//testing
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_simulate_1key_1sequence(JNIEnv *env, jclass /* thiz */, jstring key_sequence) {
    const char* str = key_sequence == nullptr ? nullptr : env->GetStringUTFChars(key_sequence, nullptr);
    if (str == nullptr) return false; /* OutOfMemoryError already thrown */
    jboolean r = RimeSimulateKeySequence(Rime::Instance().sessionId(), str);
    env->ReleaseStringUTFChars(key_sequence, str);
    return r;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1input(JNIEnv *env, jclass /* thiz */) {
    const char* c = rime_get_api()->get_input(Rime::Instance().sessionId());
    return env->NewStringUTF(c);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_get_1caret_1pos(JNIEnv *env, jclass /* thiz */) {
    return rime_get_api()->get_caret_pos(Rime::Instance().sessionId());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_set_1caret_1pos(JNIEnv *env, jclass /* thiz */, jint caret_pos) {
    return rime_get_api()->set_caret_pos(Rime::Instance().sessionId(), caret_pos);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_select_1candidate(JNIEnv *env, jclass /* thiz */, jint index) {
    return rime_get_api()->select_candidate(Rime::Instance().sessionId(), index);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_select_1candidate_1on_1current_1page(JNIEnv *env, jclass /* thiz */, jint index) {
    return rime_get_api()->select_candidate_on_current_page(Rime::Instance().sessionId(), index);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_delete_1candidate(JNIEnv *env, jclass /* thiz */, jint index) {
    return rime_get_api()->delete_candidate(Rime::Instance().sessionId(), index);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_delete_1candidate_1on_1current_1page(JNIEnv *env, jclass /* thiz */, jint index) {
    return rime_get_api()->delete_candidate_on_current_page(Rime::Instance().sessionId(), index);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1librime_1version(JNIEnv *env, jclass /* thiz */) {
    return env->NewStringUTF(LIBRIME_VERSION);
}

static jobject rimeConfigListToJObject(JNIEnv *env, RimeConfig* config, const std::string &key) {
    auto rime = rime_get_api();
    RimeConfigIterator iter = {nullptr};
    if (!rime->config_begin_list(&iter, config, key.c_str())) return nullptr;
    auto size = rime->config_list_size(config, key.c_str());
    auto obj = env->NewObject(GlobalRef->ArrayList, GlobalRef->ArrayListInit, size);
    int i = 0;
    while (RimeConfigNext(&iter)) {
        auto e = JRef<>(env, rimeConfigValueToJObject(env, config, iter.path));
        env->CallVoidMethod(obj, GlobalRef->ArrayListAdd, i++, *e);
    }
    rime->config_end(&iter);
    return obj;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1list(JNIEnv *env, jclass /* thiz */, jstring name, jstring key) {
    const char* s = env->GetStringUTFChars(name, nullptr);
    RimeConfig config = {nullptr};
    Bool b = RimeConfigOpen(s, &config);
    env->ReleaseStringUTFChars(name, s);
    jobject value = nullptr;
    if (b) {
        s = env->GetStringUTFChars(key, nullptr);
        value = rimeConfigListToJObject(env, &config, s);
        env->ReleaseStringUTFChars(key, s);
    }
    RimeConfigClose(&config);
    return value;
}

static jobject rimeConfigMapToJObject(JNIEnv *env, RimeConfig *config, const std::string &key) {
    auto rime = rime_get_api();
    RimeConfigIterator iter = {nullptr};
    if (!rime->config_begin_map(&iter, config, key.c_str())) return nullptr;
    auto obj = env->NewObject(GlobalRef->HashMap, GlobalRef->HashMapInit);
    while (rime->config_next(&iter)) {
        auto v = JRef<>(env, rimeConfigValueToJObject(env, config, iter.path));
        env->CallObjectMethod(obj, GlobalRef->HashMapPut, *JString(env, iter.key), *v);
    }
    rime->config_end(&iter);
    return obj;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeConfigMap(JNIEnv *env, jclass clazz, jstring config_id, jstring key) {
    auto rime = rime_get_api();
    RimeConfig config = {nullptr};
    jobject value = nullptr;
    if (rime->config_open(CString(env, config_id), &config)) {
        value = rimeConfigMapToJObject(env, &config, CString(env, key));
        rime->config_close(&config);
    }
    return value;
}

jobject rimeConfigValueToJObject(JNIEnv *env, RimeConfig *config, const std::string &key) {
    auto rime = rime_get_api();

    const char *value;
    if ((value = rime->config_get_cstring(config, key.c_str()))) {
        return env->NewStringUTF(value);
    }
    jobject list;
    if ((list = rimeConfigListToJObject(env, config, key))) {
        return list;
    }
    return rimeConfigMapToJObject(env, config, key);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeConfigValue(JNIEnv *env, jclass clazz, jstring config_id, jstring key) {
    auto rime = rime_get_api();
    RimeConfig config = {nullptr};
    jobject obj = nullptr;
    if (rime->config_open(CString(env, config_id), &config)) {
        obj = rimeConfigValueToJObject(env, &config, CString(env, key));
        rime->config_close(&config);
    }
    return obj;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeSchemaValue(JNIEnv *env, jclass clazz, jstring schema_id, jstring key) {
    auto rime = rime_get_api();
    RimeConfig config = {nullptr};
    jobject obj = nullptr;
    if (rime->schema_open(CString(env, schema_id), &config)) {
        obj = rimeConfigValueToJObject(env, &config, CString(env, key));
        rime->config_close(&config);
    }
    return obj;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_run_1task(JNIEnv *env, jclass /* thiz */, jstring task_name) {
    const char* s = env->GetStringUTFChars(task_name, nullptr);
    RimeConfig config = {nullptr};
    Bool b = RimeRunTask(s);
    env->ReleaseStringUTFChars(task_name, s);
    return b;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1shared_1data_1dir(JNIEnv *env, jclass /* thiz */) {
    return env->NewStringUTF(RimeGetSharedDataDir());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeUserDataDir(JNIEnv *env, jclass /* thiz */) {
    return env->NewStringUTF(rime_get_api()->get_user_data_dir());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1sync_1dir(JNIEnv *env, jclass /* thiz */) {
    return env->NewStringUTF(RimeGetSyncDir());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1user_1id(JNIEnv *env, jclass /* thiz */) {
    return env->NewStringUTF(RimeGetUserId());
}
