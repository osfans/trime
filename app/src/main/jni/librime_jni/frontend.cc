#include "frontend.h"

#include <rime/context.h>
#include <rime/service.h>

using namespace rime;

size_t rime_get_highlighted_candidate_index(RimeSessionId session_id) {
  an<Session> session(Service::instance().GetSession(session_id));
  if (!session) return 0;
  Context* ctx = session->context();
  if (!ctx || !ctx->HasMenu()) return 0;
  auto& seg(ctx->composition().back());
  return seg.selected_index;
}
