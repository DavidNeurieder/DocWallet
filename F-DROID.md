# F-Droid Build Recipe

PDF rendering uses pdf_oxide, a pure-Rust PDF library with no native C
dependencies.  No MuPDF, no Ghostscript Maven repo, no C compilation.
The only native code in the APK is the Rust `libvault_native.so` built by
`vault-native-android/build.gradle.kts`.

## Rust native library (`vault-native`)

LibreCrate uses a Rust library (`vault-native/core/`) exposed via UniFFI to
Kotlin/JNA. Neither the `.so` files nor the Kotlin bindings are committed to
git. **Gradle handles everything automatically:**

1. Builds Rust for the host platform (`x86_64-unknown-linux-gnu`)
2. Generates Kotlin UniFFI bindings from the host `.so`
3. Builds Rust for Android (`aarch64-linux-android`) with the `pdf` feature
4. Copies the `.so` to `jniLibs/` before APK packaging

Prerequisites: Rustup, `cargo`, and Android NDK (set `ANDROID_NDK_HOME`).

Rust version is pinned via `vault-native/rust-toolchain.toml`. Rustup reads
this file automatically and installs the correct version + Android target on
first `cargo build`. No `rustup target add` needed in the recipe.

### Recipe

```yaml
Builds:
  - versionName: 0.4.0
    versionCode: 4
    commit: v0.4.0
    sudo:
      - apt-get update
      - apt-get install -y make pkg-config curl
    ndk: r28c
    prebuild:
      - curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
    gradle:
      - yes
```

Gradle's `assembleRelease` task automatically runs the Rust build pipeline
(bindings generation → Android `.so` → copy to jniLibs) before compiling the
APK. Rustup reads `vault-native/rust-toolchain.toml` during `cargo build` and
installs the pinned Rust version + `aarch64-linux-android` target on the fly.

### Key details

| Item | Value |
|------|-------|
| Rust version | Pinned via `vault-native/rust-toolchain.toml` |
| UniFFI version | 0.28 (library mode, no `.udl` file) |
| Host target | `x86_64-unknown-linux-gnu` (default in Rustup, no action needed) |
| Android target | `aarch64-linux-android` (auto-installed by `rust-toolchain.toml`) |
| JNA | `net.java.dev.jna:jna:5.14.0@aar` (from Maven Central, no issue) |
| Min API | 26 |

### Updating Rust

1. Update the Rust source in `vault-native/core/`.
2. Run `./gradlew assembleDebug` — Gradle rebuilds everything automatically.
3. No pre-committed artifacts to update.
