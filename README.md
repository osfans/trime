# 同文安卓輸入法平臺/Trime/android-rime
[![自動編譯](https://travis-ci.org/osfans/trime.svg?branch=master)](https://travis-ci.org/osfans/trime)

=====
源於開源的[注音倉頡輸入法]前端，
基於著名的[Rime]輸入法框架，
使用JNI的C語言和安卓的java語言書寫，
旨在保護漢語各地方言母語，
音碼形碼通用的輸入法平臺。

## 鳴謝/Credits
- 作者：[osfans](https://github.com/osfans)
- [維基](https://github.com/osfans/trime/wiki)：[xiaoqun2016](https://github.com/xiaoqun2016)、[boboIqiqi](https://github.com/boboIqiqi)
- 翻譯：天真可愛的滿滿（繁體中文）、點解（英文）
- 鍵盤：天真可愛的滿滿、皛筱晓小笨鱼、吴琛11、熊貓阿Bo、默默ㄇㄛˋ
- [捐贈](https://github.com/osfans/trime/releases)：[yueduz](https://github.com/yueduz)、[xiaoqun2016](https://github.com/xiaoqun2016)、[ipcjs](https://github.com/ipcjs)、Anonymous、啸傲居士、 矛矛、[zcunlin](https://github.com/zcunlin)、北冥有鱼、[biopolyhedron](https://github.com/biopolyhedron)、李進、國林、雷、天使的心跳、幸運超人、小雷先生、yeachdata
- 社區：在[Issues](https://github.com/osfans/trime/issues)、[貼吧](http://tieba.baidu.com/f?kw=rime)、QQ羣中反饋意見的網友
- 項目：[Rime]、[OpenCC]、[注音倉頡輸入法]等開源項目

## 沿革/History
- 最初，輸入法是寫給[泰如拼音](http://tieba.baidu.com/f?kw=%E6%B3%B0%E5%A6%82)（thae5 rv2）的，中文名爲“泰如輸入法”。
- 然後，添加了吳語等方言碼表，做成了一個輸入法平臺，更名爲“漢字方言輸入法”。
- 後來，兼容了五筆、兩筆等形碼，在太空衛士、徵羽的建議下，更名爲“同文輸入法平臺2.x”。寓意音碼形碼同臺，方言官話同文。
- 之後，藉助JNI技術，享受了[librime](https://github.com/rime/librime)的成果，升級爲“同文輸入法平臺3.x”，簡稱“同文輸入法”。
- 所以，TRIME是Tongwen RIME或是ThaeRvInputMEthod的縮寫。

## 編譯/Build
- [Arch Linux](https://www.archlinux.org/)
```bash
yaourt -S android-{ndk,sdk,sdk-build-tools,sdk-platform-tools,platform} apache-ant
make apk
```

## 許可協議/License
[Apache License 2.0](COPYING)

## 第三方庫/3rd Party Library
- [android-cmake](https://github.com/taka-no-me/android-cmake) (BSD 3-Clause License)
- [minilog](http://ceres-solver.org/) (New BSD License)
- [OpenCC](https://github.com/BYVoid/OpenCC) (Apache License 2.0)
- [RIME](http://rime.im) (BSD License)
 - [Boost C++ Libraries](http://www.boost.org/) (Boost Software License)
   - [libiconv](http://www.gnu.org/software/libiconv/) (LGPL License)
 - [darts-clone](https://code.google.com/p/darts-clone/) (New BSD License)
 - [marisa-trie](https://code.google.com/p/marisa-trie/) (BSD License)
 - [UTF8-CPP](http://utfcpp.sourceforge.net/) (Boost Software License)
 - [yaml-cpp](https://code.google.com/p/yaml-cpp/) (MIT License)
 - [LevelDB](https://github.com/google/leveldb) (New BSD License)
   - [snappy](https://google.github.io/snappy/)(BSD License)
 
[注音倉頡輸入法]: https://code.google.com/p/android-traditional-chinese-ime/
[Rime]: http://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
