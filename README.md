# Mandir Manager (Android)

Offline app for a colony mandir committee: members, donations with WhatsApp and PDF receipts,
expenses with bill photos, monthly-fee tracking and reports. Kotlin + Room (SQLite) + Material Components.

## Run it
1. Open this folder in Android Studio (Koala 2024.1 or newer) → wait for Gradle sync.
2. Plug in a phone (USB debugging on) or start an emulator → press Run.
3. First open Settings and set the mandir name, greeting, signatory and UPI ID.

Requirements: Android 8.0+ (minSdk 26), JDK 17 (bundled with Android Studio).

## Screens
| Screen | What it does |
|---|---|
| Home | Today's collection with cash/UPI split, month income vs expense, balance, cash in hand, pending fees |
| New donation | Search member by name/mobile/house, type (General, Monthly Fee, Festival Seva, Hundi), month picker for fees, payment mode with UTR field, save + WhatsApp receipt |
| Donations | Search, resend WhatsApp receipt, share PDF receipt, delete |
| Members | Add/edit, committee designation, monthly fee, call, WhatsApp, record donation |
| Expenses | Category, paid to, date, festival tag, bill photo (camera or gallery) |
| Reports | Month-by-month summary, income by type and mode, expenses by category, pending fee list with Remind / Collect buttons, today's cash vs UPI, festival-wise collected vs spent, CSV export |
| Settings | Receipt details, defaults, database backup |

## Project layout
```
app/src/main/java/com/mandir/manager/
  data/   Entities.kt, Daos.kt (all report SQL), AppDatabase.kt
  util/   Constants, Fmt (₹ format, dates, amount in words), Prefs, WhatsApp,
          ReceiptPdf (PdfDocument API), DataExport (CSV + backup), Ui helpers
  ui/     MainActivity, AddDonation, Donations, Members, AddMember,
          Expenses, AddExpense, Reports, Settings, BaseActivity (+ shared RowAdapter)
```

## Changes from the original concept
- Dates are stored as epoch millis (`timestamp`) so date-range reports work. The concept's
  daily query used a `date` column that didn't exist on the donations table.
- `feePaid: Boolean` on members was replaced by `monthlyFee` + a `feeMonth` on each fee payment.
  A single flag can't tell you *which* month is unpaid; now the pending list is computed per month
  and handles partial payments.
- Receipt numbers are sequential (`REC-2026-00001`) instead of the last 6 digits of the clock,
  which could repeat.
- Donations keep a copy of donor name and mobile, so walk-in donors and hundi collections
  work without creating a member, and deleting a member doesn't erase history.
- Expenses can be tagged with a festival, giving a festival-wise profit/loss report.

## Notes
- WhatsApp opens with the message pre-filled; the collector taps Send. Fully automatic sending
  requires the paid WhatsApp Business Cloud API and a server.
- All data lives on one phone. Use Settings → Share database backup regularly.
  Android's automatic Google backup is also enabled (`allowBackup="true"`).
- Multi-phone sync (several collectors) would need a backend such as Firebase; the Room
  schema is ready to be mirrored there.
