# GitHub Copilot instructions

Follow **AGENTS.md** at the repository root.

- Moneydance Kotlin extension. Extension ID `starling`. Package `com.moneydance.modules.features.starling`.
- Personal access token only. Never hardcode or log tokens.
- Import via `OnlineTxn` + `showDownloadedTxns`. FITID `starling:{categoryUid}:{id}`.
- Chunk `transactions-between` at 180 days.
