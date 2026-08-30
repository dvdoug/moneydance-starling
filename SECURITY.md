# Security

This extension reads the user’s Starling Bank transactions and writes them into a Moneydance data file. Treat it like financial software.

## Secrets

- The only secret in v1 is the user’s **Starling personal access token (PAT)**.
- The user creates it in the [Starling Developer Portal](https://developer.starlingbank.com/) and pastes it into **Extensions → Starling Bank**.
- **Never** put a token in source, Gradle properties committed to git, `meta_info.dict`, CI logs, or issue trackers.
- Do not echo the token in `AppDebug` / `System.err` / exception messages. Mask in the UI (`••••` plus last 4).
- Store it in the **open Moneydance data file** (already encrypted with the file password): `LocalStorage.put("starling.pat.{id}")` plus `cacheAuthentication`. Do not use a sidecar file or logs. Do not delete the data-file copy after writing the auth cache — that cache can be empty after a restart.

If a token leaks: the user revokes it in the Developer Portal and creates a new one (Starling cannot add scopes to an existing token). Document that in Settings errors.

## What this extension must never do

- Send payments, create payees, or store Starling login passwords. Access is read-only via a PAT the user created.
- Ship any Starling secret inside the `.mxt` (the MXT is a zip).
- Send Moneydance data *to* Starling or anywhere else. Outbound traffic is GET requests to `https://api.starlingbank.com/api/v2` only. No aggregator.
- Delete Moneydance transactions we did not create. Pending cleanup only deletes parents we tagged `starling:pending:`. Never delete reminder or typed rows, or posted `starling:` FITIDs. Never follow a split onto another account’s parent.

## Signing

- Local DevKit keys (`privkey*`) stay on the developer machine and are gitignored.
- Marketplace builds are signed by **The Infinite Kind** after audit. Do not ask users to disable signature checks.

## Reporting

Open a private report (GitHub security advisory once the repo is public, or contact the vendor listed in `meta_info.dict`: Doug Wright). Do not file a public issue that contains a live PAT or a data-file dump.
