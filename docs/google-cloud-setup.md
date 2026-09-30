# Google Cloud setup (one-time)

Do all of this while signed in to **console.cloud.google.com** as `lukasz@osipiuk.net`.

## 1. Project
1. Top bar → project picker → **New project** → name `bucklog` → Create. Select it.
2. **IAM & Admin → Settings** (or the project dashboard): note the **Project number**
   (digits only, e.g. `123456789012`). This is the Picker "App ID".

## 2. APIs
**APIs & Services → Library**, enable:
- Google Sheets API
- Google Drive API
- Google Picker API

## 3. OAuth consent screen ("Google Auth Platform")
**APIs & Services → OAuth consent screen** (now called *Google Auth Platform*):
1. **Branding**: App name `Bucklog`, user support email `lukasz@osipiuk.net`,
   developer contact `lukasz@osipiuk.net`. No logo (a logo triggers a brand review). Publishing requires:
   - App home page: `https://losipiuk.github.io/bucklog-picker/about.html`
   - Privacy policy: `https://losipiuk.github.io/bucklog-picker/privacy.html`
   - Authorized domains: `losipiuk.github.io`

   (Both pages live in the public `losipiuk/bucklog-picker` repo.)
2. **Audience**: User type **External**.
3. **Data access** → Add scope → `.../auth/drive.file`
   ("See, edit, create, and delete only the specific Google Drive files you use with this app").
   Nothing else.
4. **Audience → Publishing status → Publish app** ("In production").
   With only `drive.file`, there's no verification review. Don't leave it in Testing,
   because Testing expires every login after 7 days.

## 4. Credentials
**APIs & Services → Credentials → Create credentials**:

1. **OAuth client ID → Android**
   - Package name: `net.osipiuk.bucklog`
   - SHA-1 (debug key on this Mac, `~/.android/debug.keystore`):
     `11:D4:A9:C1:68:97:48:F9:4D:50:A8:83:87:E7:03:8C:69:71:07:E1`
   - **Release APK**: create a second Android OAuth client, same package name, with the release
     key's SHA-1: `64:F9:21:49:0B:1E:96:AF:55:6D:C2:1C:0C:E0:27:12:53:D6:F9:36`
     (key `~/.bucklog/bucklog-release.jks`, see README → Release).
   - **Google Play**: Play re-signs the app with Google's own *app signing key* (ours is only the upload key),
     so each Play signing key needs its own Android client too. Find them in Play Console → App integrity →
     Play app signing. Keys registered so far:
     - `AA:A0:42:26:BD:50:29:92:80:20:5D:F0:DE:D0:AD:15:30:76:FC:B5` (`bucklog-play-previous`): the key that
       signed the first Play release (listed under "Previous app signing keys").
     - `3B:FA:BF:C7:9F:E4:52:A4:AB:22:B8:EC:06:68:83:25:85:BE:9D:EE` (`bucklog-play`): the key "In use"
       (classical) for later releases.

     If Google rotates the key again, register the new SHA-1 the same way. Symptom when one is missing: the Play
     version installs, but sign-in stays on the welcome screen. To check which key an installed app has:
     `adb shell pm path net.osipiuk.bucklog`, `adb pull` that base.apk, then
     `apksigner verify --print-certs base.apk`.
2. **OAuth client ID → Web application** (the Picker token must belong to this project;
   GIS on Android also expects a web client to exist). Name `bucklog-web`, no origins needed for now.
3. **API key** → then edit it:
   - API restrictions: **Restrict key → Google Picker API** only.
   - Application restrictions: *Websites* → `https://losipiuk.github.io/bucklog-picker/*`
     (the hosted Picker page, repo `losipiuk/bucklog-picker`, opened in a Custom Tab).

## 5. Wire into the build
Add to `local.properties` in the repo root (not committed):

```
bucklog.pickerApiKey=AIza...
bucklog.cloudProjectNumber=123456789012
```

Neither value is secret in the cryptographic sense (the Picker key ships inside any
client), but they stay out of git anyway.
