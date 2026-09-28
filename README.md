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
