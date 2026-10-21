# 主题命名约定（v1，D + S2）

本文件规定主题 YAML 里**按键 id**（D 方案）与**颜色键**（S2 方案）的命名，二者都由引擎校验，
旧名通过别名表继续可用。

## 1. 按键 id：D 方案

| 类别 | 形态 | 例 |
| --- | --- | --- |
| 字符键 | 字符本身，无后缀 | `q`、`，`、`0`、`@` |
| 功能键 | `<Role>Key`，PascalCase | `SpaceKey`、`EnterKey`、`LanguageKey` |
| 系统 Intent 键 | Android Intent 全大写原名 | `SEARCH`、`VOICE_ASSIST` |

- 键名只描述**点击的主要行为**；长按/滑动由 `long_click`/`swipe_*` 表达，不进名字；
- 变体只用限定词（`ShiftLeftKey`），**禁止编号后缀**（`Return1`、`space1`）；
- 旧名（`space`、`Return`、`BackSpace`、`Keyboard_symbols` 等）由
  `PresetKeyAliases` 映射到 D 名称，老主题无需修改；
- 只有 label 不同的变体已合并：`Return`/`Return1`/`Return2` → `EnterKey`，
  `space`/`space1` → `SpaceKey`（旧 id 仍可经别名解析）。

## 2. 颜色键：S2 方案

格式固定为 `<role>_<state>_<part>_color`：

```
role  = keyboard | root | key | function_key | candidate | candidate_button
        | comment | popup | label | text | long_text | panel | preview
state = normal | pressed | on | on_pressed | selected（on_pressed = 「开」状态下按下）
part  = back | text | border | symbol | shadow | separator
```

例：

| 颜色键 | 含义 |
| --- | --- |
| `keyboard_normal_back_color` | 键盘底色 |
| `key_normal_back_color` | 普通键常态底色 |
| `key_pressed_back_color` | 普通键按下底色 |
| `function_key_normal_back_color` | 功能键常态底色 |
| `function_key_on_back_color` | 功能键「开」状态底色 |
| `candidate_normal_text_color` | 候选常态文字 |
| `candidate_selected_text_color` | 选中候选文字 |
| `candidate_button_selected_back_color` | 候选栏按钮按下底色 |
| `popup_normal_back_color` | 长按弹层底色 |

约束：

1. 键名必须匹配 `^(role)_(state)_(part)_color$`，不允许 `*_background` 这类拼法；
2. 每个 `role` 只定义存在的状态组合（例如 `enter` 没有 `on`），不做笛卡尔积填充；
3. 旧名（`key_back_color`、`hilited_key_back_color`、`candidate_background` 等）由
   `LegacyColorAliases` 映射到 S2 名称。

## 3. 内置主题的特殊键名

两个内置主题（`trime.yaml`、`tongwenfeng.trime.yaml`）都按本规范命名。几类键的约定：

| 键族 | 规则 | 例 |
| --- | --- | --- |
| 表情分类页 | 首页 `Emoji<Category>Key`；第 N 页 `Emoji<Category>Tab<N>Key`（分页序号是语义，不是样式变体） | `EmojiPeopleKey`、`EmojiTravelTab3Key`、`EmojiPageUpKey` |
| 颜文字分类 | `Kaomoji<Mood>Key` | `KaomojiHappyKey`、`KaomojiSadKey`、`KaomojiHelplessKey` |
| 符号页 | `<EnglishName>SymbolsKey`（数字页 `Digit<N>SymbolsKey`） | `ComputerSymbolsKey`、`MathSymbolsKey`、`Digit3SymbolsKey` |
| 笔画/字母键 | `Stroke<Stroke>Key` 或 `Letter<Letter>Key` | `StrokeTurningKey`、`LetterAKey` |
| 键盘切换 | `<Qualifier>KeyboardKey` | `WindKeyboardKey`、`LastLockKeyboardKey`、`EnglishKeyboardKey` |
| 选字/光标 | 语义化 | `SelectOneCharKey`、`WordLeftKey`、`WordRightKey` |
| 行为不同的变体 | 用限定词区分，不合并 | `SlideSpaceKey`、`ShiftLockKey`、`SlideBackspaceKey` |

## 4. 按键 role：行为默认值

键条目可以用 `role:` 声明行为，不必重复写 `send`/`command`/`select`/`toggle`：

```yaml
preset_keys:
  EnterKey: {role: enter, label: enter_labels}
  SpaceKey: {role: space}
  ShiftKey: {role: shift, shift_lock: ascii_long}
  LanguageKey: {role: language, states: [中, 英]}
  SymbolPanelKey: {role: symbol, option: 标点}
```

- **显式字段优先**：条目里写了 `send`/`command`/`select`/`toggle` 时，role 只补空缺；
- 未知 role 不报错，只按条目自身字段运行（诊断里会给 WARNING）；
- 可用 role：

| role | 默认行为 |
| --- | --- |
| `space` / `enter` / `backspace` / `tab` / `escape` / `delete` / `insert` | 对应 keysym |
| `home` / `end` / `page_up` / `page_down` | 对应 keysym |
| `cursor_left` / `cursor_right` / `cursor_up` / `cursor_down` | 方向键 |
| `shift` / `control` / `alt` / `meta` | 修饰键（Shift_L 等） |
| `language` | `send: SWITCH_CHARSET` + `toggle: ascii_mode` |
| `keyboard` | `send: Eisu_toggle` + `select: .next` |
| `symbol` | `command: liquid_keyboard` |
| `clipboard` | `command: clipboard_window` |
| `editor` | `send: Eisu_toggle` + `select: editor` |
| `hide` | `send: BACK` |

命名折叠大小写与 `_`/`-`：`pageUp`、`PAGE-UP`、`page_up` 都等价。

## 5. 状态化按键：`toggle` + `states`

任何键带 `toggle:` 就是开关按键，点一下切换对应的 Rime 选项：

```yaml
preset_keys:
  PunctSwitchKey: {toggle: ascii_punct, states: [。，, ．，]}
  LanguageKey: {role: language, states: [中, 英]}   # role: language 自带 toggle: ascii_mode
```

引擎行为：

- 选项变化时自动刷新该键的开关态并重绘（不必重建键盘）；
- 标签取 `states[开 ? 1 : 0]`，没有 `states` 则用普通 `label`；
- 开关态颜色用 `function_key_on_*` 一组令牌，开态按下用 `function_key_on_pressed_*`；
- 键盘切换/重建时按运行时选项值初始化开关态。

这条机制对主题里所有 `toggle:` 键生效（全角/半角、简繁、字符集、标点、语言、单手开关等），不需要引擎逐个写特例。
