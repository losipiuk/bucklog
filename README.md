# Bucklog

Shared spend tracker (Android first, iOS later) backed by a Google Sheet that everyone
using it can open and edit.

- [SPEC.md](SPEC.md): product and technical specification
- [docs/google-cloud-setup.md](docs/google-cloud-setup.md): one-time Google Cloud setup (already done for this project)
- [docs/m0-spike.md](docs/m0-spike.md): results of the M0 access-model spike
- Companion repo [`losipiuk/bucklog-picker`](https://github.com/losipiuk/bucklog-picker) (public, GitHub Pages):
  the Google Picker page, the invite landing page and the privacy policy

## Repository layout

| Module | What |
|---|---|
| `shared/` | Kotlin Multiplatform logic: domain, local database (SQLDelight), Sheets/Drive client, sync, backups. Tests run on the JVM. |
| `composeApp/` | All screens (Compose Multiplatform), shared with the future iOS app. |
| `androidApp/` | Android entry point: Google sign-in, Picker, background sync, icon, signing. |

## Set up a development machine

Do these once per machine, in order.

### 1. Install the tools

- **JDK 21** or newer, e.g. `brew install --cask temurin@21`
- **Android Studio** (`brew install --cask android-studio`). Run its setup wizard once to install the Android SDK
  (default location: `~/Library/Android/sdk`).

### 2. Restore the signing keys

Google only accepts sign-ins from APKs signed with keys whose SHA-1 is registered in the Cloud project
(see [docs/google-cloud-setup.md](docs/google-cloud-setup.md) §4). Two keys are registered; take both from
the secrets backup (the `secrets-backup/` folder, kept outside git and backed up separately):

```sh
cp secrets-backup/debug.keystore ~/.android/debug.keystore          # debug builds
mkdir -p ~/.bucklog && chmod 700 ~/.bucklog
cp secrets-backup/bucklog-release.jks ~/.bucklog/bucklog-release.jks  # release builds
```

On a machine with its own `~/.android/debug.keystore`, either replace it as above or register that
key's SHA-1 as another Android OAuth client. Otherwise sign-in fails in debug builds.

### 3. Create `local.properties`

Create `local.properties` in the repo root (git ignores it). Start from this template, or copy
`secrets-backup/local.properties` and fix the paths:

```properties
# Android SDK (Android Studio writes this for you when you open the project)
sdk.dir=/Users/<you>/Library/Android/sdk

# Google Picker: needed by every build (debug and release) to choose a sheet
bucklog.pickerApiKey=AIza…
bucklog.cloudProjectNumber=651091914436

# Release signing: needed only for assembleRelease
bucklog.release.storeFile=/Users/<you>/.bucklog/bucklog-release.jks
bucklog.release.storePassword=…
bucklog.release.keyAlias=bucklog
bucklog.release.keyPassword=…
```

Every setting, where its value comes from, and its equivalents for environment variables and GitHub Actions:

| `local.properties` | Needed for | Where the value comes from | Env variable | GitHub secret |
|---|---|---|---|---|
| `sdk.dir` | everything | Android SDK path | `ANDROID_HOME` | none (runners have an SDK) |
| `bucklog.pickerApiKey` | choosing a sheet (all builds) | Cloud Console → Credentials → API key, or `secrets-backup/local.properties` | `BUCKLOG_PICKER_API_KEY` | `BUCKLOG_PICKER_API_KEY` |
| `bucklog.cloudProjectNumber` | choosing a sheet (all builds) | Cloud Console → project settings; `651091914436` | `BUCKLOG_CLOUD_PROJECT_NUMBER` | `BUCKLOG_CLOUD_PROJECT_NUMBER` |
| `bucklog.release.storeFile` | release signing | path to `bucklog-release.jks` | `BUCKLOG_RELEASE_STORE_FILE` | `BUCKLOG_RELEASE_KEYSTORE_BASE64` (the file itself, base64; the workflow writes it to disk) |
| `bucklog.release.storePassword` | release signing | `secrets-backup/local.properties` | `BUCKLOG_RELEASE_STORE_PASSWORD` | `BUCKLOG_RELEASE_STORE_PASSWORD` |
| `bucklog.release.keyAlias` | release signing | always `bucklog` | `BUCKLOG_RELEASE_KEY_ALIAS` | none (set in the workflow) |
| `bucklog.release.keyPassword` | release signing | `secrets-backup/local.properties` (same as the store password) | `BUCKLOG_RELEASE_KEY_PASSWORD` | `BUCKLOG_RELEASE_KEY_PASSWORD` |

A value in `local.properties` wins over the environment variable. Env var names are the property names in
upper snake case (`bucklog.release.storeFile` → `BUCKLOG_RELEASE_STORE_FILE`).

What happens when something is missing:
- **Picker settings missing:** the app builds and signs in, but **Choose a shared sheet** fails.
- **Release settings missing:** `assembleRelease` still succeeds, but produces an **unsigned**
  `androidApp-release-unsigned.apk` that phones refuse to install. The GitHub release workflow checks for this and fails instead.

### 4. Check it works

```sh
./gradlew :shared:jvmTest :androidApp:assembleDebug
```

## Everyday commands

```sh
./gradlew :shared:jvmTest            # shared logic tests (sync, suggestions, … against a fake Google API)
./gradlew :androidApp:installDebug   # build and install the debug app on a USB-connected phone
./gradlew :androidApp:assembleRelease  # signed release APK → androidApp/build/outputs/apk/release/androidApp-release.apk
```

Debug and release builds are signed with different keys, so a phone can't switch between them without
uninstalling first. That only clears the phone's local copy; the sheet has everything that synced.

## Releasing

People install a signed APK (there's no Play Store listing). Updates install over the previous version
as long as they're signed with the same release key.

### Publish a new release

Run these three commands, replacing `0.5.0` with the new version number:

```sh
git checkout main
git pull
scripts/release.sh 0.5.0
```

The release script stops without changing anything if you're not on `main`, have uncommitted changes, aren't in
sync with GitHub, or the version was already released. Otherwise it:

1. increases `versionCode` by one and sets `versionName` to the new version in `androidApp/build.gradle.kts`;
2. commits this as "Release v0.5.0" and tags it `v0.5.0`;
3. pushes the commit and the tag to GitHub.

The tag starts the **Release** workflow on GitHub, which runs the tests and builds two signed files:
`bucklog-v0.5.0.apk` for installing directly on phones, and `bucklog-v0.5.0.aab` (an app bundle) for uploading to
Google Play. It takes about 7 minutes. Then:

1. **Wait for the build.** Follow it with the command below, or on the repo's **Actions** tab:

   ```sh
   gh run watch $(gh run list --workflow release.yml --limit 1 --json databaseId -q '.[0].databaseId')
   ```

2. **Download the APK.** It's attached to the release at `https://github.com/losipiuk/bucklog/releases/tag/v0.5.0`,
   or you can fetch it with:

   ```sh
   gh release download v0.5.0 --pattern '*.apk'
   ```

   For Google Play, download the bundle instead: `gh release download v0.5.0 --pattern '*.aab'`.

3. **Tell the people using the app.** The repo is public, so they can download the APK on their phone from
   https://github.com/losipiuk/bucklog/releases/latest (or you send them the file). They open the APK and allow
   installing from that source. It installs over the previous version and their data stays.

Which number to bump: the **patch** for fixes (0.5.0 → 0.5.1), the **minor** for new features (0.5.x → 0.6.0).

### Other ways to build a release APK

- **GitHub Actions without a release:** Actions tab → **Release** → **Run workflow**. The signed APK is only kept as
  the run's artifact (`bucklog-apk`, which holds both the APK and the .aab); nothing is tagged or published.
- **Locally:** `./gradlew :androidApp:assembleRelease` →
  `androidApp/build/outputs/apk/release/androidApp-release.apk`, and `./gradlew :androidApp:bundleRelease` →
  `androidApp/build/outputs/bundle/release/androidApp-release.aab` (both need the release settings from step 3).
  Bump the version by hand first if it's going to people.

## GitHub Actions

| Workflow | Runs on | Does | Secrets |
|---|---|---|---|
| `ci.yml` | every push to `main`, pull requests | shared tests, debug build | none |
| `release.yml` | `v*` tags, manual runs | tests, signed APK and app bundle (.aab), GitHub Release | the five below |

Repository secrets (Settings → Secrets and variables → Actions), and how to set them from a configured machine:

```sh
base64 -i ~/.bucklog/bucklog-release.jks | gh secret set BUCKLOG_RELEASE_KEYSTORE_BASE64
grep '^bucklog.release.storePassword=' local.properties | cut -d= -f2- | gh secret set BUCKLOG_RELEASE_STORE_PASSWORD
grep '^bucklog.release.keyPassword=' local.properties | cut -d= -f2- | gh secret set BUCKLOG_RELEASE_KEY_PASSWORD
grep '^bucklog.pickerApiKey=' local.properties | cut -d= -f2- | gh secret set BUCKLOG_PICKER_API_KEY
grep '^bucklog.cloudProjectNumber=' local.properties | cut -d= -f2- | gh secret set BUCKLOG_CLOUD_PROJECT_NUMBER
```

## Keeping the secrets safe

The release key and its passwords can't be recovered. Losing them means nobody can install an update
without uninstalling first. Keep a copy of `secrets-backup/` (keys, `local.properties`,
and a README with the SHA-1s) somewhere other than this computer, such as a password manager or encrypted cloud storage.
The GitHub secrets can't be read back, so they don't count as a backup.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Sign-in fails in a debug build on a new machine | Restore `~/.android/debug.keystore` from the backup (step 2) or register its SHA-1. |
| Release APK can't sign in | The release key's SHA-1 must be registered as an Android OAuth client (docs/google-cloud-setup.md §4). |
| "Access blocked: app not verified" for other accounts | The OAuth consent screen must be **In production** (docs/google-cloud-setup.md §3). |
| Only `androidApp-release-unsigned.apk` appears | Release signing settings are missing (step 3). |
| `Incremental compilation failed …` | `./gradlew clean`, then build again. |
