<!--
SPDX-FileCopyrightText: 2015 - 2026 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# Trime

Rime IME for Android

![build](https://github.com/osfans/trime/actions/workflows/commit-ci.yml/badge.svg?branch=develop)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/release/osfans/trime.svg)](https://github.com/osfans/trime/releases)
[![F-Droid release](https://img.shields.io/f-droid/v/com.osfans.trime.svg)](https://f-droid.org/packages/com.osfans.trime)
[![Latest build](https://img.shields.io/github/last-commit/osfans/trime.svg)](https://github.com/osfans/trime/releases/tag/nightly)

English | [简体中文](README_sc.md) | [繁體中文](README_tc.md)

## About

Trime is an Android input method frontend built on the [RIME] input method engine via JNI and written in Kotlin. It originated from the open-source [Android Traditional Chinese IME] project. It supports Android 8.0 and later, and is designed to help preserve the native languages of Chinese dialects while providing a universal platform for shape-based and phonetic-based input methods.

[Documentation](https://github.com/osfans/trime/wiki)

## Download

- Stable Channel <br>
  [<img alt='Get it on F-Droid' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' height='80px'/>](https://f-droid.org/packages/com.osfans.trime)

- Nightly Channel [Download](https://github.com/osfans/trime/releases/tag/nightly)

- Canary Channel [Download](https://github.com/osfans/trime/actions)

- Configurations [rimerc](https://github.com/Bambooin/rimerc)

## History

TRIME is the abbreviation of _Tongwen RIME_ or _ThaeRv Input Method_.

From the beginning, TRIME was written for TaeRv Pinyin, and named _TaeRv Input Method (泰如输入法)_.

Then, we created an input method platform with some code tables, such as Wu dialect (吴语). We renamed it to _Chinese Character Dialect Input Method (汉字方言输入法)_.

Later, it supported Wubi, Liangbi, and other shape-based input methods, and we branded it [_Tongwen Input Method Platform 2.0 (同文输入法平台 2.0)_](https://github.com/osfans/trime-legacy), which implies that phonetic-based and shape-based input methods share one platform, while dialects and Mandarin share a common set of characters.

Thanks to the [librime](https://github.com/rime/librime) project and JNI, we are now at version 3.x of TRIME, also known as _Tongwen Input Method (同文输入法)_.

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md).

## Getting Started for developer

### Prepare

#### Requirements:

- Android SDK and Android NDK
  * If you are new to Android development, please install [Android Studio](https://developer.android.com/studio). It will install and configure the required SDK, NDK, and CMake versions.

- JDK (OpenJDK) 17
- Python 3 (required by OpenCC to generate dictionary text files)

#### Prerequisites for Windows

Symbolic links will be created according to current build configurations, developers need:

- Enable [Developer Mode](https://learn.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development) so that symlinks can be created without administrator privilege.

- Enable symlink support for `git`:

  ```powershell
  git config --global core.symlinks true
  ```

If you cannot or wouldn't like to enable anything, it doesn't matter. Copying will be used instead when error on creating symbolic links.

### Build

#### 1. Clone this project and fetch all submodules:

```sh
git clone https://github.com/osfans/trime.git
cd trime
git submodule update --init --recursive

# Alternatively, use a partial clone to save time:
# git submodule update --init --recursive --filter=blob:none
```

#### 2. Debug version without signature:

```sh
# On Linux or macOS
make debug

# On Windows
.\gradlew assembleDebug
```

The first native build downloads Boost automatically, so an internet connection is required.

#### 3. Release version with signature:

Create a `keystore.properties` file in the repository root with your [signing information](https://developer.android.com/studio/publish/app-signing):

```properties
storePassword=myStorePassword
keyPassword=mykeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
```

Then run:

```sh
# On Linux or macOS
make release

# On Windows
.\gradlew assembleRelease
```

### Checks

Run unit tests and Android lint:

```sh
make check
```

Apply code style formatting:

```sh
make style-apply
```

### Troubleshooting

If a native build fails after updating submodules or toolchains, remove the build artifacts and try again:

```sh
# On Linux or macOS
make clean

# On Windows
.\gradlew clean
```

Other issues:

1. Make sure all submodules are initialized and up to date. If one or more submodules are modified, make sure they are compatible with the current revision.
2. If the problem persists, try a fresh clone.
3. Check whether an existing issue or pull request covers the problem and try the suggested solution.
4. If none of the above helps, open an issue with your build logs.

## Acknowledgments

- Developer: [osfans](https://github.com/osfans)
- Contributors: [boboIqiqi](https://github.com/boboIqiqi), [Bambooin](https://github.com/Bambooin), [senchi96](https://github.com/senchi96), [heiher](https://github.com/heiher), [abay](https://github.com/a342191555), [iovxw](https://github.com/iovxw), [huyz-git](https://github.com/huyz-git), [tumuyan](https://github.com/tumuyan), [WhiredPlanck](https://github.com/WhiredPlanck), [nopdan](https://github.com/nopdan). See [AUTHORS](AUTHORS) for the full list.
- [Wiki Editors](https://github.com/osfans/trime/wiki): [xiaoqun2016](https://github.com/xiaoqun2016), [boboIqiqi](https://github.com/boboIqiqi)...
- Translators: 天真可爱的满满 (Chinese Traditional), 点解 (English) ...
- Keyboard Designers: 天真可爱的满满、皛筱晓小笨鱼、吴琛 11、熊猫阿 Bo、默默ㄇㄛ ˋ...
- Donations: See QR Code in [Releases](https://github.com/osfans/trime/releases)
- Community: Everyone who has provided feedback in [Issues](https://github.com/osfans/trime/issues), [QQ Group (811142286)](https://jq.qq.com/?_wv=1027&k=AXdR80HN), [QQ Group (224230445)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=pg_q7UVumWYLq1Rk8kIAqkK1xGt64VnX&authKey=04m9l7OBO5H5vgrEL8IbpsmtnptWM60xy%2FUwYCfyvw9VcRhe8zRzAS1ezoemZdFr&noverify=0&group_code=224230445), [Tieba](http://tieba.baidu.com/f?kw=rime), and [Telegram](https://t.me/trime_dev).
- Projects: [RIME], [OpenCC], [Android Traditional Chinese IME] and so on.

## Third-Party Libraries

- [Boost C++ Libraries](https://www.boost.org/) (Boost Software License)
- [darts-clone](https://github.com/s-yata/darts-clone) (New BSD License)
- [LevelDB](https://github.com/google/leveldb) (New BSD License)
- [libiconv](https://www.gnu.org/software/libiconv/) (LGPL License)
- [librime-lua](https://github.com/hchunhui/librime-lua) (BSD 3-Clause License)
- [librime-octagram](https://github.com/lotem/librime-octagram) (GPL-3.0 License)
- [librime-predict](https://github.com/rime/librime-predict) (BSD 3-Clause License)
- [marisa-trie](https://github.com/s-yata/marisa-trie) (BSD License)
- [glog](https://github.com/google/glog) (New BSD License)
- [OpenCC](https://github.com/BYVoid/OpenCC) (Apache License 2.0)
- [RIME](https://rime.im) (BSD License)
- [snappy](https://github.com/google/snappy) (BSD License)
- [utfcpp](https://github.com/nemtrif/utfcpp) (Boost Software License)
- [yaml-cpp](https://github.com/jbeder/yaml-cpp) (MIT License)
- [Android Traditional Chinese IME](https://code.google.com/archive/p/android-traditional-chinese-ime/) (Apache License 2.0)

[Android Traditional Chinese IME]: https://code.google.com/archive/p/android-traditional-chinese-ime/
[RIME]: https://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
