<!--
SPDX-FileCopyrightText: 2015 - 2026 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# 同文 Android 输入法平台

![build](https://github.com/osfans/trime/actions/workflows/commit-ci.yml/badge.svg?branch=develop)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/release/osfans/trime.svg)](https://github.com/osfans/trime/releases)
[![F-Droid release](https://img.shields.io/f-droid/v/com.osfans.trime.svg)](https://f-droid.org/packages/com.osfans.trime)
[![Latest build](https://img.shields.io/github/last-commit/osfans/trime.svg)](https://github.com/osfans/trime/releases/tag/nightly)

[English](README.md) | 简体中文 | [繁體中文](README_tc.md)

## 关于

同文（Trime）是一个 Android 输入法前端，基于著名的 [RIME] 输入法框架，使用 Kotlin 编写并通过 JNI 调用原生代码。它源自开源的[注音仓颉输入法]项目，支持 Android 8.0 及以上版本，旨在保护汉语各地方言母语，是音码、形码通用的输入法平台。

[查看文档](https://github.com/osfans/trime/wiki)

## 下载

- 稳定版 <br>
  [<img alt='Get it on F-Droid' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' height='80px'/>](https://f-droid.org/packages/com.osfans.trime)

- 每夜版 [点击下载](https://github.com/osfans/trime/releases/tag/nightly)

- 测试版 [点击下载](https://github.com/osfans/trime/actions)

- 配置文档 [rimerc](https://github.com/Bambooin/rimerc)

## 沿革

TRIME 是 Tongwen RIME 或是 ThaeRv Input Method 的缩写：

- 最初，输入法是写给[泰如拼音](http://taerv.nguyoeh.com/ime/)（tae5 rv2）的，中文名为“泰如输入法”；
- 然后，添加了吴语等方言码表，做成了一个输入法平台，更名为“汉字方言输入法”；
- 后来，兼容了五笔、两笔等形码，在太空卫士、征羽的建议下，更名为“[同文输入法平台 2.x](https://github.com/osfans/trime-legacy)”。寓意音码形码同台，方言官话同文；
- 之后，借助 JNI 技术，享受了 [librime](https://github.com/rime/librime) 的成果，升级为“同文输入法平台 3.x”，简称“同文输入法”。

欢迎参与[贡献](CONTRIBUTING.md)！

## 开发入门

### 准备

#### 开发环境要求

- Android SDK 和 Android NDK
  - 如果还不熟悉 Android 开发，建议安装 [Android Studio](https://developer.android.google.cn/studio)，它会自动安装并配置所需的 SDK、NDK 和 CMake 版本。

- JDK（OpenJDK）17
- Python 3（用于给 OpenCC 生成词典文本文件）

#### Windows 上的前提条件

当前构建配置会在构建过程中创建符号链接，开发者需要：

- 启用[开发者模式](https://learn.microsoft.com/zh-cn/windows/apps/get-started/enable-your-device-for-development) 以在无管理员权限的情况下创建符号链接。

- 启用 `git` 的符号链接支持：

  ```powershell
  git config --global core.symlinks true
  ```

如果无法或不想启用上述设置也没关系。构建系统会在符号链接创建失败时自动改用复制。

### 构建

#### 1. 克隆此项目并拉取所有子模块：

```sh
git clone https://github.com/osfans/trime.git
cd trime
git submodule update --init --recursive

# 或者使用部分克隆节约时间：
# git submodule update --init --recursive --filter=blob:none
```

#### 2. 编译调试版本：

```sh
# On Linux or macOS
make debug

# On Windows
.\gradlew assembleDebug
```

首次原生构建会自动下载 Boost，需要网络连接。

#### 3. 编译正式版本：

请在仓库根目录创建 `keystore.properties` 文件，包含以下[签名信息](https://developer.android.com/studio/publish/app-signing)：

```properties
storePassword=myStorePassword
keyPassword=mykeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
```

然后执行：

```sh
# On Linux or macOS
make release

# On Windows
.\gradlew assembleRelease
```

### 检查

运行单元测试和 Android Lint：

```sh
make check
```

应用代码风格格式化：

```sh
make style-apply
```

### 故障排除

如果在更新子模块或工具链后原生构建失败，请清除构建产物后重试：

```sh
# On Linux or macOS
make clean

# On Windows
.\gradlew clean
```

其他问题：

1. 确认所有子模块都已初始化且为最新版本。如果修改了一个或多个子模块，请确保它们与当前修订版本兼容。
2. 如果问题仍然存在，尝试重新克隆仓库。
3. 检查是否有相关的 issue 或 PR，并尝试其中建议的解决方案。
4. 如果以上方法都无效，请附上构建日志提交 issue。

## 鸣谢

- 开发：[osfans](https://github.com/osfans)
- 贡献：[boboIqiqi](https://github.com/boboIqiqi)、[Bambooin](https://github.com/Bambooin)、[senchi96](https://github.com/senchi96)、[heiher](https://github.com/heiher)、[abay](https://github.com/a342191555)、[iovxw](https://github.com/iovxw)、[huyz-git](https://github.com/huyz-git)、[tumuyan](https://github.com/tumuyan)、[WhiredPlanck](https://github.com/WhiredPlanck)、[nopdan](https://github.com/nopdan)。完整名单见 [AUTHORS](AUTHORS)。
- [维基](https://github.com/osfans/trime/wiki)：[xiaoqun2016](https://github.com/xiaoqun2016)、[boboIqiqi](https://github.com/boboIqiqi)......
- 翻译：天真可爱的满满（繁体中文）、点解（英文）......
- 键盘：天真可爱的满满、皛筱晓小笨鱼、吴琛 11、熊猫阿 Bo、默默ㄇㄛ ˋ......
- 捐赠：[Releases](https://github.com/osfans/trime/releases) 中的“打赏”实时更新
- 社区：在 [Issues](https://github.com/osfans/trime/issues)、[QQ 群 (811142286)](https://jq.qq.com/?_wv=1027&k=AXdR80HN)、[QQ 群 (224230445)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=pg_q7UVumWYLq1Rk8kIAqkK1xGt64VnX&authKey=04m9l7OBO5H5vgrEL8IbpsmtnptWM60xy%2FUwYCfyvw9VcRhe8zRzAS1ezoemZdFr&noverify=0&group_code=224230445)、[贴吧](http://tieba.baidu.com/f?kw=rime)、[Telegram](https://t.me/trime_dev) 中反馈意见的网友
- 项目：[RIME]、[OpenCC]、[注音仓颉输入法]等开源项目

## 第三方库

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
- [注音仓颉输入法](https://code.google.com/archive/p/android-traditional-chinese-ime/) (Apache License 2.0)

[注音仓颉输入法]: https://code.google.com/archive/p/android-traditional-chinese-ime/
[RIME]: https://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
