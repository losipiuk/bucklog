# Bucklog — Specification

A family spend tracker. Android first, iOS later. Several family members share one
Google Sheet that acts as the database. The app is optimized for one thing above
all: **logging an expense in as few taps as possible**.

Status: draft v0.2 (2026-09-28)

---

## 1. Goals and non-goals

### Goals
- Adding an expense takes a few seconds and the fewest possible key presses.
- Several family members share one data set. Access is controlled only by the
  Google Sheet's sharing settings. The app has no accounts, server or backend of its own.
- The Google Sheet stays a first-class, human-friendly artifact: readable,
  sortable, and editable by hand. The app detects manual edits and adopts them.
- Works fully offline. Sync to the sheet happens in the background.
- Simple setup: **your name** + **the sheet**.
- Porting to iOS is cheap: shared business logic and UI.
- Looks and feels good: modern, fast, satisfying to use.
- Scale: up to ~5,000 entries per year, several years of history.

### Non-goals (v1)
- Budgets, reports and charts (see §12 Later).
- Receipt photos and attachments.
- Bank import.
- Any backend server.
- Rewriting amounts into the main currency. Entries keep their original currency; conversion is only used for totals (§6.7).

---

## 2. Terminology

| Term       | Meaning |
|------------|---------|
| Entry      | One expense: when, who, what, category, amount, currency. |
| Family     | Everyone the sheet is shared with (editor access). |
| Year tab   | A sheet tab named after a calendar year, e.g. `2026`, holding that year's entries. |
| Local DB   | On-device SQLite database, the app's working copy. |
| Outbox     | Queue of local changes not yet written to the sheet. |

---

## 3. Google Sheet layout (the database)

One spreadsheet with these tabs:

### 3.1 `Categories`

| A: Name   | B: Emoji | C: Archived |
|-----------|----------|-------------|
| Groceries | 🛒       |             |
| Fuel      | ⛽       |             |
| Kids      | 🧸       | TRUE        |

- **Name** is the key. It must be unique (case-insensitive).
- **Emoji** is optional. It exists only to make category chips and History rows
  recognizable at a glance. Without it the app shows the first letter of the name.
  There is no color column: chip colors come from the theme.
- Archived categories are hidden from the picker but kept for old entries.
- Row order = display order in the picker when there is no better guess.

### 3.2 `Settings`

Key/value pairs:

| Key              | Value |
|------------------|-------|
| schema_version   | 1     |
| main_currency    | PLN   |

- `main_currency` is the currency all totals are shown in (§6.7). It is also assumed
  for rows typed by hand with an empty Currency cell.

### 3.3 Year tabs: `2025`, `2026`, …

| A: Date           | B: Who  | C: What       | D: Category | E: Amount | F: Currency | G: Rate | H: ID      |
|-------------------|---------|---------------|-------------|-----------|-------------|---------|------------|
| 2026-09-28 18:42  | Łukasz  | Milk, bread   | Groceries   | 35.30     | PLN         |         | k3f9x2ab   |
| 2026-09-29 12:10  | Łukasz  | Museum        | Fun         | 24.00     | EUR         | 4.2715  | p8d2m4qa   |
| 2026-09-30 09:05  | Anna    | Shoes         | Clothes     | -199.00   | PLN         |         | z1c7v0tr   |

- **Date**: a real Sheets date-time value, formatted `yyyy-mm-dd hh:mm`, in the
  spreadsheet's time zone. Typing `2026-09-28` by hand works.
- **Who**: the name the family member set in the app.
- **What**: free text.
- **Category**: the category *name*, with a data-validation dropdown sourced from
  `Categories!A2:A`. The dropdown is "show warning", not "reject", so hand edits never get blocked.
- **Amount**: a number, formatted with 2 decimals (or the currency's minor
  units). Positive = expense. **Negative = refund** (§5.2).
- **Currency**: ISO 4217 code. Empty = `main_currency`.
- **Rate**: the exchange rate to `main_currency` on the expense date
  (1 unit of Currency = Rate × main currency). It is empty for main-currency rows.
  The app fills it in (§6.7). A rate typed by hand is respected.
- **ID** (column H): stable row identity used by sync. Short random string (8 chars,
  base32). It is the last column, greyed out, and can be left empty when adding rows by hand;
  the app fills it in (§6.4).
- Row 1 is a frozen header. Row order is irrelevant. Users may sort/filter freely.
- An entry lives in the tab matching the year of its Date.

### 3.4 Tab/sheet creation
- The app can **create a new spreadsheet** with the structure above and a default
  set of categories, or **attach to an existing one**.
- Year tabs are created by the app on demand (the first entry of a new year):
  header, formats, validation, frozen row.
- Other tabs in the spreadsheet are ignored, so users can add their own
  pivot tables and charts.

---

## 4. Authentication and access

- No app-level accounts. Each family member signs in with **their own Google account**.
- Access control is the sheet's sharing settings: whoever has editor access to
  the sheet can use it.
- OAuth scope: **`drive.file`** (non-sensitive). This lets the OAuth app be published
  "In production" without Google verification, so logins don't expire (see §4.1).
  - With `drive.file`, the app can only access files that the app created or that the user
    explicitly picked. The user therefore **selects the sheet through Google Picker**
    instead of pasting a raw link.
  - A pasted link (or a share link opened on the phone) can still be used to *pre-select*
    the file in the Picker, which only needs a confirm tap.
- Setup flow for the owner: Sign in → "Create new sheet" (or pick an existing one) →
  enter name → done. Then share the sheet with the family in Google Sheets as usual.
- Setup flow for other members: Sign in → pick the shared sheet → enter name → done.
- **Picker hosting (validated in M0)**: Google Picker is a web component and needs a
  Google *web* session, so it can't run in a WebView (Google also blocks sign-in there).
  It runs on a static page, https://losipiuk.github.io/bucklog-picker/ (public repo
  `losipiuk/bucklog-picker`), opened in a Chrome Custom Tab. The app passes its access
  token in the URL fragment, and the page returns the file ID via `net.osipiuk.bucklog://picked`.
- The `drive.file` grant from picking persists per (user, app, file), including across
  reinstalls. The app stores the spreadsheet ID and never needs the Picker again for that user.
  Unpicked files return 404. See `docs/m0-spike.md`.


### 4.1 Google Cloud project (one-time developer setup)
Any app that calls Google APIs needs an **OAuth client registered in a Google Cloud
project**. This is a one-time setup done by the developer, not by family members.
- **Owner**: the Google account for `lukasz@osipiuk.net`. It is also the support contact shown on the consent screen. It's free; the Sheets and Drive APIs have no cost
  at this volume.
- **What gets created**:
  - Enabled APIs: Google Sheets API, Google Drive API, Google Picker API.
  - OAuth consent screen: user type *External* (personal Gmail accounts can't use
    *Internal*, which is Workspace-only). App name, support email, scope `drive.file`.
  - OAuth clients: *Android* (package name + SHA-1 of each signing key: debug, release,
    and Play App Signing if used); *iOS* (bundle ID) later; *Web* (for the hosted Picker page).
  - An API key restricted to the Picker API and the Picker page's origin.
- **Publishing status must be "In production"**. In *Testing* mode, Google expires
  every user's authorization after **7 days** for any scope other than basic profile
  (this includes `drive.file`), so everyone would have to sign in again weekly. With only
  non-sensitive scopes, switching to production needs no verification review.
  The consent screen may show the app as unverified-brand (no logo), which is fine.
  Publishing requires a home page and privacy policy on an authorized domain; both are
  hosted in `losipiuk/bucklog-picker` (`about.html`, `privacy.html`).
- **Who can sign in**: anyone with a Google account can complete the sign-in, but
  they only ever see sheets that they created with the app or picked themselves and that are shared with them.
  The sheet's sharing is the real access control.
- **Secrets**: the Android OAuth client has no secret. The Picker API key is public by
  design and is locked down by API and origin restrictions. Nothing secret lives in the repo.
- **Distribution**: a sideloaded APK signed with one fixed release keystore. Its SHA-1
  is registered in the Android OAuth client. The keystore must be backed up: losing it
  means family members can't update without uninstalling. Updates are installed by hand
  from a new APK. An in-app "new version available" check is an idea for later.

---

## 5. User flows

### 5.1 Configuration (first run only)
1. Welcome → "Sign in with Google".
2. "Create a new family sheet" or "Use an existing sheet" (Picker).
3. "Your name" (prefilled from the Google account's first name).
4. Done → straight to Add Expense.

Settings later: change name, switch sheet, sign out, manual "Sync now",
sync status/log.

### 5.2 Add expense (the main flow — optimize relentlessly)

The app **launches directly into Add Expense**, with no home screen in between.

**Step 1 — Amount**
- Large amount display and a large custom numeric keypad (`1-9`, `0`, `00`, `⌫`).
- The decimal point is fixed: digits shift in from the right, like on an ATM.
  Typing `3530` shows `0.00 → 0.03 → 0.35 → 3.53 → 35.30`.
- Currency chip next to the amount shows the **last used currency** (per device).
  Tapping it opens a picker with recently used currencies first and search below.
  The happy path costs zero extra taps.
- **Refund toggle**: a small, low-emphasis `↩ Refund` pill on the opposite side of the
  amount from the currency chip, away from the keypad so it's never hit by accident.
  It is off by default and **resets to off after every Add**, so it never costs a tap
  in the normal flow. When on, the amount shows as `+35.30` in a green/"income"
  color with a "Refund" label, and the Add button reads "Add refund".
  The entry is stored as a negative Amount.
- `→` (Next) button, disabled while the amount is 0.
- Long-press `⌫` clears the amount.
- Haptic feedback on each key.

**Step 2 — What + Category (one screen)**
- The text field has focus and the system keyboard is open.
- Autocomplete suggestions from history show as a one-line strip under the text field
  as you type (and before typing: the most frequent/recent items).
- **Tapping a suggestion** fills What (cursor at the end) *and* preselects its usual category.
- **All categories are visible at once** as wrapping chips (emoji + name), in the
  `Categories` tab order so their positions stay stable. The **guessed category is
  preselected** (highlighted, not moved).
- A **"Filter or add category"** field above the chips narrows them as you type. When no
  category has exactly that name, a **＋ name** chip creates it. Enter picks the only match,
  or creates the category.
- Big **Add** button that names the selected category ("Add · 🛒 Groceries"); also the keyboard's IME action.
- For a refund, suggestions and the category guess work the same way, so a refund
  normally lands in the category of the original purchase.
- Back returns to Step 1 without losing the input.

**Add**
- Writes the entry to the local DB and the Outbox, schedules an immediate background sync,
  shows a brief confirmation toast ("35.30 PLN · Groceries"), and **closes the app**.

**Typical key-press count** for `35.30 Groceries "milk"` with history:
`3 5 3 0` `→` `m` `i` *tap suggestion* `Add` = **9 taps**.

### 5.3 Autocomplete and category guessing
- Source: all family entries in the local DB (all years synced).
- Matching: case- and diacritic-insensitive; prefix on the whole string or on any word.
- Ranking score for a distinct What text:
  `Σ over occurrences: w_user × decay(age)`, where `w_user = 2` for my own entries and 1 otherwise,
  and `decay` has a ~90-day half-life. Top 5–8 are shown.
- Category guess for a What text: the category with the highest score among past entries with
  the same normalized What text. If there is none, a token-based vote (entries sharing words).
  If there is still none, a built-in PL/EN product dictionary (e.g. *mleko, Biedronka → Groceries*;
  *Orlen, parking → Transport*; typing a category's own name picks it), applied only when the
  sheet still has the matching default category. Last fallback: the category I used most recently.
- Everything is computed locally. It must be instant (<16 ms per keystroke at 25k entries).

### 5.4 History and editing
- Reachable from the history icon on the Add screen.
- Entries from all family members, newest first, grouped by **Day or Month** (a toggle,
  remembered). Group headers show the total in the main currency (refunds subtract, ≈ while
  a rate is pending). Month headers also show a per-category breakdown (largest first). Tapping one
  filters History to that category.
- Search box (What, category, who; case- and diacritic-insensitive). Pull to refresh = sync now.
- Each row shows: category emoji, What, category · who · time (date and time in Month mode),
  amount + currency, and `≈ 102.52 PLN` under foreign amounts. ⏳ marks changes waiting to sync, and
  ⚠ marks sheet rows that can't be read (not editable in the app).
- Tap → Edit screen: all fields editable (Amount, Currency, Refund, What, Category, Who with
  chips of family names, Date and time pickers). Save, or Delete with an Undo snackbar.
  Changing the currency or the day drops the stored rate, so it's fetched again.
- Changing the Date to another year moves the row to the other year tab.
- Entries changed on the sheet show the updated values after the next sync.

### 5.5 Categories
- Managed in the `Categories` tab or in the app.
- The category filter field on the Add screen creates a category by name.
- Settings → Categories: add, rename, set an emoji (picker grid of relevant emojis, or type any),
  archive. A rename rewrites the Category cell of every affected row. Changes to the
  Categories tab are *targeted*: the app finds the row by the category's old name and changes
  only that row, so categories others added by hand are never overwritten.
- Rows whose category name isn't in `Categories` (e.g. after a manual rename)
  are shown with the category's first letter and counted under that name. Nothing is lost.

### 5.7 Inviting others
- ☰ → **Invite** (family, flatmates, anyone sharing costs): enter their Google email. Bucklog shares the sheet with them as an
  Editor (Drive API, allowed under `drive.file`; Google emails them too), then opens the system share sheet
  with a short message and a join link. **Just send the link** skips the sharing step.
- The join link is `https://losipiuk.github.io/bucklog-picker/join.html#sheet=…&name=…&from=…` (plain https,
  so every messenger makes it clickable; the fragment never reaches a server). The page explains the invite
  in PL/EN, and its button opens `net.osipiuk.bucklog://join?…` in the app.
- The app keeps the invite until it's used. In setup, after sign-in, **Join "…"** opens the Picker with just
  that sheet (the one tap that grants access), then the name step. On a phone already connected to another
  sheet, the app asks before switching. On the same sheet, nothing happens.
- The invite message also links to the latest APK on the public GitHub releases page
  (`https://github.com/losipiuk/bucklog/releases/latest`), so an invite is all a new person needs.

### 5.6 Menu and Settings
- A ☰ button at the top left of the main screen opens a drawer: History, Settings, Invite,
  Open in Google Sheets, and **Sign out** at the bottom. Less frequent and future flows go here, keeping
  the keypad screen clean (History also stays one tap away on the right). Only the button opens the
  drawer, not an edge swipe.
- Settings: your name (used for new entries; when it changes, the app offers to rename your past
  expenses too, including their rows in the sheet), Google account, language, the sheet with **Open in Google
  Sheets**, sync status and **Sync now**, categories.
- **Use another sheet** (Settings) and **Sign out** (menu) clear the local copy (warning if changes
  haven't synced yet), then return to setup.
- Debug builds only: **Add demo expenses** (~6 months of realistic fake data), for trying out History.

---

## 6. Sync

### 6.1 Principles
- The **local DB is the source of truth for the UI**. The app never blocks on the network.
- The **sheet is the shared source of truth**. After a sync, local = sheet + pending Outbox.
- Rows are matched **by ID only**. Row position is never trusted.

### 6.2 Triggers
- Immediately after Add/Edit/Delete (expedited one-shot job).
- On app start (in the background, without blocking the UI).
- Periodically: every ~30 min with the network constraint (WorkManager / BGTaskScheduler).
- Manual "Sync now" and pull-to-refresh in History.

### 6.3 Change detection (cheap polling)
1. `Drive files.get(fields=modifiedTime,version)`: one tiny call.
2. If the version is unchanged since the last successful pull and the Outbox is empty, stop.
3. Otherwise do a full pull: `spreadsheets.values.batchGet` for `Categories`, `Settings` and
   all year tabs, with `UNFORMATTED_VALUE` + `SERIAL_NUMBER` dates.
   At 5k rows × 7 columns per year that is a few hundred KB, which is fine to read whole.

### 6.4 Pull / merge
For every remote row:
- **No ID** (typed by hand): generate one and queue a write of the ID cell.
- **Duplicate ID** (a copy-pasted row): keep the first, assign a new ID to the others.
- **Unparseable** (e.g. text in Amount): keep it in the local DB flagged `invalid`. It is shown in
  History with a warning, is excluded from totals, and is never overwritten by the app.
- Otherwise upsert into the local DB by ID. If the local copy has a pending Outbox
  change, **the local change wins** (it is pushed next). Otherwise **the remote wins**.

For every local synced row whose ID no longer exists remotely: **delete locally**
(it was deleted in the sheet). A pending local edit to a row deleted remotely is dropped,
because the delete wins.

### 6.5 Push (drain the Outbox)
Runs right after the pull, using its row numbers (the pull is the "re-read" before writing).
Queued ops are collapsed per entry (the last one wins) and written in this order:
1. **Updates** in place: one `values.batchUpdate` (RAW) covering rows whose entry stays in the
   same tab, plus ID cells for hand-typed rows and duplicates.
2. **Appends**: new entries, and entries whose date moved them to another year's tab, go into
   `values.append` with the range starting right below the tab's last row. An append
   never lands between existing rows, even if there are blank rows. Missing year tabs are created first.
   Appended rows are *inserted* rows and don't inherit column formats, so the push then
   reapplies the year-tab formats (date, amount, rate, grey ID) to the tabs it appended to
   (and once to every year tab of a spreadsheet).
3. **Deletes**: one `deleteDimension` batch, bottom-up per tab (so lower row numbers stay valid).
   A move to another year's tab is therefore copy-then-delete: if interrupted, it's
   duplicated, never lost.
- Every step is keyed by row ID, so re-running after a partial push converges: an insert
  whose ID is already in the sheet becomes an update.
- On success: the Outbox items read at the start are cleared (newer ones stay queued). The Drive
  `version` stored is the one read *before* the pull, so a change made during the sync
  (including our own write) causes one more pull next time rather than being missed.
- A new category that fails to upload fails the whole sync (retried later), so the pull
  can't drop it locally.
- Errors: WorkManager retries with exponential backoff (up to 5 attempts). Auth and other 4xx
  errors aren't retried. The last error is classified (sign-in needed / sheet not accessible /
  offline / other). The first two show a banner with a one-tap fix (**Sign in** re-authorizes the
  account; **Choose sheet** reopens the Picker, where picking the same sheet restores access).
  Offline shows only a calm status line.

### 6.6 Quotas
The Sheets API allows 60 read requests and 60 write requests per minute per user. One sync cycle uses ≤ 5 calls
plus 2 per pending update/delete. Outbox items are batched where possible.


### 6.7 Exchange rates and totals
- All totals (day totals, and later reports) are in `main_currency` (PLN), computed as
  `Amount × Rate` using the rate **on the expense date**.
- **Rate source**: when `main_currency` is PLN, the NBP API table A mid rates (`api.nbp.pl`),
  which is free, needs no key, and is the official PLN reference. If a currency is missing
  from table A, table B is used. For any other main currency, ECB rates via
  `api.frankfurter.app`. Both sit behind one `ExchangeRateProvider` interface.
- **Which day**: the latest published rate on or before the expense date. NBP doesn't
  publish on weekends or holidays, so the previous business day's rate is used.
  A same-day expense made before that day's rate is published uses the previous table's rate.
- **When it is filled in**: on Add, if the currency ≠ main and the device is online, the rate is fetched and
  stored with the entry. If offline, the entry is saved without a rate and marked
  `rate pending`. The sync job fills in the rate, then pushes. Totals show `≈` with the most recent
  known rate until then.
- **On Date or Currency change** (in the app): the rate is re-fetched.
- **Rows typed by hand** with a foreign currency and an empty Rate: the app fills the Rate cell
  on sync, just like a missing ID. A non-empty Rate is never overwritten.
- Rates are cached locally per (currency, date), so there is at most one request per currency per day.

### 6.8 Backups
- Google Sheets' own version history covers bulk-edit mistakes; backups cover the sheet being deleted
  or unshared, and mistakes found much later.
- A backup is a Drive copy of the whole spreadsheet (all tabs and formatting), named
  "<sheet> backup YYYY-MM-DD", in a "Bucklog backups" folder in the phone user's Drive. Both the folder and
  the copies are app-created, so `drive.file` covers them.
- Weekly, run by the sync worker when the Drive version changed since the last backup. The last 8 are
  kept; older copies in that folder are deleted (never any other file).
- Per phone (Settings → Backups → Weekly backup to my Drive), so family members don't all pile up
  copies. It's on by default only for whoever created the sheet. **Back up now** and **Open backups folder** are
  there too. Restoring = open a copy, or point the app at it (Use another sheet).

---

## 7. Local data model

```
entry(
  id TEXT PK,              -- same as sheet ID column
  ts INTEGER,              -- epoch millis (local time zone as of the sheet)
  who TEXT,
  what TEXT,
  what_norm TEXT,          -- lowercased, diacritics stripped (for search/autocomplete)
  category TEXT,
  amount_minor INTEGER,    -- 3530 = 35.30; negative = refund
  currency TEXT,
  rate TEXT NULL,          -- decimal string to main currency; NULL = main currency or pending
  status TEXT,             -- synced | pending | invalid
  raw_json TEXT NULL       -- original cells for invalid rows
)
outbox(seq PK, op, entry_id, payload_json, attempts, last_error)
category(name PK, emoji, archived, position)
fx_rate(currency, date, rate, PK(currency, date))
sync_state(key PK, value) -- drive_version, last_pull_at, spreadsheet_id, tz, main_currency
prefs: my_name, last_currency, spreadsheet_id  (platform key-value store)
```

Amounts are always integer minor units internally, and rates are exact decimals. There are no floats in the domain.

---

## 8. Architecture

**Kotlin Multiplatform + Compose Multiplatform.**

```
bucklog/
  shared/             # KMP: domain, SQLDelight DB, sync engine, Sheets/Drive REST (Ktor),
                      #      suggestion engine, view models
  composeApp/         # Compose Multiplatform UI (all screens)
  androidApp/         # Android entry, Google auth, WorkManager, widget (Glance)
  iosApp/             # iOS entry (later), Google Sign-In, BGTaskScheduler, WidgetKit
  picker-web/         # static Google Picker page (see §4), hosted on GitHub Pages
```

- **DB**: SQLDelight.
- **HTTP**: Ktor client + kotlinx.serialization, calling the Sheets v4 and Drive v3 REST APIs directly
  (no Google Java client, so the code stays multiplatform).
- **Auth**: `expect interface AccessTokenProvider`, implemented with Google Identity Services
  (`AuthorizationClient`) on Android and the GoogleSignIn SDK on iOS.
- **Background**: `expect` scheduler, implemented with WorkManager on Android and BGTaskScheduler on iOS.
- **DI**: Koin. **Navigation**: Compose Navigation (multiplatform).
- Min Android SDK 26. Targets the latest SDK.
- Sync engine and suggestion engine are pure Kotlin with unit tests (fake Sheets backend).

---

## 9. Look and feel
- Material 3, with dynamic color on Android 12+ and full dark mode.
- The keypad is large and thumb-reachable, filling the bottom ~55% of the screen, with ripple and haptics.
- The amount uses a big display font (tabular numerals) and animates digit shifts.
- Category chips use the emoji, or the first letter of the name when there is none.
- Subtle transitions between steps. Add → a short success animation, then the app closes.
- Accessibility: TalkBack labels, dynamic type, and a minimum 48 dp touch target.
- UI languages: English and Polish (typed Kotlin string tables, shared with iOS), chosen by the
  system or in Settings. Dates are shown as DD.MM.YYYY.
- A short ✓ animation confirms Add before the app closes.
- Adaptive launcher icon (a cream wallet with a gold coin slipping in, on rust) with a monochrome layer for themed icons.

---

## 10. Quick add without opening the app
- **Quick Settings tile** "Bucklog / Add expense" (wallet icon matching the app icon). It opens the
  **quick-add panel**: the normal add flow in a bottom sheet over whatever app is open (a translucent
  activity in its own task, not in Recents). Tapping outside cancels; after Add it shows the ✓ and returns to
  that app. It opens the full app instead while Bucklog isn't set up yet.
- From the lock screen the tile asks to unlock first (standard `unlockAndRun`). The panel is never shown
  over the lock screen, so a locked phone doesn't reveal history-based suggestions.
- Settings → Quick add offers the system's one-tap "Add tile" prompt (Android 13+), or explains how to
  add it by hand.
- Tried and dropped: a home-screen widget, app-icon shortcuts and favorites. The app icon already
  opens straight into the add flow, so they added little.

---

## 11. Milestones
- **M0 — Spike**: Google Cloud project, OAuth client, Picker page, `drive.file` access
  to a picked sheet from Android, read/append via the REST API.
- **M1 — Core add flow (offline)**: KMP skeleton, local DB, Amount and What/Category screens,
  suggestions, settings.
- **M2 — Sync**: sheet creation, year tabs, pull/merge/push, background jobs,
  manual-edit handling.
- **M3 — History and editing**: list, search, edit/delete, categories management.
- **M4 — Polish**: animations, dark mode, i18n, error UX, tests.
- **M5 — Quick Settings tile with the quick-add panel.**
- **M6 — iOS app.**

---

## 12. Later / ideas
- Monthly totals per category, per person, and per currency; simple charts.
- Budgets and alerts.
- Recurring expenses.
- Split/shared expenses between members.
- Export/backup (the sheet already is one).

---

## 13. Decisions log
| # | Decision | Choice |
|---|----------|--------|
| 1 | Stack | Kotlin Multiplatform + Compose Multiplatform |
| 2 | Sheet access | Google Sign-In, `drive.file` scope + Google Picker |
| 3 | Category column | Category name + dropdown validation (not numeric ID) |
| 4 | Currency | Multi-currency per entry; last used is the default, so no extra tap |
| 4a | Totals | In main currency (PLN), using the NBP rate on the expense date, stored in a Rate column |
| 5 | Editing | Any entry (all members), recent first |
| 6 | Date | "Now" on add; changeable in edit |
| 7 | Suggestions | Whole family's history, my entries weighted ×2 |
| 8 | Repo | `losipiuk/bucklog`, private |
| 9 | Refunds | Negative Amount; off-by-default Refund pill on the amount screen that resets after each Add |
| 10 | Category metadata | Name + optional emoji + archived; no color |
| 11 | "Who" | Editable in Edit only (e.g. logging a spouse's cash expense) |
| 12 | Note field | Not in the Add flow |
| 13 | Extra sheet columns | Columns after H are ignored and never cleared |
| 14 | OAuth app | Google Cloud project owned by lukasz@osipiuk.net, consent screen "In production" (§4.1) |
| 15 | Distribution | Sideloaded APK, one release keystore |
| 16 | FX rate day | Latest NBP rate published on or before the expense date |

## 14. Open questions
None at the moment.
