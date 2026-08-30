# Agent notes — moneydance-starling

Moneydance Kotlin extension. Imports the user’s **own** Starling Bank data with a personal access token (PAT). Not a TPP. Unofficial; not Starling Bank or Infinite Kind.

Extension ID `starling`. Package `com.moneydance.modules.features.starling`. Artifact `dist/starling.mxt`. `module_build` in `meta_info.dict` is the version.

## Product rules (locked)

- Multiple PATs. First save of a PAT walks that token’s history once (**Validating…** progress), discovers Spaces including archived ones from `CATEGORY` counterparties, persists a catalogue. **Refresh** only hits live `/accounts` + `/spaces` (+ `/savings-goals` if scoped) and stitches onto the catalogue. Missing from live list → **(archived)**; mapping kept.
- Mapping id is `accountUid:categoryUid`. Unmapped Spending Space (same Starling account): skip `INTERNAL_TRANSFER` on the parent; merchants from that Space land on the parent. Unmapped savings pot on a **different** Starling account (Easy Saver): Personal `ON_US_PAY_ME` / `CATEGORY` stays as outflow on Personal; do not import the pot feed.
- Pending: `PENDING`, `UPCOMING`, `RETRYING`. Skip `DECLINED`, `ACCOUNT_CHECK`, `UPCOMING_CANCELLED`. Skip `REVERSED` with no `settlementTime`. Dates: `transactionTime` while pending, `settlementTime` when settled.
- Import via `OnlineTxn` + `showDownloadedTxns`. Do not create `ParentTxn`s. FITID `starling:{categoryUid}:{feedItemUid}` / `starling:pending:…`. Pending set-reconcile unconfirmed only.
- `transactions-between` max window ~180 days (`QUERY_EXCEEDING_MAX_TIME_RANGE`). Chunk. PAT rate limit 5 rps / 1000 per day.

## PAT scopes (user docs)

Read Financial: `space:read`, `transaction:read`, optional `savings-goal:read`.
Read Personal: `account:read`, `account-list:read`, `account-holder-name:read`, `account-holder-type:read`, `customer:read`.

Never hardcode tokens. Never log them.

## Build

JDK 21, bytecode 17. `gradlew test`. `gradlew starling` signs locally (`dist/starling.mxt`). CI packages unsigned `.mxt`.

## Git

- **Commit and push every iteration**, even when `module_build` does not change (docs, polish, tests). Do not leave a day’s work only in the working tree. `git push origin` the current branch after each commit (this repo: `master`).
- Never commit keys, `userconfig/`, `lib/*.jar`, `scratch/`, or `*.mxt`.
- User-facing setup is GitHub-rendered markdown in `docs/user/`. The in-app **Setup guide** button opens that URL. Do not replace it with a bundled HTML viewer.
