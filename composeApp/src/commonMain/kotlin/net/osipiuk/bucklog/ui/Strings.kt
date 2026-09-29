package net.osipiuk.bucklog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import net.osipiuk.bucklog.domain.InviteLinks

/** All user-facing text. Plain Kotlin so it works the same on Android and iOS. */
interface Strings {
    val code: String

    // Add
    val history: String
    val refund: String
    val next: String
    val deleteDigit: String
    val delete: String
    val backToAmount: String
    val whatDidYouBuy: String
    val filterOrAddCategory: String
    val add: String
    val addRefund: String
    val searchCurrency: String
    fun added(amount: String, category: String, refund: Boolean): String

    // Edit
    val cancel: String
    val editExpense: String
    val amount: String
    val what: String
    val category: String
    val who: String
    val save: String
    val ok: String
    val errEnterAmount: String
    val errChooseCategory: String
    val errWhoPaid: String

    // History
    fun deleted(what: String): String
    val undo: String
    val back: String
    val settings: String
    val menu: String
    val openSheet: String
    val inviteMenu: String
    fun inviteTitle(sheet: String): String
    val inviteHelp: String
    val theirEmail: String
    val shareAndInvite: String
    val justSendLink: String
    val errEmail: String
    fun inviteMessage(sheet: String, link: String): String
    val sendInvite: String
    fun sharedWith(email: String): String
    fun invitedBy(from: String, sheet: String): String
    fun joinSheet(sheet: String): String
    fun switchToInvitedQuestion(sheet: String): String
    fun switchToInvitedText(current: String): String
    val search: String
    val clearSearch: String
    val day: String
    val month: String
    val clearFilter: String
    val noExpensesYet: String
    val nothingMatches: String
    val cantReadRow: String
    val ratePending: String
    fun synced(time: String): String
    val notSyncedYet: String
    fun waiting(n: Long): String
    val offline: String

    // Settings
    val you: String
    val yourName: String
    val nameHelp: String
    fun renamePastQuestion(n: Long, from: String, to: String): String
    val renameAll: String
    val onlyNew: String
    fun googleAccount(email: String): String
    val familySheet: String
    fun sheetInfo(name: String, currency: String): String
    val syncing: String
    val syncNow: String
    fun openInSheets(name: String): String
    val useAnotherSheet: String
    val backups: String
    val automaticBackups: String
    val backupsHelp: String
    fun lastBackup(time: String): String
    val noBackupYet: String
    val backUpNow: String
    val backingUp: String
    val openBackups: String
    fun backupFailed(message: String): String
    val categories: String
    val categoriesHelp: String
    val archivedLabel: String
    val addCategory: String
    val language: String
    val languageSystem: String
    val developer: String
    val addDemo: String
    val signOut: String
    val useAnotherSheetQuestion: String
    val signOutQuestion: String
    val addDemoQuestion: String
    fun addDemoText(sheet: String): String
    val continueLabel: String
    val localDataCleared: String
    fun pendingWillBeLost(n: Long): String
    val newCategory: String
    val editCategory: String
    val name: String
    val emoji: String
    val emojiAny: String
    val none: String
    val archived: String
    val errEnterName: String
    fun errCategoryExists(name: String): String
    val demoPartner: String
    val emojiSections: List<String>

    // Setup
    val tagline: String
    val signInWithGoogle: String
    fun signedInAs(email: String): String
    val createFamilySheet: String
    val alreadyHaveOne: String
    val sheetLinkOptional: String
    val chooseSharedSheet: String
    val useAnotherAccount: String
    fun connectedTo(sheet: String): String
    val start: String
    val chooseDifferentSheet: String

    // Dates
    val months: List<String>
    val weekdays: List<String>
    val today: String
    val yesterday: String

    // Errors and banners
    val errSignIn: String
    val errSheetAccess: String
    val errBusy: String
    val errOffline: String
    fun errGoogle(status: Int, message: String): String
    fun errGeneric(message: String?): String
    val bannerSignIn: String
    val bannerSignInAction: String
    val bannerAccess: String
    val bannerAccessAction: String
}

object EnglishStrings : Strings {
    override val code = "en"
    override val history = "History"
    override val refund = "Refund"
    override val next = "Next"
    override val deleteDigit = "Delete digit"
    override val delete = "Delete"
    override val backToAmount = "Back to amount"
    override val whatDidYouBuy = "What did you buy?"
    override val filterOrAddCategory = "Filter or add category"
    override val add = "Add"
    override val addRefund = "Add refund"
    override val searchCurrency = "Search currency"
    override fun added(amount: String, category: String, refund: Boolean) = (if (refund) "Refund " else "") + "$amount · $category"

    override val cancel = "Cancel"
    override val editExpense = "Edit expense"
    override val amount = "Amount"
    override val what = "What"
    override val category = "Category"
    override val who = "Who"
    override val save = "Save"
    override val ok = "OK"
    override val errEnterAmount = "Enter an amount"
    override val errChooseCategory = "Choose a category"
    override val errWhoPaid = "Who paid?"

    override fun deleted(what: String) = "Deleted “$what”"
    override val undo = "Undo"
    override val back = "Back"
    override val settings = "Settings"
    override val menu = "Menu"
    override val openSheet = "Open in Google Sheets"
    override val inviteMenu = "Invite"
    override fun inviteTitle(sheet: String) = "Invite to “$sheet”"
    override val inviteHelp = "Enter their Google account. Bucklog shares the sheet with them, then you send them an invite (WhatsApp, SMS, email…) with a link to download the app and a link to join."
    override val theirEmail = "Their Google email"
    override val shareAndInvite = "Give access and send invite"
    override val justSendLink = "They already have access: just send the link"
    override val errEmail = "Enter a valid email address"
    override fun inviteMessage(sheet: String, link: String) =
        "Join me in Bucklog to track expenses together in “$sheet”.\n\n1. Install the app: ${InviteLinks.DOWNLOAD}\n2. Then open: $link"
    override val sendInvite = "Send invite"
    override fun sharedWith(email: String) = "Shared with $email"
    override fun invitedBy(from: String, sheet: String) = if (from.isNotBlank()) "$from invited you to “$sheet”." else "You're invited to “$sheet”."
    override fun joinSheet(sheet: String) = "Join “$sheet”"
    override fun switchToInvitedQuestion(sheet: String) = "Join “$sheet”?"
    override fun switchToInvitedText(current: String) =
        "This phone is connected to “$current”. Switching clears its local copy; the sheet itself isn't touched."
    override val search = "Search"
    override val clearSearch = "Clear search"
    override val day = "Day"
    override val month = "Month"
    override val clearFilter = "Clear filter"
    override val noExpensesYet = "No expenses yet."
    override val nothingMatches = "Nothing matches."
    override val cantReadRow = "Can't read this row in the sheet. Fix it there."
    override val ratePending = "rate pending"
    override fun synced(time: String) = "Synced $time"
    override val notSyncedYet = "Not synced yet"
    override fun waiting(n: Long) = "$n waiting"
    override val offline = "Offline, will sync when back online"

    override val you = "You"
    override val yourName = "Your name"
    override val nameHelp = "Used for the Who column of new expenses. When you change it, you can rename your past expenses too."
    override fun renamePastQuestion(n: Long, from: String, to: String) =
        "Also rename ${if (n == 1L) "1 past expense" else "$n past expenses"} from “$from” to “$to”? This updates the sheet too."
    override val renameAll = "Rename all"
    override val onlyNew = "Only new ones"
    override fun googleAccount(email: String) = "Google account: $email"
    override val familySheet = "Family sheet"
    override fun sheetInfo(name: String, currency: String) = "$name · main currency $currency"
    override val syncing = "Syncing…"
    override val syncNow = "Sync now"
    override fun openInSheets(name: String) = "Open “$name” in Google Sheets ↗"
    override val useAnotherSheet = "Use another sheet…"
    override val backups = "Backups"
    override val automaticBackups = "Weekly backup to my Drive"
    override val backupsHelp = "Copies the whole sheet to a “Bucklog backups” folder in your Google Drive when it changed, keeping the last 8."
    override fun lastBackup(time: String) = "Last backup $time"
    override val noBackupYet = "No backup yet"
    override val backUpNow = "Back up now"
    override val backingUp = "Backing up…"
    override val openBackups = "Open backups folder ↗"
    override fun backupFailed(message: String) = "Backup failed: $message"
    override val categories = "Categories"
    override val categoriesHelp = "Renaming updates every expense in that category. Archived categories are hidden when adding."
    override val archivedLabel = "archived"
    override val addCategory = "＋ Add category"
    override val language = "Language"
    override val languageSystem = "System"
    override val developer = "Developer"
    override val addDemo = "Add demo expenses (6 months)"
    override val signOut = "Sign out"
    override val useAnotherSheetQuestion = "Use another sheet?"
    override val signOutQuestion = "Sign out?"
    override val addDemoQuestion = "Add demo expenses?"
    override fun addDemoText(sheet: String) =
        "About 400 fake expenses over the last 6 months will be added and synced to “$sheet”. " +
            "Use a separate demo sheet unless you want them in your family sheet."
    override val continueLabel = "Continue"
    override val localDataCleared = "Local data on this phone is cleared; the family sheet isn't touched."
    override fun pendingWillBeLost(n: Long) =
        "⚠️ ${if (n == 1L) "1 change hasn't" else "$n changes haven't"} reached the sheet yet and will be lost. Sync first."
    override val newCategory = "New category"
    override val editCategory = "Edit category"
    override val name = "Name"
    override val emoji = "Emoji"
    override val emojiAny = "any"
    override val none = "None"
    override val archived = "Archived"
    override val errEnterName = "Enter a name"
    override fun errCategoryExists(name: String) = "“$name” already exists"
    override val demoPartner = "Partner"
    override val emojiSections = listOf(
        "Food & shopping", "Transport", "Home", "Bills & money", "Health & care",
        "Kids & family", "Clothes", "Fun", "Travel", "Other",
    )

    override val tagline = "Log family expenses in seconds. Everything lands in a Google Sheet your family shares."
    override val signInWithGoogle = "Sign in with Google"
    override fun signedInAs(email: String) = "Signed in as $email"
    override val createFamilySheet = "Create a new family sheet"
    override val alreadyHaveOne = "Already have one? Someone in your family shares it with you, then you choose it here."
    override val sheetLinkOptional = "Sheet link (optional)"
    override val chooseSharedSheet = "Choose a shared sheet"
    override val useAnotherAccount = "Use another Google account"
    override fun connectedTo(sheet: String) = "Connected to “$sheet”. Your name goes into the Who column of every expense you add."
    override val start = "Start"
    override val chooseDifferentSheet = "Choose a different sheet"

    override val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    override val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    override val today = "Today"
    override val yesterday = "Yesterday"

    override val errSignIn = "Google sign-in is needed. Please sign in again."
    override val errSheetAccess = "Can't open the family sheet. Make sure it's still shared with you."
    override val errBusy = "Google is busy. Try again in a minute."
    override val errOffline = "No internet connection."
    override fun errGoogle(status: Int, message: String) = "Google error ($status): $message"
    override fun errGeneric(message: String?) = message?.let { "Something went wrong: $it" } ?: "Something went wrong."
    override val bannerSignIn = "Google needs you to sign in again before syncing."
    override val bannerSignInAction = "Sign in"
    override val bannerAccess = "Can't access the family sheet. It may have been unshared or deleted."
    override val bannerAccessAction = "Choose sheet"
}

object PolishStrings : Strings {
    override val code = "pl"
    override val history = "Historia"
    override val refund = "Zwrot"
    override val next = "Dalej"
    override val deleteDigit = "Usuń cyfrę"
    override val delete = "Usuń"
    override val backToAmount = "Wróć do kwoty"
    override val whatDidYouBuy = "Za co?"
    override val filterOrAddCategory = "Filtruj lub dodaj kategorię"
    override val add = "Dodaj"
    override val addRefund = "Dodaj zwrot"
    override val searchCurrency = "Szukaj waluty"
    override fun added(amount: String, category: String, refund: Boolean) = (if (refund) "Zwrot " else "") + "$amount · $category"

    override val cancel = "Anuluj"
    override val editExpense = "Edytuj wydatek"
    override val amount = "Kwota"
    override val what = "Co"
    override val category = "Kategoria"
    override val who = "Kto"
    override val save = "Zapisz"
    override val ok = "OK"
    override val errEnterAmount = "Wpisz kwotę"
    override val errChooseCategory = "Wybierz kategorię"
    override val errWhoPaid = "Kto zapłacił?"

    override fun deleted(what: String) = "Usunięto „$what”"
    override val undo = "Cofnij"
    override val back = "Wstecz"
    override val settings = "Ustawienia"
    override val menu = "Menu"
    override val openSheet = "Otwórz w Arkuszach Google"
    override val inviteMenu = "Zaproś"
    override fun inviteTitle(sheet: String) = "Zaproś do „$sheet”"
    override val inviteHelp = "Wpisz konto Google tej osoby. Bucklog udostępni jej arkusz, a Ty wyślesz zaproszenie (WhatsApp, SMS, e-mail…) z linkiem do pobrania aplikacji i linkiem do dołączenia."
    override val theirEmail = "Adres e-mail Google tej osoby"
    override val shareAndInvite = "Udostępnij i wyślij zaproszenie"
    override val justSendLink = "Ma już dostęp – wyślij tylko link"
    override val errEmail = "Wpisz poprawny adres e-mail"
    override fun inviteMessage(sheet: String, link: String) =
        "Dołącz do wspólnych wydatków „$sheet” w Bucklog.\n\n1. Zainstaluj aplikację: ${InviteLinks.DOWNLOAD}\n2. Potem otwórz: $link"
    override val sendInvite = "Wyślij zaproszenie"
    override fun sharedWith(email: String) = "Udostępniono: $email"
    override fun invitedBy(from: String, sheet: String) = if (from.isNotBlank()) "$from zaprasza Cię do „$sheet”." else "Zaproszenie do „$sheet”."
    override fun joinSheet(sheet: String) = "Dołącz do „$sheet”"
    override fun switchToInvitedQuestion(sheet: String) = "Dołączyć do „$sheet”?"
    override fun switchToInvitedText(current: String) =
        "Ten telefon jest połączony z „$current”. Zmiana wyczyści dane na telefonie; sam arkusz pozostaje bez zmian."
    override val search = "Szukaj"
    override val clearSearch = "Wyczyść"
    override val day = "Dzień"
    override val month = "Miesiąc"
    override val clearFilter = "Wyczyść filtr"
    override val noExpensesYet = "Brak wydatków."
    override val nothingMatches = "Nic nie pasuje."
    override val cantReadRow = "Nie da się odczytać tego wiersza w arkuszu. Popraw go tam."
    override val ratePending = "kurs wkrótce"
    override fun synced(time: String) = "Zsynchronizowano $time"
    override val notSyncedYet = "Jeszcze nie zsynchronizowano"
    override fun waiting(n: Long) = "$n ${plural(n, "czeka", "czekają", "czeka")}"
    override val offline = "Brak sieci, synchronizacja po powrocie online"

    override val you = "Ty"
    override val yourName = "Twoje imię"
    override val nameHelp = "Trafia do kolumny Kto nowych wydatków. Przy zmianie możesz zaktualizować też wcześniejsze."
    override fun renamePastQuestion(n: Long, from: String, to: String) =
        "Zmienić też „$from” na „$to” w $n ${plural(n, "wcześniejszym wydatku", "wcześniejszych wydatkach", "wcześniejszych wydatkach")}? Arkusz zostanie zaktualizowany."
    override val renameAll = "Zmień wszystkie"
    override val onlyNew = "Tylko nowe"
    override fun googleAccount(email: String) = "Konto Google: $email"
    override val familySheet = "Arkusz rodzinny"
    override fun sheetInfo(name: String, currency: String) = "$name · waluta główna $currency"
    override val syncing = "Synchronizuję…"
    override val syncNow = "Synchronizuj"
    override fun openInSheets(name: String) = "Otwórz „$name” w Arkuszach Google ↗"
    override val useAnotherSheet = "Użyj innego arkusza…"
    override val backups = "Kopie zapasowe"
    override val automaticBackups = "Cotygodniowa kopia na moim Dysku"
    override val backupsHelp = "Kopiuje cały arkusz do folderu „Bucklog backups” na Twoim Dysku Google, gdy się zmienił; zostaje 8 ostatnich."
    override fun lastBackup(time: String) = "Ostatnia kopia $time"
    override val noBackupYet = "Brak kopii"
    override val backUpNow = "Zrób kopię teraz"
    override val backingUp = "Kopiuję…"
    override val openBackups = "Otwórz folder z kopiami ↗"
    override fun backupFailed(message: String) = "Kopia nie powiodła się: $message"
    override val categories = "Kategorie"
    override val categoriesHelp = "Zmiana nazwy dotyczy wszystkich wydatków w tej kategorii. Zarchiwizowane nie pojawiają się przy dodawaniu."
    override val archivedLabel = "archiwum"
    override val addCategory = "＋ Dodaj kategorię"
    override val language = "Język"
    override val languageSystem = "Systemowy"
    override val developer = "Deweloper"
    override val addDemo = "Dodaj przykładowe wydatki (6 miesięcy)"
    override val signOut = "Wyloguj"
    override val useAnotherSheetQuestion = "Użyć innego arkusza?"
    override val signOutQuestion = "Wylogować?"
    override val addDemoQuestion = "Dodać przykładowe wydatki?"
    override fun addDemoText(sheet: String) =
        "Około 400 fikcyjnych wydatków z ostatnich 6 miesięcy trafi do „$sheet”. " +
            "Użyj osobnego arkusza demo, chyba że chcesz je w arkuszu rodzinnym."
    override val continueLabel = "Dalej"
    override val localDataCleared = "Dane na tym telefonie zostaną wyczyszczone; arkusz rodzinny pozostaje bez zmian."
    override fun pendingWillBeLost(n: Long) =
        "⚠️ $n ${plural(n, "zmiana nie trafiła", "zmiany nie trafiły", "zmian nie trafiło")} jeszcze do arkusza i przepadnie. Najpierw zsynchronizuj."
    override val newCategory = "Nowa kategoria"
    override val editCategory = "Edytuj kategorię"
    override val name = "Nazwa"
    override val emoji = "Emoji"
    override val emojiAny = "dowolne"
    override val none = "Brak"
    override val archived = "Zarchiwizowana"
    override val errEnterName = "Wpisz nazwę"
    override fun errCategoryExists(name: String) = "„$name” już istnieje"
    override val demoPartner = "Partner"
    override val emojiSections = listOf(
        "Jedzenie i zakupy", "Transport", "Dom", "Rachunki i pieniądze", "Zdrowie i pielęgnacja",
        "Dzieci i rodzina", "Ubrania", "Rozrywka", "Podróże", "Inne",
    )

    override val tagline = "Zapisuj rodzinne wydatki w kilka sekund. Wszystko trafia do wspólnego Arkusza Google."
    override val signInWithGoogle = "Zaloguj przez Google"
    override fun signedInAs(email: String) = "Zalogowano jako $email"
    override val createFamilySheet = "Utwórz nowy arkusz rodzinny"
    override val alreadyHaveOne = "Masz już arkusz? Ktoś z rodziny udostępnia go Tobie, a Ty wybierasz go tutaj."
    override val sheetLinkOptional = "Link do arkusza (opcjonalnie)"
    override val chooseSharedSheet = "Wybierz udostępniony arkusz"
    override val useAnotherAccount = "Użyj innego konta Google"
    override fun connectedTo(sheet: String) = "Połączono z „$sheet”. Twoje imię trafia do kolumny Kto każdego dodanego wydatku."
    override val start = "Zaczynamy"
    override val chooseDifferentSheet = "Wybierz inny arkusz"

    // Nominative for month headers ("Wrzesień 2026").
    override val months = listOf(
        "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
        "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień",
    )
    override val weekdays = listOf("pon.", "wt.", "śr.", "czw.", "pt.", "sob.", "niedz.")
    override val today = "Dziś"
    override val yesterday = "Wczoraj"

    override val errSignIn = "Trzeba ponownie zalogować się do Google."
    override val errSheetAccess = "Nie można otworzyć arkusza rodzinnego. Sprawdź, czy nadal jest Ci udostępniony."
    override val errBusy = "Google jest przeciążone. Spróbuj za minutę."
    override val errOffline = "Brak połączenia z internetem."
    override fun errGoogle(status: Int, message: String) = "Błąd Google ($status): $message"
    override fun errGeneric(message: String?) = message?.let { "Coś poszło nie tak: $it" } ?: "Coś poszło nie tak."
    override val bannerSignIn = "Google prosi o ponowne zalogowanie przed synchronizacją."
    override val bannerSignInAction = "Zaloguj"
    override val bannerAccess = "Brak dostępu do arkusza rodzinnego. Mógł zostać usunięty lub przestał być udostępniony."
    override val bannerAccessAction = "Wybierz arkusz"

    /** Polish plural: 1 → [one], 2–4 (not 12–14) → [few], otherwise [many]. */
    private fun plural(n: Long, one: String, few: String, many: String): String = when {
        n == 1L -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }
}

/** Warning before clearing this phone's copy (switching sheets, signing out). */
fun Strings.clearedMessage(pending: Long) = localDataCleared + if (pending > 0) "\n\n" + pendingWillBeLost(pending) else ""

fun stringsFor(language: String): Strings = if (language == "pl") PolishStrings else EnglishStrings

val LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }
