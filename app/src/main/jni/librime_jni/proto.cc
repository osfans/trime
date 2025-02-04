#include "proto.h"

#include <rime/component.h>
#include <rime/composition.h>
#include <rime/context.h>
#include <rime/menu.h>
#include <rime/schema.h>
#include <rime/service.h>
#include <rime_api.h>

#include "jni-utils.h"

using namespace rime;

void rime_commit_proto(RimeSessionId session_id,
                       RIME_PROTO_BUILDER *commit_builder) {
  an<Session> session(Service::instance().GetSession(session_id));
  if (!session) return;
  auto env = GlobalRef->AttachEnv();
  const string &commit_text(session->commit_text());
  if (!commit_text.empty()) {
    auto *commit = (jobject *)commit_builder;
    *commit = env->NewObject(GlobalRef->CommitProto, GlobalRef->CommitProtoInit,
                             *JString(env, commit_text));
    session->ResetCommitText();
  }
}

void rime_context_proto(RimeSessionId session_id,
                        RIME_PROTO_BUILDER *context_builder) {
  an<Session> session = Service::instance().GetSession(session_id);
  if (!session) return;
  Context *ctx = session->context();
  if (!ctx) return;
  auto env = GlobalRef->AttachEnv();
  auto *context = (jobject *)context_builder;
  jobject composition = env->NewObject(GlobalRef->CompositionProto,
                                       GlobalRef->CompositionProtoDefault);
  if (ctx->IsComposing()) {
    const Preedit &preedit = ctx->GetPreedit();
    composition = env->NewObject(
        GlobalRef->CompositionProto, GlobalRef->CompositionProtoInit,
        preedit.text.length(), preedit.caret_pos, preedit.sel_start,
        preedit.sel_end, *JString(env, preedit.text),
        *JString(env, ctx->GetCommitText()));
  }
  jobject menu =
      env->NewObject(GlobalRef->MenuProto, GlobalRef->MenuProtoDefault);
  if (ctx->HasMenu()) {
    Segment &seg = ctx->composition().back();
    Schema *schema = session->schema();
    int page_size = schema ? schema->page_size() : 5;
    int selected_index = seg.selected_index;
    int page_number = selected_index / page_size;
    int highlighted_index = selected_index % page_size;
    const string &select_keys = schema ? schema->select_keys() : "";
    the<Page> page(seg.menu->CreatePage(page_size, page_number));
    if (page) {
      vector<string> labels;
      auto dest_labels =
          env->NewObjectArray(page_size, GlobalRef->String, nullptr);
      if (schema) {
        Config *config = schema->config();
        auto src_labels = config->GetList("menu/alternative_select_labels");
        if (src_labels && (size_t)page_size <= src_labels->size()) {
          for (int i = 0; i < page_size; ++i) {
            if (an<ConfigValue> value = src_labels->GetValueAt(i)) {
              env->SetObjectArrayElement(dest_labels, i,
                                         *JString(env, value->str()));
              labels.emplace_back(value->str());
            }
          }
        } else if (!select_keys.empty()) {
          for (const char key : select_keys) {
            labels.emplace_back(1, key);
            if (labels.size() >= page_size) break;
          }
        }
      }
      int num_candidates = page->candidates.size();
      auto dest_candidates = env->NewObjectArray(
          num_candidates, GlobalRef->CandidateProto, nullptr);
      int index = 0;
      for (const an<Candidate> &src : page->candidates) {
        const string &label =
            index < labels.size() ? labels[index] : std::to_string(index + 1);
        auto dest = JRef(env, env->NewObject(GlobalRef->CandidateProto,
                                             GlobalRef->CandidateProtoInit,
                                             *JString(env, src->text()),
                                             *JString(env, src->comment()),
                                             *JString(env, label)));
        env->SetObjectArrayElement(dest_candidates, index++, *dest);
      }
      menu = env->NewObject(
          GlobalRef->MenuProto, GlobalRef->MenuProtoInit, page_size,
          page_number, page->is_last_page, highlighted_index,
          *JRef<jobjectArray>(env, dest_candidates), *JString(env, select_keys),
          *JRef<jobjectArray>(env, dest_labels));
    }
  }
  *context =
      env->NewObject(GlobalRef->ContextProto, GlobalRef->ContextProtoInit,
                     *JRef(env, composition), *JRef(env, menu),
                     *JString(env, ctx->input()), ctx->caret_pos());
}

void rime_status_proto(RimeSessionId session_id,
                       RIME_PROTO_BUILDER *status_builder) {
  an<Session> session(Service::instance().GetSession(session_id));
  if (!session) return;
  Schema *schema = session->schema();
  Context *ctx = session->context();
  if (!schema || !ctx) return;
  auto env = GlobalRef->AttachEnv();
  auto *status = (jobject *)status_builder;
  *status = env->NewObject(
      GlobalRef->StatusProto, GlobalRef->StatusProtoInit,
      *JString(env, schema->schema_id()), *JString(env, schema->schema_name()),
      Service::instance().disabled(), ctx->IsComposing(),
      ctx->get_option("ascii_mode"), ctx->get_option("full_shape"),
      ctx->get_option("simplification"), ctx->get_option("traditional"),
      ctx->get_option("ascii_punct"));
}

static void rime_proto_initialize() {}

static void rime_proto_finalize() {}

static RimeCustomApi *rime_proto_get_api() {
  static RimeProtoApi s_api = {0};
  if (!s_api.data_size) {
    RIME_STRUCT_INIT(RimeProtoApi, s_api);
    s_api.commit_proto = &rime_commit_proto;
    s_api.context_proto = &rime_context_proto;
    s_api.status_proto = &rime_status_proto;
  }
  return (RimeCustomApi *)&s_api;
}

RIME_REGISTER_CUSTOM_MODULE(proto) { module->get_api = &rime_proto_get_api; }
