# Termoak for Android

The Android app of [Termoak](https://termoak.com), the open-source SSH client
with an optional self-hosted server: Jetpack Compose on top of the same Rust
engine as the desktop app and the CLI (through UniFFI).

The app is built in Docker, with nothing else to install:
`scripts/release-local.sh build android` puts the signed APK in
`dist/android/` (see [Releases](#releases)). It uses AGP 9
(with built-in Kotlin), compileSdk 37 and minSdk 26 (Android 8).

What it does:

- **Hosts**: search, filters by group and favorites, detected OS, full
  editor (password, key or identity, group, tags, notes, only on this phone)
  and pull-to-refresh sync.
- **Terminal**: the desktop emulator (`TerminalScreen`) with several tabs,
  pinch to zoom, two rows of extra keys (Esc, Ctrl, Alt, Tab, arrows…),
  snippets with variables, copy/paste, reconnect, and dialogs for the
  fingerprint and passwords. A foreground service keeps the connections
  alive while the app is in the background.
- **Server sessions**: open a host as a persistent session, attach to the
  active ones or to those shared with you, and end them.
- **AI**: tasks with their conversation, new tasks on specific hosts with a
  permission mode, and approving or denying actions from the phone (with a
  live notice in the tab).
- **Keychain and snippets**: generate and import keys, copy the public key,
  and create or edit snippets.
- **Settings**: account, 2FA, font size, keep screen on, theme, and checking
  your server for APK updates.

## Building

This repository includes [TermoakSSH/core](https://github.com/TermoakSSH/core) as a git submodule in
`core/`, pinned to a release tag: the native engine (`termoak-ffi`) is built
from it and the bindings come from its `bindings/` folder.

```sh
git clone --recurse-submodules https://github.com/TermoakSSH/mobile-android
# or, in an existing clone:
git submodule update --init
```

To move to a newer core:

```sh
git -C core fetch --tags && git -C core checkout v0.2.1
git add core && git commit -m "Core 0.2.1"
```

With Android Studio or Gradle, build the native libraries first (they go to
`core/bindings/kotlin/src/main/jniLibs`, which core ignores). That needs the
Android NDK, rustup and `cargo install cargo-ndk`:

```sh
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<version>
ABIS="arm64-v8a x86_64" ANDROID_API=26 core/scripts/build-android.sh debug
./gradlew assembleDebug
```

Without any of that installed, the release build below does everything in
Docker.

## Releases

The app is released as the `android-vX.Y.Z` GitHub release (currently
0.3.6, `termoakVersion` in `gradle.properties`), with an APK signed with
your keystore, built in the `core/scripts/android-builder.Dockerfile` image
(JDK 17, SDK 37, NDK 30, Rust and `cargo-ndk`):

```sh
scripts/release-local.sh android-keystore     # once: the signing key
scripts/release-local.sh version android 0.3.7
scripts/release-local.sh build android        # dist/android/Termoak-android-vX.Y.Z.apk
scripts/release-local.sh publish android
```

The keystore is kept in `~/.config/termoak/android/` (`keystore.jks` and its
password in `keystore.env`). **Keep a copy outside the machine**: Android
only installs an update over the app if it is signed with the same key.

## Documentation

- [Mobile apps and the FFI layer](https://github.com/TermoakSSH/core/blob/main/docs/MOBILE.md) (in core)
- [Internationalization](https://github.com/TermoakSSH/core/blob/main/docs/I18N.md): the strings are in
  `app/src/main/res/values[-<lang>]/strings.xml`

## The Termoak repositories

| Repository | Contents |
|---|---|
| [TermoakSSH/core](https://github.com/TermoakSSH/core) | Shared crates (SSH engine, vault, API client, AI engine, FFI bindings, updates) and the `termoak` CLI |
| [TermoakSSH/server](https://github.com/TermoakSSH/server) | `termoak-server`: HTTP/WebSocket API, basic web app, deployment files |
| [TermoakSSH/desktop](https://github.com/TermoakSSH/desktop) | Desktop app (GPUI) for Windows, Linux and macOS |
| **[TermoakSSH/mobile-android](https://github.com/TermoakSSH/mobile-android)** | Android app (Jetpack Compose) |
| [TermoakSSH/mobile-ios](https://github.com/TermoakSSH/mobile-ios) | iOS app (SwiftUI) |
| [TermoakSSH/public-web](https://github.com/TermoakSSH/public-web) | Public website of termoak.com: landing, pricing and downloads |


## Contributing and translations

Bug reports, fixes, features and translations are welcome: see
[CONTRIBUTING.md](CONTRIBUTING.md). Translating Termoak into your language
needs no programming: copy the English strings file of an app, translate it
and open a pull request ([docs/I18N.md](https://github.com/TermoakSSH/core/blob/main/docs/I18N.md)).

## License

Copyright © Ohz Digital SL.

Termoak is free software released under the
[GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0-only).

"Termoak" and the Termoak logo are trademarks of Ohz Digital SL and are not
covered by the code license: see [TRADEMARK.md](TRADEMARK.md).
