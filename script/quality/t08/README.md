# T08 候选延迟与驻留预算

这里提供正式构建真机测量的采集器、汇总器和解析器测试。尚未附带真机数据；
主机测试、Debug 构建和模拟器结果不能证明 T08 性能验收通过。

## 构建与测量范围

基线与候选都使用同一台真机、相同方案/词库/用户词典起点、主题切换顺序、
反馈设置、屏幕密度、字体缩放、方向、刷新率和固定输入语料。记录环境温度、
充电状态、热降频情况和输入节奏；交替执行基线与候选，避免把缓存预热或
温度变化当成改进。不要在个人编辑器输入测试语料，不要清除个人应用数据。

使用正式 `release` 构建，并通过 `-Ptrime.performanceTrace=true` 开启内容无关
的 `TrimePerf` 日志。保留安装用 APK、源码提交、Gradle 构建参数和签名信息。
基线也必须带同一套埋点，否则测量开销不同。日常构建默认关闭埋点。
工具链更新后，基线和候选也使用相同的 Gradle、Kotlin 和构建插件版本重新
生成，不能直接沿用旧工具链 APK 的测量作为同条件对照。
采集器核对安装 APK 与传入正式 APK 的 SHA256，并拒绝 debuggable 应用和
已知模拟器；这些检查无法单独证明构建来自指定源码或硬件未经伪装，仍需
保存构建记录并人工确认真机。正式包启用 R8 优化与代码缩减及资源压缩；当前
通过 `-dontobfuscate` 保留类名。调试包带 `.debug` 后缀，原有
`t05/run_startup.py` 的模拟器 Debug 测量不适用本验收。

`candidate_draw` 是候选子树实际执行 draw 回调的时间，位于候选异步列表提交
之后，并且至少有一个真实候选。空列表刷新记录 `candidate_clear`，其次数与
耗时单列，不能混入候选显示 P95 或凑足 1000 个显示样本。同一个请求仅记录
首次有效显示结束事件（draw 或 clear），两种事件共用重复显示守卫和
compute/generation 校验。它们衡量应用绘制提交，不能直接声称屏幕像素已呈现；需要物理呈现时延时，
另保留帧时间/呈现跟踪。触摸反馈到提交请求之间包含按住按键的时长，所以主
指标使用 `feedback_ns > 0` 的按键请求 `request.submitted_ns → candidate_draw`，
另列反馈到候选绘制的时延。`feedback_ns=0` 的控制请求单列，不能混入主 P95。
采样包含有 KeyView 反馈关联的按键，以及普通活动编辑器已经完成 native
绑定时的控制请求；初始化、绑定和清理旧编辑器等不具备可靠归属的任务不
创建请求。控制请求参与总体队列、计算和提交延迟统计及日志完整性检查；
这些延迟目前没有按控制/按键或直接/native 提交拆分独立分布。
IME 串行队列等待（submitted→rime_queued）、native 队列等待
（rime_queued→engine_start）、引擎计算、计算到绘制、编辑器提交分别报告。
所有时间使用
`SystemClock.elapsedRealtimeNanos`；输入内容、键值、候选文字和编辑器应用名
不进入此日志。隐私输入不采样，必须使用普通测试编辑器。

当前代码 `Rime.startup(fullCheck=false)` 把实际参数传给 native
`start_maintenance(fullCheck)`，随后等待维护完成。当前调用链未发现
`BuildConfig.DEBUG` 自动强制 `true`；更早版本若传 `fullCheck=true`，测的是
维护全量检查路径，不能代表正式版普通启动。`fullCheck` 也不意味着每次显示
键盘都全量重编译。首次部署必须单独测量，不能混入已部署连续输入的 P95。

## 有界、只读采集

采集器不会启动设备、安装 APK、强停进程、部署资源、输入文本或修改设置。
先自行准备真机、部署并打开普通测试编辑器，再开始 `steady` 采集。若需要
测首次部署，先开始 `--phase first-deployment` 采集，再在隔离测试配置中执行
首次部署；不要删掉个人词典来制造首次部署。保存操作开始、部署完成和首次
可输入的独立时间记录，并保留对应日志/帧跟踪；本汇总器不把首次输入时延
冒充部署耗时。

建立一个冻结的 JSON 运行协议，例如：

```json
{
  "corpus_sha256": "替换为固定语料文件的64位SHA256",
  "input_method": "普通测试编辑器中点击实际键盘，固定输入和退格节奏",
  "schema_and_dictionary_snapshot": "固定测试配置的SHA256",
  "themes": ["trime", "tongwenfeng.trime"],
  "round": "相同连续输入语料→相同模式切换→两主题来回切换→静置期间完成至少三次PSS采样",
  "feedback": "固定声音、TTS、振动设置",
  "refresh_rate_hz": 60,
  "conditions": "记录环境温度/电源状态，排除热降频"
}
```

协议不写测试输入明文也可以，使用固定文件的 SHA256 标识；基线和候选必须
传入完全相同字节的协议文件。每一轮末尾按实际时间保证三次完整静置采样，让
最后三个 PSS 样本代表完成相同工作后的驻留。连续进行至少五轮，相同构建
各轮保持同一应用进程和启动周期。每轮使用新目录、递增 `--round`：

```sh
python3 -B script/quality/t08/collect.py \
  --adb /absolute/android-sdk/platform-tools/adb --serial PHYSICAL_DEVICE_SERIAL \
  --release-apk /absolute/frozen/trime-release.apk --protocol /absolute/protocol.json \
  --output build/t08/candidate-round-1 --round 1 --seconds 180 --interval 5
```

把下一轮输出改成 `candidate-round-2`、`--round 2`，依次进行五轮。
默认每次采样完成后等待五秒再执行 `dumpsys meminfo`，实际间隔包含 ADB 耗时；若需要改频率，基线与
候选保持相同。默认每次采集 180 秒、至多 16 MiB 日志；上限是 1800 秒/64 MiB，
日志触及上限就明确失败，不会删除输入记录来伪造完整性。APK 流式落到主机
临时文件后计算 SHA256，日志直接写文件；不启动 Gradle 或模拟器占用主机内存。
汇总器逐行读日志、流式计算证据散列，最多分析 100000 条范围内事件，超出就
明确要求缩短冻结协议；不会悄悄只留下最快的一部分记录。

每次保留 `capture.json`、`trace.log`、`memory.jsonl`、原始 `meminfo-*.txt`、
包信息、协议文件和前后电池信息。进程变化、连接失败、日志写满或样本不足
会留下 `status=incomplete`，不能用于验收。捕获边界之前的 logcat 历史依据
PID 和设备启动时间过滤；跨边界的旧请求单独计数，不加入候选时延分布。
每轮采集开始和结束保持短暂静置，避免把正常进行中的请求切在记录边界。

## 预算、完整性和准确率

主指标初始预算为候选绘制 P95 ≤100 ms；若同协议基线 P95 >100 ms，可暂以
下降至少 30% 为阶段目标，并保留最终 100 ms 目标。使用最近秩 P95；至少
1000 个真实候选绘制样本才满足默认样本量检查。队列拥堵与计算时延分别
输出，不能只展示最快的最终绘制样本。`stale`/`cancelled` 请求仍留在提交
分母；报告真实候选绘制覆盖率、含清空的显示更新覆盖率，以及计算、绘制、
清空、提交、过期、取消、失败、未结算数量。清空请求可以结算，但无法通过
真实候选样本量与候选显示 P95 检查。
`job_failed` 表示任务失败，后续已计算快照仍可能合法绘制；它不会被伪装成
取消事件，但只要出现一次就无法通过预算检查。同一请求可以先使旧 generation
过期，再计算并绘制新 generation；这不属于旧结果覆盖。256 项诊断缓存发生
淘汰后，缺少真实结束事件的请求保留为未结算，不能猜成取消或显示合并。
旧 generation 的过期事件也不能掩盖较新快照缺失结束事件。未结算请求、旧
编辑器结果、错误 generation 或重复绘制阶段会阻止通过。
即使缺失 stale 事件，汇总器也按 elapsed 时间跨请求检查同一编辑器已完成的
最新 generation：新世代计算完成后，旧世代 draw/clear 会被拒绝；旧显示在新
计算之前发生可以接受。编辑器提交保留输入顺序，旧 generation 的合法提交
可以晚于新计算到达，不受这个显示新鲜度检查影响。
准确率样本应覆盖连续输入、退格、翻页、候选选择、编辑器切换和主题切换，
实际提交全文的散列可以检测漏字、重复提交和顺序错误。

直接提交动作不必经过 Rime：例如主题按键的 `commit` 字面量、函数命令
`commit`、函数命令 `date` 在 KeyView 反馈作用域内成功回写编辑器时，会关联
一个 `request → editor_commit`（generation=0），可以没有 `rime_queued`、
`engine_start`、`compute_end` 或 `candidate_draw`。这些动作算成功提交，不算
候选显示样本。直接提交在调用 `InputConnection.commitText` 或
`finishComposingText` 之前创建 request，成功返回后记录 editor_commit；
request→commit 包含该编辑器调用的耗时。feedback→editor_commit 还包含此前
的按键交互与按住按键的时长。返回失败会记录 job_failed，不能算作成功提交。
native 候选提交（generation>0）必须关联已完成的
同 generation 快照，不能把缺失计算事件当成直接提交。
此阶段记录 `commitText` / `finishComposingText` 的成功返回。未处理的空格、
回车若通过 `sendKeyEvent` 或 `performEditorAction` 转发，不计为这类提交；
当前没有这些编辑器动作的独立结束时间，不能声称覆盖全部编辑器响应耗时。

PSS 使用 `dumpsys meminfo` 详细表第一列：Java 为 `Dalvik Heap + Dalvik Other`，
native 为 `Native Heap`，graphics 为 `Gfx dev + EGL mtrack + GL mtrack`，
total 为 `TOTAL PSS`/详细表 `TOTAL`。Java 分组不包含 `.art mmap`，这些分类
不能相加得到整进程 total；原始表供设备相关分析。App Summary 的私有堆或
RSS 不会被冒充 PSS。设备没有 GPU 记账时 graphics 为未知，不能视作零或
宣称 graphics 验收通过；需要该设备另外提供 GPU/图形内存证据。

每轮最后三个 PSS 样本取中位数，观察最近五轮是否持续增长。暂定各分类与
total 的正向驻留增长预算为 `max(4 MiB, 起始驻留的5%)`，同时检查线性增长
趋势；这是可调整的诊断预算，不是所有机型通用的峰值内存上限。可用
`--growth-kib`、`--growth-percent`、`--tail-rounds` 更改，并在基线/候选保持
相同设置。新增键盘模式前先保存这些结果。`keyboard_cache` 记录窗口标识、
布局数量和按键数量，通用 request/editor/generation 关联字段均为 0，帮助关联
驻留趋势，不能替代 Java/native/graphics/total PSS。

准确率文件由独立语料执行器或人工核对生成，不由性能日志猜测：

```json
{
  "complete": true,
  "corpus_sha256": "固定语料文件的64位SHA256",
  "cases": [
    {
      "id": "case-001",
      "expected_sha256": "按固定UTF-8编码与换行规则得到的预期提交全文SHA256",
      "committed_sha256": "从测试编辑器读出的实际提交全文SHA256"
    }
  ]
}
```

基线、候选保持相同 case ID 和预期提交；任意原先正确的用例变错即为回归，
不能用其他用例变好抵消。使用候选列表排名/Top-N 准确率的方案还应保留相应
语料与排名报告，提交文本散列并不能证明所有候选排名完全一致。

```sh
python3 -B script/quality/t08/summarize.py \
  build/t08/baseline-round-1 build/t08/baseline-round-2 build/t08/baseline-round-3 \
  build/t08/baseline-round-4 build/t08/baseline-round-5 \
  --accuracy build/t08/baseline-accuracy.json --output build/t08/baseline-summary.json

python3 -B script/quality/t08/summarize.py \
  build/t08/candidate-round-1 build/t08/candidate-round-2 build/t08/candidate-round-3 \
  build/t08/candidate-round-4 build/t08/candidate-round-5 \
  --accuracy build/t08/candidate-accuracy.json --baseline build/t08/baseline-summary.json \
  --output build/t08/candidate-summary.json

python3 -B -m unittest discover -s script/quality/t08 -p 'test_*.py'
```

`budget_checks_passed` 仅表示提供的证据满足这些自动检查；最终验收仍需要核对
真实正式构建、实物机型、测试协议执行情况、候选排名回归报告和独立首次部署
记录。`--minimum-draws` 只用于工具诊断；降低样本量不能作为充分的 T08 验收。

## 当前验证记录

主机解析器测试覆盖旧 generation/编辑器、丢失显示、过期请求分母、重复阶段、
logcat 边界、PSS 与 RSS 区分、未知 graphics、持续驻留增长、Debug/模拟器拒绝
和准确率回归，还覆盖同请求多快照、超过 256 个请求后的丢失测量、无需 Rime
的直接提交、空刷新无法凑足显示样本，以及合法绘制无法掩盖任务失败的完整预算检查。主机测试通过
不代表已经测得任何真机 P95 或 PSS。
