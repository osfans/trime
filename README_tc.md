<!--
SPDX-FileCopyrightText: 2015 - 2026 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# 同文 Android 輸入法平臺

![build](https://github.com/osfans/trime/actions/workflows/commit-ci.yml/badge.svg?branch=develop)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/release/osfans/trime.svg)](https://github.com/osfans/trime/releases)
[![F-Droid release](https://img.shields.io/f-droid/v/com.osfans.trime.svg)](https://f-droid.org/packages/com.osfans.trime)
[![Latest build](https://img.shields.io/github/last-commit/osfans/trime.svg)](https://github.com/osfans/trime/releases/tag/nightly)

[English](README.md) | [简体中文](README_sc.md) | 繁體中文

## 關於

同文（Trime）是一個 Android 輸入法前端，基於著名的 [RIME] 輸入法框架，使用 Kotlin 撰寫並透過 JNI 呼叫原生程式碼。它源自開源的[注音倉頡輸入法]專案，支援 Android 8.0 以上版本，旨在保護漢語各地方言母語，是音碼、形碼通用的輸入法平臺。

[查看文檔](https://github.com/osfans/trime/wiki)

## 下載

- 穩定版 <br>
  [<img alt='Get it on F-Droid' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' height='80px'/>](https://f-droid.org/packages/com.osfans.trime)

- 每夜版 [點選下載](https://github.com/osfans/trime/releases/tag/nightly)

- 測試版 [點選下載](https://github.com/osfans/trime/actions)

- 配置文件 [rimerc](https://github.com/Bambooin/rimerc)

## 沿革

TRIME 是 Tongwen RIME 或是 ThaeRv Input Method 的縮寫：

- 最初，輸入法是寫給[泰如拼音](http://taerv.nguyoeh.com/ime/)（tae5 rv2）的，中文名為“泰如輸入法”；
- 然後，添加了吳語等方言碼錶，做成了一個輸入法平臺，更名為“漢字方言輸入法”；
- 後來，相容了五筆、兩筆等形碼，在太空衛士、徵羽的建議下，更名為“[同文輸入法平臺 2.x](https://github.com/osfans/trime-legacy)”。寓意音碼形碼同臺，方言官話同文；
- 之後，藉助 JNI 技術，享受了 [librime](https://github.com/rime/librime) 的成果，升級為“同文輸入法平臺 3.x”，簡稱“同文輸入法”。

歡迎參與[貢獻](CONTRIBUTING.md)！

## 開發入門

### 準備

#### 開發環境要求

- Android SDK 和 Android NDK
  - 如果還不熟悉 Android 開發，建議安裝 [Android Studio](https://developer.android.com/studio)，它會自動安裝並配置所需的 SDK、NDK 和 CMake 版本。

- JDK（OpenJDK）17
- Python 3（用於給 OpenCC 生成詞典文字檔案）

#### Windows 上的前提條件

目前建置設定會在建置過程中建立符號連結，開發者需要：

- 啟用[開發人員模式](https://learn.microsoft.com/zh-tw/windows/apps/get-started/enable-your-device-for-development) 以在無管理員權限的情況下建立符號連結。

- 啟用 `git` 的符號連結支援：

  ```powershell
  git config --global core.symlinks true
  ```

如果無法或不想啟用上述設定也沒關係。建置系統會在符號連結建立失敗時自動改用複製。

### 建置

#### 1. 克隆此專案並拉取所有子模組：

```sh
git clone https://github.com/osfans/trime.git
cd trime
git submodule update --init --recursive

# 或使用部分克隆節省時間：
# git submodule update --init --recursive --filter=blob:none
```

#### 2. 編譯偵錯版本：

```sh
# On Linux or macOS
make debug

# On Windows
.\gradlew assembleDebug
```

首次原生建置會自動下載 Boost，需要網路連線。

#### 3. 編譯正式版本：

請在倉庫根目錄建立 `keystore.properties` 檔案，包含以下[簽名資訊](https://developer.android.com/studio/publish/app-signing)：

```properties
storePassword=myStorePassword
keyPassword=mykeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
```

然後執行：

```sh
# On Linux or macOS
make release

# On Windows
.\gradlew assembleRelease
```

### 檢查

執行單元測試和 Android Lint：

```sh
make check
```

套用程式碼風格格式化：

```sh
make style-apply
```

### 疑難排解

如果在更新子模組或工具鏈後原生建置失敗，請清除建置產物後重試：

```sh
# On Linux or macOS
make clean

# On Windows
.\gradlew clean
```

其他問題：

1. 確認所有子模組都已初始化且為最新版本。如果修改了一個或多個子模組，請確保它們與目前修訂版本相容。
2. 如果問題仍然存在，嘗試重新克隆倉庫。
3. 檢查是否有相關的 issue 或 PR，並嘗試其中建議的解決方案。
4. 如果以上方法都無效，請附上建置日誌開啟 issue。

## 鳴謝

- 開發：[osfans](https://github.com/osfans)
- 貢獻：[boboIqiqi](https://github.com/boboIqiqi)、[Bambooin](https://github.com/Bambooin)、[senchi96](https://github.com/senchi96)、[heiher](https://github.com/heiher)、[abay](https://github.com/a342191555)、[iovxw](https://github.com/iovxw)、[huyz-git](https://github.com/huyz-git)、[tumuyan](https://github.com/tumuyan)、[WhiredPlanck](https://github.com/WhiredPlanck)、[nopdan](https://github.com/nopdan)。完整名單見 [AUTHORS](AUTHORS)。
- [維基](https://github.com/osfans/trime/wiki)：[xiaoqun2016](https://github.com/xiaoqun2016)、[boboIqiqi](https://github.com/boboIqiqi)......
- 翻譯：天真可愛的滿滿（繁體中文）、點解（英文）......
- 鍵盤：天真可愛的滿滿、皛筱曉小笨魚、吳琛 11、熊貓阿 Bo、默默ㄇㄛ ˋ......
- 捐贈：[Releases](https://github.com/osfans/trime/releases) 中的“打賞”實時更新
- 社群：在 [Issues](https://github.com/osfans/trime/issues)、[QQ 群 (811142286)](https://jq.qq.com/?_wv=1027&k=AXdR80HN)、[QQ 群 (224230445)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=pg_q7UVumWYLq1Rk8kIAqkK1xGt64VnX&authKey=04m9l7OBO5H5vgrEL8IbpsmtnptWM60xy%2FUwYCfyvw9VcRhe8zRzAS1ezoemZdFr&noverify=0&group_code=224230445)、[貼吧](http://tieba.baidu.com/f?kw=rime)、[Telegram](https://t.me/trime_dev) 中反饋意見的網友
- 專案：[RIME]、[OpenCC]、[注音倉頡輸入法]等開源專案

## 第三方庫

- [Boost C++ Libraries](https://www.boost.org/) (Boost Software License)
- [darts-clone](https://github.com/s-yata/darts-clone) (New BSD License)
- [LevelDB](https://github.com/google/leveldb) (New BSD License)
- [libiconv](https://www.gnu.org/software/libiconv/) (LGPL License)
- [librime-lua](https://github.com/hchunhui/librime-lua) (BSD 3-Clause License)
- [librime-octagram](https://github.com/lotem/librime-octagram) (GPL-3.0 License)
- [librime-predict](https://github.com/rime/librime-predict) (BSD 3-Clause License)
- [marisa-trie](https://github.com/s-yata/marisa-trie) (BSD License)
- [spdlog](https://github.com/gabime/spdlog) (MIT License)
- [fmt](https://github.com/fmtlib/fmt) (MIT License)
- [OpenCC](https://github.com/BYVoid/OpenCC) (Apache License 2.0)
- [RIME](https://rime.im) (BSD License)
- [snappy](https://github.com/google/snappy) (BSD License)
- [utfcpp](https://github.com/nemtrif/utfcpp) (Boost Software License)
- [yaml-cpp](https://github.com/jbeder/yaml-cpp) (MIT License)
- [注音倉頡輸入法](https://code.google.com/archive/p/android-traditional-chinese-ime/) (Apache License 2.0)

[注音倉頡輸入法]: https://code.google.com/archive/p/android-traditional-chinese-ime/
[RIME]: https://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
