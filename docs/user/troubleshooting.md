# If something looks wrong

## The Developer Portal will not accept my Starling app login

The [Developer Portal](https://developer.starlingbank.com/) is a separate account. Sign up there, then **Connect accounts** to link your bank account. See [setup](setup.md).

## Add token says a permission is missing

The message names the missing scopes (for example `space:read`). You cannot add scopes to an existing token. Create a **new** token with every scope in [setup](setup.md), then **Add token** again.

## The table has accounts but few Spaces

If **Add token** already succeeded, the required scopes are fine. You may have no live Spaces. Closed ones are marked **(archived)**. **Refresh accounts** updates what Starling lists as live and keeps archived rows already in the table.

## Import says up to date

The same transactions are not imported twice. **From** may have moved forward — type an older **From** and Import again. If it is not in the Starling app, it will not appear here.

## Money sent to Easy Saver still shows on the current account

A savings account is separate from the current account. If the current account is mapped, Moneydance **must** show the money leaving. Map Easy Saver (and its Spaces if you want their own registers) if you want the other side.

## Spending Space purchases on the current account

That Space is **— not mapped —**. Map it to a subaccount if you want those spends there.

## A transfer into a Space

Money from the current account into a mapped Space is **one** Moneydance transfer: it leaves the current account and arrives on the Space. Confirm the current-account download as a transfer to that Space if the Category column is not already the Space account. Do not Confirm it as ordinary income or spending — the Space register would then not show it as coming from the current account.

If only the Space is mapped, the credit lands there as a normal row.

## I changed Import into after importing

Old rows stay put. New Imports follow the new mapping.

## Pending and Merge

Pending holds are unconfirmed until they post. Moneydance uses the UK settlement date. If you Merged a hold into a reminder and the posted amount is the same, a later Import updates that row. If the amount changed, you get a new download to Merge.

## Wrong register or currency

**Import into** is which Moneydance account receives the row. Delete unconfirmed downloads (or undo), change the mapping, Import again. Currencies must match (usually GBP).

## Starling asked us to slow down

Personal tokens are limited to a few requests a second (and 1000 a day). A long first import waits and retries. If it still fails, wait a minute and Import again. Do not click Import repeatedly.

## The yellow Download button in the register

That is Moneydance’s own online-banking signup, not this extension. Ignore it. Starling uses the blue-dot confirmation panel.

## Still stuck

**Help → Console Window**, lines starting `starling:` (no token). [Open an issue](https://github.com/dvdoug/moneydance-starling/issues).
