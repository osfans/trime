# 同文 Android 输入法平台
![build](https://github.com/osfans/trime/actions/workflows/commit-ci.yml/badge.svg?branch=develop)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/release/osfans/trime.svg)](https://github.com/osfans/trime/releases)
[![F-Droid release](https://img.shields.io/f-droid/v/com.osfans.trime.svg)](https://f-droid.org/packages/com.osfans.trime)
[![Latest build](https://img.shields.io/github/last-commit/osfans/trime.svg)](http://osfans.github.io/trime/)

[English](README.md) | [简体中文](README_sc.md) | [繁體中文](README_tc.md)

## 关于

源于开源的[注音仓颉输入法]前端，基于著名的 [RIME] 输入法框架，使用 JNI 的 C 语言和 Android 的 Java/Kotlin 语言书写，旨在保护汉语各地方言母语，音码、形码通用的输入法平台。

## 下载
- 稳定版 <br>
[<img alt='Get it on F-Droid' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' height='80px'/>](https://f-droid.org/packages/com.osfans.trime)
[<img alt='Google Play 立即下载' src='https://play.google.com/intl/en_us/badges/images/generic/zh-cn_badge_web_generic.png' height='80px'/>](https://play.google.com/store/apps/details?id=com.osfans.trime)
[<img alt='酷安' src='https://static.coolapk.com/static/web/v8/img/icon.png' height='60px'/>](https://www.coolapk.com/apk/com.osfans.trime)

- 测试版 [点击下载](https://github.com/osfans/trime/actions)

- 配置文档 [rimerc](https://github.com/Bambooin/rimerc)

## 鸣谢
- 开发：[osfans](https://github.com/osfans)
- 贡献：[boboIqiqi](https://github.com/boboIqiqi)、[Bambooin](https://github.com/Bambooin)、[senchi96](https://github.com/senchi96)、[heiher](https://github.com/heiher)、[abay](https://github.com/a342191555)、[iovxw](https://github.com/iovxw)、[huyz-git](https://github.com/huyz-git)、[tumuyan](https://github.com/tumuyan)、[WhiredPlanck](https://github.com/WhiredPlanck)......
- [维基](https://github.com/osfans/trime/wiki)：[xiaoqun2016](https://github.com/xiaoqun2016)、[boboIqiqi](https://github.com/boboIqiqi)......
- 翻译：天真可爱的满满（繁体中文）、点解（英文）......
- 键盘：天真可爱的满满、皛筱晓小笨鱼、吴琛11、熊猫阿Bo、默默ㄇㄛˋ......
- 捐赠：[Releases](https://github.com/osfans/trime/releases) 中的“打赏”实时更新
- 社区：在 [Issues](https://github.com/osfans/trime/issues)、[QQ 群（811142286）](https://jq.qq.com/?_wv=1027&k=AXdR80HN)、[QQ 群（458845988）](https://jq.qq.com/?_wv=1027&k=n6xT4G3q)、[酷安](http://www.coolapk.com/apk/com.osfans.trime)、[Google Play](https://play.google.com/store/apps/details?id=com.osfans.trime) 和[贴吧](http://tieba.baidu.com/f?kw=rime)中反馈意见的网友
- 项目：[RIME]、[OpenCC]、[注音仓颉输入法]等开源项目

## 沿革
TRIME 是 Tongwen RIME 或是 ThaeRvInputMEthod 的缩写:

- 最初，输入法是写给[泰如拼音](http://taerv.nguyoeh.com/ime/)（tae5 rv2）的，中文名为“泰如输入法”;
- 然后，添加了吴语等方言码表，做成了一个输入法平台，更名为“汉字方言输入法”;
- 后来，兼容了五笔、两笔等形码，在太空卫士、征羽的建议下，更名为“[同文输入法平台 2.x](https://github.com/osfans/trime-legacy)”。寓意音码形码同台，方言官话同文。
- 之后，借助 JNI 技术，享受了 [librime](https://github.com/rime/librime) 的成果，升级为“同文输入法平台 3.x”，简称“同文输入法”。

现在欢迎你前来[贡献](CONTRIBUTING.md) ～！:tada:

## 编译

1. 克隆此项目，**请注意**由于 `boost` 子模块很大，这会花费一些时间。同时，请确保你的磁盘有足够空间保存源代码（约 1.5 GB);
```bash
cd $your_folder
git clone --recursive https://github.com/osfans/trime.git
```
2. 如果你想测试应用，运行命令：

```bash
cd $trime_folder
make debug
```

或者要为应用签名以释放更新，请创建一个名为 `keystore.properties` 的文件，包含以下内容，注明[签名信息](https://developer.android.com/studio/publish/app-signing.html)：

```bash
storePassword=myStorePassword
keyPassword=mykeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
```

然后运行命令：

```bash
cd $trime_folder
make release
```

### 在特定平台上预编译
#### [Arch Linux](https://www.archlinux.org/)

  ```bash
   yay -S android-{ndk,sdk,sdk-build-tools,sdk-platform-tools,platform} gradle clang capnproto
   make release
  ```

  对于其他 Linux 发行版，请同样使用它们各自的包管理器安装上述开发环境。

#### macOS

1. 安装 [Android SDK](https://developer.android.com/sdk/index.html)

2. 设置相应的 ANDROID_SDK_ROOT
  ```bash
  # Android
   export ANDROID_SDK_ROOT="android_sdk_path"
  ```

3. 设置 Homebrew [镜像](https://mirrors.tuna.tsinghua.edu.cn/help/homebrew/)(可选)

4. 安装 [Homebrew](https://brew.sh/)
  ```bash
   brew install cmake capnp
  ```

## 第三方库
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
- [注音仓颉输入法](https://code.google.com/p/android-traditional-chinese-ime/) (Apache License 2.0)

[注音仓颉输入法]: https://code.google.com/p/android-traditional-chinese-ime/
[RIME]: http://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
