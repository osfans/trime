# T11 轻量离线下一词联想

默认关闭「下一词联想」。主仓库为默认朙月全拼、简化字和九键接入 `predictor`（在 `key_binder` 前）、`predict_translator` 和内部 `prediction` 选项；保留原来的拼音补全、整句翻译及排序设置。这三种行为不使用同一状态。

「下一词联想」由 Trime 设置中的全局偏好统一控制。三个默认方案保留 `prediction` 的 `name` 和 `reset: 0`，不配置 `states` 标签，因此不在 Rime F4 或 Android 方案选项菜单中额外显示开关。Rime 的原生 F4 开关直接修改引擎上下文，不能同步 Android 保存的偏好；隐藏这个重复入口可避免菜单状态与全局设置不一致。内部选项仍可由应用按当前输入框、隐私模式和中英文模式设置，正常方案切换与其他选项的标签保留。

全拼配置使用主仓库的 `luna_pinyin.custom.yaml`，不修改 Rime 数据子模块；简化字方案继承全拼引擎，但替换了 switches，因此另补 `luna_pinyin_simp.custom.yaml`。用户目录中已有同名 custom 文件时，Rime 优先采用用户配置，不覆盖它。如果用户配置没有接入预测组件，开启应用开关也不会为该方案增加联想。

`prediction.tsv` 是本次原创、手工编写的离线示例词组，以 CC0-1.0 提供。它不是收集的用户记录，不学习输入内容，也不是全面的语言模型。模型含 34 个上下文、76 个下一词条目，序列化后为 6,364 字节；简繁上下文均有少量示例。测试约定包括「你好」→「世界」和「世界」→「和平」。「你好」有 10 个不同的后继，用于检验显示数量限制。

默认最多显示 5 个联想候选、连续联想 1 次；运行时硬上限分别为 8 个和 2 次。数据预算为 64 KiB，生成器限制总条目 128、每个上下文的数据库条目 32。数据库条目数与 UI 同时显示数是两个独立上限。不提供 `$` 起始联想，只有新提交的中文内容能开始下一词联想。

模型资产是 `app/src/main/assets/shared/trime_predict.db`，旁边的 `.db.json` 记录原始 TSV 与模型 SHA-256、字节数和格式。应用沿用既有资源校验和安装流程。原生 CMake 在构建时从随包模型生成 SHA-256 常量，运行时仅使用与该模型匹配的、经过大小与边界检查的私有字节快照，不信任用户可修改的 sidecar。丢失、截断、损坏或不匹配的预测数据库使联想不可用，基础字典部署和输入不依赖预测数据库。

从源码重新生成，需要宿主 C++17、librime、marisa 和 glog 开发文件以及 Boost 头文件。生成器可以使用 Android 构建准备的 `app/src/main/jni/boost/libs/*/include`，也可以使用系统 Boost。它编译主仓库校验工具与已锁定子模块中的原始 `predict_db.cc`，不会构建 Android APK：

```sh
python3 -B script/quality/t11/build_predict_db.py \
  --native-builder build/t11/predict-data/build_predict \
  --compile-native --write
```

每次生成在临时目录中构建两遍，要求字节完全一致；逐个查询核对全部词条、顺序和权重，检查没有初始或未知上下文候选，并在发布资产前校验头部、偏移、数组、Darts 和 MARISA 的边界。生成器不在 Android 启动或输入过程中运行。模型格式为 `Rime::Predict/1.0`；随包模型 SHA-256 为 `2bc7ee4fe184c433b82a0650625e0fc4ecbc1c64352ea9aa36342d2c7a612add`。

```sh
python3 -B script/quality/t11/build_predict_db.py
python3 -B script/quality/t11/build_predict_db.py \
  --native-builder build/t11/predict-data/build_predict
python3 -B -m unittest discover -s script/quality/t11 -p 'test_*.py' -v
```

数据测试覆盖源文件限制、数据库截断、错误偏移、错误数组、非有限权重和等长内容篡改。宿主 schema 测试通过 `rime_deployer` 编译三个真实方案及实际 custom 补丁，使用小型测试字典，确认继承后仍保留基础管线、内部预测选项默认关闭且没有重复 UI 标签、其他常用选项标签保留、限制正确，以及缺少模型时基础字典部署成功。该宿主测试不代替 Android 上的基础输入、隐私隔离、候选点击、九键数字、编辑按键及输入框切换验收；这些由独立 Android 测试覆盖。
