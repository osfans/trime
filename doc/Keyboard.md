<!--
SPDX-FileCopyrightText: 2015 - 2024 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# Liquid Keyboard 參數

(3.2.17), 只有使用 `single_width` 。其餘的還未有作用。

- `single_width` : 在 `SINGLE` 類別中，每項的闊度 (和高度相等)。

# 藍芽鍵盤問題
https://github.com/osfans/trime/issues/1058#issuecomment-1672635413

# 橫屏鍵盤

(3.2.17) 新增兩個參數到 `preset_keyboards` :

- `landscape_keyboard`: 橫屏時轉用的 keyboard id。當判定需顯示橫屏鍵盤時，會顯示此值代表的鍵盤。該 id 需包含在 `style/keyboards`中。
- `landscape_split_percent`:  0 - 200。此值只在橫屏時生效，優先等級高於「鍵盤設定」>「自動分割鍵盤比例」。此值代表鍵盤自動分割時中間的空白闊度比例。如：
	- `100` 代表 100%，即空白的闊度與鍵盤的總闊度比例為 1:1。
	- `150` 的話，代表空白的闊度與鍵盤的總闊度比例為 1.5:1。
	- `0` 代表不做自動分割。

例：
```
patch:
  "style/keyboard": [my_keyboard, my_landscape_keyboard, mini, .....]
  "preset_keyboards/my_keyboard":
    name: My Keyboard
    landscape_keyboard: my_landscape_keyboard
    ...
    keys:
      - ......
  "preset_keyboards/my_landscape_keyboard":
    name: My Landscape Keyboard
    landscape_split_percent: 0
    ...
    keys:
      - ......
```
以上例子代表：
直屏時使用 `my_keyboard` 
橫屏時使用 `my_landscape_keyboard`，並且不做自動分割。若 `landscape_split_percent` > 0，則會分割顯示。