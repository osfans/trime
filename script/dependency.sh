#!/usr/bin/env bash

sudo apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 04EE7237B7D453EC
sudo apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 648ACFD622F3D138
sudo add-apt-repository 'deb http://deb.debian.org/debian experimental main'
sudo apt update
sudo apt-get -t experimental -y install capnproto=0.8.0-1
