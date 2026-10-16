#!/bin/zsh
# Headless benchmark of MTR 4.0.5's OBJ vehicle path vs the FastObj prototype. Never starts Minecraft.
#   tools/objbench/run.sh cpu <unzipped pack dir>...   # equivalence + CPU timing + heap
#   tools/objbench/run.sh gl  <unzipped pack dir>...   # shader reload + real GL upload (headless CGL context, no window)
# Needs JDK 21, the Gradle cache of this project (MTR 4.0.5 jar, Minecraft intermediary jar) and LWJGL 3.3.1 from PrismLauncher.
set -e
HERE=${0:A:h}
JH=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}
C=~/.gradle/caches/modules-2/files-2.1
L=~/Library/Application\ Support/PrismLauncher/libraries/org/lwjgl
f() { find $C/$1 -name "$2" ! -name "*sources*" ! -name "*javadoc*" | head -1 }
CP="$(f maven.modrinth/minecraft-transit-railway 'minecraft-transit-railway-FABRIC-4.0.5+1.20.1.jar')"
CP="$CP:$(find ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-intermediary -name '*yarn*.jar' | head -1)"
for spec in "org.joml joml-1.10.5.jar" "commons-io commons-io-2.11.0.jar" "org.apache.commons commons-lang3-3.12.0.jar" "com.google.guava guava-31.1-jre.jar" "com.google.guava failureaccess-1.0.1.jar" "com.google.code.gson gson-2.10.1.jar" "it.unimi.dsi fastutil-8.5.9.jar" "org.apache.logging.log4j log4j-api-2.19.0.jar" "org.apache.logging.log4j log4j-core-2.19.0.jar" "org.slf4j slf4j-api-2.0.1.jar"; do
	CP="$CP:$(f ${=spec})"
done
CP="$CP:$(find $C/com.mojang -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | grep -E 'logging|datafixerupper|brigadier|authlib' | tr '\n' ':')"
CP="$CP:$L/lwjgl/3.3.1/lwjgl-3.3.1.jar:$L/lwjgl-opengl/3.3.1/lwjgl-opengl-3.3.1.jar:$L/lwjgl-natives-macos-arm64/3.3.1/lwjgl-natives-macos-arm64-3.3.1.jar:$L/lwjgl-opengl-natives-macos-arm64/3.3.1/lwjgl-opengl-natives-macos-arm64-3.3.1.jar"
OUT=$HERE/out
mkdir -p $OUT
$JH/bin/javac -nowarn -d $OUT -cp "$CP" $HERE/src/*.java $HERE/../../src/main/java/com/aureliatransit/utilities/perf/MergedObjUpload.java
MODE=$1
shift
if [[ $MODE == gl ]]; then
	nice -n 10 $JH/bin/java -Xmx2g -Djava.awt.headless=true -cp "$OUT:$CP" GlBench ~/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar "$@"
else
	nice -n 10 $JH/bin/java -Xmx2g -cp "$OUT:$CP" Bench "$@" --runs 3 --threads 4 ${=BENCH_ARGS}
fi
