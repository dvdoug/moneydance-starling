# Create a Starling token

You are not becoming a software vendor. Starling’s [Developer Portal](https://developer.starlingbank.com/) is a **separate website** from the banking app. It issues a **personal access token**: a long password so this unofficial extension can **read** your accounts and Spaces. It never sends payments. Treat the token like online banking.

The token stays in **your** Moneydance file. This extension talks **only** to Starling (`api.starlingbank.com`) from your computer. There is no aggregator or other company in the middle.

You need two logins:

1. Your **Starling bank** account (the app you already use).
2. A **Developer Portal** account (new). Those are not the same sign-in. Linking them is a one-off step Starling calls **Connect accounts**.

Skip anything about registering an OAuth app, the sandbox, or becoming a Marketplace provider. Personal access is enough.

## 1. Create a Developer Portal account

Open [developer.starlingbank.com](https://developer.starlingbank.com/) and **sign up**. Use an email and password for the portal — not “log in with the Starling app”.

## 2. Connect your bank account

Once you are in the portal, **Connect accounts** and follow Starling’s prompts (they will ask you to approve access to the bank account you already have). That is the only reason linking exists: the portal login does not know which customer account is yours until you connect it.

When it has worked, you can create tokens for **your** data.

## 3. Create a personal access token

Give it a name you will recognise, such as `Moneydance`. Tick **only** these boxes:

**Read Financial**

- `space:read` — lists Spaces (spending Spaces and savings Spaces). This extension supports both, including nested Spaces on Easy Saver.
- `transaction:read`

**Read Personal**

- `account:read`
- `account-list:read`
- `account-holder-name:read`
- `account-holder-type:read`
- `customer:read`

Leave **Edit Financial**, **Transact Financial**, and **Edit Personal** entirely off. Leave `card:read` off.

The token does **not** expire. Starling **cannot add ticks** to a token you already made. Wrong boxes means create a **new** token. If you miss a box, **Add token** in Moneydance will name it.

`space:read` lists **active** Spaces. Closed ones can still show in Moneydance after the first **Validating…** when you add the token.

## 4. Copy the token once

Copy it. Do not screenshot or email it. You will paste it into Moneydance next.

## 5. Joint or business

Personal, joint, and business are separate Starling **customer** accounts. Connect the one you want, then create a token with the **same ticks**. Repeat if you have another holder.

## Next

[Use the extension in Moneydance](moneydance.md).
