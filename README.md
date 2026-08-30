# Starling Bank for Moneydance

An **unofficial** Moneydance extension by **Doug Wright**. It imports transactions from [Starling Bank](https://www.starlingbank.com/) into Moneydance using Starling’s personal API — the same job as downloading a CSV, without the monthly fee of an aggregator.

**This is not a Starling Bank product.** The author is not affiliated with Starling Bank or The Infinite Kind (makers of Moneydance). You need your own Starling account and a personal access token from the [Developer Portal](https://developer.starlingbank.com/).

```text
Your Starling account  →  Starling personal API  →  this extension  →  Moneydance
```

The extension never sends payments. It only reads accounts, Spaces, and transactions you already own.

For other banks (Amex, and so on) use the sibling [Lunch Flow extension](https://github.com/dvdoug/moneydance-lunchflow).

## Install and use

Full instructions (token scopes, mapping, Import): **[docs/user](docs/user/README.md)**.

Requires **Moneydance 2024** or newer. Download a build from [Releases](https://github.com/dvdoug/moneydance-starling/releases), then **Extensions → Manage Extensions → Add from File…**. You will see an unrecognized-signature warning until Infinite Kind list the extension.

Take a **File → Export Backup** before the first import on a file you care about.

## Changes and privacy

- [CHANGELOG.md](CHANGELOG.md) — what each version changed
- [SECURITY.md](SECURITY.md) — how the token is stored

## Building from source

See [CONTRIBUTING.md](CONTRIBUTING.md). Short version: JDK 21+, `gradlew starling` → `dist/starling.mxt`.

## License

[MIT](LICENSE), © Doug Wright. Moneydance is a trademark of The Infinite Kind. Starling Bank is a trademark of Starling Bank Limited. This extension is not affiliated with Starling Bank or The Infinite Kind.
