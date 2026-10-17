# T00：可追溯的九键测试基线

这套工具把 APK、方案、词典、固定学习步骤和测试产物关联到一个不可复用的 run ID。
它不安装、卸载或清空手机上的 Trime，不读取个人词库内容。每次测试创建新的用户目录，不提供 reset 命令。

## 环境

- Python 3.11 或更高版本，仅使用标准库。
- Android SDK 的 `apkanalyzer`、`apksigner`、`adb`，以及 JDK 17；设置 `JAVA_HOME` 和 `ANDROID_SDK_ROOT`。
- 编译测试驱动需要已初始化的 librime/OpenCC 子模块。Linux 本地冒烟需要 C++17 编译器和系统 librime。
- 真机引擎测试需要 Linux Android NDK，当前驱动支持 APK 与设备共同支持的 `arm64-v8a` 或 `x86_64`。

SDK 工具的 Java 堆上限为 256 MiB。测试只编译一个小型驱动，不运行 Gradle、不重新编译 Trime 或 Rime，也不启动模拟器。两个词库状态顺序执行。

## 1. 先确认手机实际安装的版本

在仓库根目录执行，`SERIAL` 使用 `adb devices -l` 显示的设备序列号：

```sh
python3 script/baseline/baseline.py device --serial SERIAL
```

命令只读取 Android 版本、API、系统构建指纹、设备 ABI，以及 `com.osfans.trime` / `com.osfans.trime.debug` 的安装包。
结果在 `build/baseline/device-*/inventory.json`。包括已安装的 APK 校验和、签名证书 SHA-256、manifest 版本与包内 Git SHA。
split APK 的校验和会逐一记录。未连接设备、未授权或读取失败时，不产生“已验证手机版本”的结论。

## 2. 固定 latest 与 develop APK

```sh
python3 script/baseline/baseline.py fetch-latest
python3 script/baseline/baseline.py prepare --apk /path/to/latest-arm64.apk --label latest-TAG
python3 script/baseline/baseline.py prepare --apk /path/to/develop.apk --label develop-GIT_SHA
```

`fetch-latest` 返回下载文件路径；同目录 `release.json` 保留当时的 release ID、标签、下载地址与 GitHub 提供的资产摘要，并验证下载 SHA-256。
`prepare` 返回新 run 目录。重复执行会产生不同目录，不会覆盖已有数据。

每个 run 包含：

```text
RUN/
  owner.json
  report.json                    完整构建身份、资源摘要和执行状态
  report.md                      简明报告
  inputs/application.apk         冻结的 APK
  inputs/shared/                 该 APK 实际携带的方案与词典
  inputs/lib/                    该 APK 实际携带的 native 库
  inputs/corpus.json             固定测量及学习步骤
  inputs/user-seed/               两组共同的最小测试配置
  cold/user/                     全新词库组
  learned/user/                  固定学习后词库组
```

测试配置只在专用目录中将方案列表设为 corpus 指定的方案，避免部署无关方案；不会重写 APK 中的 schema 或词典。
`fixture_version=2` 同时将 APK 的 OpenCC 文本与配置复制到各自的 `user/opencc`。
驱动使用 APK 导出的 `opencc::ConvertDictionary`，执行与应用相同的 TXT 到 OCD2 转换，并验证“漢字”转换为“汉字”。
Android 驱动要求兼容的 NDK libc++ ABI；无法找到转换函数或转换失败就停止，不静默跳过简繁转换。
早期缺少该部署步骤的报告没有 `fixture_version=2`，现在 `verify` 会拒绝将其视为有效基线。
每个资源文件、schema、corpus、APK、native 库都有 SHA-256；测试开始前检查冻结文件没有改变。
`report.json` 中 `not_run` 明确表示未运行，目录存在不代表验收通过。

包内 `BuildConfig.BUILD_COMMIT_HASH` 是 Git SHA 来源。旧包经混淆后可能读不到，报告会标为 unknown，不会根据文件名、release 标签或当前工作树猜测。
未包含工作树状态的旧包也不会被声明为“干净源码构建”；APK 哈希用于精确区分二进制。

## 3. 真机执行全新与学习后两组测试

```sh
python3 script/baseline/baseline.py run --run /path/to/RUN --serial SERIAL --ndk /path/to/android-ndk
```

测试驱动通过 `rime_get_api` 加载冻结 APK 自带的 `librime_jni.so`，方案与词典也来自同一 APK。
仅使用新建的 `/data/local/tmp/trime-baseline-UUID/`，不使用 Android 应用的数据目录。
驱动同时检查隔离目录的 owner 标记；任一组已执行、已失败或存在额外用户文件，都要求重新 `prepare`。

执行顺序：

1. `cold` 从共同 seed 开始，输入固定数字串，读取候选和预编辑，不选词、不上屏。
2. `learned` 从独立的同一 seed 开始，按 `corpus.json` 对“你”和“你好”各选择并提交五次。
3. 每次学习按候选文字找到正确项，验证最终提交内容，不盲选固定序号。找不到目标时测试失败。
4. 学习进程退出并完成 Rime finalize 后，启动新进程读取相同用户目录，验证学习后的候选。
5. 两组分别保存 `measure.json`、用户目录和编译资源的文件摘要；学习组另存 `train.json`。

候选测量覆盖 `64`、`64426`、`9426`、显式音节分隔及特殊韵母相关输入。
结果记录实际 schema、选项、Rime 版本、数字输入、预编辑、候选文字和注释；测试不要求初版具备尚未实现的可点击拼音筛选。
学习后不承诺所有候选排序必然改变。学习步骤和实际提交记录作为证据，结果本身保留原样。

如仅比较候选而非首次词典部署耗时，可复用**同一个 APK** 已生成的系统词典，减少慢速转译下的重复编译：

```sh
python3 script/baseline/baseline.py prepare --apk /path/to/application.apk --label cached-dictionary --compiled-from /path/to/PREVIOUS_RUN
```

仅导入经过文件摘要验证的 `cold/user/build/` 中的 YAML 与 BIN，记录来源 run ID、APK 摘要及文件清单。
不会复制用户数据库、学习数据或 installation ID。两组依然从同一 seed、各自独立的新用户词库开始。
这种结果不代表全新安装的部署时间；不同 APK 之间禁止复用编译缓存。
来源必须是 `apk_native_engine`，不能用 Linux 系统引擎的编译结果冒充 APK 编译结果。
也可给 `run` 增加 `--reuse-cold-build`，让学习组只复用本次 cold 组刚编译出的系统词典；报告会单独记录该来源和摘要。

这是 **APK 原生引擎测试**，没有经过 Android 键盘 UI/JNI 上层事件流。
`engine_query_ms` 只表示该进程输入完整测试串并读取候选的时间，不是按键到屏幕延迟，不能代替后续真机 UI 性能测试。
不同 Android 版本、ABI、corpus 或选项的结果需要分开比较。

### 使用已安装的模拟器

同一套 `device`、`run`、`capture` 命令也支持模拟器，将 `SERIAL` 替换为实际序列号（例如 `emulator-5554`）。
优先使用独立测试 AVD；也可用 `emulator -avd NAME -read-only -no-snapshot` 启动已有 AVD 的临时会话，避免保存安装与配置变化。
不要对日常使用的 AVD 执行 `-wipe-data` 或 `pm clear`。控制模拟器为两个 CPU 核心，并顺序运行测试。

ARM64 APK 需要 ARM64 系统，或支持 ARM64 转译及独立可执行文件转译的系统镜像；普通 x86_64 AOSP 镜像不一定支持。
报告记录主 ABI、支持 ABI、模拟器标记、AVD 名称、native bridge 及其版本。
`engine.execution_mode=native_bridge` 表示运行的是 APK 原库但经过转译；它与手机原生执行的耗时不可混比。
模拟器测试不能替代确认实际手机安装版本，也不能证明手机触摸延迟或功耗达标。
慢速 ARM 转译首次部署可为 `run` 增加 `--phase-timeout 1800`，单位为秒，范围为 1–3600，默认 600。
每个阶段仍有明确超时，所用限制也记录在报告中；超时或失败的 run 不能当作通过结果或直接重跑。

如果外部终止了驱动而模拟器仍在线，可在确认测试进程已停止后执行：

```sh
python3 script/baseline/baseline.py recover --run /path/to/INTERRUPTED_RUN --serial SERIAL
```

命令检查原设备、临时目录路径和 owner 标记，并拒绝归档仍在运行的 probe。
拉回的文件被标为 `interrupted`，不冒充测试通过；可作为同 APK 的 `--compiled-from` 来源，让 Rime 在新 run 内继续检查及完成部署。
不会复用旧用户数据库。只读模拟器退出后临时数据会消失，不能恢复已经退出的会话。

## 4. 无设备时验证测试工具

```sh
python3 script/baseline/baseline.py run --run /path/to/NEW_RUN --host-library /usr/lib/librime.so
```

此模式使用 Linux 系统的 Rime，只验证资源部署与测试驱动流程，明确标记 `host_smoke_only` / `host_smoke_passed`。
**它不能证明 APK 在手机上的候选表现、稳定性或性能**。要跑 Android 测试，请对同一 APK 重新 `prepare` 新目录，不能把本机运行目录转换为真机结果。

## 5. 截图、内存记录和问题报告

```sh
python3 script/baseline/baseline.py capture --run /path/to/RUN --serial SERIAL
```

先核对当前安装包与冻结 APK 的 SHA-256 完全一致，再记录当前屏幕截图和该包的 `dumpsys meminfo`。
目前 capture 限定单 APK 安装；split 安装的清单可以通过 device 命令记录，但不当作单 APK 身份。

每个截图、内存记录和引擎测试结果都有相邻的 `.identity.json`，记录 run ID、APK 摘要、corpus、cohort 和相应设备/引擎信息。
截图与内存采集反映现有 Android 应用状态，标为 `installed_uncontrolled`，不会错误归入隔离引擎的 cold/learned 组。
真实界面当前使用的 schema 尚未通过此工具读取，因此 `schema_state` 标为 `not_observed`；APK 内置方案不能冒充手机当前部署方案。
可用 `--runtime-export /path/to/export` 对手动导出的运行配置记录文件摘要，不复制其内容；导出目录可能不是实时快照，报告会保留这一限制。
提交问题时一并附上 `report.json` 与相关 `.identity.json`，补充当前实际方案、配置变更和复现步骤。

```sh
python3 script/baseline/baseline.py compare /path/to/LATEST_RUN /path/to/DEVELOP_RUN --out build/baseline/comparison.md
```

comparison 比较包身份与测试状态，不输出未经测量的体验优劣结论。
正式比较候选时，核对 report 中 corpus、选项、Android 版本与 ABI，并比较各自的 cold 或各自的 learned；不要把两种学习状态混在一起。

可重新验证冻结输入、所有产物的身份/摘要、完整学习提交序列与两组运行数据：

```sh
python3 script/baseline/baseline.py verify --run /path/to/RUN
```

未运行、部分失败、文件被改动或学习步骤缺失时，verify 返回非零状态；本机冒烟即使通过，也保留其 host 标记。

## 验证工具本身

```sh
python3 -m unittest discover -s script/baseline -p 'test_*.py' -v
```

测试覆盖真实 manifest 版本与文件名分离、签名缺失、Git SHA 缺失、资源冻结、目录隔离、无 owner 目录拒绝、资源路径检查和产物关联。
固定学习、Rime 的实际候选与重启后的行为，另通过上述真实引擎运行验证。

生成报告和 APK 只保存在 Git 已忽略的 `build/` 中。手机上的独立测试目录在完成后保留以便检查，不执行自动删除。
