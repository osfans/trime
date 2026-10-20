# Nine-key Pinyin input

Enable **朙月拼音九键** (`luna_pinyin_t9`) in the existing input-scheme selector,
deploy the configuration, and choose that scheme. The built-in default and
standard themes provide its layout. Existing users' scheme selections and custom
configuration are preserved; no prediction model or extra dictionary is installed.
The scheme uses the existing `luna_pinyin` dictionary and its user dictionary.

Press 2–9 to enter Pinyin. The spelling panel shows the current segmentation and
alternative syllables. Select a syllable to lock its spelling, select a segment
to change the focus, or use unlock, undo, and cancel. Locked spellings are represented
privately inside the engine; preedit text and literal commits retain the original
input instead of exposing those markers. Stale spelling actions are rejected by
the native state revision.

Long-press a number to enter it literally. If a composition is pending, this follows
the complete raw-input Return path before adding the digit. The number and symbol
pages use literal mode; returning to the default layout restores the current
scheme. Switching to the English layout follows the existing layout-switch commit
behavior. Restarting an editor finishes its currently visible composing text and
clears native spelling locks, matching the existing finish-input behavior.
Custom themes need to provide the `luna_pinyin_t9` keyboard or an
appropriate `trime/keyboard` mapping.

The existing batched candidate response is retained. One additional bounded T9
state snapshot is read on the same Rime dispatcher; candidate rows do not perform
individual syllable queries. This module does not enable next-word prediction,
optional fuzzy-spelling preferences, or candidate pinning.

Build and JVM checks use the normal project commands. Native T9 checks and their
host dependencies are described in `app/src/test/native/t9/README.md`.
