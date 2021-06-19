#!/usr/bin/env bash

current=$(git describe --tags --abbrev=0)
previous=$(git describe --always --abbrev=0 --tags ${current}^)

git log --oneline --decorate ${previous}...${curent} --pretty="format:- %h %s" | grep -v Merge
