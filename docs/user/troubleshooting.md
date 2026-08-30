# If something looks wrong

## Add token says a permission is missing

The message names the missing ticks (for example `space:read`). You **cannot** add boxes to that token. Create a **new** token with every required box in [setup](setup.md), then **Add token** again.

## The table has accounts but few Spaces

If **Add token** already succeeded, the required ticks are fine. You may have no live Spaces. Closed ones are marked **(archived)** after the first **Validating…**. **Refresh accounts** only updates what Starling lists as live, and keeps archived rows you already found.

## Import says up to date

The same transactions are not imported twice. **From** may have moved forward — type an older **From** and Import again. If it is not in the Starling app, it will not appear here.

## Money sent to Easy Saver still shows on the current account

A savings account is separate from the current account. If the current account is mapped, Moneydance **must** show the money leaving. Map Easy Saver (and its Spaces if you want their own registers) if you want the other side.

## Spending Space purchases on the current account

That Space is **— not mapped —**. Map it to a subaccount if you want those spends there.

## I changed Import into after importing

Old rows stay put. New Imports follow the new mapping.

## Pending, Merge, dates

`[PENDING]` until it posts. Moneydance uses the settlement date. If you Merged the pending row into a reminder, merge the settled download too. The reminder’s name stays.

## Wrong register or currency

**Import into** is which Moneydance account receives the row. Delete unconfirmed downloads (or undo), change the mapping, Import again. Currencies must match (usually GBP).

## Starling asked us to slow down

Personal tokens are limited to a few requests a second (and 1000 a day). A long first import waits and retries. If it still fails, wait a minute and Import again. Do not click Import repeatedly.

## The yellow Download button in the register

That is Moneydance’s own online-banking signup, not this extension. Ignore it. Starling uses the blue-dot confirmation panel.

## Still stuck

**Help → Console Window**, lines starting `starling:` (no token). [Open an issue](https://github.com/dvdoug/moneydance-starling/issues).
