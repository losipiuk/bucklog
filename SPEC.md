# Bucklog — Specification

A family spend tracker. Android first, iOS later. Several family members share one
Google Sheet that acts as the database. The app is optimized for one thing above
all: **logging an expense in as few taps as possible**.

Status: draft v0.1 (2026-09-28)

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
- Currency conversion (entries keep their original currency).

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

| A: Name   | B: Icon | C: Color  | D: Archived |
|-----------|---------|-----------|-------------|
| Groceries | 🛒      | #4CAF50   |             |
| Fuel      | ⛽      | #FF9800   |             |
| Kids      | 🧸      | #2196F3   | TRUE        |

- **Name** is the key. It must be unique (case-insensitive).
- Icon (emoji) and Color are optional. The app picks defaults if they are empty.
- Archived categories are hidden from the picker but kept for old entries.
- Row order = display order in the picker when there is no better guess.

### 3.2 `Settings`

Key/value pairs:

| Key              | Value |
|------------------|-------|
| schema_version   | 1     |
| default_currency | PLN   |

- `default_currency` is used for rows typed by hand with an empty Currency cell.

### 3.3 Year tabs: `2025`, `2026`, …

| A: Date           | B: Who  | C: What       | D: Category | E: Amount | F: Currency | G: ID      |
|-------------------|---------|---------------|-------------|-----------|-------------|------------|
| 2026-09-28 18:42  | Łukasz  | Milk, bread   | Groceries   | 35.30     | PLN         | k3f9x2ab   |

- **Date**: a real Sheets date-time value, formatted `yyyy-mm-dd hh:mm`, in the
  spreadsheet's time zone. Typing `2026-09-28` by hand works.
- **Who**: the name the family member set in the app.
- **What**: free text.
- **Category**: the category *name*, with a data-validation dropdown sourced from
  `Categories!A2:A`. The dropdown is "show warning", not "reject", so hand edits never get blocked.
- **Amount**: a number, formatted with 2 decimals (or the currency's minor
  units). Positive = expense. Negative is allowed (refund/correction).
- **Currency**: ISO 4217 code. Empty = `default_currency`.
- **ID**: stable row identity used by sync. Short random string (8 chars,
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
- OAuth scope: **`drive.file`** (non-sensitive). This avoids Google app verification
  and avoids the 7-day token expiry of unverified apps.
  - With `drive.file`, the app can only access files that the app created or that the user
    explicitly picked. The user therefore **selects the sheet through Google Picker**
    instead of pasting a raw link.
  - A pasted link (or a share link opened on the phone) can still be used to *pre-select*
    the file in the Picker, which only needs a confirm tap.
- Setup flow for the owner: Sign in → "Create new sheet" (or pick an existing one) →
  enter name → done. Then share the sheet with the family in Google Sheets as usual.
- Setup flow for other members: Sign in → pick the shared sheet → enter name → done.
- ⚠️ **Spike needed (M0)**: Google Picker is a web (JS) component. On Android it has
  to be hosted, e.g. a small static page opened in a Custom Tab/WebView that returns
  the file ID through an app link. The spike must confirm:
  1. that Picker-granted `drive.file` access works for Sheets API calls from the app, and
  2. that a sheet *created by the app* on one account is accessible to other
     members only after they pick it.
  Fallback if the spike fails: the `spreadsheets` scope with Google verification.

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
- `→` (Next) button, disabled while the amount is 0.
- Long-press `⌫` clears the amount.
- Haptic feedback on each key.

**Step 2 — What + Category (one screen)**
- The text field has focus and the system keyboard is open.
- Autocomplete suggestions from history show above the keyboard as you type
  (and before typing: the most frequent/recent items).
- **Tapping a suggestion** fills What *and* preselects its usual category.
- Category chips row (horizontally scrollable, emoji + name). The **guessed
  category is preselected** and first; the rest follow by frequency.
- Big **Add** button (also the keyboard's IME action).
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
  the same normalized What text. If there is none, a token-based vote (entries sharing words). If
  there is still none, the category I used most recently.
- Everything is computed locally. It must be instant (<16 ms per keystroke at 25k entries).

### 5.4 History and editing
- Reachable from a `≡` / history icon on the Add screen.
- A list of entries from all family members, newest first, grouped by day, with
  day totals and infinite scroll. Search box (What, category, who).
- Each row shows: icon, What, category, who (initial/avatar), amount + currency,
  and a sync status dot (pending / synced / error).
- Tap → Edit screen: all fields editable (Date via date+time picker, Who,
  What, Category, Amount, Currency). Save / Delete (with Undo snackbar).
- Changing the Date to another year moves the row to the other year tab.
- Entries changed on the sheet show the updated values after the next sync.

### 5.5 Categories
- Managed primarily in the `Categories` tab.
- In the app: a "+ New" chip at the end of the category row creates a category
  (name, optional emoji) and appends it to the tab.
- Rename/archive in the app (Settings → Categories). A rename rewrites the
  Category cell in all affected rows (batched update).
- Rows whose category name isn't in `Categories` (e.g. after a manual rename)
  are shown with a neutral "❓ <name>" chip and are counted as that name. Nothing is lost.

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

### 6.5 Push (drain the Outbox, in order)
- **Insert**: `values.append` to the target year tab (create the tab first if missing).
- **Update / Delete**: re-read column G (IDs) of the tab to find the current row
  number, then `batchUpdate` the row (update) or `deleteDimension` (delete).
  The re-read is done just before writing to minimize races with hand edits.
- **Year change**: delete from the old tab and append to the new one.
- Idempotency: an insert whose ID is already present remotely becomes an update.
- On success: clear the Outbox item and store the new Drive `version`.
- Errors: exponential backoff. Auth errors show a "Sign in again" banner.
  Permission errors (the sheet was unshared) show a clear message in Settings.

### 6.6 Quotas
The Sheets API allows 60 read requests and 60 write requests per minute per user. One sync cycle uses ≤ 5 calls
plus 2 per pending update/delete. Outbox items are batched where possible.

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
  amount_minor INTEGER,    -- 3530 = 35.30
  currency TEXT,
  status TEXT,             -- synced | pending | invalid
  raw_json TEXT NULL       -- original cells for invalid rows
)
outbox(seq PK, op, entry_id, payload_json, attempts, last_error)
category(name PK, icon, color, archived, position)
sync_state(key PK, value) -- drive_version, last_pull_at, spreadsheet_id, tz, default_currency
prefs: my_name, last_currency, spreadsheet_id  (platform key-value store)
```

Amounts are always integer minor units internally. There are no floats in the domain.

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
- Category chips use their emoji + color accent.
- Subtle transitions between steps. Add → a short success animation, then the app closes.
- Accessibility: TalkBack labels, dynamic type, and a minimum 48 dp touch target.
- UI languages: English and Polish (resources from day one).

---

## 10. Follow-up: quick add without opening the app
- **Home-screen widget** (Android Glance / iOS WidgetKit): a "+" button that opens the
  Add flow directly as a lightweight overlay activity. An optional variant shows 3–4
  one-tap favorite What+Category combos, which go straight to the amount keypad.
- **App shortcuts** (long-press on the launcher icon): "Add expense", and "Add <top favorite>".
- **Quick Settings tile** (Android): opens the Add flow.
- (Android widgets can't host text input, so the amount is always entered in the overlay.)

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
- **M5 — Widget, shortcuts and QS tile.**
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
| 5 | Editing | Any entry (all members), recent first |
| 6 | Date | "Now" on add; changeable in edit |
| 7 | Suggestions | Whole family's history, my entries weighted ×2 |
| 8 | Repo | `losipiuk/bucklog`, private |

## 14. Open questions
1. Should the "who" of an entry be editable to another member (e.g. logging a
   spouse's cash expense)? Currently yes, in Edit only.
2. Should the Add flow offer an optional note field, or keep only What?
3. Who owns the Google Cloud project/OAuth client (a personal account)? The consent
   screen stays in "Testing" with family members added as test users, or goes
   "In production" (possible without verification because `drive.file` is non-sensitive).
4. Should totals ever mix currencies, or always be shown per currency?
5. Should the app manage a sheet it didn't create, with extra columns the user added?
   (Proposal: ignore columns after G and never clear them.)
