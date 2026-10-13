# FinMoney - Developer Architecture, Framework Flow & Contribution Guide

Welcome to **FinMoney**! This document provides a complete end-to-end breakdown of the application's architecture, data layer, state management, background notification listening services, bank statement parsing engine, and release pipeline. 

Whether you are a newcomer to Android development or an experienced engineer onboarding onto the project, this guide explains how every piece fits together so you can confidently make new changes.

---

## 1. Project High-Level Overview

**FinMoney** is an Android personal finance, salary budget planner, recurring bill tracker, bank statement reconciler, and peer-to-peer loan ledger app inspired by modern Indian fintech styling (INDmoney).

### Core Features:
1. **Smart Salary & Expense Budgeting**: Monthly income vs. planned debits, category spend breakdowns, daily safe spend limits, and flexible cash calculations.
2. **Bank Transaction Notification Listener**: Android `NotificationListenerService` that actively intercepts transaction SMS/app notifications from Indian banks and UPI apps (HDFC, ICICI, SBI, Axis, PhonePe, Google Pay, Paytm, Super Money), de-duplicates them via SHA-256 fingerprinting, and surfaces them for 1-tap approval/rejection.
3. **Universal Statement Importer**: Parses PDF, Excel (`.xlsx`), and CSV transaction histories from PhonePe, Google Pay, Super Money, and bank accounts, filtering only confirmed successful transactions.
4. **Recurring Debit Obligations**: Tracks monthly recurring bills (EMIs, subscriptions, rent) with a 1-tap pre-fill feature to populate new months.
5. **Peer-to-Peer Loans Ledger (Khaata)**: Lent vs. Borrowed ledger with mutual party tracking and settling.
6. **Local-First with Cloud Sync**: Built on Room Database with Firestore cloud sync capabilities.

---

## 2. Tech Stack & Libraries

| Domain | Technology / Library |
| :--- | :--- |
| **Language** | Kotlin 2.0+ (100% Kotlin) |
| **UI Framework** | Jetpack Compose with Material 3 (M3) |
| **Architecture** | MVVM (Model-View-ViewModel) + Repository Pattern + Clean Architecture |
| **Local Database** | Room Database (SQLite) + KSP (Kotlin Symbol Processing) |
| **Concurrency & Async** | Kotlin Coroutines (`viewModelScope`, `Dispatchers.IO`) & StateFlow / Flow |
| **Lifecycle** | AndroidX Lifecycle `collectAsStateWithLifecycle()` |
| **Background Services** | Android `NotificationListenerService`, `BroadcastReceiver` |
| **Document Parsing** | Apache POI / custom XLSX parser, Android PDF text engine, CSV string parser |
| **Testing** | JUnit 4, Robolectric for local JVM tests |
| **Build System** | Gradle (Kotlin DSL: `build.gradle.kts`) with Version Catalog (`libs.versions.toml`) |

---

## 3. Directory & File Structure

```
FinMoney/
├── app/
│   ├── build.gradle.kts                # App module build configuration, signing & SDK setup
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml     # App permissions, activities, receivers, and services
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt     # Single Activity host with edge-to-edge support
│   │   │   │   ├── ai/                 # AI assistant integration (Kubera AI, Context builders)
│   │   │   │   ├── data/
│   │   │   │   │   ├── bank/           # Regex parsers for bank SMS & notifications
│   │   │   │   │   │   ├── BankTransactionParser.kt
│   │   │   │   │   │   └── MerchantEnrichmentEngine.kt
│   │   │   │   │   ├── local/          # Room DB, Entities, DAOs, Migrations, Health Guard
│   │   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   │   ├── FinMoneyDao.kt
│   │   │   │   │   │   └── DatabaseHealthGuard.kt
│   │   │   │   │   ├── model/          # Data models (DebitItem, UserProfile, SalaryRecord, etc.)
│   │   │   │   │   │   └── FinModels.kt
│   │   │   │   │   └── repository/     # FinMoneyRepository bridging DB and Business Logic
│   │   │   │   │       └── FinMoneyRepository.kt
│   │   │   │   ├── service/            # Background notification services and statement engine
│   │   │   │   │   ├── BankNotificationListenerService.kt
│   │   │   │   │   ├── BankExpenseActionReceiver.kt
│   │   │   │   │   ├── BankSyncManager.kt
│   │   │   │   │   ├── BankTransactionDeduplicator.kt
│   │   │   │   │   ├── BootReceiver.kt
│   │   │   │   │   └── statement/      # Universal statement parsing (PDF/Excel/CSV)
│   │   │   │   │       ├── UniversalStatementParser.kt
│   │   │   │   │       ├── StatementImportManager.kt
│   │   │   │   │       ├── StatementModels.kt
│   │   │   │   │       ├── PdfTextExtractor.kt
│   │   │   │   │       └── XlsxTextExtractor.kt
│   │   │   │   └── ui/
│   │   │   │       ├── FinMoneyViewModel.kt    # Primary centralized ViewModel
│   │   │   │       ├── MainScreen.kt           # Scaffold, TopBar, BottomNavigationBar
│   │   │   │       ├── components/             # Reusable M3 UI components & dialogs
│   │   │   │       │   ├── FinComponents.kt
│   │   │   │       │   ├── FinDialogs.kt
│   │   │   │       │   └── StatementImportDialog.kt
│   │   │   │       ├── screens/                # Composable screen views
│   │   │   │       │   ├── DashboardScreen.kt
│   │   │   │       │   ├── AnalyticsScreen.kt
│   │   │   │       │   ├── LentBorrowedScreen.kt
│   │   │   │       │   ├── BankReconciliationScreen.kt
│   │   │   │       │   └── PendingTransactionsScreen.kt
│   │   │   │       └── theme/                  # Theme, Color, Type, Dimensions
│   │   │   └── res/
│   │   │       ├── values/strings.xml
│   │   │       └── drawable/
│   │   └── test/java/com/example/              # Local unit and Robolectric test suite
│   │       ├── DatabaseHealthGuardTest.kt
│   │       └── ...
├── build.gradle.kts                            # Root project build configuration
├── settings.gradle.kts                         # Included modules & plugin management
├── codemagic.yaml                              # Codemagic CI/CD configuration
└── .github/workflows/build.yml                 # GitHub Actions automated release pipeline
```

---

## 4. Architectural Patterns & Framework Flow

```
┌────────────────────────────────────────────────────────────────────────┐
│                        USER INTERFACE (Jetpack Compose)                 │
│  MainScreen -> DashboardScreen / AnalyticsScreen / LentBorrowedScreen  │
└─────────────────────────────────▲──────────────────────────────────────┘
                                  │ (StateFlow / collectAsStateWithLifecycle)
┌─────────────────────────────────▼──────────────────────────────────────┐
│                     VIEWMODEL (FinMoneyViewModel)                      │
│   Exposes UI states, calculates budgets, launches coroutines           │
└─────────────────────────────────▲──────────────────────────────────────┘
                                  │ (Repository pattern)
┌─────────────────────────────────▼──────────────────────────────────────┐
│                    DATA LAYER (FinMoneyRepository)                     │
│   Coordinates Room Database & Firestore Sync                           │
└─────────────────────────────────▲──────────────────────────────────────┘
            ┌─────────────────────┴─────────────────────┐
            │                                           │
┌───────────▼───────────┐                   ┌───────────▼───────────┐
│ Room Database (Local) │                   │  Firestore (Cloud)    │
│ FinMoneyDao (SQLite)  │                   │  Multi-device sync    │
└───────────────────────┘                   └───────────────────────┘
```

### 1. Data Layer (`com.example.data`)
- **`FinModels.kt`**: Houses all entities:
  - `DebitItem`: An individual expense or scheduled bill (`month`, `year`, `title`, `amount`, `category`, `dueDateDay`, `isPaid`, `isPaidViaCreditCard`, `isRecurring`).
  - `SalaryRecord`: Monthly salary, additional income, and JSON array of `IncomeSource`.
  - `LoanTransaction`: P2P loan (`LENT` or `BORROWED`, amount, person name, status).
  - `BankTransactionRecord`: Ingested bank alerts (`PENDING`, `APPROVED`, `REJECTED`, `fingerprint`, `sbnKey`).
- **`FinMoneyDao.kt`**: SQLite operations. Uses `Flow<List<T>>` for automatic reactive UI updates upon database mutations.
- **`AppDatabase.kt`**: Singleton Room database instance with schema migrations (e.g., `MIGRATION_15_16`).

### 2. State Management (`FinMoneyViewModel.kt`)
- All reactive state is represented as Kotlin `StateFlow`.
- **Derived State**: `monthlySummary` combines `currentSalaryRecord`, `currentDebits`, and `currentReconciliation` to compute real-time numbers (total income, total debits, yet to pay, flexible cash balance).
- When a user adds an expense, toggles an item as paid, or imports statements, the database is updated on `Dispatchers.IO`, and Room automatically emits new values through `StateFlow` to recompose the UI.

---

## 5. Bank Notification Capture & De-Duplication Engine

One of FinMoney's most powerful features is background transaction interception.

```
Bank SMS / UPI App Notification Posted
                   │
                   ▼
┌───────────────────────────────────────────────────────────┐
│ BankNotificationListenerService.onNotificationPosted()    │
│ - Filters out ongoing/progress notifications              │
│ - Extracts text from title, bigText, and subText          │
└──────────────────────────┬────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────┐
│ BankTransactionParser.parseTransaction()                  │
│ - Matches Indian bank formats (HDFC, ICICI, SBI, UPI, etc.)│
│ - Extracts: amount, merchant, type (DEBIT/CREDIT), last4   │
└──────────────────────────┬────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────┐
│ BankTransactionDeduplicator.evaluate()                    │
│ 1. Computes SHA-256 fingerprint:                         │
│    (bank + amount + type + merchant + last4 + refNo)      │
│ 2. Checks against pending & confirmed transactions        │
│ 3. Applies 3-minute sliding window check for missing refs │
│ 4. Checks against rejected transactions                   │
└──────────────────────────┬────────────────────────────────┘
                           │ If Unique & Valid
                           ▼
┌───────────────────────────────────────────────────────────┐
│ BankSyncManager.persistParsedTransaction()                │
│ - Inserts into bank_transactions (status: PENDING)        │
│ - Triggers FinMoney status bar alert with Approve/Dismiss │
└───────────────────────────────────────────────────────────┘
```

### How to test or inspect notifications:
1. Ensure Notification Access permission is enabled (`Settings -> Notification Access -> FinMoney`).
2. When a notification arrives, tap **Approve** directly from the notification shade or in the app's **Auto-Identified Payments** section.
3. Upon approval:
   - A `DebitItem` is created in Room and synced to Firestore.
   - The status is marked as `APPROVED`.
   - The original notification is cancelled from the Android status bar.
4. Upon rejection:
   - Status is marked as `REJECTED`.
   - The notification is dismissed immediately.
   - The fingerprint is retained so re-posted notifications will not re-alert the user.

---

## 6. Universal Statement Import Engine (`com.example.service.statement`)

The import engine allows users to bring in historical statements:
- **`UniversalStatementParser.kt`**:
  - Automatically identifies whether input text is from **PhonePe** (CSV/PDF), **Super Money / UPI Table**, **Google Pay**, or standard Indian bank formats (HDFC, ICICI, SBI, Axis, etc.).
  - **Success Filter Rule**: Discards any row marked with `FAILED`, `CANCELLED`, `PENDING`, or `DECLINED`. Only records with `SUCCESS`, `COMPLETED`, `PAID`, `SETTLED`, or `CR`/`DR` are extracted.
- **`StatementImportDialog.kt`**:
  - UI dialog providing a system file picker (supporting `.pdf`, `.xlsx`, `.csv`, `.txt`) and text-pasting area.
  - Previews successful vs. skipped transactions before confirming import.
  - Commits valid records to Room as both `DebitItem` and `BankTransactionRecord`.

---

## 7. Recurring Expenses & Pre-Fill Flow

- **Flag**: `DebitItem.isRecurring: Boolean = true` marks an expense as a recurring monthly bill.
- **UI Toggle**: Available both in `AddEditDebitDialog` and directly via the `Repeat` icon on any card in `DashboardScreen`.
- **Pre-fill Logic**:
  - `viewModel.prefillRecurringDebitsForMonth(targetMonth, targetYear)`
  - Reads all templates (`isRecurring == true`) from Room.
  - Checks if the item already exists in the target month (by title, category, and amount) to prevent duplicates.
  - Automatically generates non-paid copies for the new month with a single tap.

---

## 8. Step-by-Step Guide: How to Add a New Feature

Here is a practical checklist for a new developer adding a feature (for example, adding a **"Savings Goal"** entity):

### Step 1: Define the Data Model
Open `app/src/main/java/com/example/data/model/FinModels.kt` and add your `@Entity`:
```kotlin
@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val targetDateTimestamp: Long = 0L
)
```

### Step 2: Add DAO Queries
Open `app/src/main/java/com/example/data/local/FinMoneyDao.kt`:
```kotlin
@Query("SELECT * FROM savings_goals ORDER BY id ASC")
fun getAllSavingsGoals(): Flow<List<SavingsGoal>>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertSavingsGoal(goal: SavingsGoal): Long

@Delete
suspend fun deleteSavingsGoal(goal: SavingsGoal)
```

### Step 3: Update Room Database Version
Open `app/src/main/java/com/example/data/local/AppDatabase.kt`:
- Add `SavingsGoal::class` to the `@Database(entities = [...])` array.
- Increment `version` (e.g., from 16 to 17).
- Add the corresponding `Migration(16, 17)` block or rely on Room migration helpers.

### Step 4: Expose in Repository & ViewModel
- In `FinMoneyRepository.kt`, add pass-through functions.
- In `FinMoneyViewModel.kt`:
```kotlin
val allSavingsGoals: StateFlow<List<SavingsGoal>> = repository.getAllSavingsGoals()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

fun addSavingsGoal(title: String, target: Double) {
    viewModelScope.launch {
        repository.insertSavingsGoal(SavingsGoal(title = title, targetAmount = target))
    }
}
```

### Step 5: Build the Composable UI
- Create `app/src/main/java/com/example/ui/screens/SavingsGoalsScreen.kt`.
- Follow Material 3 standards using `AppTheme.colors`, `AppTheme.dimensions`, and unique `Modifier.testTag("tag_name")` on interactive elements.
- Collect state using:
```kotlin
val goals by viewModel.allSavingsGoals.collectAsStateWithLifecycle()
```

### Step 6: Verify Build & Tests
Run in terminal or execute tools:
```bash
gradle :app:testDebugUnitTest
```

---

## 9. Release, Keystore & CI/CD Pipeline Instructions

Always follow these rules when preparing builds or modifying deployment workflows:

### 1. Version Code & Name Increment Rule
- **Rule**: Every production build or modification to `app/build.gradle.kts` must increment `versionCode` by at least `+1` (e.g., 181 -> 182).
- Ensure `versionName` reflects the increment (e.g., `"182.0"`).

### 2. Keystore & Certificate Fingerprints
- Release bundles (`.aab`) must never silently sign with `debug.keystore` for Google Play releases.
- The build script checks for:
  - Local `my-upload-key.jks` file or
  - Environment variables: `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`.
- In CI/CD, the secret `KEYSTORE_BASE64` or `CM_KEYSTORE_BASE64` is decoded prior to executing `./gradlew :app:bundleRelease`.

### 3. Codemagic Configuration (`codemagic.yaml`)
- Always use `instance_type: mac_mini_m2` for free-tier compatibility.
- Uses Java 17 via `/usr/libexec/java_home -v 17`.
- Precedes release commands with `./gradlew clean --no-daemon`.

### 4. GitHub Actions (`.github/workflows/build.yml`)
- Runner: `ubuntu-latest` with JDK 17 (`temurin`).
- Unlimited free runner minutes apply to public repositories. If running into spending limits on private forks, toggle visibility to Public or use local builds.

### 5. Build Artifact Paths
- Release AAB: `app/build/outputs/bundle/release/*.aab`
- Release APK: `app/build/outputs/apk/release/*.apk`

---

## 10. Best Practices & Do's and Don'ts

1. **Do use `collectAsStateWithLifecycle()`** instead of `collectAsState()` in Compose UI to prevent battery drain when screens are in the background.
2. **Do assign unique `testTag` modifiers** (e.g., `Modifier.testTag("submit_button")`) to all clickable buttons, text fields, and cards for UI testing.
3. **Do keep UI files modular** (< 500 lines ideally). Break larger screens into separate component files under `ui/components/`.
4. **Do use `Dispatchers.IO`** for any database, cryptographic fingerprint, file I/O, or parsing work.
5. **Don't hardcode hex colors in Composables**; use `AppTheme.colors` or dynamic Material 3 tokens.
6. **Don't insert mock or simulated data** into production database flows; allow the app to initialize clean and handle empty states gracefully.

Happy coding! If you have any questions, explore the unit tests under `app/src/test/java/com/example/` for concrete examples of how transactions, deduplication, and database migrations work.
