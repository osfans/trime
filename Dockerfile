FROM ubuntu:18.04
# VERSION 3
# 如果网络环境不好，可先curl ndk cmdline-tools capnproto cmake 的包，然后把对应的curl换成COPY
LABEL maintainer="sloera sloera@163.com"
# RUN sed -i 's/archive.ubuntu.com/mirrors.ustc.edu.cn/g' /etc/apt/sources.list
RUN sed -i s@/archive.ubuntu.com/@/mirrors.aliyun.com/@g /etc/apt/sources.list
RUN sed -i s@/security.ubuntu.com/@/mirrors.aliyun.com/@g /etc/apt/sources.list
RUN apt update -y
# RUN apt upgrade -y
RUN export DEBIAN_FRONTEND=noninteractive
RUN apt install -y \
make \
cmake \
vim \
git \
wget curl \
# google-android-ndk-installer \
# google-android-platform-24-installer \
ninja-build \
android-sdk \
android-sdk-build-tools \
android-sdk-platform-tools \
gradle clang

WORKDIR /tmp
# capnproto
# install android-ndk
RUN curl https://dl.google.com/android/repository/android-ndk-r23b-linux.zip?hl=zh-cn -o android-ndk-r23b-linux.zip
# COPY android-ndk-r23b-linux.zip /tmp
RUN unzip /tmp/android-ndk-r23b-linux.zip -d /usr/lib && mv /usr/lib/android-ndk-r23b/ /usr/lib/android-ndk
RUN wget https://github.com/Kitware/CMake/releases/download/v3.18.1/cmake-3.18.1-Linux-x86_64.tar.gz
# COPY cmake-3.18.1-Linux-x86_64.tar.gz /tmp
RUN tar zxf cmake-3.18.1-Linux-x86_64.tar.gz -C /tmp
# 在工程的`local.properties` 添加 `cmake.dir=/tmp/cmake-3.18.1-Linux-x86_64`
RUN curl https://dl.google.com/android/repository/commandlinetools-linux-8092744_latest.zip\?hl\=zh-cn -o commandlinetools
# COPY commandlinetools /tmp
RUN mkdir -p /usr/lib/android-sdk/ && unzip /tmp/commandlinetools -d /usr/lib/android-sdk && mkdir -p /usr/lib/android-sdk/cmdline-tools/latest && find /usr/lib/android-sdk/cmdline-tools/ -maxdepth 1 -not -name latest -not -wholename /usr/lib/android-sdk/cmdline-tools/ -exec mv {} /usr/lib/android-sdk/cmdline-tools/latest \;
# 默认授权
RUN yes | sdkmanager --licenses
# capnproto
RUN curl -O https://capnproto.org/capnproto-c++-0.9.1.tar.gz  && tar zxf capnproto-c++-0.9.1.tar.gz -C /tmp && cd capnproto-c++-0.9.1 && ./configure --prefix=/usr && make -j6 check && make install && cp /usr/bin/capnp* /usr/local/bin/

# ndk
ENV NDK_HOME "/usr/lib/android-ndk"
ENV ANDROID_SDK "/usr/lib/android-sdk"
ENV ANDROID_SDK_ROOT "/usr/lib/android-sdk"
ENV CMAKE_ROOT "/tmp/cmake-3.18.1-Linux-x86_64"
ENV PATH "$PATH:$NDK_HOME:$ANDROID_SDK/tools:$ANDROID_SDK/platform-tools:$ANDROID_SDK/cmdline-tools/latest/bin:$CMAKE_ROOT/bin"
# Build librime
WORKDIR /
RUN git clone --recursive https://github.com/osfans/trime.git
WORKDIR trime/
RUN make debug
