# T11 轻量离线下一词联想

默认关闭「下一词联想」。主仓库为默认朙月全拼、简化字和九键接入 `predictor`（在 `key_binder` 前）、`predict_translator` 和内部 `prediction` 选项；保留原来的拼音补全、整句翻译及排序设置。这三种行为不使用同一状态。

「下一词联想」由 Trime 设置中的全局偏好统一控制。三个默认方案保留 `prediction` 的 `name` 和 `reset: 0`，不配置 `states` 标签，因此不在 Rime F4 或 Android 方案选项菜单中额外显示开关。Rime 的原生 F4 开关直接修改引擎上下文，不能同步 Android 保存的偏好；隐藏这个重复入口可避免菜单状态与全局设置不一致。内部选项仍可由应用按当前输入框、隐私模式和中英文模式设置，正常方案切换与其他选项的标签保留。

全拼配置使用主仓库的 `luna_pinyin.custom.yaml`，不修改 Rime 数据子模块；简化字方案继承全拼引擎，但替换了 switches，因此另补 `luna_pinyin_simp.custom.yaml`。用户目录中已有同名 custom 文件时，Rime 优先采用用户配置，不覆盖它。如果用户配置没有接入预测组件，开启应用开关也不会为该方案增加联想。

`prediction.tsv` 是本次原创、手工编写的离线示例词组，以 CC0-1.0 提供。它不是收集的用户记录，不学习输入内容，也不是全面的语言模型。模型含 34 个上下文、76 个下一词条目，序列化后为 6,364 字节；简繁上下文均有少量示例。测试约定包括「你好」→「世界」和「世界」→「和平」。「你好」有 10 个不同的后继，用于检验显示数量限制。

默认最多显示 5 个联想候选、连续联想 1 次；运行时硬上限分别为 8 个和 2 次。数据预算为 64 KiB，生成器限制总条目 128、每个上下文的数据库条目 32。数据库条目数与 UI 同时显示数是两个独立上限。不提供 `$` 起始联想，只有新提交的中文内容能开始下一词联想。

模型资产是 `app/src/main/assets/shared/trime_predict.db`，旁边的 `.db.json` 记录原始 TSV 与模型 SHA-256、字节数和格式。应用沿用既有资源校验和安装流程。原生 CMake 在构建时从随包模型生成 SHA-256 常量，运行时仅使用与该模型匹配的、经过大小与边界检查的私有字节快照，不信任用户可修改的 sidecar。丢失、截断、损坏或不匹配的预测数据库使联想不可用，基础字典部署和输入不依赖预测数据库。

宿主原生回归和模型生成要求 Python 3.12 或更新版、C++17、CMake 3.31.6 或更新版、Ninja、readelf、X11 开发头文件，以及生产构建所用的 `boost-1.92.0.tar.xz`。Boost 压缩包必须通过固定 SHA-256 校验。递归初始化全部子模块；不要把当前私有 Rime 头文件与系统安装的 `librime` 混合链接。工具只在新的 `build/t11/` 子目录准备隔离副本，串行构建生产同源 Rime、插件、依赖及配置／隐私／预测兼容层，使用真实生成的 `build_config.h`。它不启动 Gradle、Java 或模拟器。

以下命令中的 Boost 路径需指向已取得的生产压缩包。若 `cmake` 不是所需版本，原生 runner 可显式传 `--cmake`，或设置 `CMAKE_EXECUTABLE`。输出目录必须不存在；工具保留原日志、源码／子模块／工具／静态库与实际 ELF 链接信息。任何警告、超时、外部构建或内存保护失败都使本轮失败；通过宿主回归不等于 Android 或发布验收。

```sh
python3 -B script/quality/t11/run_native_prediction_tests.py \
  --out build/t11/native-check --boost-archive /path/to/boost-1.92.0.tar.xz
```

原有 384 项真实原生检查保持不变。默认要求已提交且干净的源码；生成模型时，只有显式 `--allow-model-source-change` 才允许 `prediction.tsv` 这一项未暂存变动，其真实字节仍在编译前后核对。其它源码、模型资产或暂存变更均不放行。

`--workers` 只接受 1 或 2，默认 1；它同时绑定实际构建的并行参数、环境变量和报告值，内存、进程所有权、时限及警告检查保持不变。`--cache-recovery` 会在原 384 项通过后，使用同一生产静态图另行编译、审计和运行缓存恢复回归，单列结果，不计入 384。它直接检查零字节和短头部的 Prism／Table／ReverseDb、正常工作区部署重建依赖词典并继续后续方案、真实字典查询、用户词条和配置保留，以及有效二进制缓存不重复写入。封闭数据库期间还比较其实际目录和文件字节；最后重新打开 LevelDB 后只检查真实词条，避免把正常 LOG／LOCK 更新误报为词库损坏。这些检查不证明任意损坏偏移都安全，也不代替 Android 验收。

```sh
python3 -B script/quality/t11/run_native_prediction_tests.py \
  --out build/t11/native-cache-check --boost-archive /path/to/boost-1.92.0.tar.xz \
  --workers 2 --cache-recovery
```

```sh
python3 -B script/quality/t11/build_predict_db.py \
  --compile-native --native-build-out build/t11/predict-data \
  --boost-archive /path/to/boost-1.92.0.tar.xz \
  --allow-model-source-change --write
```

模型工具复用同源静态库，但将锁定子模块的通用 `predict_db.cc` 和校验工具放入独立 `trime_model_tool` 命名空间；只作可逆的名称隔离与移除无用局部常量，保留原序列化和查询算法。生产读取器固定随包模型 SHA，不能用于验证修改 TSV 后的新模型；其运行时安全检查保持不变。真实 ELF 符号检查要求通用工具没有生产 `rime::PredictDb` 或预测模块注册符号，拒绝出现系统 Rime 等动态依赖。

旧的 `--compile-native --native-builder ...` 系统库编译方式已禁用，不会静默回退。如果复用已有工具，必须同时给出该次成功的同源审计 `result.json` 与其中实际绑定的可执行文件；不能单独信任任意 `--native-builder`。复用时源码和模型须仍与该次审计输入一致；成功 `--write` 已改变模型资产，再次原生审计需使用新的输出目录。纯 Python `--check` 不需要原生审计记录。

模型和描述文件是两个文件，不能宣称成对原子替换。发布前会在 `build/t11/model-recovery-*` 保留两个旧文件的字节、SHA-256、目标路径和 `before.json`；成功结果给出恢复目录。普通异常（包括 KeyboardInterrupt）会回滚所有已尝试替换的文件，保留原异常和备份；回滚失败会明确报告目标与备份路径，避免继续使用混合版本。SIGKILL、掉电或系统突发中断仍可能打断这两个替换。此后应先运行 `--check`，不一致会被拒绝；按保留的 `before.json` 将两个备份恢复到原目标，并再次执行 `--check` 后再继续构建。备份不位于 APK 资产目录。资产回滚不会撤销对 `prediction.tsv` 的编辑：若要使用旧模型，应先保存新的 TSV 修改并恢复与旧模型匹配的 TSV，再执行 `--check`；也可保留新 TSV，重新生成并校验新模型。

每次生成在临时目录中构建两遍，要求字节完全一致；逐个查询核对全部词条、顺序和权重，检查没有初始或未知上下文候选，并在发布资产前校验头部、偏移、数组、Darts 和 MARISA 的边界。生成器不在 Android 启动或输入过程中运行。模型格式为 `Rime::Predict/1.0`；随包模型 SHA-256 为 `2bc7ee4fe184c433b82a0650625e0fc4ecbc1c64352ea9aa36342d2c7a612add`。

```sh
python3 -B script/quality/t11/build_predict_db.py --check
python3 -B script/quality/t11/build_predict_db.py \
  --native-builder build/t11/predict-data/stage/binary/host-bin/prediction-model-native \
  --same-source-result build/t11/predict-data/result.json --check
python3 -B -m unittest discover -s script/quality/t11 -p 'test_*.py' -v
```

数据测试覆盖源文件限制、数据库截断、错误偏移、错误数组、非有限权重和等长内容篡改。宿主 schema 测试通过 `rime_deployer` 编译三个真实方案及实际 custom 补丁，使用小型测试字典，确认继承后仍保留基础管线、内部预测选项默认关闭且没有重复 UI 标签、其他常用选项标签保留、限制正确，以及缺少模型时基础字典部署成功。该宿主测试不代替 Android 上的基础输入、隐私隔离、候选点击、九键数字、编辑按键及输入框切换验收；这些由独立 Android 测试覆盖。
