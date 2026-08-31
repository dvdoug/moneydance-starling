# Create a Starling personal access token

Work through this **before** you paste anything into Moneydance. Starling issues the token in the [Developer Portal](https://developer.starlingbank.com/). This unofficial extension uses it to **read** your accounts and Spaces. It never sends payments.

The Developer Portal is a **separate** login from the Starling banking app. After you sign up, **Connect accounts** links your existing bank account so you can create a personal access token. You do not need to register an OAuth application, use the sandbox, or apply as a Marketplace provider.

The token stays in your Moneydance file. The extension talks only to Starling (`api.starlingbank.com`).

## 1. Sign up for the Developer Portal

Open [developer.starlingbank.com](https://developer.starlingbank.com/) and create an account.

## 2. Connect accounts

Use **Connect accounts** and complete Starling’s approval. Until this is done, you cannot create a personal access token for your live account.

## 3. Create a token

Give it a name such as `Moneydance`. Enable only these **scopes**:

**Read Financial**

- `space:read`
- `transaction:read`

**Read Personal**

- `account:read`
- `account-list:read`
- `account-holder-name:read`
- `account-holder-type:read`
- `customer:read`

Leave **Edit Financial**, **Transact Financial**, and **Edit Personal** off. Leave `card:read` off.

The token does not expire. Starling cannot add scopes to an existing token. If **Add token** in Moneydance reports a missing scope, create a **new** token with every scope above.

Treat the token like a password. Do not email it or put it in a screenshot.

## 4. Joint or business

Personal, joint, and business are separate Starling customer accounts. Connect the one you want, then create a token with the same scopes.

## Next

[Use the extension in Moneydance](moneydance.md).
