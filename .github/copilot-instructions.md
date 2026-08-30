# GitHub Copilot instructions

Follow **AGENTS.md** at the repository root. That file is the source of truth for this project.

Short version:

- This is a **Moneydance Kotlin extension** by Doug Wright that imports data from **Starling Bank’s personal API**. Unofficial; not Starling Bank or Infinite Kind.
- Extension ID: `starling`. Package: `com.moneydance.modules.features.starling`.
- **Never hardcode PATs.** Settings UI only.
- Personal API only (`https://api.starlingbank.com/api/v2`, Bearer token). Not TPP OAuth.
- Import via `OnlineTxn` + `showDownloadedTxns`. Do not create `ParentTxn`s. Posted FITID `starling:{categoryUid}:{feedItemUid}`. Pending set-reconcile unconfirmed `starling:pending:…` only.
- Swing on the EDT; HTTP off the EDT. Release `AccountBook` listeners on file close.
- Commit and push every iteration. Setup guide is GitHub `docs/user/setup.md`.
