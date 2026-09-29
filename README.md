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

### On GitHub Actions

`.github/workflows/release.yml` builds the same signed APK when you push a tag (`git tag v0.5.0 && git push --tags`)
or run it from the Actions tab. The APK is attached to the GitHub Release and kept as a workflow artifact.
It reads these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `BUCKLOG_RELEASE_KEYSTORE_BASE64` | `base64 -i ~/.bucklog/bucklog-release.jks` |
| `BUCKLOG_RELEASE_STORE_PASSWORD` / `BUCKLOG_RELEASE_KEY_PASSWORD` | from `local.properties` |
| `BUCKLOG_PICKER_API_KEY` / `BUCKLOG_CLOUD_PROJECT_NUMBER` | from `local.properties` |

Locally, any `local.properties` setting can also come from an environment variable
(`bucklog.release.storeFile` → `BUCKLOG_RELEASE_STORE_FILE`). `ci.yml` runs the tests and a debug build on every push.

A debug build and a release build are signed with different keys, so switching between them
needs an uninstall first. Only the phone's local copy is lost; the sheet has everything that synced.
