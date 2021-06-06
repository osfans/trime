# 同文安卓輸入法平臺/Trime: Rime IME for Android
![build](https://github.com/osfans/trime/actions/workflows/last-commit-release.yml/badge.svg?branch=develop)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/release/osfans/trime.svg)](https://github.com/osfans/trime/releases)
[![F-Droid release](https://img.shields.io/f-droid/v/com.osfans.trime.svg)](https://f-droid.org/packages/com.osfans.trime)
[![Latest build](https://img.shields.io/github/last-commit/osfans/trime.svg)](http://osfans.github.io/trime/)

## 關於/About
源於開源的[注音倉頡輸入法]前端，
基於著名的[RIME]輸入法框架，
使用JNI的C語言和安卓的Java語言書寫，
旨在保護漢語各地方言母語，
音碼形碼通用的輸入法平臺。

## 下載/Download
- 穩定版<br>
[<img alt='Get it on F-Droid' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' height='80px'/>](https://f-droid.org/packages/com.osfans.trime)
[<img alt='Google Play立即下載' src='https://play.google.com/intl/en_us/badges/images/generic/zh-tw_badge_web_generic.png' height='80px'/>](https://play.google.com/store/apps/details?id=com.osfans.trime)
[<img alt='酷安' src='https://static.coolapk.com/images/header-logo.png'
height='80px'/>](https://www.coolapk.com/apk/com.osfans.trime)
- 測試版
[點擊下載](https://github.com/osfans/trime/tree/gh-pages)

- 配置文件
[rimerc](https://github.com/Bambooin/rimerc)

## 鳴謝/Credits
- 開發：[osfans](https://github.com/osfans)
- 貢獻：[boboIqiqi](https://github.com/boboIqiqi)、[Bambooin](https://github.com/Bambooin)、[senchi96](https://github.com/senchi96)、[heiher](https://github.com/heiher)、[abay](https://github.com/a342191555)、[iovxw](https://github.com/iovxw)、[huyz-git](https://github.com/huyz-git)
- [維基](https://github.com/osfans/trime/wiki)：[xiaoqun2016](https://github.com/xiaoqun2016)、[boboIqiqi](https://github.com/boboIqiqi)
- 翻譯：天真可愛的滿滿（繁體中文）、點解（英文）
- 鍵盤：天真可愛的滿滿、皛筱晓小笨鱼、吴琛11、熊貓阿Bo、默默ㄇㄛˋ
- 捐贈：[Releases](https://github.com/osfans/trime/releases)中的“打賞”實時更新
- 社區：在[Issues](https://github.com/osfans/trime/issues)、[QQ羣811142286](https://shang.qq.com/wpa/qunwpa?idkey=d68b19daf218e0f0feacc3533493e44bf9cd79f4895f9b598aa1f9079910af27)、[酷安](http://www.coolapk.com/apk/com.osfans.trime)、[Google Play](https://play.google.com/store/apps/details?id=com.osfans.trime)、[貼吧](http://tieba.baidu.com/f?kw=rime)中反饋意見的網友
- 項目：[RIME]、[OpenCC]、[注音倉頡輸入法]等開源項目

## 沿革/History
- 最初，輸入法是寫給[泰如拼音](http://taerv.nguyoeh.com/ime/)（tae5 rv2）的，中文名爲“泰如輸入法”。
- 然後，添加了吳語等方言碼表，做成了一個輸入法平臺，更名爲“漢字方言輸入法”。
- 後來，兼容了五筆、兩筆等形碼，在太空衛士、徵羽的建議下，更名爲“[同文輸入法平臺2.x](https://github.com/osfans/trime-legacy)”。寓意音碼形碼同臺，方言官話同文。
- 之後，藉助JNI技術，享受了[librime](https://github.com/rime/librime)的成果，升級爲“同文輸入法平臺3.x”，簡稱“同文輸入法”。
- 所以，TRIME是Tongwen RIME或是ThaeRvInputMEthod的縮寫。

## 編譯/Build

  Clone the project, **pay attention** it will take a while for large submodule boost and make sure your disk is enough to hold the source(about 1.5GB).
```bash
cd $your_folder
git clone --recursive https://github.com/osfans/trime.git trime
```
  If you want to test the application, run the command:
```bash
cd $trime_folder
make debug
```
  Or you can build signed application for release and make sure you create a file named gradle.properties which contains:
```bash
storePassword=myStorePassword
keyPassword=mykeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
```
for [signing information](https://developer.android.com/studio/publish/app-signing.html). And run the command:
```bash
cd $trime_folder
make release
```
The following guide is for the specific platform prebuild setting:
- [Arch Linux](https://www.archlinux.org/)
  ```bash
   yaourt -S android-{ndk,sdk,sdk-build-tools,sdk-platform-tools,platform} gradle clang capnproto
   make release
  ```

- Other Linux distributions

  Use the package manager to install the dev environment and Android SDK NDK.

- macOS

  Install Android [SDK](https://developer.android.com/studio/index.html)
  and [NDK](https://developer.android.com/ndk/index.html)
  (You'd better install it by Android Studio or manually instead of by Homebrew).

  Install [Homebrew](http://brew.sh/) and set
  [Homebrew mirror](https://lug.ustc.edu.cn/wiki/mirrors/help/brew.git) of it if your network is slow.

  ```bash
   brew install automake cmake opencc boost python gradle doxygen clang capnp
  ```

  Set the path environment in ~/.bashrc if you use bash like this
  ```bash
   # Android
   export ANDROID_HOME="your_android_sdk"
   export ANDROID_NDK="your_android_ndk"
   export PATH=${PATH}:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$ANDROID_NDK
  ```
## 第三方庫/Third Party Library
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
