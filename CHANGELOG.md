# Changelog

## 16 - 2026-08-31

Pending holds no longer show `[PENDING]` on the Description. When a hold settles and matches, that row gets the posted payee and memo.

## 15 - 2026-08-31

Setup guide matches the Developer Portal (new account, then Connect accounts). Listing copy: Spaces, and no aggregator.

## 14 - 2026-08-30

Pending holds keep the merchant for category matching; Confirm no longer freezes them. Lookback is last posted minus 7 days, or the oldest open hold. Extension icon is Starling’s site mark.

## 13 - 2026-08-30

A current-account ↔ Space transfer is counted on both mapping rows. Import status sits under the table instead of a thin strip in the header.

## 12 - 2026-08-30

Calendar dates use UK local time, not UTC. BACS credits that Starling stamps at 23:01 UTC land on the next morning in Britain.

## 11 - 2026-08-30

Stop deleting the download rows we just added. Import was reporting “added 21” then pruning them before the register could show them.

## 10 - 2026-08-30

Space movements use the download/Confirm path again, then the other side is pointed at the mapped Space. v8–v9 wrote transfers the register never showed.

## 9 - 2026-08-30

Space transfers appear in the register immediately. v8 saved them as unconfirmed without a download row, so they did not show.

## 8 - 2026-08-30

Money moved between a mapped current account and a mapped Space is one Moneydance transfer, visible on both registers. If a matching transfer already exists, Import tags it instead of adding a second row.

## 7 - 2026-08-30

Do not import the Space-side of a transfer when the current account is mapped. Confirm the current-account row as a transfer instead.

## 6 - 2026-08-30

Honour Starling rate limits: slower request spacing, retry on HTTP 429, and do not fetch years before the account opened.

## 5 - 2026-08-30

Mapping hint and docs say Space, not pot. Moving money into an unmapped Spending Space is not imported as spending.

## 4 - 2026-08-30

Dropped the unused `savings-goal:read` API; `space:read` is enough.

## 3 - 2026-08-30

Grouped mapping table (accounts, then Spaces). Unmapped savings Spaces fold into a mapped savings account. Date picker for From. Clearer setup and troubleshooting.

## 2 - 2026-08-30

Fixed Moneydance refusing the extension without `minbuild`. Named missing PAT scopes on Add token. Setup guide is the GitHub user doc. Mapping skip is **— not mapped —**.

## 1 - 2026-08-30

First release. Import Starling current accounts, Easy Saver, and Spaces with a personal access token. Archived Spaces discovered on first token save stay in the mapping table.
