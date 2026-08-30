# Use the extension in Moneydance

Take a **File → Export Backup** before the first import on a file you care about.

## Install

1. Download the latest `.mxt` from [Releases](https://github.com/dvdoug/moneydance-starling/releases).
2. **Extensions → Manage Extensions → Add from File…** and choose that file.
3. Accept the unrecognized-signature warning until Infinite Kind list the extension in the store.

Requires Moneydance 2024 or newer. Installing a newer file with the same id replaces the old one. Restarting Moneydance without Add from File does not pick up a new build.

## Open the window and add a token

1. **Extensions → Starling Bank**.
2. Paste the personal access token into **Personal access token (PAT)**.
3. Click **Add token**.

The first save shows **Validating…** while the extension checks permissions and reads your history so archived Spaces can appear in the table. That walk happens **once per token**.

You should see one row per Starling current account, Easy Saver, Space, and savings pot. Saved mappings appear as soon as the window opens; **Refresh accounts** updates the **live** list and keeps archived rows you already discovered.

**Remove token** forgets the selected token stored in this data file.

A joint or business Starling is a **second** token (same ticks as [setup](setup.md)).

## Map accounts and choose From

Each Starling row has a Moneydance account menu and a **From** date.

- Map **bank** accounts (including subaccounts you already created).
- **Import into** is which Moneydance account receives the row. Choose **— not mapped —** to skip that Space.
- You do not have to map Spaces. If you only map the current account, spending looks like a Starling CSV: Tesco on the current account. Easy Saver is a **different** Starling account, so money you send there still leaves the current account unless that pot is mapped.
- A Space Starling no longer lists as active is marked **(archived)**. If you mapped it, that mapping is kept.
- **From** is how far back the *next* Import asks Starling to look.
  - Default for a new mapping is the first day of this month.
  - After a successful Import, From only moves **forward**. Later Imports then use about a 31-day overlap. Pick an older date any time you want a longer backfill.

Mappings are saved when you **Import** or when you close the window (the X, Alt+F4, Escape, or Close). There is no separate Save button.

Create Moneydance subaccounts yourself if you want a Space as its own register. The extension does not invent accounts.

## Import

Click **Import**.

New rows appear in the Moneydance register as **unconfirmed downloads** (a solid blue dot). That is the same Confirm / Merge process as when you import a file you downloaded from your bank:

- **Confirm** keeps the new row.
- **Merge** combines it with a matching row you already typed (for example a reminder). Merge keeps the **existing** description.

Pending card holds and upcoming Direct Debits show with a `[PENDING]` prefix until they settle. The Starling **app** often keeps showing the tap time after a card settles; Moneydance uses the **settlement** date so it lines up with a statement.

The bottom status bar shows progress. More detail is in **Help → Console Window** (lines starting `starling:`). The token is never written there.

**Import when this file opens** is **off** until you tick it. Tick it only after mappings look right.

## Next

If a row is missing or the list of Starling accounts is wrong, see [troubleshooting](troubleshooting.md).
