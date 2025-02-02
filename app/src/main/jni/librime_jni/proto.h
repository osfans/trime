#pragma once

#include <jni.h>
#include <rime_api.h>

#ifdef __cplusplus
extern "C" {
#endif

//! For passing pointer to jni object as opaque pointer through C API.
#define RIME_PROTO_OBJ jobject

typedef struct rime_proto_api_t {
  int data_size;

  RIME_PROTO_OBJ (*commit_proto)(RimeSessionId session_id);
  RIME_PROTO_OBJ (*context_proto)(RimeSessionId session_id);
  RIME_PROTO_OBJ (*status_proto)(RimeSessionId session_id);
} RimeProtoApi;

#ifdef __cplusplus
}
#endif
