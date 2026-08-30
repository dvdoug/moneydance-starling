# Create a Starling personal access token

The extension reads **your** Starling accounts. Starling issues a **personal access token (PAT)** in the [Developer Portal](https://developer.starlingbank.com/). It does not expire. Treat it like a password.

You need a Starling current account and a developer login. Link them in the portal first (Connect accounts), then create a token. Give it a name such as `Moneydance`.

The portal groups scopes under the headings below. Tick **only** the ones listed. Leave every other box in that heading off.

## Read Financial (View your financial information & transactions)

Turn **on**:

- `space:read`
- `transaction:read`
- `savings-goal:read` (optional; same active pots as `space:read`. It does **not** list archived Spaces or old goals)

Turn **off** everything else in this heading, including `balance:read`, `savings-goal-transfer:read`, statements, payees, and mandates.

`space:read` is how we list Spending Spaces **and** savings pots (names and balances). `transaction:read` is the transaction feed (including pending and upcoming).

`savings-goal-transfer:read` is only the **standing order** that tops a savings pot up (amount, next date). Transfers still appear in the transaction feed without it. Leave it off unless you want that schedule in the mapping table later.

## Edit Financial (Edit your financial information & transactions)

Turn **off** the whole heading. We never change Starling (no card controls, no deleting payees, no editing transaction tags).

## Transact Financial (Make financial transactions on your behalf)

Turn **off** the whole heading. We never send payments or create payees.

## Read Personal (View your personal information)

Turn **on**:

- `account:read`
- `account-list:read`
- `account-holder-name:read`
- `account-holder-type:read`
- `customer:read`

Turn **off** everything else in this heading, including `account-identifier:read`, `card:read`, and `address:read`.

`account:read` and `account-list:read` together list your accounts (GBP, euro, and so on). The name, type, and `customer:read` boxes are so the extension can label the token (for example *Douglas (Personal)*).

`card:read` is **card hardware**: last digits, whether the physical/virtual card is on, ATM / online / wallet switches. Card **payments** come from `transaction:read`, not this. Leave it off.

## Edit Personal (Edit your personal information)

Turn **off** the whole heading. We never change your address, email, or profile image.

## Joint or business as well as personal

Each Starling account-holder type is a **separate** token (personal, joint, business, sole trader). Create one PAT per type you want in this Moneydance file, with the same ticks.

## After you copy the token

Paste it into the extension as **Personal access token (PAT)** and click **Add token**. The first save shows **Validating…** while it reads your history so archived Spaces can appear in the mapping table. That walk happens once per token.

Do not email the token or put it in a screenshot.

If **Refresh accounts** later shows no Spaces, `space:read` was left off. If it shows no accounts at all, check `account:read` and `account-list:read`. If transactions are missing, check `transaction:read`.
