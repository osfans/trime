# TRIME
Rime IME for Android

![build](https://github.com/osfans/trime/actions/workflows/commit-ci.yml/badge.svg?branch=develop)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/release/osfans/trime.svg)](https://github.com/osfans/trime/releases)
[![F-Droid release](https://img.shields.io/f-droid/v/com.osfans.trime.svg)](https://f-droid.org/packages/com.osfans.trime)
[![Latest build](https://img.shields.io/github/last-commit/osfans/trime.svg)](http://osfans.github.io/trime/)

[English](README.md) | [简体中文](README_sc.md) | [繁體中文](README_tc.md)

## About

Trime is originally a frontend of open-source [Android Traditional Chinese IME], based on [RIME] input method framework and written in Java/Kotlin with JNI. It is designed to protect the native language of various local dialects of Chinese and is a universal shape-based and phonetic-based input method platform.

## Download

- Stable Channel <br>
[<img alt='Get it on F-Droid' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' height='80px'/>](https://f-droid.org/packages/com.osfans.trime)
[<img alt='Google Play Download Now' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png' height='80px'/>](https://play.google.com/store/apps/details?id=com.osfans.trime)
[<img alt='Coolapk' src='https://static.coolapk.com/static/web/v8/img/icon.png' height='80px'/>](https://www.coolapk.com/apk/com.osfans.trime)

- Canary Channel [Download](https://github.com/osfans/trime/actions)

- Configurations [rimerc](https://github.com/Bambooin/rimerc)

## Acknowledgments
- Developer: [osfans](https://github.com/osfans)
- Contributors: [boboIqiqi](https://github.com/boboIqiqi)、[Bambooin](https://github.com/Bambooin)、[senchi96](https://github.com/senchi96)、[heiher](https://github.com/heiher)、[abay](https://github.com/a342191555)、[iovxw](https://github.com/iovxw)、[huyz-git](https://github.com/huyz-git)、[tumuyan](https://github.com/tumuyan)、[WhiredPlanck](https://github.com/WhiredPlanck)...
- [Wiki Editors](https://github.com/osfans/trime/wiki): [xiaoqun2016](https://github.com/xiaoqun2016)、[boboIqiqi](https://github.com/boboIqiqi)...
- Translators: 天真可爱的满满 (Chinese Traditional), 点解 (English) ...
- Keyboard Designers: 天真可爱的满满、皛筱晓小笨鱼、吴琛11、熊猫阿Bo、默默ㄇㄛˋ...
- Donations: See QR Code in [Releases](https://github.com/osfans/trime/releases)
- Community: [Issues](https://github.com/osfans/trime/issues), [QQ Group（811142286）](https://jq.qq.com/?_wv=1027&k=AXdR80HN), [QQ Group（458845988）](https://jq.qq.com/?_wv=1027&k=n6xT4G3q), [Coolapk](http://www.coolapk.com/apk/com.osfans.trime), [Google Play](https://play.google.com/store/apps/details?id=com.osfans.trime) and [Tieba](http://tieba.baidu.com/f?kw=rime)
- Projects: [RIME]、[OpenCC]、[Android Traditional Chinese IME] and so on.

## History
TRIME is the abbreviation of *Tongwen RIME* or *ThaeRv Input Method*.

From the beginning, TRIME was written for TaeRv Pinyin, and named *TaeRv Input Method (泰如输入法)*.

Then, we created an input method platform with some code tables, such as Wu dialect (吴语). We renamed it to *Chinese Character Dialect Input Method (汉字方言输入法)*.

Later, it supports Wubi and Liangbi and other shape-based input method, we branded it [*Tongwen Input Method Platform 2.0 (同文输入法平台 2.0)*](https://github.com/osfans/trime-legacy), which implies that the phonetic-based and shape-based input method on one platform, while dialects and Mandrain share one kind of characters.

Benefit from the [librime](https://github.com/rime/librime) project by JNI, we are now in version 3.0 of TRIME aka *Tongwen Input Method (同文输入法)*.

Your [contribution](CONTRIBUTING.md) are welcome ~ ! :tada:

## Build

1. Clone this project, please **pay attention** that it would take a while for large-size `boost` submodule. And make sure that you have enough available disk space to hold the source code (about 1.5 GB).

```bash
cd $your_folder
git clone --recursive https://github.com/osfans/trime.git
```

2. If you would like to test the application, run:

```bash
cd $trime_folder
make debug
```

Or if you want to sign for the app to release, create a `keystore.properties` contains following contents for [signing information](https://developer.android.com/studio/publish/app-signing.html):

```bash
storePassword=myStorePassword
keyPassword=mykeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
```

then run:

```bash
cd $trime_folder
make release
```

### Guides
#### [Arch Linux](https://www.archlinux.org/)

  ```bash
   yay -S android-{ndk,sdk,sdk-build-tools,sdk-platform-tools,platform} gradle clang capnproto
   make release
  ```

  For other Linux distributions, you can install the required packages above with their own package managers.

#### macOS

1. Install [Android SDK](https://developer.android.com/sdk/index.html)

2. Set up ANDROID_SDK_ROOT
  ```bash
  # Android
   export ANDROID_SDK_ROOT="android_sdk_path"
  ```

3. Set up [mirror](https://mirrors.tuna.tsinghua.edu.cn/help/homebrew/) of Homebrew(Optional)

4. Install [Homebrew](https://brew.sh/)
  ```bash
   brew install cmake capnp
  ```

## Third Party Libraries
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
- [Android Traditional Chinese IME](https://code.google.com/p/android-traditional-chinese-ime/) (Apache License 2.0)

[Android Traditional Chinese IME]: https://code.google.com/p/android-traditional-chinese-ime/
[RIME]: http://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
