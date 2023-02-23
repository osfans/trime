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

    bool processKey(int keycode, int mask) {
        return rime->process_key(session, keycode, mask);
    }

    bool simulateKeySequence(const std::string &sequence) {
        return rime->simulate_key_sequence(session, sequence.data());
    }

    bool commitComposition() {
        return rime->commit_composition(session);
    }

    void clearComposition() {
        rime->clear_composition(session);
    }

    void setOption(const std::string &key, bool value) {
        rime->set_option(session, key.c_str(), value);
    }

    bool getOption(const std::string &key) {
        return rime->get_option(session, key.c_str());
    }

    std::string currentSchemaId() {
        char result[MAX_BUFFER_LENGTH];
        return rime->get_current_schema(session, result, MAX_BUFFER_LENGTH) ? result : "";
    }

    bool selectSchema(const std::string &schemaId) {
        return rime->select_schema(session, schemaId.c_str());
    }

    std::string rawInput() {
        return rime->get_input(session);
    }

    size_t caretPosition() {
        return rime->get_caret_pos(session);
    }

    void setCaretPosition(size_t caretPos) {
        rime->set_caret_pos(session, caretPos);
    }

    bool selectCandidateOnCurrentPage(size_t index) {
        return rime->select_candidate_on_current_page(session, index);
    }

    bool deleteCandidateOnCurrentPage(size_t index) {
        return rime->delete_candidate_on_current_page(session, index);
    }

    void exit() {
        rime->destroy_session(session);
        session = 0;
        rime->finalize();
    }

    bool sync() {
        return rime->sync_user_data();
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

GlobalRefSingleton *GlobalRef;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* jvm, void* reserved)
{
    GlobalRef = new GlobalRefSingleton(jvm);
    declare_librime_module_dependencies();
    return JNI_VERSION_1_6;
}

static jobject rimeConfigValueToJObject(JNIEnv *env, RimeConfig* config, const std::string &key);

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

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_exitRime(JNIEnv *env, jclass /* thiz */) {
    RETURN_IF_NOT_RUNNING
    Rime::Instance().exit();
}

// deployment
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deployRimeSchemaFile(JNIEnv *env, jclass /* thiz */, jstring schema_file) {
    return rime_get_api()->deploy_schema(CString(env, schema_file));
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deployRimeConfigFile(JNIEnv *env, jclass /* thiz */, jstring file_name, jstring version_key) {
    return rime_get_api()->deploy_config_file(CString(env, file_name), CString(env, version_key));
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_syncRimeUserData(JNIEnv *env, jclass /* thiz */) {
    return Rime::Instance().sync();
}

// input
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_processRimeKey(JNIEnv *env, jclass /* thiz */, jint keycode, jint mask) {
    RETURN_VALUE_IF_NOT_RUNNING(false)
    return Rime::Instance().processKey(keycode, mask);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_commitRimeComposition(JNIEnv *env, jclass /* thiz */) {
    RETURN_VALUE_IF_NOT_RUNNING(false)
    return Rime::Instance().commitComposition();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_clearRimeComposition(JNIEnv *env, jclass /* thiz */) {
    RETURN_IF_NOT_RUNNING
    Rime::Instance().clearComposition();
}

void rimeCommitToJObject(JNIEnv *env, const RimeCommit &commit, const jobject &jcommit) {
    env->SetObjectField(jcommit, GlobalRef->RimeCommitText, JString(env, commit.text));
}

// output
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_getRimeCommit(JNIEnv *env, jclass /* thiz */, jobject jcommit) {
    RETURN_VALUE_IF_NOT_RUNNING(false)
    RIME_STRUCT(RimeCommit, commit)
    auto rime = rime_get_api();
    if (rime->get_commit(Rime::Instance().sessionId(), &commit)) {
        rimeCommitToJObject(env, commit, jcommit);
        rime->free_commit(&commit);
        return true;
    }
    return false;
}

void rimeContextToJObject(JNIEnv *env, const RimeContext &context, const jobject &jcontext) {
    auto composition = JRef<>(env, env->AllocObject(GlobalRef->RimeComposition));
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

    const auto &menu = context.menu;
    auto jmenu = JRef<>(env, env->AllocObject(GlobalRef->RimeMenu));
    env->SetIntField(jmenu, GlobalRef->RimeMenuPageSize, menu.page_size);
    env->SetIntField(jmenu, GlobalRef->RimeMenuPageNo, menu.page_no);
    env->SetBooleanField(jmenu, GlobalRef->RimeMenuIsLastPage, menu.is_last_page);
    env->SetIntField(jmenu, GlobalRef->RimeMenuHighlightedCandidateIndex,
                     menu.highlighted_candidate_index);
    env->SetIntField(jmenu, GlobalRef->RimeMenuNumCandidates, context.menu.num_candidates);

    size_t numSelectKeys = menu.select_keys ? std::strlen(menu.select_keys) : 0;
    bool hasLabel = RIME_STRUCT_HAS_MEMBER(context, context.select_labels) && context.select_labels;
    auto selectLabels = JRef<jobjectArray>(env, env->NewObjectArray(menu.num_candidates, GlobalRef->String, nullptr));
    auto candidates = JRef<jobjectArray>(env, env->NewObjectArray(menu.num_candidates, GlobalRef->CandidateListItem, nullptr));
    for (int i = 0; i < menu.num_candidates; ++i) {
        std::string label;
        if (i < menu.page_size && hasLabel) {
            label = context.select_labels[i];
        } else if (i < numSelectKeys) {
            label = std::string(1, menu.select_keys[i]);
        } else {
            label = std::to_string((i + 1) % 10);
        }
        label.append(" ");
        env->SetObjectArrayElement(selectLabels, i, JString(env, label));
        auto &candidate = context.menu.candidates[i];
        auto jcandidate = JRef<>(env, env->NewObject(GlobalRef->CandidateListItem, GlobalRef->CandidateListItemInit,
                                                     *JString(env, candidate.comment ? candidate.comment : ""),
                                                     *JString(env, candidate.text ? candidate.text : "")));
        env->SetObjectArrayElement(candidates, i, jcandidate);
    }
    env->SetObjectField(jmenu, GlobalRef->RimeMenuCandidates, candidates);

    env->SetObjectField(jcontext, GlobalRef->RimeContextMenu, jmenu);
    env->SetObjectField(jcontext, GlobalRef->RimeContextCommitTextPreview, JString(env, context.commit_text_preview));
    env->SetObjectField(jcontext, GlobalRef->RimeContextSelectLabels, selectLabels);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_getRimeContext(JNIEnv *env, jclass /* thiz */, jobject jcontext) {
    RETURN_VALUE_IF_NOT_RUNNING(false)
    RIME_STRUCT(RimeContext, context)
    auto rime = rime_get_api();
    if (rime->get_context(Rime::Instance().sessionId(), &context)) {
        rimeContextToJObject(env, context, jcontext);
        rime->free_context(&context);
        return true;
    }
    return false;
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
Java_com_osfans_trime_core_Rime_getRimeStatus(JNIEnv *env, jclass /* thiz */, jobject jstatus) {
    RETURN_VALUE_IF_NOT_RUNNING(false)
    RIME_STRUCT(RimeStatus, status)
    auto rime = rime_get_api();
    Bool r = RimeGetStatus(Rime::Instance().sessionId(), &status);
    if (rime->get_status(Rime::Instance().sessionId(), &status)) {
        rimeStatusToJObject(env, status, jstatus);
        rime->free_status(&status);
        return true;
    }
    return false;
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
Java_com_osfans_trime_core_Rime_setRimeOption(JNIEnv *env, jclass /* thiz */, jstring option, jboolean value) {
    RETURN_IF_NOT_RUNNING
    auto rime = rime_get_api();
    RimeConfig user = {nullptr};
    auto opt = CString(env, option);
    bool b;
    if (is_save_option(opt)) {
        if (rime->user_config_open("user", &user)) {
            std::string key = "var/option/" + std::string (opt);
            rime->config_set_bool(&user, key.c_str(), value);
            rime->config_close(&user);
        }
    }
    Rime::Instance().setOption(opt, value);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_getRimeOption(JNIEnv *env, jclass /* thiz */, jstring option) {
    RETURN_VALUE_IF_NOT_RUNNING(false)
    return Rime::Instance().getOption(CString(env, option));
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
    RETURN_VALUE_IF_NOT_RUNNING(env->NewStringUTF(""))
    return env->NewStringUTF(Rime::Instance().currentSchemaId().c_str());
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
    return Rime::Instance().selectSchema(schema);
}

//testing
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_simulateKeySequence(JNIEnv *env, jclass /* thiz */, jstring key_sequence) {
    RETURN_VALUE_IF_NOT_RUNNING(false)
    return Rime::Instance().simulateKeySequence(CString(env, key_sequence));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeRawInput(JNIEnv *env, jclass /* thiz */) {
    RETURN_VALUE_IF_NOT_RUNNING(env->NewStringUTF(""))
    return env->NewStringUTF(Rime::Instance().rawInput().data());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_getRimeCaretPos(JNIEnv *env, jclass /* thiz */) {
    RETURN_VALUE_IF_NOT_RUNNING(-1)
    return static_cast<jint>(Rime::Instance().caretPosition());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_setRimeCaretPos(JNIEnv *env, jclass /* thiz */, jint caret_pos) {
    RETURN_IF_NOT_RUNNING
    Rime::Instance().setCaretPosition(caret_pos);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_selectRimeCandidateOnCurrentPage(JNIEnv *env, jclass /* thiz */, jint index) {
    RETURN_VALUE_IF_NOT_RUNNING(false)
    return Rime::Instance().selectCandidateOnCurrentPage(index);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deleteRimeCandidateOnCurrentPage(JNIEnv *env, jclass /* thiz */, jint index) {
    RETURN_VALUE_IF_NOT_RUNNING(false)
    return Rime::Instance().deleteCandidateOnCurrentPage(index);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getLibrimeVersion(JNIEnv *env, jclass /* thiz */) {
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
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_runRimeTask(JNIEnv *env, jclass /* thiz */, jstring task_name) {
    const char* s = env->GetStringUTFChars(task_name, nullptr);
    RimeConfig config = {nullptr};
    Bool b = RimeRunTask(s);
    env->ReleaseStringUTFChars(task_name, s);
    return b;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeSharedDataDir(JNIEnv *env, jclass /* thiz */) {
    return env->NewStringUTF(RimeGetSharedDataDir());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeUserDataDir(JNIEnv *env, jclass /* thiz */) {
    return env->NewStringUTF(rime_get_api()->get_user_data_dir());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeSyncDir(JNIEnv *env, jclass /* thiz */) {
    return env->NewStringUTF(RimeGetSyncDir());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeUserId(JNIEnv *env, jclass /* thiz */) {
    return env->NewStringUTF(RimeGetUserId());
}
