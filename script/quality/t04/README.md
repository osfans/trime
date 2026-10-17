# T04: 九键词频与整句评测

本工具只在新建的 `build/t04/` 和模拟器 `/data/local/tmp/trime-t04-UUID/`
目录中运行，不安装 APK，不接触 Trime 的实际用户目录，不重置个人词库。
当前默认朙月方案和已经发布的 t9.6 APK 不被实验修改。
首轮实测结论、改进/退步样例及未完成验收见[验证报告](../VALIDATION-T04.md)。

## 固定输入

- `corpus.json`：64 条人工编写的回归用例，开发集、保留集各 32 条。
  聊天、地点、人名、地址、技术、同音词、整句、特殊拼写各 8 条。
  每条分别测简体、繁体；同音词存在相同输入、不同目标，不能全部同时首选。
  每行依次为 `id, split, category, kind, pinyin, simplified, traditional`。
- 普通空格分隔的拼音转为不带边界的九键数字；显式 `'` 保留。
  不穷举字母，也不从候选注释推断读音。
- 学习脚本：4 个词各提交 5 次；用精确全拼选择指定词，只在隔离测试库学习。
  关闭进程再测九键，检查用户词库导出中的规范拼音；保留集不是“从未学习集”，
  所以冷启动和固定学习后的结果必须分别解释。

## 词库实验

来源：[iDvel/rime-ice](https://github.com/iDvel/rime-ice/tree/fbb516b2786e4d5444383706d13c31c2e4d10c08)。
提交、原文件 SHA-256 固定在 `sources.lock.json`，不跟随 main 更新。

`layers.py` 流式读取来源，按独立于测试答案的规则选取：8105 字表中的 BMP 汉字读音、
基础库最高频 10,000 条 2-6 字词、扩展库最高频 2,000 条 3-6 字词。
同频按内容 SHA-256 排序，避免简单截取拼音前部。舍弃无可靠注音、无数值词频的行。
固定的 OpenCC STCharacters/STPhrases 数据负责简转繁，保留 `nv/nve/lv/lve` 编码；
方案已有 `nue/lue` 派生规则。生成物记录转换器版本、数据版本和每个输出文件校验和。

五个固定配置：

| 配置 | 组成 | 新词条权重 |
|---|---|---|
| luna | 发布 APK 的朙月词典 | 原值 |
| common | 常用字 + 10k 基础词 + 原朙月 | 来源词频 |
| extended | common + 2k 扩展词 | 来源词频 |
| balanced | 与 extended 同词条 | 来源词频 × 0.1 |
| compact | 拼音仅常用字 + 10k 基础 + 2k 扩展，不导入朙月/essay | 来源词频 |

前三个扩展配置的实验词条先于朙月导入，同词同音以实验权重为准；原朙月及 essay 的其余词条保留。
compact 单独验证小型完整词库的准确率、覆盖率及部署成本，不把扩展文本小误当成总词库小。
笔画反查依赖仍会部署，仍可能读取 essay，因此不是“整个输入法不再需要 essay”。
扩展字典使用 `luna_pinyin.t9_*` 名称，显式指定 `user_dict: luna_pinyin`；每个配置有独立
prism，不更换全拼、双拼方案。补丁是评测覆盖层，不复制雾凇的桌面配置或个人短语文件。

来源的 GPL-3.0 全文、逐层原始来源说明、OpenCC 的 Apache-2.0 全文及作者信息随生成包保留。
字表说明中还包含 Wiktionary/字频资料引用，基础库和扩展库包含各自的上游来源，
不得删除这些说明、将派生数据宣称为自行创作，或只保留本项目的许可证。
生成实验包不表示已经独立审计所有间接来源的授权；对外分发前仍须核对适用条款。
大词库、腾讯词库、英文/emoji 配置及 octagram 数据均未导入。

## 运行

需要 Python 3.11+、OpenCC 命令行工具和现有 Android NDK 编译数据库。

先用 T00 的 `script/baseline/baseline.py prepare` 冻结要测试的 APK；`BASELINE`
指向生成的运行目录。该 APK 必须与现有 `CXX` 编译数据库的 Rime C++ ABI 一致。
工具会核对 APK 源码提交所固定的 librime 与当前头文件版本；没有可靠源码提交的 APK
不能使用此私有 C++ 探针，应改用 T00 的稳定 C API 探针或准备匹配的源码。
仅编译一个小驱动，链接冻结 APK 的 ARM64 `librime_jni.so`，不重新编译整个 Android 工程。

```sh
python3 -B -m unittest discover -s script/quality/t04 -p 'test_*.py' -v
python3 -B script/quality/t04/layers.py --out build/t04/packs-local
python3 -B script/quality/t04/run.py \
  --baseline "$BASELINE" --packs build/t04/packs-local \
  --cxx "$CXX" --adb "$ANDROID_SDK_ROOT/platform-tools/adb" \
  --serial emulator-5560
python3 -B script/quality/t04/summarize.py build/t04/runs/RUN_ID
python3 -B script/quality/t04/audit.py path/to/application.apk
```

只运行一个 2 GB / 2 核模拟器；不要并行运行 Gradle 或多个评测进程。
运行目录一次性使用；失败结果保留，不覆盖重试。需要调整时创建新目录。
默认单阶段超时 1800 秒；脚本异常时只停止该 UUID 所属的评测进程。
默认运行 luna/common/extended/balanced；独立 compact 用 `--profiles compact` 单独运行，
缺词导致固定学习脚本无法完成时保留 failed 状态，不能合并或冒充完整冷/热对照结果。
同一 APK、词表、模拟器及探针源码的多个完整顺序运行可用 `merge.py RUN1 RUN2` 合并；
它保留各组原始运行身份，拒绝覆盖同名配置，不用于合并失败结果或不同测试算法。
`audit.py` 逐项核验 APK 中的词典、方案与 `assets/checksums.json`，并复算 Gradle 的资源总校验和。
词典生成包的 `provenance.json` 与 APK 的部署校验和是两个不同层次，不能互相替代。

## 指标与解释

Top-1、Top-3 同时要求候选文字完全一致、覆盖完整输入；整句匹配率只计算 sentence 用例。
普通查询和纠错回放均不产生最终提交。回放按前 80 个候选中最长的正确目标前缀逐段选择，
最后一个候选只检查完整输入覆盖、不点击提交，避免把评测答案写入学习库。

“每百字额外选词”是有上限的 oracle 回放指标，不是真人操作次数：
每次中间分段选择计一次，最后一段只有选择非首选时才额外计一次；不包含滚动、消歧区点击和重试。
`comparison.md/json` 从已校验的逐步轨迹重新计算，避免重复计算中间的非首选。
早期探针的加权成本字段不用于此指标，原始文件仍保留，不修改历史测量输出。
无法完成的输入单列，不记为零成本；报告原始覆盖率及双方都可完成的配对子集结果。
评测前后导出用户词库比较，必须没有学习记录变化；学习组还须通过跨进程持久化检查。

无语法模型时，当前 librime 的句子评分使用词频与固定组句惩罚。
`initial_quality` 主要影响不同 translator 的质量合并，不是单一词典内部的首选开关。
报告实际部署配置；`grammar:/hant?` 引用本身不构成模型加载证据。
未读取到的配置项显示 null，不能据此认定某个引擎默认值为 false。

部署时间从空隔离目录初始化计时，包含 OpenCC 转换；学习组仅复用编译表。
PSS 是查询后采样的独立原生引擎进程 PSS，不是输入法服务 PSS、部署峰值或 UI 性能。
ARM64 转译模拟器的时延不能代替 ARM64 真机结果。
探针设置 `min_log_level=2`，并非应用完整日志/UI 路径的开销。

默认候选只按开发集两种模式、两种词库状态的 Top-1 总分提名；平分优先原方案。
保留集每组须 Top-1 至少提高 6.25 个百分点、Top-3/整句/可完成覆盖不下降、
引擎 PSS 增量不超过 20 MiB、首次部署不超过基线 2 倍，否则保留朙月默认。
这是小型回归集的保守准入门槛，不是统计显著性证明，也不能替代手机体验验收。
