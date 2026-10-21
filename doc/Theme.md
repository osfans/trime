# 主题命名约定

本文件规定主题 YAML 里**颜色键**的命名，以及按键 `role` 的行为默认值。

## 1. 颜色键

格式为 `<role>_[state_]<part>_color`：

```
role  = keyboard | root | key | function_key | candidate | candidate_button
        | comment | popup | label | preedit | long_text | liquid_keyboard
        | preview
state = pressed | on | on_pressed | selected（on_pressed = 「开」状态下按下）
part  = back | text | border | symbol | shadow | separator
```

默认状态不写 `state` 段；只有同一角色确实存在多种状态时才拼状态：

| 颜色键 | 含义 |
| --- | --- |
| `keyboard_back_color` | 键盘底色 |
| `keyboard_border_color` | 键盘描边 |
| `key_back_color` | 普通键常态底色 |
| `key_pressed_back_color` | 普通键按下底色 |
| `function_key_back_color` | 功能键常态底色 |
| `function_key_on_back_color` | 功能键「开」状态底色 |
| `function_key_on_pressed_back_color` | 功能键「开」状态按下底色 |
| `candidate_text_color` | 候选常态文字 |
| `candidate_selected_text_color` | 选中候选文字 |
| `candidate_button_back_color` | 候选栏按钮常态底色 |
| `candidate_button_pressed_back_color` | 候选栏按钮按下底色 |
| `popup_back_color` | 长按弹层底色 |
| `key_shadow_color` | 普通键阴影 |

约束：

1. 键名匹配 `^(role)_[state_](part)_color$`；不写冗余的 `normal`，例如键盘底色是
   `keyboard_back_color`，不是 `keyboard_normal_back_color`；
2. 每个 `role` 只定义实际存在的状态组合，不做笛卡尔积填充；
3. 旧名（`hilited_key_back_color`、`off_key_back_color`、`candidate_background`、
   `shadow_color`、`text_color`、`border_color` 等）由 `LegacyColorAliases` 映射到上表名称，
   旧主题无需修改；
4. 内置主题 `trime.yaml`、`tongwenfeng.trime.yaml` 仍使用旧名，新主题请使用上表名称。

## 2. 按键 role：行为默认值

键条目可以用 `role:` 声明行为，不必重复写 `send`/`command`/`select`/`toggle`：

```yaml
preset_keys:
  Return: {role: enter, label: enter_labels}
  space: {role: space}
  Shift_L: {role: shift, shift_lock: ascii_long}
  Language: {role: language, states: [中, 英]}
  SymbolPanel: {role: symbol, option: 标点}
```

- **显式字段优先**：条目里写了 `send`/`command`/`select`/`toggle` 时，role 只补空缺；
- 未知 role 不报错，只按条目自身字段运行（诊断里会给 WARNING）；
- 可用 role：

| role | 默认行为 |
| --- | --- |
| `space` / `enter` / `backspace` / `tab` / `escape` / `delete` / `insert` | 对应 keysym |
| `home` / `end` / `page_up` / `page_down` | 对应 keysym |
| `cursor_left` / `cursor_right` / `cursor_up` / `cursor_down` | 方向键 |
| `shift` / `control` / `alt` / `meta` | 修饰键（Shift_L 等，区分左右请直接写 `send`） |
| `language` | `send: SWITCH_CHARSET` + `toggle: ascii_mode` |
| `keyboard` | `send: Eisu_toggle` + `select: .next` |
| `symbol` | `command: liquid_keyboard` |
| `clipboard` | `command: clipboard_window` |
| `editor` | `send: Eisu_toggle` + `select: editor` |
| `hide` | `send: BACK` |

命名折叠大小写与 `_`/`-`：`pageUp`、`PAGE-UP`、`page_up` 都等价。

## 3. 状态化按键：`toggle` + `states`

任何键带 `toggle:` 就是开关按键，点一下切换对应的 Rime 选项：

```yaml
preset_keys:
  PunctSwitch: {toggle: ascii_punct, states: [。，, ．，]}
  Language: {role: language, states: [中, 英]}   # role: language 自带 toggle: ascii_mode
```

引擎行为：

- 选项变化时把新值同步到带同一 `toggle:` 的按键并重绘（不重建键盘）；
- 标签取 `states[开 ? 1 : 0]`，没有 `states` 则用普通 `label`；
- 开关态颜色用 `function_key_on_*` 一组令牌，开态按下用 `function_key_on_pressed_*`；
- 键盘切换/重建时按运行时选项值初始化开关态。

这条机制对主题里所有 `toggle:` 键生效（全角/半角、简繁、字符集、标点、语言、单手开关等），不需要引擎逐个写特例。
