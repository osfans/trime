#!/usr/bin/env bash
# version on submodule must match
# update summodule if you change this
version=0.9.1
echo "$(nproc) thread to build"
curl -O https://capnproto.org/capnproto-c++-${version}.tar.gz
tar zxf capnproto-c++-${version}.tar.gz
cd capnproto-c++-${version}
./configure
make -j$(nproc)
sudo make install
