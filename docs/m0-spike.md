# M0 spike checklist

Goal: prove the SPEC §4 access model before building on it.

Build and install: `./gradlew :androidApp:installDebug` (phone with USB debugging, or an emulator
**with Google Play**). Sign the device into your Google account.

| # | Step (buttons in the spike app) | Expected | Result |
|---|---------------------------------|----------|--------|
| 1 | **1. Authorize** | Consent sheet mentioning "specific Drive files", then a token | |
| 2 | **Create sheet** | New "Bucklog spike …" in Drive with tabs Categories/Settings/2026 | |
| 3 | **Append row**, then **Read** | Row visible in the sheet; Read shows it + Drive `version` | |
| 4 | Edit a cell by hand in Sheets, then **Read** | Drive `version` increased, new value read | |
| 5 | Paste a link to a sheet the app did *not* create → **Open link w/o picking** | Fails with 404/403 (proves drive.file is enforced) | |
| 6 | **Pick sheet** (same link in the field) | Picker opens in-app, the file preselected; pick it | |
| 7 | **Read** / **Append row** on the picked sheet | Works | |
| 8 | Share the sheet from step 2 with a second Google account; on a second device/account: **Authorize**, link → **Open link w/o picking** | Expected to fail (another user's app-created file) | |
| 9 | Same second account → **Pick sheet** → **Read**/**Append** | Works | |
| 10 | Kill app, reopen, **Authorize** | No consent shown again; token returned silently | |

The Picker runs on https://losipiuk.github.io/bucklog-picker/ in a Chrome Custom Tab and returns
the file ID through the `net.osipiuk.bucklog://picked` link. (A WebView-hosted Picker was tried first and failed: see Findings.)

## Findings
- **WebView-hosted Picker doesn't work.** The Picker needs a Google *web* session besides the
  OAuth token. The WebView had none and sent the user to accounts.google.com in Chrome, and the WebView
  picker then hung after selection. Google also blocks sign-in inside WebViews. → Moved to Custom Tab + hosted page.
- **Custom Tab + hosted Picker works.** No sign-in prompt (Chrome's session is used), and the result returns via
  `net.osipiuk.bucklog://picked`. The picked sheet is then readable and writable through the Sheets API.
- **drive.file is enforced**: opening an unpicked sheet by ID → `404 NOT_FOUND`.
- **The grant persists** server-side per (user, app, file). After uninstall/reinstall the same sheet opens by ID
  without picking again. So the app only needs the Picker once per user, and can store the ID.
- **Silent re-authorization**: no consent shown again after the first grant.
- **Change detection**: Drive `version` goes up on every write (6 → 8 after one append), so it's usable for §6.3.
- **USER_ENTERED parsing**: in an en/GMT sheet, `2026-09-28 21:52` became serial 46293.91 and `1.23` became a number.
  Not yet tested on a Polish-locale sheet. M2 should write raw numbers/serials (`RAW`) and not rely on parsing.
- **Open**: steps 8–9 (a second family account, on a sheet another user created).

