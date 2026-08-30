# If something looks wrong

## Add token says the token is missing a permission

Starling named the missing ticks (for example `space:read`). You cannot add boxes to an existing token. Create a **new** PAT in the Developer Portal with every box in [setup](setup.md), then Add token again.

## Refresh accounts shows no Spaces

Turn on `space:read` (new token). Archived Spaces only appear after the first **Validating…** walk, not from Refresh alone.

## Import says up to date but you expected new rows

- Already-imported posted transactions are skipped on purpose (they keep a hidden id). Importing the same month twice should not create duplicates.
- **From** may have walked forward after the last success. Type an older From and Import again if you want a longer window.
- Check the transaction in the Starling app first. If it is not there, Moneydance cannot invent it.

## Easy Saver £100 still on the current account

Easy Saver is a **different** Starling account. If that pot is not mapped, the current account **must** show the money leaving. Map the pot to a Moneydance account if you want it as a movement between accounts.

## Spending Space Tesco on the current account

That Space is **— not mapped —**. Map it if you want those spends on a subaccount.

## I remapped a Space after importing

History does not move. New imports follow the new mapping. Old rows stay on the account they landed on.

## Pending card holds

They show as `[PENDING]` until they post. The app may still show the original tap time; the register uses the settlement date after it posts. If you **Merged** the pending row into a reminder, merge the settled download too. The reminder’s name stays.

## Wrong Moneydance account

That is the **Import into** column in this extension.

If a row landed in the wrong register, delete those unconfirmed downloads (or undo) and change **Import into**, then Import again.

## Amounts or dates look off

Card payments use tap time while pending and settlement date once posted. Faster Payments are usually the same calendar day for both. The extension does not flip signs; Starling `OUT` is money leaving.

## Currency mismatch

The Moneydance account’s currency must match the Starling account (usually GBP).

## HTTP 429 / slow down

Personal tokens are limited to 5 requests a second and 1000 a day. Wait and try again.

## The yellow Download button in the register

That is Moneydance’s own online-banking signup, not this extension. Ignore it. Confirm and Merge for Starling rows use the blue-dot confirmation panel.

## Still stuck

Open **Help → Console Window** and look for `starling:` lines (no token is logged). You can also [open an issue](https://github.com/dvdoug/moneydance-starling/issues).
