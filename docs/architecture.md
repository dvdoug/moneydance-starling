# Architecture

## Shape

This is a single Moneydance **FeatureModule**, written in Kotlin, shipped as `starling.mxt`.

It talks **outbound HTTPS** to Starling’s public personal API and writes into the open `AccountBook`. There is no local server, no OAuth callback, and no TPP registration.

```text
┌─────────────────────────────────────────────────────────┐
│ Moneydance JVM (JRE 21 / 25)                            │
│  FeatureModule (Main)                                   │
│    SettingsStore  ← AccountBook local storage           │
│    StarlingClient → https://api.starlingbank.com/api/v2 │
│    mappings       ← starling.mappings in local storage  │
│    catalogue      ← starling.catalogue                  │
│    SyncService    → OnlineTxn + showDownloadedTxns      │
│    Swing UI (SecondaryDialog) on the EDT                │
└─────────────────────────────────────────────────────────┘
```

## Why a PAT, not TPP OAuth

| | Personal access token | TPP / AISP |
| --- | --- | --- |
| Who is authorised | The account holder, for their own data | A regulated third party, for any customer who consents |
| Auth | `Authorization: Bearer` long-lived token | OAuth, 90-day consent rules, SCA |
| Secrets in the extension | Only the user’s PAT, entered at runtime | App credentials that must not ship in an `.mxt` |
| Rate limit | 5 rps / 1000 per day | Higher TPP limits |

PAT is an explicit product decision. This extension is for people importing **their** Starling, not a marketplace Open Banking app.

Sibling: [moneydance-lunchflow](https://github.com/dvdoug/moneydance-lunchflow) uses a Lunch Flow API key the same way (paste secret, pull, import). Same UI and OFX path; different client.

## Moneydance integration points

Use the official extension API, not unsupported internals, unless a documented Infinite Kind sample does the same thing.

| Need | Mechanism |
| --- | --- |
| Menu item | `FeatureModuleContext.registerFeature` |
| Open UI | `invoke(uri)` |
| File lifecycle | `handleEvent`: `md:file:opened`, `md:file:closing`, `md:file:closed` |
| Optional auto-sync | Same events; never sync when no book is open |
| Logging | Status bar via `MoneydanceGUI.setStatus`; console via `System.err` + `AppDebug.ALL` with a `starling:` prefix. Never log the PAT. |
| Dialogs | `SecondaryDialog` so window position persists |

`init()` must not assume a GUI or a data file. Register the feature there; construct windows lazily.

Entry class: `com.moneydance.modules.features.starling.Main`  
Metadata: `meta_info.dict` with `id = starling`, `minbuild = 5100`.

## Settings and secrets

Per **data file**, not per machine:

- PAT index (`starling.pats`) and tokens (`starling.pat.{id}`)
- Account mappings (`sourceId` → Moneydance account UUID)
- Per-mapping `syncStartDate`, `lastPostedDate`
- Space catalogue (`starling.catalogue`)
- Auto-import on file open (`starling.importOnOpen`, default off)

Cleared status follows Moneydance **Mark Transactions as Cleared When Confirmed**, not our import code.

Store non-secrets in `AccountBook` local storage under `starling.*`. Store each PAT in the same encrypted data file via `LocalStorage.put` and `cacheAuthentication`. Do not delete the put-copy after a cache write. Never a sidecar file or logs. UI: PAT list, Add token, Remove token, Refresh accounts. Mappings persist on Import and on window close (X / Alt+F4 / Escape / Close), not a dedicated Save mappings button.

## Decision: import through downloaded transactions

Write `OnlineTxn`s into `account.getDownloadedTxns()`, then call **`MoneydanceGUI.showDownloadedTxns(account)`** so Moneydance’s own `OnlineTxnMerger` creates unconfirmed register rows (`ol.orig-txn`, Confirm, Merge Choices). That is the OFX path. Auto-add runs later on the EDT; after it, we retarget an unconfirmed Space-movement split to the mapped counterpart bank account.

- **Confirm** = standalone register txn. **Merge** = combine with an existing row. Merge copies **FITID** onto the survivor; next sync skips it. Delete without confirm → it comes back.
- Merge into a reminder keeps the **reminder’s description**. The downloaded `[PENDING]` name is discarded. A later settled download is a second merge (reminder name still wins).
- **Automatically Merge Downloaded Transactions** is the user’s pref; we do not force it.
- **Cleared** follows **Mark as Cleared When Confirmed**.

Hidden metadata (not user Keywords):

| Key | Where | Purpose |
| --- | --- | --- |
| FITID `starling:{categoryUid}:{feedItemUid}` | `OnlineTxn.setFITxnId` / `AbstractTxn.setFiTxnId(PROTO_TYPE_OFX, …)` | Posted identity; skip + merge |
| FITID `starling:pending:{categoryUid}:{feedItemUid}` | same | Pending identity |
| `starling.pending` = `true` | `ParentTxn.setParameter` | Cheap filter |
| `OnlineTxn.setPending(true)` | downloaded row | Staging flag |

Pending set-reconcile applies **only to unconfirmed (`isNew`) register `ParentTxn`s we tagged** with `starling:pending:`. Never delete a confirmed register txn.

Pending → posted, still unconfirmed, **unique** match (exact amount, merchant case-insensitive, date within 7 days, 1:1): retarget that parent to the posted FITID, clear `starling.pending`, take settled payee. Ambiguous or amount-changed: `deleteItem` the unconfirmed pending parent and add a new posted download.

If the user confirms a pending hold, we leave that register txn alone. The later posted row may show as another blue dot for them to merge.

## First import window

Per mapping, **sync start date** (`YYYY-MM-DD`). Default: first day of the current month. Blank = all history we can fetch **for this run** (chunked). After a successful import, set `syncStartDate = max(current start, lastPostedDate − 31 days)` so we only move From **forward**. Typing an older From and clicking Import still backfills; then the date walks forward again.

Unconfirmed pending downloaded rows use a `[PENDING] ` name prefix. Hidden FITID / `starling.pending` remain the source of truth. The prefix is stripped on promote to posted **only if the row is still unconfirmed**.

Currency: if Starling `currency` differs from the Moneydance account’s `CurrencyType.idString`, skip that mapping (hard error).

## Spaces and routing

`TxnRouter` decides which mapping receives a feed item:

- **Main feed, `INTERNAL_TRANSFER` / `ON_US_PAY_ME` + `CATEGORY`:** if the other category is a **Spending Space on the same account** and that Space is not mapped, skip (CSV-like). Otherwise keep on the main mapping (a savings account and old savings Spaces actually leave the current account).
- **Spending Space feed:** if mapped, merchants and internals go there; if not, merchants go to the parent mapping and internals are skipped.
- **Savings Space feed:** skip `ON_US_PAY_ME` + `CUSTOMER` when **another** Starling account MAIN is mapped — that is the other leg of the current-account transfer (holder uid, not a category). Same rule for joint and business current accounts. Interest and spending on the Space still import. Unmapped Spaces fold into the mapped savings account if any.
- **Linked transfer:** when both sides are mapped, download the current-account leg as usual, then (after auto-add) point that unconfirmed split at the Space mapping. A unique existing transfer (date, amount, other account) gets the FITID instead of a second row.

Catalogue: first PAT save walks the main category from `createdAt` in 180-day chunks and records every `CATEGORY` counterparty. Refresh merges live `/spaces` onto that list. Live miss → **(archived)**.

## Sync algorithm

1. HTTP off the EDT (`SyncService` / `SwingWorker`). Apply download-list writes and `showDownloadedTxns` on the EDT.
2. For each relevant category, `GET .../transactions-between` in 180-day windows (`from` = mapping start, `to` = today).
3. Route each item; skip FITIDs still on **live register** `ParentTxn`s on the destination account. Prune download-list rows whose FITID is already on the register.
4. Posted: skip if FITID known; else `downloaded.newTxn()`, fill, `STATUS_NEW`.
5. Pending (`PENDING`, `UPCOMING`, `RETRYING`): set-reconcile; promote or remove as above; else add a NEW download.
6. Skip `DECLINED`, `ACCOUNT_CHECK`, `UPCOMING_CANCELLED`, and `REVERSED` with no `settlementTime`.
7. Dates: `transactionTime` while pending; `settlementTime` when settled.
8. `downloaded.syncItem()` + `account.downloadedTxnsUpdated()`.
9. `MoneydanceGUI.showDownloadedTxns(account)`.
10. On success, persist `lastPostedDate` and roll `syncStartDate`.

Amount sign: Starling `minorUnits` are unsigned; `direction` `OUT` → negative cashflow on `OnlineTxn.setAmount`.

## HTTP client

`HttpURLConnection`. JSON via `api/Json.kt`, not Jackson. ~220 ms between requests. User-Agent `moneydance-starling/1`. Treat 401 as bad token, 403 as missing scope (`ScopeCheck` parses `insufficient_scope`), 429 as rate limit.

## Threading

- HTTP + parse + FITID scan: background.
- All Swing and download-list writes: EDT unless a moneydance_open sample does otherwise. Default: compute on background, apply writes on EDT.

## Testing

Layer the code so `StarlingClient`, `FeedParser`, `TxnRouter`, and FITID / amount conversion have pure unit tests with fixture JSON. The FeatureModule and Swing stay thin.

A full GUI test requires installing an `.mxt` into a throwaway Moneydance file. Do not use production data files in automated tests. GitHub Actions runs unit tests only; master also uploads an unsigned `.mxt` artifact.

## Build

Infinite Kind DevKit **5.1** jars in `lib/`:

- Gradle wrapper 9.x, Microsoft OpenJDK 21 on the owner’s Windows machine
- Kotlin language and API **1.9**, Java release **17**
- `layout.buildDirectory` under `%TEMP%` (OneDrive)
- If `dist/starling.mxt` is locked by Moneydance, sign writes `dist/starling-new.mxt`

Official listing requires Infinite Kind to audit and **counter-sign** the MXT. Local `genkeys` is for development force-load only.
