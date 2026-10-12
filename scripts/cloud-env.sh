#!/usr/bin/env bash
# Source this file in each new cloud shell after running setup-cloud.sh.
export CAMPOSE_TOOLCHAIN_DIR="${CAMPOSE_TOOLCHAIN_DIR:-/workspace/toolchains}"
export JAVA_HOME="$CAMPOSE_TOOLCHAIN_DIR/jdk"
export ANDROID_HOME="$CAMPOSE_TOOLCHAIN_DIR/android-sdk"
export ANDROID_USER_HOME="$CAMPOSE_TOOLCHAIN_DIR/android-user-home"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$CAMPOSE_TOOLCHAIN_DIR/avd"
export GRADLE_USER_HOME="$CAMPOSE_TOOLCHAIN_DIR/gradle-home"
export XDG_RUNTIME_DIR="$CAMPOSE_TOOLCHAIN_DIR/run"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/19.0/bin:$PATH"
if [[ -f "$CAMPOSE_TOOLCHAIN_DIR/java-options" ]]; then
    export JAVA_OPTS="$(cat "$CAMPOSE_TOOLCHAIN_DIR/java-options")"
fi
