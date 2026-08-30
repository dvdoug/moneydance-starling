# AGENTS.md

Instructions for AI coding agents working in this repository. Humans should start with [README.md](README.md) (install and use only — no roadmap or agent-speak). Product intent lives in [docs/product.md](docs/product.md).

## Current state (read this first)

Shipped as **`module_build` 13** (`Version.kt`, `meta_info.dict`, and [CHANGELOG.md](CHANGELOG.md) must stay in lockstep). First public build of the Starling importer.

**Import path (do not regress):** write `OnlineTxn`s onto `account.getDownloadedTxns()`, then `MoneydanceGUI.showDownloadedTxns(account)` (`OnlineManager.processDownloadedTxns`). That is Moneydance’s OFX Confirm / Merge path (`ol.orig-txn`, blue dots). **Do not create `ParentTxn`s.** Staging is always `OnlineTxn` + `showDownloadedTxns` (Moneydance auto-adds on the EDT). After that auto-add, for a mapped current-account ↔ Space movement, retarget the unconfirmed split to the other mapped bank account so both registers show one transfer. If a unique existing transfer matches date, amount, and other account, tag its FITID instead of adding a row. Space-side `ON_US_PAY_ME` + `CUSTOMER` is skipped when the other Starling account MAIN is mapped. Do not special-case the name “Personal”; joint and business current accounts use the same MAIN + counterpart rule.

**What works now**

- Settings: list of personal access tokens (PATs). **Add token** validates scopes, then walks that token’s history **once** (**Validating…**). Persist tokens as `starling.pat.{id}` with **both** `LocalStorage.put` and `cacheAuthentication`. Index in `starling.pats`. Never log a token.
- Catalogue: first walk records `CATEGORY` counterparties from the main feed (archived Spaces). **Refresh accounts** only hits live `/accounts` + `/spaces` and stitches onto `starling.catalogue`. Missing from live list → **(archived)**; mapping kept. Never call `/savings-goals`.
- Mapping table: Starling account / Space → Moneydance account + **From** date. Saved as `starling.mappings`. **No Save mappings button.** Persist on **Import**, and on Close / title-bar X / Alt+F4 / Escape (`goneAway`), but only if accounts loaded this session.
- **Import** and **import when this file opens** (checkbox, default **off**) share `SyncService`. HTTP off EDT; `showDownloadedTxns` + pending reconcile on EDT.
- Progress: `MoneydanceGUI.setStatus("Starling: …", progress)` and Help → Console (`starling:` via `System.err` + `AppDebug.ALL`). Never log the PAT.
- From date: this run uses `syncStartDate` if set (default first of month). If From is **blank** and we already have `lastPostedDate`, fetch last posted − 31 days. After a **successful** import, set From to `max(current From, lastPostedDate − 31 days)` — never earlier than the date the user set.
- Posted FITID `starling:{categoryUid}:{feedItemUid}`; pending `starling:pending:…`. Skip only live register `ParentTxn` FITIDs. Pending set-reconcile **unconfirmed** (`isNew`) pending parents on the mapped account only; never `deleteItem` confirmed rows.
- Prune download-list rows **only at the start of apply**, and only against FITIDs already on the live register. Do not prune after adding this run’s `OnlineTxn`s — that deletes them before `showDownloadedTxns`.
- Routing: unmapped **Spending Space** (same Starling account) — skip `INTERNAL_TRANSFER` on the parent; merchants from that Space land on the parent. Unmapped **savings Space** — fold into the mapped savings **account** if that account is mapped; else skip the Space feed. Current-account `ON_US_PAY_ME` to a mapped Space becomes a Moneydance transfer to that mapping (or to the savings-account catch-all if the Space is unmapped). Space-side of that move is `ON_US_PAY_ME` + `CUSTOMER` (holder uid, not a category) — skip it when **any other** Starling account MAIN is mapped. Mapping table groups account then indented children. Do not infer leftover Spaces from names. FITID uses the **feed** category uid even when folding onto a parent mapping.
- Feed window: `transactions-between` max ~180 days (`QUERY_EXCEEDING_MAX_TIME_RANGE`). Chunk. PAT rate limit 5 rps / 1000 per day.
- `meta_info.dict` must include `"minbuild" = "5100"` (Moneydance 2024). Missing `minbuild` → **Extension version too old**.
- `module_desc`: unofficial Starling import via PAT; unaffiliated disclaimer. Do not imply this is a Starling or Infinite Kind product.

**Do not do next unless asked**

- README / in-app screenshots of the real UI (`docs/user/images/`).
- Auto-create Moneydance subaccounts for Spaces.
- Marketplace outreach.

**Dogfood:** throwaway Moneydance file. Owner may also alpha the main file **after a backup**. Agents still must not request production data or paste PATs into git.

## What this repo is

A **Moneydance extension** that downloads transactions from **Starling Bank’s personal API** (the user’s own accounts, via a PAT) and imports them into the open Moneydance data file.

It is a sibling of [moneydance-lunchflow](https://github.com/dvdoug/moneydance-lunchflow): same UI shape, same OFX import path. Lunch Flow is a paid aggregator. Starling is this owner’s UK bank and has a free personal API. Use Lunch Flow for Amex and other institutions; this extension for Starling.

**Target quality:** polished enough to submit to the official Moneydance extension directory (`Extensions → Manage Extensions`).

## Canonical names

| Item | Value |
| --- | --- |
| Extension ID (`meta_info.dict` `id`) | `starling` |
| Display name | Starling Bank |
| `meta_info.dict` `module_build` | Integer (`Version.MODULE_BUILD`). Moneydance shows this as **vN**. Increase on every shipped `.mxt`; keep `meta_info.dict` and [CHANGELOG.md](CHANGELOG.md) in lockstep with `Version.kt`. Do not invent a second version string. |
| Author / `meta_info.dict` `vendor` | Doug Wright |
| Java/Kotlin package | `com.moneydance.modules.features.starling` |
| Artifact | `dist/starling.mxt` |
| Language | Kotlin (Java only if a library forces it) |

Do not rename the extension ID after the first public build. Infinite Kind treats it as the install identity.

## Hard rules

- **Never hardcode a PAT or any credential.** Tokens come from the extension Settings UI and are stored in the open data file (`LocalStorage.put` + `cacheAuthentication`). Never delete the put-copy after a cache write.
- **Starling signed cashflow goes on `OnlineTxn.setAmount`.** Convert `minorUnits` + `direction` (`OUT` negative) at the API boundary. Moneydance’s download converter owns register signs. Do not flip amounts. After auto-add, retarget unconfirmed Space-movement splits to the mapped counterpart bank account.
- **Personal access only** (`https://api.starlingbank.com/api/v2`, `Authorization: Bearer`). Do not add TPP OAuth, payments, or message signing. See [docs/starling-api.md](docs/starling-api.md).
- **Never log the PAT**, paste it into commits, write it to `System.err` / `AppDebug`, or include it in crash reports. Mask it in the UI (`••••` plus last 4).
- **Import through Moneydance’s download converter.** Write `OnlineTxn`s onto `account.getDownloadedTxns()`, then call `MoneydanceGUI.showDownloadedTxns(account)`. FITID skip is against live register `ParentTxn`s only.
- **FITIDs:** posted `starling:{categoryUid}:{feedItemUid}`; pending `starling:pending:{categoryUid}:{feedItemUid}`. Protocol `OnlineTxn.PROTO_TYPE_OFX`. Hidden flag `starling.pending` via `ParentTxn.setParameter`. Never use user Keywords for this.
- **Pending set-reconcile only our unconfirmed pending `ParentTxn`s** (`starling:pending:` FITID, `isNew`). Remove vanished holds with `deleteItem()`. Unique pending→posted match (exact amount, merchant, date within 7 days) updates that `ParentTxn` in place. Ambiguous / amount-changed → delete the **unconfirmed** pending row and add posted. Unconfirmed pending names get a `[PENDING] ` prefix. If the user **Confirm**s or **Merge**s the pending row, we do not strip `[PENDING]` later — they merge the settled download.
- **Honor the mapping start date as the fetch floor for this run.** After a successful import, persist `syncStartDate = max(current start, lastPostedDate − 31 days)` so From only moves **forward**.
- **Chunk `transactions-between`** at 180 days. Do not request 2019–now in one call.
- **Help button** (**Setup guide**) opens [docs/user/setup.md](docs/user/setup.md) on GitHub. Keep that guide non-technical. Do not replace it with a bundled HTML viewer.
- **End-user docs are for a released product.** [docs/user/](docs/user/README.md) (especially troubleshooting) is for a stranger who installed from GitHub Releases. Do not add this session’s QA bugs, “install the current extension”, FITID cleanup, or “older builds did X”. Those belong in **Current state**, Help → Console, or a gitignored scratch note. If a behaviour is the intended product, describe that — not how we got there.
- **GUI on the EDT.** Network I/O and JSON parsing on a background thread (`SwingWorker`). Never block the Event Dispatch Thread on HTTP.
- **Release listeners** on `md:file:closing` / `md:file:closed` and in `unload()`. Do not retain `AccountBook`, `Account`, or `AbstractTxn` references after the file closes.
- **User-facing copy must say this is unofficial.** This extension is by Doug Wright. It is not Starling Bank, not Infinite Kind, and not affiliated with either. Reuse `Main.THIRD_PARTY_DISCLAIMER` (or the same wording) in the window, Help, and `module_desc`.
- **Do not commit** Moneydance signing keys, `privkey*`, Gradle local properties with passphrases, `*.mxt`, `scratch/`, or real user data files.
- Keep this file true. If you change architecture, IDs, or commands, update `AGENTS.md` and the matching doc in `docs/` in the same change.
- **Changelog.** Maintain [CHANGELOG.md](CHANGELOG.md) using Keep a Changelog *categories* (Added/Changed/Fixed/Removed/Security). Headings are `## Unreleased` and `## 1 - YYYY-MM-DD` (integer `module_build`, **no square brackets**). Every bump gets a **1–2 line high-level** entry. GitHub Release notes copy the section **body** only.
- **Commit and push every iteration**, even when `module_build` does not change (docs, polish, tests). Do not leave a day’s work only in the working tree. `git push origin` the current branch after each commit (this repo: `master`). Never commit keys, `userconfig/` secrets, `lib/*.jar`, or `*.mxt`.
- **Do not commit planning scratch.** `docs/roadmap.md`, `docs/review-*.md`, and `docs/_local/` are gitignored. Durable next-steps live in **Current state** above.
- **Build the `.mxt` locally** on every iteration that the owner will install (`gradlew test starling` → `dist/starling.mxt`, signed with the gitignored key). If `dist/starling.mxt` is locked, the sign task writes `dist/starling-new.mxt`. CI’s unsigned GitHub Release is extra, not a replacement.

## Non-goals (unless the user asks)

- Embedding Starling OAuth for *other people’s* accounts (that is TPP / AISP).
- Sending payments, creating payees, or message-signing write endpoints.
- Replacing the Lunch Flow extension (Amex and other banks stay there).
- Auto-creating Moneydance subaccounts for Spaces.
- Python / Jython. New Moneydance extensions should be Kotlin.
- A standalone desktop app. The deliverable is an `.mxt` that runs inside Moneydance.

## Architecture snapshot

```
Moneydance (JVM, Swing)
  └─ FeatureModule Main
        ├─ Settings UI  → PAT list, Add token (validate + history walk)
        ├─ Mapping table → Starling account/Space ↔ Moneydance Account + From
        ├─ SyncService  → StarlingClient → TxnRouter → OnlineTxn + showDownloadedTxns
        └─ Catalogue    → first walk + live stitch
```

Settings live **in the open `AccountBook`**. Tokens: `starling.pat.{id}` via `LocalStorage.put` and `cacheAuthentication`. Index `starling.pats`. Mappings `starling.mappings`. Catalogue `starling.catalogue`.

HTTP client: `HttpURLConnection` from the JRE Moneydance ships (MD2024: JRE 21). No extra HTTP stack. JSON via `api/Json.kt`, not Jackson.

Details: [docs/architecture.md](docs/architecture.md). API contract: [docs/starling-api.md](docs/starling-api.md).

## Build and run

DevKit **5.1** jars in `lib/` (`moneydance-dev.jar`, `extadmin.jar`). Target **Java 17 bytecode**, **Kotlin language/API 1.9**. Gradle `layout.buildDirectory` is under `%TEMP%` because OneDrive syncs this repo.

```text
./gradlew clean genKeys starling     # first machine: generate local signing keys
./gradlew test starling              # compile, test, package, sign → dist/starling.mxt
```

Install: Moneydance → **Extensions → Manage Extensions → Add from File…** → `dist/starling.mxt`.

On Windows: `gradlew.bat starling`. Copy `lib/moneydance-dev.jar` and `lib/extadmin.jar` first (see `lib/README.md`). First machine also needs `userconfig/user.properties` (from the example) and `gradlew genKeys`. Signing keys can be the same vendor 99 pair as moneydance-lunchflow.

**Toolchain:** **JDK 17+** (21 preferred). The Gradle Kotlin plugin downloads the Kotlin 1.9 compiler.

## Code layout (intended)

```text
src/main/kotlin/com/moneydance/modules/features/starling/
  Main.kt                 FeatureModule; `md:file:opened` auto-import (~1.8s delay)
  api/                    Starling client, feed parser, scope check, JSON
  settings/               PATs, mappings, catalogue, import-on-open
  sync/                   SyncService, SyncEngine, FITID, Catalogue, TxnRouter
  ui/                     SecondaryDialog, mapping table, MdNotify, ImportStatus
src/main/java/.../sync/MdAccess.java
                          Java facade over the Moneydance model
src/main/resources/.../meta_info.dict
```

Keep the API client free of Swing. Keep Swing free of raw JSON.

## Moneydance constraints that agents get wrong

- Entry point extends `com.moneydance.apps.md.controller.FeatureModule`.
- `init()` runs at app start: GUI and data file may **not** exist yet. Register the feature there; open windows on `invoke()` or `md:file:opened`.
- Package the class as `com/moneydance/modules/features/starling/Main.class` plus `meta_info.dict`.
- **Do not create register `ParentTxn`s for import.** Staging is `OnlineTxn`; Moneydance’s converter creates the parent. After auto-add, we may change that unconfirmed parent’s split account so a Space movement is a transfer. Pending promote/delete may edit or `deleteItem` unconfirmed parents we tagged. Never `syncItem()` only on a split.
- Amounts are integer minor units in Moneydance; convert via the account’s `CurrencyType`.
- `SplitTxn.amount` is the wrong sign. Use `value` / `parentAmount` as documented by Infinite Kind.
- `minbuild` in `meta_info.dict` is the oldest MD **application** build we support (5100+). It is not `module_build`. Missing it → “Extension version too old.”

Reference implementations: https://github.com/TheInfiniteKind/moneydance_open  
DevKit / API: https://infinitekind.com/developer and https://infinitekind.com/dev/apidoc/index.html  
Sibling: https://github.com/dvdoug/moneydance-lunchflow

## UX bar for marketplace

- Settings: PAT list, **Add token** (scope check + one-time history walk), Remove token, **Refresh accounts**. Refresh is the live-list update (no second Test button).
- Account mapping: Starling name → Moneydance account + From date. Combo default **— not mapped —**. Persist on Import and window close, not a Save mappings button.
- **Import** and optional auto-import on `md:file:opened` (per-file checkbox, default **off**). Status bar + Help → Console. Never log the PAT.
- Help (**Setup guide**) opens [docs/user/setup.md](docs/user/setup.md) on GitHub. User-facing docs live in `docs/user/`. Update them in the same change as UI copy.

## Verification

There is no headless Moneydance. GitHub Actions (`.github/workflows/ci.yml`) runs `./gradlew test` on PRs. On master it also uploads a 90-day workflow artifact and, the first time a given `module_build` appears, a GitHub Release tagged `vN` with the unsigned `.mxt`. Do not retag or overwrite an existing `vN`. That does **not** exercise the Swing window or Import. Dependabot files weekly PRs for Actions and Gradle; merge when CI is green — not a `module_build` bump.

1. Unit-test the API client, feed parser, router, FITID / amount conversion **without** the Moneydance UI (mock HTTP).
2. Install the `.mxt` into a throwaway file. Owner may also alpha the main file after **File → Export Backup**. Agents must not request production data. Map, Import twice, confirm no extra blue dots.
3. Confirm the PAT is not present in logs or in any file under `dist/` except the user’s live data file.

## Doc map

| File | Audience |
| --- | --- |
| [README.md](README.md) | Short human pointer |
| [docs/user/](docs/user/README.md) | End-user setup, Moneydance steps, troubleshooting |
| [docs/product.md](docs/product.md) | Why this exists, user flow |
| [docs/architecture.md](docs/architecture.md) | Technical design |
| [docs/starling-api.md](docs/starling-api.md) | Personal API contract we code against |
| [docs/marketplace.md](docs/marketplace.md) | Infinite Kind listing |
| [CHANGELOG.md](CHANGELOG.md) | Notable changes per `module_build` |
| [SECURITY.md](SECURITY.md) | Secrets handling |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to work in the repo |
