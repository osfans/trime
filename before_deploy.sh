#!/usr/bin/env bash

DIR=app/build/outputs/apk
pushd $DIR
FILE=$(ls */*.apk)
APKNAME=$(basename $FILE)
SHA256=$(sha256sum $FILE | head -c 64)
popd
echo "theme: jekyll-theme-cayman" > $DIR/_config.yml
cat > $DIR/README.md <<EOF
# 同文最新測試版  
**文件名：** $APKNAME [點擊下載](https://github.com/osfans/trime/raw/gh-pages/release/app-release.apk)  
**SHA256：** $SHA256  
**Trime版本：** [$(git describe --tags)-$(date +%Y%m%d)](https://github.com/osfans/trime/commits/$(git rev-parse HEAD))  
**RIME版本：** [$(git --git-dir=app/src/main/jni/librime/.git describe --tags)](https://github.com/rime/librime/commits/$(git --git-dir=app/src/main/jni/librime/.git rev-parse HEAD))  
**OpenCC版本：** [$(git --git-dir=app/src/main/jni/OpenCC/.git describe --tags)](https://github.com/BYVoid/OpenCC/commits/$(git --git-dir=app/src/main/jni/OpenCC/.git rev-parse HEAD))  
**類型：** 測試版  
**更新於：** $(date +%Y年%m月%d日)  

## 更新日志
$(git log --no-merges $(git describe --tags HEAD^ --abbrev=0).. --format="* %s [%h](https://github.com/osfans/trime/commit/%H)")
EOF
