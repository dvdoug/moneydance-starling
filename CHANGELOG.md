# Changelog

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
