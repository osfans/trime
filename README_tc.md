# 同文 Android 輸入法平臺
![build](https://github.com/osfans/trime/actions/workflows/commit-ci.yml/badge.svg?branch=develop)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/release/osfans/trime.svg)](https://github.com/osfans/trime/releases)
[![F-Droid release](https://img.shields.io/f-droid/v/com.osfans.trime.svg)](https://f-droid.org/packages/com.osfans.trime)
[![Latest build](https://img.shields.io/github/last-commit/osfans/trime.svg)](http://osfans.github.io/trime/)

[English](README.md) | [简体中文](README_sc.md) | [繁體中文](README_tc.md)

## 關於

源於開源的[注音倉頡輸入法]前端，基於著名的 [RIME] 輸入法框架，使用 JNI 的 C 語言和 Android 的 Java/Kotlin 語言書寫，旨在保護漢語各地方言母語，音碼、形碼通用的輸入法平臺。

## 下載
- 穩定版 <br>
[<img alt='Get it on F-Droid' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' height='80px'/>](https://f-droid.org/packages/com.osfans.trime)
[<img alt='Google Play 立即下載' src='https://play.google.com/intl/en_us/badges/images/generic/zh-tw_badge_web_generic.png' height='80px'/>](https://play.google.com/store/apps/details?id=com.osfans.trime)
[<img alt='酷安' src='https://static.coolapk.com/static/web/v8/img/icon.png' height='80px'/>](https://www.coolapk.com/apk/com.osfans.trime)

- 測試版 [點擊下載](https://github.com/osfans/trime/actions)

- 配置文件 [rimerc](https://github.com/Bambooin/rimerc)

## 鳴謝
- 開發：[osfans](https://github.com/osfans)
- 貢獻：[boboIqiqi](https://github.com/boboIqiqi)、[Bambooin](https://github.com/Bambooin)、[senchi96](https://github.com/senchi96)、[heiher](https://github.com/heiher)、[abay](https://github.com/a342191555)、[iovxw](https://github.com/iovxw)、[huyz-git](https://github.com/huyz-git)、[tumuyan](https://github.com/tumuyan)、[WhiredPlanck](https://github.com/WhiredPlanck)......
- [維基](https://github.com/osfans/trime/wiki)：[xiaoqun2016](https://github.com/xiaoqun2016)、[boboIqiqi](https://github.com/boboIqiqi)......
- 翻譯：天真可愛的滿滿（繁體中文）、點解（英文）......
- 鍵盤：天真可愛的滿滿、皛筱晓小笨鱼、吴琛11、熊貓阿Bo、默默ㄇㄛˋ......
- 捐贈：[Releases](https://github.com/osfans/trime/releases) 中的“打賞”實時更新
- 社區：在 [Issues](https://github.com/osfans/trime/issues)、[QQ 羣（811142286）](https://jq.qq.com/?_wv=1027&k=AXdR80HN)、[QQ 羣（458845988）](https://jq.qq.com/?_wv=1027&k=n6xT4G3q)、[酷安](http://www.coolapk.com/apk/com.osfans.trime)、[Google Play](https://play.google.com/store/apps/details?id=com.osfans.trime) 和[貼吧](http://tieba.baidu.com/f?kw=rime)中反饋意見的網友
- 項目：[RIME]、[OpenCC]、[注音倉頡輸入法]等開源項目

## 沿革
TRIME 是 Tongwen RIME 或是 ThaeRvInputMethod 的縮寫:
- 最初，輸入法是寫給[泰如拼音](http://taerv.nguyoeh.com/ime/)（tae5 rv2）的，中文名爲“泰如輸入法”;
- 然後，添加了吳語等方言碼表，做成了一個輸入法平臺，更名爲“漢字方言輸入法”;
- 後來，兼容了五筆、兩筆等形碼，在太空衛士、徵羽的建議下，更名爲“[同文輸入法平臺 2.x](https://github.com/osfans/trime-legacy)”。寓意音碼形碼同臺，方言官話同文。
- 之後，藉助 JNI 技術，享受了 [librime](https://github.com/rime/librime) 的成果，升級爲“同文輸入法平臺 3.x”，簡稱“同文輸入法”。

現在歡迎你前來[貢獻](CONTRIBUTING.md) ～！:tada:

## 編譯

1. 克隆此專案，**請注意**由於 `boost` 子模組很大，這會花費一些時間。同時，請確保你的磁碟有足夠空間保存源代碼（約 1.5 GB);
```bash
cd $your_folder
git clone --recursive https://github.com/osfans/trime.git
```
2. 如果你想測試應用，執行命令：

```bash
cd $trime_folder
make debug
```

或者要爲應用簽名以釋放更新，請創立一個名爲 `keystore.properties` 的檔案，包含以下內容，註明[簽名資訊](https://developer.android.com/studio/publish/app-signing.html)：

```bash
storePassword=myStorePassword
keyPassword=mykeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
```

然後執行命令：

```bash
cd $trime_folder
make release
```

### 在特定平臺上預編譯
#### [Arch Linux](https://www.archlinux.org/)

  ```bash
   yay -S android-{ndk,sdk,sdk-build-tools,sdk-platform-tools,platform} gradle clang capnproto
   make release
  ```

  對於其他 Linux 發行版，請同樣使用它們各自的包管理員安裝上述開發環境。

#### macOS

1. 安裝 [Android SDK](https://developer.android.com/sdk/index.html)

2. 設置相應的 ANDROID_SDK_ROOT
  ```bash
  # Android
   export ANDROID_SDK_ROOT="android_sdk_path"
  ```

3. 設置 Homebrew [鏡像](https://mirrors.tuna.tsinghua.edu.cn/help/homebrew/)(可選)

4. 安裝 [Homebrew](https://brew.sh/)
  ```bash
   brew install cmake capnp
  ```

## 第三方庫
- [Boost C++ Libraries](https://www.boost.org/) (Boost Software License)
- [Cap'n Proto](https://capnproto.org/) (MIT License)
- [darts-clone](https://github.com/s-yata/darts-clone) (New BSD License)
- [LevelDB](https://github.com/google/leveldb) (New BSD License)
- [libiconv](https://www.gnu.org/software/libiconv/) (LGPL License)
- [marisa-trie](https://github.com/s-yata/marisa-trie) (BSD License)
- [minilog](http://ceres-solver.org/) (New BSD License)
- [OpenCC](https://github.com/BYVoid/OpenCC) (Apache License 2.0)
- [RIME](https://rime.im) (BSD License)
- [snappy](https://github.com/google/snappy)(BSD License)
- [UTF8-CPP](http://utfcpp.sourceforge.net/) (Boost Software License)
- [yaml-cpp](https://github.com/jbeder/yaml-cpp) (MIT License)
- [注音倉頡輸入法](https://code.google.com/p/android-traditional-chinese-ime/) (Apache License 2.0)

[注音倉頡輸入法]: https://code.google.com/p/android-traditional-chinese-ime/
[RIME]: http://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
