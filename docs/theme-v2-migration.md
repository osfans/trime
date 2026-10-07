# Trime 主题系统 V2：对齐「元书/Hamster」皮肤标准

本文档记录主题系统重构后的新格式、字段映射与迁移指引。

## 一、重构目标

原主题系统存在三个痛点，本次重构逐一解决：

| 痛点 | 解决方案 |
|---|---|
| 与 librime 深度绑定（`__include`/`__patch` 走 librime 部署通道） | 彻底解耦：主题由 Trime 独立解析，删除 `Rime.deployRimeConfigFile` 部署路径 |
| 配置复杂（单文件混装 8 类段，`style` 段 58 个平铺键） | 按关注点分层：`keyboard`/`candidateBar`/`popup`/`fonts`/`enterKey` 等 |
| 参数不统一（snake_case 与缩写混用） | 统一 camelCase 命名，对齐元书/Hamster 的语义化风格 |

## 二、新格式（`version: "1.0"`）

顶层段结构（对照旧格式）：

| 旧段 | 新段 | 说明 |
|---|---|---|
| `style` | `keyboard` + `candidateBar` + `popup` + `fonts` + `enterKey` | 按关注点拆分 |
| `tool_bar` | `toolbar` | 键名 snake→camel |
| `preedit` | `preedit` | 键名 snake→camel |
| `window` | `window` | 键名 snake→camel |
| `preset_color_schemes` | `colorSchemas`（严格 `light` + `dark` 两个） | 颜色键 snake→camel |
| `fallback_colors` | `fallbackColors` | 颜色键 snake→camel |
| `preset_keys` | `keys` | 字段 snake→camel |
| `preset_keyboards` | `keyboards` | 字段 snake→camel |
| `liquid_keyboard` | `symbolKeyboard` | 字段 snake→camel |

完整示例见默认主题 `app/src/main/assets/shared/trime.yaml`（已采用 V2 格式）。

## 三、字段映射要点

### `style` → 五段拆分

- `keyboard`：`auto_caps`、`key_height`、`key_width`、`key_text_size`、`keyboard_height`、`round_corner`、`vertical_gap`、各种 offset 等键盘几何/按键样式。
- `candidateBar`：`candidate_*` 与 `comment_*`（候选栏字号、间距、圆角、注释位置等）。
- `popup`：`popup_*`（按键弹窗尺寸）。
- `fonts`：`*_font`（九种字体声明）。
- `enterKey`：`enter_label_mode`、`enter_labels`。

### 键盘高度：推荐用 `keyboardHeightRatio`

`keyboard.height`（dp）在不同 dpi / 屏幕尺寸上会拉伸变形，因为键宽本就按屏幕宽度百分比计算
（`keyWidth` 是百分比）。因此新增 `keyboard.keyboardHeightRatio`：**键盘高度 = 屏幕宽度 × ratio%**，
> 0 时优先于 `height` / `heightLandscape`。

```yaml
keyboard:
  keyWidth: 10.0
  height: 250          # 回退值：ratio 为 0 或旧主题时使用
  heightLandscape: 200
  keyboardHeightRatio: 65
```

优先级：**用户设置「键盘高度」（设置项，0 = 跟随主题）> 主题 `keyboardHeightRatio` > 固定 dp**。
横屏等宽屏场景会自动以屏幕可视高度的 60% 封顶，避免键盘顶满屏幕。

### 按键跨行：`rowSpan`

键支持 `rowSpan`（legacy 写作 `row_span`），默认 1。大于 1 时该键纵向占多行，键高为所跨各行
高度之和，下方各行同一 x 区间会被预留出来，后续键自动跳过，不会压在跨行键身上。

```yaml
keyboards:
  nine_grid:
    name: 九宫格
    keys:
      # ... 前 8 个键
      - {click: Return, rowSpan: 2}   # 确认键占两行
      - {click: space}
      - {click: BackSpace}
```

要点：

- 跨行键会**吃掉格子**：上例中确认键占住第 3 行第 3 列与第 4 行第 3 列，末行只剩两个位置，
  原本 12 个键的九宫格要少写一个键，否则多出来的键会被挤到新增的第 5 行（行高随之被压矮）。
- `rowSpan` 超出末行会被收敛到末行，不会算出超高的键。
- 不可点击的键（只占位的 spacer）忽略 `rowSpan`，也不计入列数。
- 横屏分屏（中缝）与跨行同时启用时，中缝之后的键可能有轻微偏移——分屏 gap 是按行插入的。
- 该能力对所有走 `keyboards` / `preset_keyboards` 的键盘生效（主键盘、数字、符号、编辑等）；
  符号/表情面板（liquid keyboard）是独立的流式布局，不受影响。

### 颜色键：snake_case → camelCase（52 个）

`back_color → backColor`、`key_text_color → keyTextColor`、`hilited_candidate_text_color → hilitedCandidateTextColor` 等，规则为去掉下划线并驼峰化。

### 配色方案：严格日间/夜间两个

主题只允许 `light`（日间）与 `dark`（夜间）两个配色，**不允许多余配色**：

```yaml
colorSchemas:
  light:
    backColor: 0xe4e7e9
    keyTextColor: 0x37474f
  dark:
    backColor: 0x222222
    keyTextColor: 0xcccccc
```

运行时由 `followSystemDayNight` 偏好决定：开启时跟随系统深浅色在 `light`/`dark` 间切换，关闭时固定使用 `light`。旧格式的多 scheme（及 `light_scheme`/`dark_scheme` 链接键）由适配层合并为这两个配色——以 `default` scheme（或首个）为基准，其深浅色链接分别提供 `light`/`dark`，无链接时两个模式共用同一配色。

## 四、兼容性

- 旧格式主题（snake_case）由 `LegacyThemeAdapter` 自动转换为 `ThemeV2`，存量第三方主题**无需改动**即可继续使用。
- `ThemeLoader` 按文件顶层键自动识别新旧格式（`ThemeFormatDetector`）。
- 运行时统一消费 `ThemeV2`；渲染层通过 `ThemeV2` 的兼容视图（`style`/`presetKeys`/`presetKeyboards` 等）渐进迁移。

## 五、已废弃参数（lint 提示）

- `style` 的 `preview_font`/`preview_height`/`preview_offset`/`preview_text_size`（运行时从未读取）。
- 颜色键 `preview_back_color`/`preview_text_color`。
- `liquid_keyboard` 的 `row`/`row_land`/`key_height_land`/`vertical_gap`/`author`。

## 六、关键文件

- 加载链：`data/theme/{ThemeLoader, ThemeFormat, ThemeYamlV2, LegacyThemeAdapter, GeneralStyleAdapter, ThemeManager}.kt`
- 新模型：`data/theme/model/v2/{ThemeV2, KeyboardStyle, CandidateBarStyle, PopupStyle, Fonts, EnterKeyStyle}.kt`
- 颜色系统：`data/theme/{ColorKey, ColorTable, ColorSchemeResolver, ColorManager, ThemeScope}.kt`
- 复用模型：`data/theme/model/{Preedit, Window, ToolBar, LiquidKeyboard, PresetKey, TextKeyboard, KeyActionToken}.kt`

## 七、后续工作（渐进迁移）

1. 渲染层（`ime/` 下约 55 个消费点）分节从兼容视图迁移到 V2 字段，迁移后删除对应兼容视图。
2. `ThemeDiagnostics` 增加 V2 键清单与 V2 格式的静态检查。
3. legacy 模型迁入 `model/legacy/` 包。
4. `ThemeDslExpander` 扩展 `__merge` 与 include 列表支持。
