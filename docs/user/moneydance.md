# Use the extension in Moneydance

Take a **File → Export Backup** before the first import on a file you care about.

## Install

1. Download the latest `.mxt` from [Releases](https://github.com/dvdoug/moneydance-starling/releases).
2. **Extensions → Manage Extensions → Add from File…** and choose that file.
3. You will see an unrecognized-signature warning until Infinite Kind list the extension in the store.

Needs Moneydance 2024 or newer. Installing a newer download **replaces** the old Starling Bank extension. Restarting without Add from File does not pick up a new build.

## Open the window and add the token

1. **Extensions → Starling Bank**.
2. Paste the long token into **Personal access token (PAT)**.
3. Click **Add token**.

The first save shows **Validating…** (permissions, then a one-time history read so older Spaces can appear). On success you get a labelled row such as *Douglas Wright (Personal)*.

**Remove token** only forgets it in this Moneydance file.

A joint or business Starling needs its **own** token (same ticks as [setup](setup.md)).

**Setup guide** in the window opens these pages on GitHub.

## Map accounts

The table has **Starling account**, **Import into**, and **From**. Default **Import into** is **— not mapped —**.

- **Current account** — map this if you want the register to match the main Starling feed (cards, salary, bills). Joint and business current accounts work the same way.
- **Spending Spaces** sit *inside* the current account. Leave them **— not mapped —** and purchases from that Space still land on the current account. Moving money from the current account *into* that Space is not imported (the money has not left the bank). Map to a subaccount you already created if you want a separate register. The extension does not create accounts.
- **A savings account** (Easy Saver and similar) is a **separate** Starling account, even if you keep it as a Moneydance subaccount of your current account. Money moved there **has left** the current account, so the current-account register still shows the outflow. Map the savings account to collect any Spaces you did not map separately. Map a Space (Home repairs, Service Charge, …) only when it should have its own register.

You can map a Starling savings account or Space to a **subaccount** of your Moneydance current account. That is a normal **Import into** choice.

Closed Spaces are marked **(archived)** and stay in the table under the account they belonged to.

**From** is how far back the next Import goes. New mappings default to the first of this month. Click the cell for a calendar. Clear it for all history Starling will return. After a successful Import, From only moves **forward**. Type an older date if you want more history.

Mappings save when you **Import** or close the window (X, Alt+F4, Escape, Close). There is no Save button.

## Import

Click **Import**.

New rows appear as **unconfirmed downloads** (a solid blue dot), the same Confirm / Merge process as a file from your bank:

- **Confirm** keeps the new row.
- **Merge** combines it with a matching row you already typed (for example a reminder). Merge keeps the **existing** description.

Pending card holds and upcoming Direct Debits show `[PENDING]` until they settle. The Starling app often keeps the tap time; Moneydance uses the **settlement** date so it lines up with a statement.

Progress is in the bottom status bar. Detail is in **Help → Console Window** (`starling:` lines). The token is never written there.

**Import when this file opens** is off until you tick it. Tick it only after mappings look right.

## Next

[If something looks wrong](troubleshooting.md).
