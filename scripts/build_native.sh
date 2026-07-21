#!/bin/bash
set -euo pipefail

NDK="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/28.2.13676358}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"

export CC_x86_64_linux_android="$TOOLCHAIN/bin/x86_64-linux-android26-clang"
export CC_aarch64_linux_android="$TOOLCHAIN/bin/aarch64-linux-android26-clang"
export AR_x86_64_linux_android="$TOOLCHAIN/bin/llvm-ar"
export AR_aarch64_linux_android="$TOOLCHAIN/bin/llvm-ar"
export RANLIB_x86_64_linux_android="$TOOLCHAIN/bin/llvm-ranlib"
export RANLIB_aarch64_linux_android="$TOOLCHAIN/bin/llvm-ranlib"

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN/bin/aarch64-linux-android26-clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$TOOLCHAIN/bin/x86_64-linux-android26-clang"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VAULT_DIR="$SCRIPT_DIR/../vault-native"
OUTPUT_DIR="$SCRIPT_DIR/../vault-native-android/src/main"

for target in aarch64-linux-android x86_64-linux-android; do
    echo "Building for $target..."
    cargo build --manifest-path "$VAULT_DIR/Cargo.toml" --target "$target" --release
done

cp "$VAULT_DIR/target/aarch64-linux-android/release/libvault_native.so" "$OUTPUT_DIR/jniLibs/arm64-v8a/"
cp "$VAULT_DIR/target/x86_64-linux-android/release/libvault_native.so" "$OUTPUT_DIR/jniLibs/x86_64/"

echo "Generating Kotlin bindings from x86_64 .so..."
BINDING_OUT="$OUTPUT_DIR/java"
cargo run --manifest-path "$VAULT_DIR/Cargo.toml" --example gen_kotlin -- \
    "$VAULT_DIR/target/x86_64-linux-android/release/libvault_native.so" \
    "$BINDING_OUT"

echo "Done. Files updated in vault-native-android/src/main/."

if [ "${1:-}" = "--apk" ]; then
    echo "Rebuilding debug APK..."
    touch "$OUTPUT_DIR/jniLibs/arm64-v8a/libvault_native.so" "$OUTPUT_DIR/jniLibs/x86_64/libvault_native.so"
    "$SCRIPT_DIR/../gradlew" -p "$SCRIPT_DIR/.." assembleDebug
fi
