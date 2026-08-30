# Use the extension in Moneydance

Install `starling.mxt` (Extensions → Manage Extensions → Add from file). Open **Extensions → Starling Bank**.

## Add a personal access token

Paste the token from the [setup guide](setup.md). Click **Add token**. The first time, a **Validating…** bar runs while the extension reads your history so archived Spaces can appear in the table. That walk happens once per token.

You can add another token for a joint or business Starling (each is a separate PAT).

## Map accounts

Each Starling current account, Easy Saver, Space, and savings pot is a row.

- **Import into** is the Moneydance account (including subaccounts you already created).
- Choose **— not mapped —** to skip that row.
- **From** is the first date to import.
- A Space that Starling no longer lists as active is marked **(archived)**. If you mapped it, that mapping is kept.

You do not have to map Spaces. If you only map your current account, spending looks like a Starling CSV: Tesco on the current account. Money you sent to Easy Saver still leaves the current account (it is a different Starling account).

If you map a Space or pot to a Moneydance account, later top-ups and spends for that pot go there instead.

## Import

**Import** downloads new rows as unconfirmed (blue dots). Confirm or Merge as you would any Moneydance download. Card holds and upcoming Direct Debits show `[PENDING]` until they settle.

Tick **Import when this file opens** if you want that to happen automatically.

Never put the token in an email or screenshot. Remove token removes it from this data file.
