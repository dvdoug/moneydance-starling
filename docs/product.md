# Product

## Problem

Moneydance can download transactions automatically in two official ways:

1. **OFX Direct Connect** — free, but many banks have dropped it.
2. **Moneydance+** — Plaid aggregator, optional subscription, **US and Canada only**.

The sibling [Lunch Flow extension](https://github.com/dvdoug/moneydance-lunchflow) covers UK/EU aggregators (Amex and similar) for a monthly fee. **Starling Bank** already gives account holders a **free personal API**, including Spaces. This extension talks **only** to Starling from the user’s computer. Paying Lunch Flow to pull Starling is unnecessary.

## Goal

Ship an unofficial Moneydance extension, authored by **Doug Wright**, that is **the same import experience as the Lunch Flow extension**, using **Starling’s personal API** instead of Lunch Flow:

- User pastes one or more **personal access tokens (PATs)** in Settings (never hardcoded).
- User maps Starling accounts and Spaces to Moneydance accounts.
- User imports (manually, and optionally when the data file opens).
- **Posted** transactions import once (FITID) via Moneydance’s download converter. **Pending** authorisations and **upcoming** Direct Debits appear as unconfirmed rows (blue dot), with no extra Description prefix. If a dropped pending uniquely matches a new posted row on date, merchant, and exact amount, **update that row in place** (settled payee and memo). Otherwise delete our pending parent and add a new posted **download**.
- Quality is high enough to list in the Moneydance extension directory.

## What this is not

- Not a Starling Bank product. Not Infinite Kind. Unaffiliated with both.
- Not TPP / Open Banking for *other people’s* accounts. PAT only, own data.
- Not a replacement for the Lunch Flow extension (keep that for Amex and other institutions).

## User journey

1. User has Moneydance and a Starling current account (and optionally Easy Saver, joint, business).
2. In the [Starling Developer Portal](https://developer.starlingbank.com/) (a separate account from the banking app): **Connect accounts**, then create a PAT with the scopes in [user/setup.md](user/setup.md). Treat the token like a password. Starling cannot add scopes to an existing token — missed scopes means a **new** token.
3. In Moneydance: install the extension, open **Extensions → Starling Bank**.
4. Paste token → **Add token**. On success we **validate scopes**, then walk history once (**Validating…**) so archived Spaces appear in the table.
5. Map each Starling row to a Moneydance **bank** account (including subaccounts you already created). Choose **— not mapped —** to skip that Space. Default for the current account is statement-shaped: Spending Space merchants land on the parent; Easy Saver is a **different** Starling account, so money sent there still leaves the current account unless that Space is mapped.
6. Choose a **From** date (default: first of the current month). After a successful import, From becomes `max(current From, last posted − 7 days)` — it never moves earlier than you set. Fetch still looks back to an open hold if that is earlier.
7. **Import**. Mappings also save when the window closes. New rows appear as **unconfirmed downloaded transactions** (solid blue dot). Confirm or merge them in the register the same way as a file from your bank. Pending holds stay unconfirmed until they post or vanish.
8. Later imports (including auto on open) fetch last posted − 7 days (or the oldest open hold) and **always refresh the current pending set**. FITIDs skip anything already in the register. **Refresh accounts** does **not** re-walk all history.

## Success criteria

A reviewer at Infinite Kind can:

- Install from a signed `.mxt` with no extra JARs or scripts.
- Enter their own PAT; nothing in the binary is a secret.
- Map two accounts, sync twice, and see each **posted** bank transaction once. Pending rows may appear, then disappear and be replaced by a posted row with a new id.
- Understand errors (bad token, missing scope, currency mismatch) without a console dump.
- Find a short in-app explanation of how to create a PAT (GitHub setup guide).

## Out of scope

- Sending payments or changing anything at the bank.
- Becoming a Starling TPP that onboards other customers.
- Auto-creating Moneydance subaccounts for Spaces.
- Mobile Moneydance. This is a desktop extension.
