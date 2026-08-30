# Architecture

Kotlin `FeatureModule`. Swing window. Starling Personal API (`https://api.starlingbank.com/api/v2`, `Authorization: Bearer`).

Settings live in the open Moneydance data file (`starling.pats`, `starling.pat.{id}`, `starling.mappings`, `starling.catalogue`). Tokens go through `LocalStorage` authentication cache as well as plain keys (same pattern as the Lunch Flow sibling).

First PAT save: `SourceLoader.walkHistory` chunks the main feed from `createdAt` and records `CATEGORY` counterparties. Refresh: live accounts/spaces stitched with that catalogue (`Catalogue.stitch`). Import: fetch mapped (and parent) category feeds, `TxnRouter.destination` chooses the Moneydance row, `SyncEngine` writes `OnlineTxn`s.
