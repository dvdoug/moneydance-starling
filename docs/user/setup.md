# Create a Starling token

You are not becoming a developer. Starling’s [Developer Portal](https://developer.starlingbank.com/) is where they issue a **personal access token**: a long password so this unofficial extension can **read** your accounts. It never sends payments. Treat the token like online banking.

## 1. Open the portal

Sign in with the **same Starling login** as the app. **Connect accounts** so your bank account is linked to the developer login, then create a personal access token. Name it `Moneydance`.

## 2. Tick only these boxes

**Read Financial**

- `space:read`
- `transaction:read`

**Read Personal**

- `account:read`
- `account-list:read`
- `account-holder-name:read`
- `account-holder-type:read`
- `customer:read`

Leave **Edit Financial**, **Transact Financial**, and **Edit Personal** entirely off. Leave `card:read` off.

`space:read` lists **active** Spaces. Closed ones can still show in Moneydance after the first **Validating…** when you add the token.

## 3. Copy the token once

Copy it. Do not screenshot or email it.

Starling **cannot add ticks to a token you already made**. Wrong boxes means create a **new** token. If you miss a box, **Add token** in Moneydance will name it.

## 4. Joint or business

Personal, joint, and business are separate tokens. Use the same ticks on each.

## Next

[Use the extension in Moneydance](moneydance.md).
