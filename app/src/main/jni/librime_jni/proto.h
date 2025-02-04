#pragma once

#include <rime_api.h>

#ifdef __cplusplus
extern "C" {
#endif

//! For passing pointer to jni object as opaque pointer through C API.
#define RIME_PROTO_BUILDER void

typedef struct rime_proto_api_t {
  int data_size;

  void (*commit_proto)(RimeSessionId session_id,
                       RIME_PROTO_BUILDER* commit_builder);
  void (*context_proto)(RimeSessionId session_id,
                        RIME_PROTO_BUILDER* context_builder);
  void (*status_proto)(RimeSessionId session_id,
                       RIME_PROTO_BUILDER* status_builder);
} RimeProtoApi;

#ifdef __cplusplus
}
#endif
