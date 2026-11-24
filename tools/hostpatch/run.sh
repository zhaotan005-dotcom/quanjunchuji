#!/bin/bash
set -e
export JAVA_HOME=/workspace/work/rkenv/jdk-21.0.13+9; export PATH="$JAVA_HOME/bin:$PATH"
AJ=/workspace/work/tools/android-36/android.jar; APKSIG=/workspace/work/tools/android-16/lib/apksigner.jar
R=/workspace/work/quanjunchuji; O=/workspace/work/full; mkdir -p $O
V=$(cat $R/core/b1.ver)
for e in 1 2; do
 n=$([ $e = 1 ] && echo 服务端 || echo 客户端)
 java -Xmx3g -cp "/workspace/work/hostpatch/cls:$AJ:$APKSIG" Host /workspace/work/game_apk/game.apk $R/core/b$e.dex "$O/全军出击-ZFrame-v4.27-$n-整包.apk" /workspace/work/hostpatch/wd$e $R/zframe/assets/gkey.pk8 $R/zframe/assets/gcert.der
done
ls -la $O
