// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <rime/key_event.h>

namespace trime::prediction {
inline bool ShouldDismissForKey(const rime::KeyEvent& key) {
  const auto code = key.keycode();
  // ascii_composer may commit on Shift press/release before predictor runs.
  // Dismiss before either edge, including macro and JNI/T9 entry points.
  if (code >= XK_Shift_L && code <= XK_Hyper_R) return true;
  if (key.release()) return false;
  if (!key.ctrl() && !key.alt() && !key.super()) {
    switch (code) {
      case XK_Up: case XK_Down: case XK_Left: case XK_Right:
      case XK_Prior: case XK_Next: case XK_Home: case XK_End:
      case XK_KP_Up: case XK_KP_Down: case XK_KP_Left: case XK_KP_Right:
      case XK_KP_Prior: case XK_KP_Next: case XK_KP_Home: case XK_KP_End:
        return false;
    }
  }
  return true;
}
}  // namespace trime::prediction
