# Starling Bank for Moneydance

Unofficial [Moneydance](https://infinitekind.com/moneydance) extension that imports your Starling Bank transactions using a [personal access token](https://developer.starlingbank.com/). Not affiliated with Starling Bank or The Infinite Kind.

## Use it

1. Create a PAT in the Starling Developer Portal. Required permissions are listed in [docs/user/setup.md](docs/user/setup.md).
2. In Moneydance, Extensions → Starling Bank. Paste the token. The first save reads your history once (progress bar) so archived Spaces can appear in the mapping table.
3. Map accounts (and any Spaces you want as their own Moneydance accounts). Import.

Pending card payments and upcoming Direct Debits show as `[PENDING]` until they settle.

## Build

JDK 21. `./gradlew test`. `./gradlew starling` builds a signed `dist/starling.mxt` if you have local signing keys (`userconfig/`).

See [AGENTS.md](AGENTS.md) if you are changing the code.
