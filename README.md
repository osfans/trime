# Trime
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

## Getting Started for developer

### Prepare

Android SDK and Android NDK should be correctly installed and configured. If you are new to Android development, please install Android Studio.

### Build

<details>
<summary>Prerequisites for Windows</summary>

Symbolic links will be created according to current build configurations, developers need:

- Enable [Developer Mode](https://learn.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development) so that symlinks can be created without administrator privilege.

- Enable symlink support for `git`:

    ```powershell
    git config --global core.symlinks true
    ```

If you cannot or wouldn't like to enable anything, it doesn't matter. Copying will be used instead when error on creating symbolic links.

</details>

1. Clone this project and fetch all submodules:

```sh
git clone git@github.com:osfans/trime.git
git submodule update --init --recursive
```

2. Debug version without signature:

On Linux or macOS, you may run:

```bash
make debug
```

On Windows, run:

```powershell
.\gradlew assembleDebug
```

3. Release version with signture:

Create `keystore.properties` file which contains following contents for [signing information](https://developer.android.com/studio/publish/app-signing.html):

```gradle.properties
storePassword=myStorePassword
keyPassword=mykeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
```

Then, on Linux or macOS, you may run:

```bash
make release
```

On Windows, run:

```powershell
.\gradlew assembleRelease
```

### Troubleshooting

```
Target "boost_log_setup" links to target "Boost::coroutine" but the target was not found.
```

Run `make clean` on Linux or macOS, or run `.\gradlew clean` on Windows.

Other issues:
1. Try `make clean`
2. Make sure your repo is up-to-date. If one or more submodules are modified, also make sure they are compatible with the current version.
3. If the problem still exists(very unlikely), try to make a new clone.
4. Check if this is there is an issue/PR related to your problem. If yes, try their solutions.
5. If none of them works, you may make an issue to ask for help.(optional)


## Third Party Libraries
- [Boost C++ Libraries](https://www.boost.org/) (Boost Software License)
- [darts-clone](https://github.com/s-yata/darts-clone) (New BSD License)
- [LevelDB](https://github.com/google/leveldb) (New BSD License)
- [libiconv](https://www.gnu.org/software/libiconv/) (LGPL License)
- [marisa-trie](https://github.com/s-yata/marisa-trie) (BSD License)
- [glog](https://github.com/google/glog) (New BSD License)
- [OpenCC](https://github.com/BYVoid/OpenCC) (Apache License 2.0)
- [RIME](https://rime.im) (BSD License)
- [snappy](https://github.com/google/snappy)(BSD License)
- [utfcpp](https://github.com/nemtrif/utfcpp) (Boost Software License)
- [yaml-cpp](https://github.com/jbeder/yaml-cpp) (MIT License)
- [Android Traditional Chinese IME](https://code.google.com/p/android-traditional-chinese-ime/) (Apache License 2.0)

[Android Traditional Chinese IME]: https://code.google.com/p/android-traditional-chinese-ime/
[RIME]: http://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
