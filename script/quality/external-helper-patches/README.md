# 验收辅助程序补丁

这些补丁用于本地独立验收工具；它们不是 Trime APK 的打包依赖，也不表示第三方上游已经合并或发布。

## eSpeak 1.52.0

- 官方源码：`espeak-ng/espeak-ng`，提交 `4870adfa25b1a32b4361592f1be8a40337c58d6c`。
- 补丁：`espeak-1.52.0-runtime.patch`，在该源码根目录执行 `git apply --check` 后应用。
- 修正标点参数已经写入却返回错误，以及 FileUtils、CheckVoiceData、DownloadVoiceData 的资源关闭。
- 本地 x86_64 辅助 APK 已从完整官方源与 Sonic 源单线程重建，未复用原 APK 的 dex 或 so。纯离线数据来自已验签的原版 APK，保持字节一致。原 APK 和其签名未修改。
- 新旧引擎对照轮各执行 12 次标点设置和 12 次合成，参数错误与资源未关闭警告在新轮为 0，中英文 PCM 逐字节相同；`readBinary(File)` 未作设备路径覆盖。没有真实扬声器或完整第三方应用验收结论。

`FileResourceCheck.java` 直接调用原版／修复后的 `FileUtils`，检查文本与二进制读回、异常后的文件句柄释放，以及借用流的所有权。Linux／JDK 17 实测：128 次读取、16 次异常后，原版遗留 144 个目标文件句柄，修复版为 0；不依赖主动 GC。这是宿主 JVM 检查，不替代 Android 资源生命周期验收。

宿主检查可用 `javac --release 8 -Xlint:all -Werror -d <classes> <source>/android/src/com/reecedunn/espeak/FileUtils.java FileResourceCheck.java` 编译，再执行 `java -Xmx64m -XX:MaxMetaspaceSize=64m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -cp <classes> FileResourceCheck <true|false> <new-fixture-file>`；修复版传 `true`，原版传 `false`。需要 Linux `/proc/self/fd`，每次使用新的类目录和夹具文件。

## TalkBack FOSS 13.0-f02

- 上游源码提交：`b480c47f1ee4a48aa79ed8c5f71d25d55f28a022`；补丁为 `talkback-foss-13.0-f02-runtime.patch`。在对应源码根目录运行 `git apply --check <patch>` 后应用；合并补丁的实际正反向检查通过。
- 修改 11 个功能文件：独立包的 SplitCompat 和标签 authority、接收器生命周期、语音状态发布与初始化队列、教程 IPC 绑定，以及 Settings.Global 读取。包名来自既有 `talkbackApplicationId`，没有修改原 provider 保护。
- 不含本地构建所用的版本号、ABI、SDK 或依赖仓库覆盖。实际单线程重建辅助 APK 为 `13.0-f02-trime-validation3`／code 6／x86_64，SHA-256 `5cace5145611d49c87ac5930711668fd67f1ef7ef9d0ea8a1f953d7ddedd67ef`；补丁功能文件与该构建源码一致。
- 实际语音合成、停服后接收器注销和七项设置恢复通过；第二处标签 authority 的首轮遗漏原件保留，补齐后实际 CRUD 与原有行恢复通过。测试界面仍是教程／权限弹窗，不表示完成 Settings 操作、真实扬声器或新的整套 Trime TalkBack 任务。
- 此辅助程序 minSdk 26；不能替代 Android 5.0 无障碍证据，也没有改变 Trime 的 minSdk。辅助构建仍有 114 条实际诊断，不作为零警告 Trime 发布构建。

## 运行测试约束

安装本地辅助 APK 时明确使用 `adb install --no-incremental`，并读回核对 APK，避免 `.idsig` 触发默认增量安装。不能仅以安装返回成功代替字节一致或功能验证。所有测试使用隔离模拟器；退出后按原存在状态恢复七项 TTS／无障碍设置。

目录手势工具的实际模块已保存为 `../directory_gesture_guard.py`，需要调用方提供有进程身份、截止时间和内存保护的 `Probe`。它保留原图、窗口、完整连续日志及触摸目标，不是独立的导入验收命令。可用 `python3 -m unittest discover -s script/quality -p test_directory_gesture_guard.py` 运行 10 项宿主语义检查；合成日志用于异常边界，不能替代模拟器实测。

每次独立验收都应记录工具版本、来源、构建身份和运行边界；上述历史结果不能代替当前 APK 的验收。本地辅助 APK 不作为 Trime Release 附件发布。

## Observer9 主机文件检查

`observer9-direct-predicates.patch` 用于冻结的 `observer9_session_client_v1.py`，原文件 SHA-256 为 `e5d6c12b781bf228ab2554ee8b6c4695ec4c6528a367b86f33f13f7fa18495fc`。在它的独立副本目录执行 `patch --dry-run -p1 -i <补丁绝对路径>`，通过后去掉 `--dry-run` 应用；结果文件 SHA-256 应为 `190a86e1fa6f4ae84d163c010fb711da4948bc81f20eab506b79881e41ec915d`。历史采集脚本和日志保持原件。

API23 以上用应用 UID 直接执行 `/system/bin/toybox test`，避免额外启动 shell 时对继承的 adbd socket 进行 TTY 探测；API21 保留兼容路径。四处文件存在性检查的退出码、唯一会话身份、请求大小、截止时间和正常停止要求保持不变，没有修改 SELinux、权限或日志级别。

API35 同一启动会话和辅助应用 UID 下，新旧写法各执行 60 个真实只读谓词，120 项退出码断言通过。旧写法产生 120 条对应的 W 级 TCGETS 拒绝，新写法为 0；I/W 两份原始输出合计 240 行，按 audit ID 计为 120 个事件。连续日志两个完整区间未发现审计丢失标记；不保证未被观察的底层事件为零。新传输随后完成真实目录授权并正常停止，配置与持久化读写授权已核对。首轮错误统计假定 `untrusted_app` 和固定空格，原件保留，以上结果来自按实际 `runas_app` 和空白匹配的第二次复核。

证据见 `build/t13/warnings19-api35-observer-command1/comparison-root-review-v2.json`、`build/t13/warnings19-api35-tree2/` 及 `build/warnings19/observer-patch-check1/result.json`。这项修复不表示其他运行警告已经消除。
