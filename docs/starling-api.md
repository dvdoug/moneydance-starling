# Starling personal API

This extension uses **only** the personal API (own-account PAT). Official docs: https://developer.starlingbank.com/docs

Do not call Payment Services, Open Banking TPP, or write/payment endpoints.

## Setup the user must do

1. Create a **Developer Portal** account (not the banking-app login). **Connect accounts** to link the existing Starling customer account.
2. Create a personal access token. Tick the scopes in [user/setup.md](user/setup.md). Starling cannot add scopes to an existing token. The token does not expire.
3. Paste the token into Moneydance (**Add token**). We probe the endpoints and name any missing scopes.

Joint / business / sole trader is a **separate** PAT (separate account holder). Same ticks.

## Contract we code against

**Base URL:** `https://api.starlingbank.com/api/v2`

**Auth:**

```http
Authorization: Bearer <personal access token>
Accept: application/json
```

**Limits:** 5 requests per second, 1000 per day. We sleep ~400 ms between calls and honour `Retry-After` on 429.

### `GET /accounts`

Lists accounts for this holder. Typical: `PRIMARY` “Personal” (GBP) plus `SAVINGS` “Easy Saver”. Optional EUR.

```json
{
  "accounts": [
    {
      "accountUid": "uuid",
      "accountType": "PRIMARY",
      "defaultCategory": "uuid",
      "currency": "GBP",
      "name": "Personal",
      "createdAt": "2019-03-11T16:50:01.000Z"
    }
  ]
}
```

`defaultCategory` is the main feed. Scopes: `account:read` and `account-list:read`.

### `GET /account/{accountUid}/spaces`

**Active** savings goals and spending Spaces only.

```json
{
  "savingsGoals": [{ "savingsGoalUid": "uuid", "name": "Home repairs", "state": "ACTIVE" }],
  "spendingSpaces": [{ "spaceUid": "uuid", "name": "Bills", "state": "ACTIVE" }]
}
```

Scope: `space:read`. Archived Spaces are **not** listed. We discover them from `CATEGORY` counterparties on the main feed during the first PAT walk. Do not call `/savings-goals` (`savings-goal:read`); it is the same active list and does not return archived ids.

### `GET /feed/account/{accountUid}/category/{categoryUid}/transactions-between`

Query: `minTransactionTimestamp`, `maxTransactionTimestamp` (ISO instants). Both required.

A window longer than about **180 days** returns `400` `QUERY_EXCEEDING_MAX_TIME_RANGE`. Chunk.

Feed item (abridged):

| Field | Use |
| --- | --- |
| `feedItemUid` | Identity (always present) |
| `amount.minorUnits` + `direction` | Unsigned amount; `OUT` → negative cashflow |
| `status` | See below |
| `transactionTime` / `settlementTime` | Pending uses transaction; posted uses settlement. Calendar day is Europe/London, not UTC. |
| `counterPartyName` / `reference` | Payee / memo |
| `source` | `MASTER_CARD`, `DIRECT_DEBIT`, `ON_US_PAY_ME`, `INTERNAL_TRANSFER`, … |
| `counterPartyType` / `counterPartyUid` | Current-account Space movements: `CATEGORY` + the Space uid. Space-side of the same move: `CUSTOMER` + holder uid (not a category). |

Statuses we import as pending: `PENDING`, `UPCOMING`, `RETRYING`.  
Posted: `SETTLED` (and money-back **sources** such as `FASTER_PAYMENTS_REVERSAL`, `MASTERCARD_CHARGEBACK`, `DIRECT_DEBIT_DISPUTE`).  
Skip: `DECLINED`, `ACCOUNT_CHECK`, `UPCOMING_CANCELLED`, `REVERSED` with no `settlementTime`.

Same-account Spending Space funding is usually `INTERNAL_TRANSFER` + `CATEGORY`. Current account → savings Spaces is `ON_US_PAY_ME`: `CATEGORY` + Space uid on the current-account feed, `CUSTOMER` + holder uid on the Space feed. That money **does** leave the current account. Joint and business current accounts use the same types.

### Holder

`GET /account-holder` (`account-holder-type:read`, `customer:read`)  
`GET /account-holder/name` (`account-holder-name:read`)

Used to label the PAT (e.g. `Douglas Wright (Personal)`).

## Errors to treat as user-facing

| HTTP | Meaning |
| --- | --- |
| 401 | Invalid/revoked token. Create a new PAT. |
| 403 `insufficient_scope` | Missing ticks. Parse `Required:` / `Granted:`. Create a **new** token. |
| 404 | Account or Space uid gone. |
| 429 | Rate limit. Wait; honour `Retry-After` if present. |

Never put the token in error text.

## Live-test checklist (do not commit the token)

Hit `GET /accounts` with a real PAT and keep **redacted** fixtures under `src/test/resources`. Synthetic JSON is better in git. Scratch explorers live in `scratch/` (gitignored).
