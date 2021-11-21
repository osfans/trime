#!/usr/bin/env bash

# build from submodule source, keep same version
echo "$(nproc) threads to build"
cd app/src/main/jni/capnproto/c++
autoreconf -i
./configure
make -j$(nproc)
sudo make install
