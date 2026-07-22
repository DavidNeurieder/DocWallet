#!/bin/bash
set -euo pipefail

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    echo "ERROR: ANDROID_NDK_HOME is not set." >&2
    echo "Set it to your Android NDK path, e.g.:" >&2
    echo "  export ANDROID_NDK_HOME=\$HOME/Android/Sdk/ndk/28.2.13676358" >&2
    exit 1
fi

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"

export CC_aarch64_linux_android="$TOOLCHAIN/bin/aarch64-linux-android26-clang"
export AR_aarch64_linux_android="$TOOLCHAIN/bin/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN/bin/aarch64-linux-android26-clang"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VAULT_DIR="$SCRIPT_DIR/../vault-native"

echo "Building for aarch64-linux-android..."
cargo build --manifest-path "$VAULT_DIR/Cargo.toml" -p vault-native --target aarch64-linux-android --release

echo "Done. Use './gradlew assembleDebug' to build the full APK (Gradle auto-generates bindings and copies the .so)."
