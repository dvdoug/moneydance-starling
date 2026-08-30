# Troubleshooting

**No accounts after Add token.** The token is missing `account:read` and `account-list:read`, or Starling rejected it. Create a new PAT; you cannot add scopes to an old one.

**No Spaces in the table.** Turn on `space:read`. Archived Spaces only appear after the first **Validating…** walk (Add token), not from Refresh alone.

**Refresh is missing an old Space.** Refresh only asks Starling for **active** Spaces and reattaches anything already in this file’s catalogue. The first token save is what finds archived ones.

**Amounts backwards or dates off.** Card payments use the tap time while pending and the settlement date once posted. The Starling app often keeps showing the tap time after it settles. Statements use the settlement date. Faster Payments usually have the same calendar day for both.

**Easy Saver £100 still on the current account.** Easy Saver is a different Starling account. If that pot is not mapped, the current account **must** show the money leaving. Map the pot to a Moneydance account if you want it as a transfer.

**Spending Space Tesco on the current account.** That Space is not mapped. Map it if you want those spends on a subaccount.

**I remapped a Space after importing.** History does not move. New imports follow the new mapping. Old rows stay on the account they landed on.

**HTTP 429 / slow down.** Personal tokens are limited to 5 requests a second and 1000 a day. Wait and try again.

**Currency mismatch.** The Moneydance account’s currency must match the Starling account (usually GBP).
