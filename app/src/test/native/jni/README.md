# JNI Unicode transport check

This host-only test includes the production `jni-utils.h` and `objconv.h`. It
uses a real JDK 17 VM with `-Xcheck:jni`, a 128 MiB Java heap, 64 MiB Metaspace,
and the production NDK `jni.h`. It does not construct `GlobalRefSingleton` or
stand in for Android ART, a Rime session, or device input tests.

Initialize the pinned librime submodule, then run on Linux:

```sh
python3 app/src/test/native/jni/run_unicode.py \
  --jdk /path/to/jdk17 \
  --ndk /path/to/android-ndk \
  --rime-source app/src/main/jni/librime \
  --out /tmp/trime-jni-unicode-check
```

Use an empty output path. An optional `--baseline <git-sha>` first compiles the
same valid-Unicode cases against those original Git headers and expects a
supplementary-character failure. Only that control disables the old signedness
and pessimizing-move diagnostics; the current headers compile with `-Werror`.
Every compiler and runtime stdout/stderr stream is retained under the output.

The check covers ASCII, BMP and supplementary Hanzi, emoji, a confirmed-prefix
shape, embedded NUL in length-aware strings, invalid UTF-8 replaced by U+FFFD,
isolated UTF-16 surrogates with a retained valid prefix, explicit null policy,
Unicode exception messages, and original pending-exception identity. Array
conversion uses 1,024 entries for 32 rounds, checks order and text, and rejects
null elements without returning a partial vector. Native `const char*` consumers
still have ordinary C-string termination semantics; this does not claim that
Rime APIs accept embedded NUL in configuration keys or input.
