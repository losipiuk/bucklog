# Bucklog

Family spend tracker (Android first, iOS later) backed by a shared Google Sheet.

- [SPEC.md](SPEC.md): product and technical specification
- [docs/google-cloud-setup.md](docs/google-cloud-setup.md): one-time Google Cloud setup
- [docs/m0-spike.md](docs/m0-spike.md): M0 spike checklist

## Build

Requires JDK 21+ and the Android SDK (`local.properties` with `sdk.dir`, or Android Studio).

```
./gradlew :shared:jvmTest            # shared logic tests, no SDK needed
./gradlew :androidApp:installDebug   # build + install on a connected device
```

## Release

The family installs a signed APK (no Play Store).

1. One-time: the release key is `~/.bucklog/bucklog-release.jks`. Its passwords are in `local.properties`
   (`bucklog.release.*`). **Back up both**: without them, installed apps can't be updated
   (only uninstalled and reinstalled). Its SHA-1 must be registered as an Android OAuth
   client (docs/google-cloud-setup.md §4).
2. Bump `versionCode`/`versionName` in `androidApp/build.gradle.kts`.
3. `./gradlew :androidApp:assembleRelease` → `androidApp/build/outputs/apk/release/androidApp-release.apk`
4. Send the APK to family members. They open it and allow installing from that source.
   Updates install over the old version (same key); local data is kept.

A debug build and a release build are signed with different keys, so switching between them
needs an uninstall first. Only the phone's local copy is lost; the sheet has everything that synced.
